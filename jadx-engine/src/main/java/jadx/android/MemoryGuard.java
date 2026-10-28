package jadx.android;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import jadx.core.dex.attributes.AFlag;
import jadx.core.dex.nodes.ClassNode;
import jadx.core.dex.nodes.ProcessState;

/**
 * Android-only memory control for the save phase of a decompile.
 * <p>
 * Upstream JADX targets a desktop JVM: before a class is generated, every dependency is loaded and fully processed,
 * and a dependency that was already generated (and unloaded) is processed again and then stays loaded for good.
 * Over a whole APK the loaded set only grows, and the classes with the most dependencies are scheduled last, so the
 * heap fills up towards the end of the run. Here a class that is already generated, loaded again only as somebody's
 * dependency, and no longer used by any running generation is unloaded again once the heap is under pressure.
 * Nothing changes while the heap has room; a released class is simply reloaded the next time a class needs it, the
 * same path upstream takes for a class it unloaded after generation.
 * <p>
 * Also finds and interrupts JADX worker threads. After an {@link OutOfMemoryError} the upstream executor stops
 * taking new batches but lets running ones finish, so those threads keep their classes alive while the caller
 * already starts the next attempt.
 */
public final class MemoryGuard {
	private static final double PRESSURE_RATIO = 0.70;
	private static final double ECONOMY_RATIO = 0.30;
	private static final double GATE_RATIO = 0.85;
	private static final long GATE_STEP_MS = 10L;
	private static final long GATE_MAX_WAIT_MS = 3000L;
	private static final long SWEEP_GAP_NANOS = 100_000_000L;
	private static final String WORKER_PREFIX = "jadx-task-";
	private static final int THREAD_SLACK = 32;

	private static final Set<ClassNode> GENERATED = ConcurrentHashMap.newKeySet();
	private static final Set<ClassNode> CANDIDATES = ConcurrentHashMap.newKeySet();
	private static final Map<ClassNode, Integer> PINS = new ConcurrentHashMap<>();
	private static final AtomicBoolean SWEEPING = new AtomicBoolean(false);
	private static final AtomicInteger RELEASED = new AtomicInteger(0);
	private static final AtomicInteger ACTIVE = new AtomicInteger(0);

	private static volatile boolean enabled;
	private static volatile boolean economy;
	private static volatile long lastSweepNanos;

	private MemoryGuard() {
	}

	/**
	 * Starts a run. In economy mode the heap threshold is much lower and classes are released at every opportunity.
	 */
	public static void begin(boolean economyMode) {
		GENERATED.clear();
		CANDIDATES.clear();
		PINS.clear();
		RELEASED.set(0);
		ACTIVE.set(0);
		lastSweepNanos = 0L;
		economy = economyMode;
		enabled = true;
	}

	/**
	 * Ends a run and drops every class reference held here.
	 */
	public static void end() {
		enabled = false;
		GENERATED.clear();
		CANDIDATES.clear();
		PINS.clear();
	}

	/**
	 * Classes released since the last {@link #begin(boolean)}; still readable after {@link #end()}.
	 */
	public static int released() {
		return RELEASED.get();
	}

	/**
	 * Marks a class and its dependencies as in use by the calling generation until {@link Pins#release()}.
	 */
	public static Pins pin(ClassNode cls) {
		awaitHeadroom();
		Pins pins = new Pins();
		pins.add(Collections.singletonList(cls));
		pins.add(cls.getDependencies());
		pins.add(cls.getCodegenDeps());
		return pins;
	}

	/**
	 * Called after a class was generated and saved, on the worker thread and outside any class lock.
	 */
	public static void classSaved(ClassNode cls) {
		if (!enabled) {
			return;
		}
		try {
			GENERATED.add(cls.getTopParentClass());
			if (underPressure()) {
				sweep();
			}
		} catch (RuntimeException ignored) {
			// memory relief is best effort: a decompile must never fail because of it
		}
	}

	/**
	 * Interrupts every live JADX worker thread (task executor and its parallel pool); returns how many were found,
	 * so zero means every worker has stopped.
	 */
	public static int interruptWorkers() {
		List<Thread> workers = workerThreads();
		for (Thread worker : workers) {
			worker.interrupt();
		}
		return workers.size();
	}

