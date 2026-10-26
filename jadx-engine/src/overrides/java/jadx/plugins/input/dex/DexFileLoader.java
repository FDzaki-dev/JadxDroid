package jadx.plugins.input.dex;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.Adler32;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jadx.api.plugins.utils.CommonFileUtils;
import jadx.core.utils.files.FileUtils;
import jadx.plugins.input.dex.sections.DexConsts;
import jadx.plugins.input.dex.sections.DexHeaderV41;
import jadx.plugins.input.dex.utils.DexCheckSum;
import jadx.zip.IZipEntry;
import jadx.zip.ZipContent;
import jadx.zip.ZipReader;

public class DexFileLoader {
	private static final Logger LOG = LoggerFactory.getLogger(DexFileLoader.class);

	// sharing between all instances (can be used in other plugins) // TODO:
	private static int dexUniqId = 1;

	private final DexInputOptions options;

	private ZipReader zipReader = new ZipReader();

	public DexFileLoader(DexInputOptions options) {
		this.options = options;
	}

	public void setZipReader(ZipReader zipReader) {
		this.zipReader = zipReader;
	}

	public List<DexReader> collectDexFiles(List<Path> pathsList) {
		return pathsList.stream()
				.map(Path::toFile)
				.map(this::loadDexFromFile)
				.filter(list -> !list.isEmpty())
				.flatMap(Collection::stream)
				.peek(dr -> LOG.debug("Loading dex: {}", dr))
				.collect(Collectors.toList());
	}

	private List<DexReader> loadDexFromFile(File file) {
		try (InputStream inputStream = new FileInputStream(file)) {
			return load(file, inputStream, file.getAbsolutePath());
		} catch (Exception e) {
			LOG.error("File open error: {}", file.getAbsolutePath(), e);
			return Collections.emptyList();
		}
	}

	private List<DexReader> load(@Nullable File file, InputStream inputStream, String fileName) throws IOException {
		try (InputStream in = inputStream.markSupported() ? inputStream : new BufferedInputStream(inputStream)) {
			byte[] magic = new byte[DexConsts.MAX_MAGIC_SIZE];
			in.mark(magic.length);
			if (in.read(magic) != magic.length) {
				return Collections.emptyList();
			}
			if (isStartWithBytes(magic, DexConsts.DEX_FILE_MAGIC)) {
				if (file != null) {
					List<DexReader> mapped = loadMappedFromFile(file, fileName);
					if (mapped != null) {
						return mapped;
					}
				}
				in.reset();
				byte[] content = readAllBytes(in);
				return loadDexReaders(fileName, content);
			}
			if (fileName.endsWith(".dex")) {
				// report invalid magic in '.dex' file
				String hex = FileUtils.bytesToHex(magic);
				String str = new String(magic, StandardCharsets.US_ASCII);
				LOG.warn("Invalid DEX magic: 0x{}(\"{}\") in file: {}", hex, str, fileName);
			}
			if (file != null) {
				// allow only top level zip files
				if (isStartWithBytes(magic, DexConsts.ZIP_FILE_MAGIC) || CommonFileUtils.isZipFileExt(fileName)) {
					return collectDexFromZip(file);
				}
			}
			return Collections.emptyList();
		}
	}

	private List<DexReader> loadFromZipEntry(byte[] content, String fileName) {
		if (isStartWithBytes(content, DexConsts.DEX_FILE_MAGIC) || fileName.endsWith(".dex")) {
			return loadDexReaders(fileName, content);
		}
		return Collections.emptyList();
	}

	public List<DexReader> loadDexReaders(String fileName, byte[] content) {
		DexHeaderV41 dexHeaderV41 = DexHeaderV41.readIfPresent(content);
		if (dexHeaderV41 != null) {
			return DexHeaderV41.readSubDexOffsets(content, dexHeaderV41)
					.stream()
					.map(offset -> loadSingleDex(fileName, content, offset))
					.collect(Collectors.toList());
		}
		DexReader dexReader = loadSingleDex(fileName, content, 0);
		return Collections.singletonList(dexReader);
	}

	private DexReader loadSingleDex(String fileName, byte[] content, int offset) {
		if (options.isVerifyChecksum()) {
			DexCheckSum.verify(fileName, content, offset);
		}
		return new DexReader(getNextUniqId(), fileName, content, offset);
	}

	/**
	 * Since DEX v41, several sub DEX structures can be stored inside container of a single DEX file
	 * Use {@link DexFileLoader#loadDexReaders(String, byte[])} instead.
	 */
	@Deprecated
	public DexReader loadDexReader(String fileName, byte[] content) {
		return loadSingleDex(fileName, content, 0);
	}

	private List<DexReader> collectDexFromZip(File file) {
		List<DexReader> result = new ArrayList<>();
		try (ZipContent zip = zipReader.open(file)) {
			for (IZipEntry entry : zip.getEntries()) {
				if (entry.isDirectory()) {
					continue;
				}
				try {
					List<DexReader> readers = Collections.emptyList();
					if (entry.getName().endsWith(".dex") || startsWithDexMagic(entry)) {
						readers = loadZipEntryMapped(entry);
					}
					if (!readers.isEmpty()) {
						result.addAll(readers);
					}
				} catch (Exception e) {
					LOG.error("Failed to read zip entry: {}", entry, e);
				}
			}
		} catch (Exception e) {
			LOG.error("Failed to process zip file: {}", file.getAbsolutePath(), e);
		}
		return result;
	}

