package soundsculptor.core.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.SoundSet;

import org.junit.jupiter.api.Test;

/** The set editor's draft (PRD FR-4.3, FR-4.7 limits, FR-12.4): nothing changes in game until Save builds the set. */
class SetDraftTest {
	private static final SetEntry COWS = SetEntry.of("e1", "minecraft:entity.cow.*").withVolume(40);
	private static final SetEntry PIGS = SetEntry.of("e2", "minecraft:entity.pig.*").withMute(true);

	@Test
	void changedAfterEntryEdit() {
		SoundSet farm = new SoundSet("set-farm", "Farm animals", List.of(COWS, PIGS));
		SetDraft draft = SetDraft.of(farm);
		assertFalse(draft.changed(), "a draft just opened has no changes");
		assertEquals(farm, draft.build());

		assertTrue(draft.putEntry(COWS.withVolume(60)));
		assertTrue(draft.changed(), "editing an entry is a change");
		assertEquals(60, draft.entry("e1").volume());

		draft.putEntry(COWS);
		assertFalse(draft.changed(), "putting the entry back as it was is no change");

		draft.setEntryEnabled("e2", false);
		assertTrue(draft.changed(), "turning an entry off is a change");
		assertFalse(draft.entry("e2").enabled());
		draft.setEntryEnabled("e2", true);
		assertFalse(draft.changed());

		draft.setName("Farm");
		assertTrue(draft.changed(), "a new name is a change");
		draft.setName(" Farm animals ");
		assertFalse(draft.changed(), "spaces around the name do not count");

		draft.removeEntry("e2");
		assertTrue(draft.changed(), "removing an entry is a change");
		assertNull(draft.entry("e2"));
	}

	@Test
	void buildKeepsEntryOrder() {
		SetDraft draft = SetDraft.blank("set-new", "New set");
		SetEntry sheep = SetEntry.of(draft.newEntryId(), "minecraft:entity.sheep.*");
		draft.putEntry(sheep);
		SetEntry cows = SetEntry.of(draft.newEntryId(), "minecraft:entity.cow.*");
		draft.putEntry(cows);
		SetEntry pigs = SetEntry.of(draft.newEntryId(), "minecraft:entity.pig.*");
		draft.putEntry(pigs);
		assertEquals(List.of("e1", "e2", "e3"), ids(draft.build()), "new entries get the next free ID, in the order added");

		draft.moveEntry("e3", -1);
		draft.putEntry(sheep.withMute(true));
		SoundSet built = draft.build();
		assertEquals("set-new", built.id());
		assertEquals("New set", built.name());
		assertEquals(List.of("e1", "e3", "e2"), ids(built), "moves keep their place and an edited entry stays where it was");
		assertEquals(Boolean.TRUE, built.entry("e1").mute());
	}

	@Test
	void refusesEntry513() {
		SetDraft draft = SetDraft.blank("set-full", "Full");
		for (int i = 0; i < SoundSet.MAX_ENTRIES; i++) {
			assertTrue(draft.putEntry(SetEntry.of(draft.newEntryId(), "minecraft:sound." + i)));
		}
		SetEntry extra = SetEntry.of(draft.newEntryId(), "minecraft:one.more");
		assertNotNull(draft.canPut(extra), "a full set says so before an entry is added");
		assertEquals("soundsculptor.sets.error.entries_full", draft.canPut(extra).key());
		assertFalse(draft.putEntry(extra), "entry 513 is refused, not thrown");
		assertEquals(SoundSet.MAX_ENTRIES, draft.build().entries().size());

		// Editing an entry of a full set still works: it adds nothing.
		SetEntry first = draft.entries().get(0);
		assertNull(draft.canPut(first.withVolume(10)));
		assertTrue(draft.putEntry(first.withVolume(10)));
		assertEquals(10, draft.build().entries().get(0).volume());
	}

	private static List<String> ids(SoundSet set) {
		return set.entries().stream().map(SetEntry::id).toList();
	}
}