	/**
	 * Classes held in use by one generation. Only the owning thread adds and releases.
	 */
	public static final class Pins {
		private final Map<ClassNode, Boolean> held = new IdentityHashMap<>();
		private final boolean counted;

		private Pins() {
			this.counted = enabled;
			if (counted) {
				ACTIVE.incrementAndGet();
			}
		}

		public void add(List<ClassNode> classes) {
			if (!enabled || classes == null) {
				return;
			}
			for (ClassNode cls : classes) {
				ClassNode top = cls.getTopParentClass();
				if (held.put(top, Boolean.TRUE) == null) {
					PINS.merge(top, 1, Integer::sum);
				}
			}
		}

		public void release() {
			for (ClassNode cls : held.keySet()) {
				PINS.computeIfPresent(cls, (key, count) -> count <= 1 ? null : count - 1);
				if (enabled) {
					CANDIDATES.add(cls);
				}
			}
			held.clear();
			if (counted) {
				ACTIVE.updateAndGet(count -> count > 0 ? count - 1 : 0);
			}
		}
	}

	private static double usedRatio() {
		Runtime runtime = Runtime.getRuntime();
		return (double) (runtime.totalMemory() - runtime.freeMemory()) / runtime.maxMemory();
	}

	private static boolean underPressure() {
		return usedRatio() > (economy ? ECONOMY_RATIO : PRESSURE_RATIO);
	}

	/**
	 * Adaptive parallelism: while the heap is nearly full and another generation is running, a new generation waits
	 * (bounded) instead of adding a second working set. A lone generation never waits, so the run cannot stall; when
	 * memory is fine every worker thread runs at full speed.
	 */
	private static void awaitHeadroom() {
		long waited = 0L;
		while (enabled && waited < GATE_MAX_WAIT_MS && ACTIVE.get() > 0 && usedRatio() > GATE_RATIO) {
			try {
				Thread.sleep(GATE_STEP_MS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
			waited += GATE_STEP_MS;
		}
	}

	private static void sweep() {
		if (!economy && System.nanoTime() - lastSweepNanos < SWEEP_GAP_NANOS) {
			return;
		}
		if (!SWEEPING.compareAndSet(false, true)) {
			return;
		}
		try {
			int released = 0;
			for (ClassNode cls : CANDIDATES) {
				CANDIDATES.remove(cls);
				if (enabled && releaseIfIdle(cls)) {
					released++;
				}
			}
			RELEASED.addAndGet(released);
		} finally {
			lastSweepNanos = System.nanoTime();
			SWEEPING.set(false);
		}
	}

	/**
	 * Unloads a generated class that was loaded again as a dependency and that no running generation uses.
	 * State and pin are checked again under the class lock: a generation pins before it processes a dependency, and
	 * processing takes the same lock, so it either sees the class unloaded (and reloads it) or is seen as a pin here.
	 */
	private static boolean releaseIfIdle(ClassNode cls) {
		if (cls.getState() != ProcessState.PROCESS_COMPLETE || !GENERATED.contains(cls) || PINS.containsKey(cls)) {
			return false;
		}
		synchronized (cls.getClassInfo()) {
			if (cls.getState() != ProcessState.PROCESS_COMPLETE
					|| PINS.containsKey(cls)
					|| cls.contains(AFlag.DONT_UNLOAD_CLASS)) {
				return false;
			}
			cls.unload();
			cls.setState(ProcessState.GENERATED_AND_UNLOADED);
			return true;
		}
	}

	private static List<Thread> workerThreads() {
		List<Thread> result = new ArrayList<>();
		ThreadGroup root = Thread.currentThread().getThreadGroup();
		while (root != null && root.getParent() != null) {
			root = root.getParent();
		}
		if (root == null) {
			return result;
		}
		Thread[] all = new Thread[root.activeCount() + THREAD_SLACK];
		int count = root.enumerate(all, true);
		for (int i = 0; i < count; i++) {
			Thread thread = all[i];
			if (thread != null && thread.isAlive() && thread.getName().startsWith(WORKER_PREFIX)) {
				result.add(thread);
			}
		}
		return result;
	}
}
