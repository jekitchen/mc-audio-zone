package soundsculptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
import org.junit.jupiter.api.Test;

/**
 * The built fabric.mod.json: one jar for Minecraft 26.2 and 26.3 (docs/PORTING.md, "One jar for every version"), so its
 * "minecraft" range, read with Fabric Loader's own parser, must accept every 26.2 and 26.3 release and refuse older
 * versions, 26.2 pre-releases and anything of 26.4, snapshots included. (26.3 pre-releases fall inside the range.)
 * Update the versions here when supported_minecraft_versions in gradle.properties changes.
 */
class ModJsonTest {
	@Test
	void minecraftRangeAcceptsEverySupportedReleaseAndRefusesOtherVersions() throws Exception {
		VersionPredicate range = VersionPredicate.parse(modJson().getAsJsonObject("depends").get("minecraft").getAsString());
		for (String supported : new String[] {"26.2", "26.2.1", "26.2.5", "26.3", "26.3.1", "26.3.4"}) {
			assertTrue(range.test(version(supported)), supported + " should be accepted by " + range);
		}
		// Fabric Loader reads a snapshot such as 26.4-snapshot-1 as 26.4-alpha.1.
		for (String other : new String[] {"26.1", "26.1.2", "26.2-pre.1", "26.2-rc.1", "26.4-alpha.1", "26.4-pre.1", "26.4-rc.1", "26.4", "27.1"}) {
			assertFalse(range.test(version(other)), other + " should be refused by " + range);
		}
	}

	@Test
	void versionNamesTheSupportedRangeNotTheBuildVersion() throws Exception {
		String version = modJson().get("version").getAsString();
		assertTrue(version.endsWith("+mc26.2-26.3"), version);
		assertNotNull(version(version));
	}

	@Test
	void modMenuStaysOptionalForEveryVersion() throws Exception {
		JsonObject json = modJson();
		assertFalse(json.getAsJsonObject("depends").has("modmenu"));
		assertEquals("*", json.getAsJsonObject("suggests").get("modmenu").getAsString());
	}

	private static Version version(String text) throws VersionParsingException {
		return Version.parse(text);
	}

	/** Ours, not one of the Fabric API modules' on the test class path. */
	private static JsonObject modJson() throws IOException {
		for (URL url : Collections.list(ModJsonTest.class.getClassLoader().getResources("fabric.mod.json"))) {
			try (Reader reader = new InputStreamReader(url.openStream(), StandardCharsets.UTF_8)) {
				JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
				if ("soundsculptor".equals(json.get("id").getAsString())) {
					return json;
				}
			}
		}
		throw new AssertionError("no soundsculptor fabric.mod.json on the test class path");
	}
}
