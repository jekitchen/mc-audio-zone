package soundsculptor.core.sets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import soundsculptor.core.TestEnglish;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import org.junit.jupiter.api.Test;

/** PRD FR-11.8 and section 7.5: the Sound Set file format. */
class SetCodecTest {
	private static final TestEnglish EN = new TestEnglish();

	private static SoundSet full() {
		return new SoundSet("set-1", "Farm animals", List.of(
				new SetEntry("e1", false, SoundPattern.of("*cow*"), true, 50, new Limit(3, 1000, true), false),
				SetEntry.of("e2", "minecraft:entity.pig.ambient")));
	}

	private static String problem(String text) {
		SetFormatException e = assertThrows(SetFormatException.class, () -> SetCodec.read(text));
		return e.problem().text(EN);
	}

	private static String file(String entries) {
		return "{\"schemaVersion\":3,\"id\":\"farm\",\"name\":\"Farm animals\",\"entries\":[" + entries + "]}";
	}

	private static String header(String id, String name) {
		return "{\"schemaVersion\":3,\"id\":\"" + id + "\",\"name\":\"" + name + "\",\"entries\":[]}";
	}

	@Test
	void roundTripKeepsEveryField() throws Exception {
		SoundSet set = full();
		assertEquals(set, SetCodec.read(SetCodec.toJson(set)));
		assertEquals(set, SetCodec.read(SetCodec.toJsonObject(set), "Sound Set 'Farm animals'"));
	}

	@Test
	void unsetPropertiesAreOmitted() {
		JsonObject json = JsonParser.parseString(SetCodec.toJson(full())).getAsJsonObject();
		JsonObject plain = json.getAsJsonArray("entries").get(1).getAsJsonObject();
		assertEquals(3, json.get("schemaVersion").getAsInt());
		assertEquals(java.util.Set.of("id", "enabled", "target"), plain.keySet());
	}

	@Test
	void levelAbove200NamesSetAndEntry() {
		String text = problem(file("{\"id\":\"a\",\"target\":\"*cow*\"},{\"id\":\"b\",\"target\":\"*pig*\"},"
				+ "{\"id\":\"c\",\"target\":\"*hen*\",\"volume\":250}"));
		assertTrue(text.contains("Sound Set 'Farm animals', entry 3, level"), text);
		assertTrue(text.contains("250 is above the maximum 200"), text);
	}

	@Test
	void newerSchemaIsRefused() {
		String text = problem("{\"schemaVersion\":4,\"id\":\"farm\",\"name\":\"x\",\"entries\":[]}");
		assertTrue(text.contains("newer Sound Sculptor"), text);
	}

	@Test
	void olderOrMissingSchemaIsRefused() {
		assertTrue(problem("{\"schemaVersion\":2,\"id\":\"farm\",\"name\":\"x\",\"entries\":[]}").contains("Unknown schemaVersion 2"));
		assertTrue(problem("{\"id\":\"farm\",\"name\":\"x\",\"entries\":[]}").contains("no schemaVersion"));
	}

	@Test
	void unknownFieldWarns() throws Exception {
		SetCodec.Read read = SetCodec.readWithWarnings(
				"{\"schemaVersion\":3,\"id\":\"farm\",\"name\":\"Farm\",\"color\":1,\"entries\":"
						+ "[{\"id\":\"a\",\"target\":\"*cow*\",\"pitch\":2}]}");
		assertEquals(2, read.warnings().size());
		assertTrue(read.warnings().get(0).text(EN).contains("\"color\""));
		assertTrue(read.warnings().get(1).text(EN).contains("entry 1"));
		assertEquals(1, read.set().entries().size());
	}

	@Test
	void rejectsNameLongerThan64() {
		String text = problem(header("farm", "n".repeat(65)));
		assertTrue(text.contains("Sound Set") && text.contains("at most 64"), text);
	}

	@Test
	void acceptsNameOf64() throws Exception {
		assertEquals(64, SetCodec.read(header("farm", "n".repeat(64))).name().length());
	}

	@Test
	void rejectsBadAndReservedIds() {
		assertTrue(problem(header("Farm Set", "Farm")).contains("Sound Set 'Farm', id"));
		assertTrue(problem(header("quick", "Farm")).contains("reserved"));
		assertTrue(problem(header("", "Farm")).contains("id"));
	}

	@Test
	void rejectsMoreThan512Entries() {
		StringBuilder entries = new StringBuilder();
		for (int i = 0; i <= SoundSet.MAX_ENTRIES; i++) {
			entries.append(i == 0 ? "" : ",").append("{\"id\":\"e").append(i).append("\",\"target\":\"*cow*\"}");
		}
		String text = problem(file(entries.toString()));
		assertTrue(text.contains("Sound Set 'Farm animals'") && text.contains("513") && text.contains("512"), text);
	}

	@Test
	void rejectsDuplicateEntryIdsAsAFormatProblem() {
		String text = problem(file("{\"id\":\"a\",\"target\":\"*cow*\"},{\"id\":\"a\",\"target\":\"*pig*\"}"));
		assertTrue(text.contains("entry 2") && text.contains("already used by entry 1"), text);
	}

	@Test
	void badValuesAreFormatProblems() {
		assertTrue(problem(file("{\"id\":\"a\",\"target\":\"Bad Sound!\"}")).contains("entry 1, target"));
		assertTrue(problem(file("{\"id\":\"a\"}")).contains("entry 1, target: missing"));
		assertTrue(problem(file("{\"id\":\"a\",\"target\":\"*c*\",\"limit\":{\"count\":0,\"windowMs\":1000}}")).contains("limit count"));
		assertTrue(problem(file("{\"id\":\"a\",\"target\":\"*c*\",\"limit\":{\"count\":1,\"windowMs\":10}}")).contains("limit window"));
		assertTrue(problem(file("{\"id\":\"a\",\"target\":\"*c*\",\"volume\":10.5}")).contains("not a whole number"));
		assertTrue(problem(file("5")).contains("entry 1"));
		assertTrue(problem("[]").contains("not a Sound Set"));
		assertTrue(problem("not json").contains("not valid JSON"));
		assertTrue(problem("").contains("empty"));
	}
}
