package soundsculptor.core.browser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.SoundSet;

import org.junit.jupiter.api.Test;

/** PRD FR-1.7: the picker adds one settings-free entry per pick and refuses past the entry limit. */
class PickerModelTest {
	private static final String COW = "minecraft:entity.cow.ambient";
	private static final String PIG = "minecraft:entity.pig.ambient";

	private static SoundSet set(SetEntry... entries) {
		return new SoundSet("s1", "Farm", List.of(entries));
	}

	@Test
	void inSetMarksExistingSingleSounds() {
		PickerModel picker = new PickerModel(set(SetEntry.of("e1", COW), SetEntry.of("e2", "minecraft:entity.villager.*")));
		assertTrue(picker.inSet(COW));
		assertTrue(picker.inSet("Minecraft:Entity.Cow.Ambient"));
		assertFalse(picker.inSet(PIG));
		assertTrue(picker.groupInSet("minecraft:entity.villager.*"));
		assertFalse(picker.groupInSet("minecraft:entity.zombie.*"));
	}

	@Test
	void soundsInTheSetCannotBePicked() {
		PickerModel picker = new PickerModel(set(SetEntry.of("e1", COW)));
		picker.toggleSound(COW);
		assertEquals(0, picker.pickedCount());
	}

	@Test
	void toggleTwiceUnpicks() {
		PickerModel picker = new PickerModel(set());
		picker.toggleSound(COW);
		picker.toggleGroup("minecraft:entity.pig.*");
		assertEquals(2, picker.pickedCount());
		picker.toggleSound(COW);
		assertEquals(1, picker.pickedCount());
	}

	@Test
	void addCreatesOneEntryPerPick() {
		SoundSet base = set(SetEntry.of("e1", "minecraft:block.stone.*").withMute(true));
		PickerModel picker = new PickerModel(base);
		picker.toggleSound(PIG);
		picker.toggleGroup("minecraft:entity.villager.*");
		picker.toggleSound(COW);
		SoundSet result = picker.addTo(base);
		assertEquals(4, result.entries().size());
		assertEquals(base.entries().get(0), result.entries().get(0));
		List<SetEntry> added = result.entries().subList(1, 4);
		assertEquals(List.of(PIG, "minecraft:entity.villager.*", COW), added.stream().map(e -> e.pattern().text()).toList());
		assertEquals(List.of("e2", "e3", "e4"), added.stream().map(SetEntry::id).toList());
		for (SetEntry e : added) {
			assertTrue(e.enabled());
			assertNull(e.mute());
			assertNull(e.volume());
			assertNull(e.limit());
			assertNull(e.cue());
		}
	}

	@Test
	void addSkipsPicksAlreadyInTheSet() {
		PickerModel picker = new PickerModel(set());
		picker.toggleSound(COW);
		SoundSet later = set(SetEntry.of("e1", COW));
		assertSame(later, picker.addTo(later));
	}

	@Test
	void addOverLimitIsRefused() {
		List<SetEntry> entries = new ArrayList<>();
		for (int i = 1; i <= 510; i++) {
			entries.add(SetEntry.of("e" + i, "minecraft:test.s" + i));
		}
		SoundSet full = new SoundSet("s1", "Big", entries);
		PickerModel picker = new PickerModel(full);
		picker.toggleSound(COW);
		picker.toggleSound(PIG);
		assertNull(picker.canAdd());
		picker.toggleSound("minecraft:entity.sheep.ambient");
		assertEquals("soundsculptor.sets.error.entries_full", picker.canAdd().key());
		assertSame(full, picker.addTo(full));
	}
}
