package jadx.android;

import java.util.ArrayList;
import java.util.List;

import jadx.api.plugins.JadxPlugin;
import jadx.api.plugins.loader.JadxPluginLoader;
import jadx.plugins.input.dex.DexInputPlugin;
import jadx.plugins.input.java.JavaInputPlugin;

/**
 * Registers input plugins explicitly instead of java.util.ServiceLoader,
 * so loading does not depend on META-INF/services merging or R8 service optimisation.
 */
public class AndroidPluginLoader implements JadxPluginLoader {

	@Override
	public List<JadxPlugin> load() {
		List<JadxPlugin> list = new ArrayList<>(2);
		list.add(new DexInputPlugin());
		list.add(new JavaInputPlugin());
		return list;
	}

	@Override
	public void close() {
		// nothing to close
	}
}