	private static final int HEADER_PROBE_SIZE = 128;
	private static final int COPY_BUFFER_SIZE = 64 * 1024;

	/**
	 * Android: map a standalone dex file instead of reading it into the Java heap.
	 * Returns null if the file can't be handled here (caller falls back to in-memory loading).
	 */
	private @Nullable List<DexReader> loadMappedFromFile(File file, String fileName) throws IOException {
		try (FileChannel channel = FileChannel.open(file.toPath(), StandardOpenOption.READ)) {
			long size = channel.size();
			if (size > Integer.MAX_VALUE) {
				return null;
			}
			return loadMappedDex(fileName, channel.map(FileChannel.MapMode.READ_ONLY, 0, size));
		}
	}

	/**
	 * Android: stream a dex zip entry to a temp file and map it, so neither the compressed nor the
	 * uncompressed bytes occupy the Java heap. The temp file is unlinked right after mapping.
	 */
	private List<DexReader> loadZipEntryMapped(IZipEntry entry) throws IOException {
		Path tmp = Files.createTempFile("jadx-dex-", ".dex");
		try {
			try (InputStream in = entry.getInputStream(); OutputStream out = Files.newOutputStream(tmp)) {
				byte[] chunk = new byte[COPY_BUFFER_SIZE];
				int count;
				while ((count = in.read(chunk)) != -1) {
					out.write(chunk, 0, count);
				}
			}
			try (FileChannel channel = FileChannel.open(tmp, StandardOpenOption.READ)) {
				List<DexReader> readers = loadMappedDex(entry.getName(), channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size()));
				if (readers != null) {
					return readers;
				}
			}
			// DEX v41 container or tiny file: needs random access to the whole content
			return loadDexReaders(entry.getName(), Files.readAllBytes(tmp));
		} finally {
			Files.deleteIfExists(tmp);
		}
	}

	private @Nullable List<DexReader> loadMappedDex(String fileName, ByteBuffer mapped) {
		if (mapped.capacity() < HEADER_PROBE_SIZE) {
			return null;
		}
		byte[] head = new byte[HEADER_PROBE_SIZE];
		ByteBuffer probe = mapped.duplicate();
		probe.position(0);
		probe.get(head);
		if (DexHeaderV41.readIfPresent(head) != null) {
			return null;
		}
		if (options.isVerifyChecksum()) {
			verifyMappedChecksum(fileName, mapped);
		}
		return Collections.singletonList(new DexReader(getNextUniqId(), fileName, mapped, 0));
	}

	private static void verifyMappedChecksum(String fileName, ByteBuffer mapped) {
		ByteBuffer buf = mapped.duplicate();
		buf.order(ByteOrder.LITTLE_ENDIAN);
		int len = buf.getInt(32);
		if (len < 12 || len > buf.capacity()) {
			throw new DexException("Dex file truncated, length in header: " + len + ", file: " + fileName);
		}
		int checksum = buf.getInt(8);
		buf.limit(len);
		buf.position(12);
		Adler32 adler32 = new Adler32();
		byte[] chunk = new byte[COPY_BUFFER_SIZE];
		while (buf.hasRemaining()) {
			int count = Math.min(chunk.length, buf.remaining());
			buf.get(chunk, 0, count);
			adler32.update(chunk, 0, count);
		}
		int fileChecksum = (int) adler32.getValue();
		if (checksum != fileChecksum) {
			throw new DexException(String.format("Bad dex file checksum: 0x%08x, expected: 0x%08x, file: %s",
					fileChecksum, checksum, fileName));
		}
	}

	/**
	 * Check only the first bytes of an entry: reading every resource of an APK fully into memory
	 * just to look for the dex magic exhausts the Java heap on large apps.
	 */
	private static boolean startsWithDexMagic(IZipEntry entry) throws IOException {
		byte[] magic = new byte[DexConsts.MAX_MAGIC_SIZE];
		try (InputStream in = entry.getInputStream()) {
			int total = 0;
			while (total < magic.length) {
				int read = in.read(magic, total, magic.length - total);
				if (read == -1) {
					return false;
				}
				total += read;
			}
		}
		return isStartWithBytes(magic, DexConsts.DEX_FILE_MAGIC);
	}

	private static boolean isStartWithBytes(byte[] fileMagic, byte[] expectedBytes) {
		int len = expectedBytes.length;
		if (fileMagic.length < len) {
			return false;
		}
		for (int i = 0; i < len; i++) {
			if (fileMagic[i] != expectedBytes[i]) {
				return false;
			}
		}
		return true;
	}

	private static byte[] readAllBytes(InputStream in) throws IOException {
		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		byte[] data = new byte[8192];
		while (true) {
			int read = in.read(data);
			if (read == -1) {
				break;
			}
			buf.write(data, 0, read);
		}
		return buf.toByteArray();
	}

	private static synchronized int getNextUniqId() {
		dexUniqId++;
		if (dexUniqId >= 0xFFFF) {
			dexUniqId = 1;
		}
		return dexUniqId;
	}

	private static synchronized void resetDexUniqId() {
		dexUniqId = 1;
	}
}
