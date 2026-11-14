import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Checks that two builds of the mod jar are the same file, byte for byte. Sound Sculptor ships one jar for every
 * supported Minecraft version (docs/PORTING.md, "One jar for every version"); each version is built on its own, and
 * this proves the builds agree, so the jar that ships is the one every version's game tests ran.
 *
 * <p>Nothing is ignored. Lists every entry that exists in only one jar or whose content differs, then, if the entries
 * all match, every entry whose zip metadata (order, timestamp, compression) differs. Exit code 0 when the jars are
 * identical, 1 when they differ, 2 on bad arguments. Compare the main jars only: the {@code -sources} jars are not
 * shipped and are out of scope.
 *
 * <pre>java tools/CompareJars.java build-26.2/soundsculptor-X.jar build-26.3/soundsculptor-X.jar</pre>
 */
public final class CompareJars {
	private record Entry(int index, byte[] content, long time, int method) {
	}

	public static void main(String[] args) throws IOException {
		if (args.length != 2) {
			System.err.println("usage: java tools/CompareJars.java <first.jar> <second.jar>");
			System.exit(2);
		}
		Path first = Path.of(args[0]);
		Path second = Path.of(args[1]);
		Map<String, Entry> a = read(first);
		Map<String, Entry> b = read(second);

		List<String> problems = new ArrayList<>();
		for (String name : a.keySet()) {
			if (!b.containsKey(name)) {
				problems.add("only in " + first.getFileName() + ": " + name);
			}
		}
		for (String name : b.keySet()) {
			if (!a.containsKey(name)) {
				problems.add("only in " + second.getFileName() + ": " + name);
			}
		}
		for (Map.Entry<String, Entry> e : a.entrySet()) {
			Entry other = b.get(e.getKey());
			if (other != null && !Arrays.equals(e.getValue().content(), other.content())) {
				problems.add("content differs: " + e.getKey() + " (" + e.getValue().content().length + " vs "
						+ other.content().length + " bytes)");
			}
		}
		if (problems.isEmpty()) {
			for (Map.Entry<String, Entry> e : a.entrySet()) {
				Entry x = e.getValue();
				Entry y = b.get(e.getKey());
				if (x.index() != y.index() || x.time() != y.time() || x.method() != y.method()) {
					problems.add("zip metadata differs: " + e.getKey() + " (position " + x.index() + " vs " + y.index()
							+ ", time " + x.time() + " vs " + y.time() + ", method " + x.method() + " vs " + y.method() + ")");
				}
			}
		}
		byte[] firstBytes = Files.readAllBytes(first);
		byte[] secondBytes = Files.readAllBytes(second);
		if (problems.isEmpty() && !Arrays.equals(firstBytes, secondBytes)) {
			problems.add("the entries and their metadata match, but the files differ (zip headers, extra fields or comments)");
		}

		if (problems.isEmpty()) {
			System.out.println("IDENTICAL: " + a.size() + " entries, " + firstBytes.length + " bytes, sha256 " + sha256(firstBytes));
			return;
		}
		System.out.println("DIFFERENT: " + first + " and " + second);
		problems.forEach(p -> System.out.println("  " + p));
		System.out.println(problems.size() + " difference(s). Every supported Minecraft version must build the same jar: see"
				+ " docs/PORTING.md, \"One jar for every version\".");
		System.exit(1);
	}

	private static Map<String, Entry> read(Path jar) throws IOException {
		Map<String, Entry> entries = new LinkedHashMap<>();
		try (ZipFile zip = new ZipFile(jar.toFile())) {
			int index = 0;
			for (ZipEntry entry : zip.stream().toList()) {
				byte[] content;
				try (var in = zip.getInputStream(entry)) {
					content = in.readAllBytes();
				}
				if (entries.put(entry.getName(), new Entry(index++, content, entry.getTime(), entry.getMethod())) != null) {
					throw new IOException(jar + " has two entries named " + entry.getName());
				}
			}
		}
		return entries;
	}

	private static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
