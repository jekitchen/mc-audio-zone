package soundsculptor.core.quick;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import soundsculptor.core.Message;
import soundsculptor.core.profile.Profile;
import soundsculptor.core.profile.ProfileSettings;
import soundsculptor.core.sets.Attachment;
import soundsculptor.core.sets.Limit;
import soundsculptor.core.sets.PlaceRef;
import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.sets.QuickChanges;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.SetLibrary;
import soundsculptor.core.sets.SoundSet;
import soundsculptor.core.sets.Workspace;
import soundsculptor.core.trace.EntryEdit;
import soundsculptor.core.trace.TraceEditError;
import soundsculptor.core.undo.UndoSlot;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;

import org.junit.jupiter.api.Test;

/**
 * PRD FR-1.5, FR-4.10: quick actions edit the sound's single-sound entry in the Quick changes set of the place "Apply
 * to" chose, and Save to library turns that set into a library set in the same slot.
 */
class QuickChangesTest {
	private static final String COW = "minecraft:entity.cow.ambient";
	private static final String PIG = "minecraft:entity.pig.ambient";
	private static final PlaceRef EVERYWHERE = PlaceRef.EVERYWHERE;
	private static final PlaceRef PEN = PlaceRef.zone("pen");
	private static final SoundSet MUTES_COWS = new SoundSet("set-cows", "Cows",
			List.of(SetEntry.of("e1", "minecraft:entity.cow.*").withMute(true)));
	private static final SoundSet COWS_NOT_MUTED = new SoundSet("set-calm", "Calm cows",
			List.of(SetEntry.of("e1", COW).withMute(false)));
	private static final SoundSet QUIET_PIGS = new SoundSet("set-pigs", "Quiet pigs",
			List.of(SetEntry.of("e1", PIG).withVolume(40)));

	private static Zone pen(PlaceSets sets) {
		return new Zone("pen", "Pen", true, "play.example.net", "minecraft:overworld", new Point(0, 64, 0),
				new Shape.Sphere(10), sets);
	}

	private static Workspace ws(PlaceSets everywhere, PlaceSets pen, SoundSet... library) {
		Profile profile = new Profile("p", ProfileSettings.DEFAULTS, everywhere).withZones(List.of(pen(pen)));
		return new Workspace(profile, new SetLibrary(List.of(library)));
	}

	private static Workspace ws(PlaceSets everywhere, SoundSet... library) {
		return ws(everywhere, PlaceSets.EMPTY, library);
	}

	private static Workspace apply(Workspace ws, PlaceRef where, String soundId, EntryEdit edit) {
		QuickActionResult result = QuickActions.apply(ws, where, soundId, edit);
		assertTrue(result.ok(), () -> "refused: " + result.error());
		return result.after();
	}

	private static Workspace toggle(Workspace ws, PlaceRef where, String soundId) {
		return apply(ws, where, soundId, QuickActions.toggleMute(ws, where, soundId, true));
	}

	private static List<String> ids(PlaceSets place) {
		return place.attached().stream().map(Attachment::setId).toList();
	}

	private static SetEntry quickEntry(Workspace ws, PlaceRef where, String soundId) {
		return QuickActions.quickEntry(ws, where, soundId);
	}

	// ---- FR-4.10: Mute and Unmute show and change the actual state at the place ----

	@Test
	void muteWritesQuickEntryEvenWhenLibrarySaysNotMuted() {
		Workspace start = ws(PlaceSets.EMPTY.attach(COWS_NOT_MUTED.id()), COWS_NOT_MUTED);
		assertFalse(QuickActions.isMuted(start, EVERYWHERE, COW));
		assertEquals(new EntryEdit.MuteWithCue(true), QuickActions.toggleMute(start, EVERYWHERE, COW, true));

		Workspace muted = toggle(start, EVERYWHERE, COW);
		PlaceSets everywhere = muted.profile().everywhere();
		assertEquals(List.of(SoundSet.QUICK_ID, COWS_NOT_MUTED.id()), ids(everywhere));
		SetEntry entry = everywhere.quickEntries().get(0);
		assertEquals(COW, entry.pattern().text());
		assertTrue(entry.pattern().isExact());
		assertEquals(Boolean.TRUE, entry.mute());
		assertEquals(Boolean.TRUE, entry.cue(), "a new mute starts with the Cue default (FR-7.6)");
		assertTrue(QuickActions.isMuted(muted, EVERYWHERE, COW), "Quick changes is pinned first, so the mute wins");
		assertSame(start.library(), muted.library());
	}

	@Test
	void unmuteMutedOnlyByQuickClearsSetting() {
		Workspace start = ws(PlaceSets.EMPTY.attach(QUIET_PIGS.id()), QUIET_PIGS);
		Workspace muted = toggle(start, EVERYWHERE, COW);
		assertTrue(QuickActions.isMuted(muted, EVERYWHERE, COW));
		assertEquals(new EntryEdit.UnmuteWithCue(true), QuickActions.toggleMute(muted, EVERYWHERE, COW, true));

		Workspace unmuted = toggle(muted, EVERYWHERE, COW);
		assertNull(quickEntry(unmuted, EVERYWHERE, COW), "the entry is gone");
		assertNull(unmuted.profile().everywhere().quickChanges(), "the empty Quick changes set is removed");
		assertEquals(start, unmuted, "Mute then Unmute leaves everything as it was");
		assertFalse(QuickActions.isMuted(unmuted, EVERYWHERE, COW));
	}

	@Test
	void unmuteMutedByLibraryWritesNotMuted() {
		Workspace start = ws(PlaceSets.EMPTY.attach(MUTES_COWS.id()), MUTES_COWS);
		assertTrue(QuickActions.isMuted(start, EVERYWHERE, COW), "the button shows the actual state: Unmute");
		assertEquals(new EntryEdit.Mute(false), QuickActions.toggleMute(start, EVERYWHERE, COW, true));

		Workspace unmuted = toggle(start, EVERYWHERE, COW);
		assertEquals(Boolean.FALSE, quickEntry(unmuted, EVERYWHERE, COW).mute(), "a \"Not muted\" entry");
		assertFalse(QuickActions.isMuted(unmuted, EVERYWHERE, COW));
		assertSame(start.library(), unmuted.library(), "the library set is unchanged");
		assertEquals(MUTES_COWS, unmuted.library().get(MUTES_COWS.id()));
	}

	@Test
	void unmuteMutedByBothWritesNotMuted() {
		Workspace start = ws(PlaceSets.EMPTY.attach(MUTES_COWS.id()), MUTES_COWS);
		Workspace both = apply(start, EVERYWHERE, COW, new EntryEdit.MuteWithCue(true));
		assertTrue(QuickActions.isMuted(both, EVERYWHERE, COW));
		assertEquals(new EntryEdit.Mute(false), QuickActions.toggleMute(both, EVERYWHERE, COW, true),
				"clearing the Quick changes mute alone would leave the library's mute");

		QuickActionResult result = QuickActions.apply(both, EVERYWHERE, COW, QuickActions.toggleMute(both, EVERYWHERE, COW, true));
		assertEquals(Boolean.FALSE, quickEntry(result.after(), EVERYWHERE, COW).mute());
		assertFalse(QuickActions.isMuted(result.after(), EVERYWHERE, COW));
		assertSame(both.library(), result.after().library());

		UndoSlot<Workspace> undo = new UndoSlot<>();
		undo.offer(result.before(), result.after(), "soundsculptor.recent.toast.unmuted", List.of(COW), 1000);
		Workspace undone = undo.undo(result.after(), 2000).orElseThrow();
		assertEquals(both, undone, "Undo restores the Quick changes mute and leaves the library mute");
		assertTrue(QuickActions.isMuted(undone, EVERYWHERE, COW));
	}

	@Test
	void unmuteMutedByAPatternInQuickChangesWritesNotMuted() {
		// The single-sound entry is what quick actions edit; a group entry in Quick changes still mutes, so clearing
		// nothing would not unmute.
		Workspace start = ws(PlaceSets.EMPTY.withQuickEntries(List.of(SetEntry.of("e1", "minecraft:entity.cow.*").withMute(true))));
		assertEquals(new EntryEdit.Mute(false), QuickActions.toggleMute(start, EVERYWHERE, COW, true));
		assertFalse(QuickActions.isMuted(toggle(start, EVERYWHERE, COW), EVERYWHERE, COW));
	}

	@Test
	void zoneTargetEditsZoneQuickNotEverywhere() {
		Workspace start = ws(PlaceSets.EMPTY, PlaceSets.EMPTY);
		Workspace muted = toggle(start, PEN, COW);
		assertNull(muted.profile().everywhere().quickChanges());
		assertEquals(List.of(SoundSet.QUICK_ID), ids(muted.profile().zone("pen").sets()));
		assertEquals(Boolean.TRUE, quickEntry(muted, PEN, COW).mute());
		assertTrue(QuickActions.isMuted(muted, PEN, COW));
		assertFalse(QuickActions.isMuted(muted, EVERYWHERE, COW));
	}

	@Test
	void mutedAtAgreesWithIsMutedForEverySoundAndPlace() {
		// One resolver per list rebuild (FR-1.6) must show the same state as asking row by row.
		Workspace ws = ws(PlaceSets.EMPTY.attach(MUTES_COWS.id()).attach(QUIET_PIGS.id()),
				PlaceSets.EMPTY.attach(COWS_NOT_MUTED.id()), MUTES_COWS, COWS_NOT_MUTED, QUIET_PIGS);
		ws = apply(ws, EVERYWHERE, PIG, new EntryEdit.Mute(true));
		ws = apply(ws, PEN, PIG, new EntryEdit.Mute(true));
		List<String> sounds = List.of(COW, PIG, "minecraft:entity.cow.hurt", "minecraft:block.stone.step");
		for (PlaceRef where : List.of(EVERYWHERE, PEN, PlaceRef.zone("gone"))) {
			Predicate<String> muted = QuickActions.mutedAt(ws, where);
			for (String sound : sounds) {
				assertEquals(QuickActions.isMuted(ws, where, sound), muted.test(sound), where + " " + sound);
			}
		}
		Predicate<String> everywhere = QuickActions.mutedAt(ws, EVERYWHERE);
		assertTrue(everywhere.test(COW) && everywhere.test(PIG), "the library mutes cows; Quick changes mutes pigs");
		assertFalse(everywhere.test("minecraft:block.stone.step"));
		Predicate<String> pen = QuickActions.mutedAt(ws, PEN);
		assertFalse(pen.test(COW), "Pen's set says cows are not muted");
		assertTrue(pen.test(PIG));
		assertFalse(QuickActions.mutedAt(ws, PlaceRef.zone("gone")).test(PIG), "a deleted zone mutes nothing");
	}

	// ---- Save to library ----

	@Test
	void saveToLibraryKeepsPlaceAndFreesPin() {
		Workspace start = toggle(ws(PlaceSets.EMPTY.attach(QUIET_PIGS.id()), QUIET_PIGS), EVERYWHERE, COW);
		List<SetEntry> quick = start.profile().everywhere().quickEntries();
		assertNull(QuickChanges.canSave(start, EVERYWHERE));

		Workspace saved = QuickChanges.saveToLibrary(start, EVERYWHERE, "Cow fixes");
		PlaceSets everywhere = saved.profile().everywhere();
		assertNull(everywhere.quickChanges());
		String newId = everywhere.attached().get(0).setId();
		assertEquals(List.of(newId, QUIET_PIGS.id()), ids(everywhere));
		SoundSet set = saved.library().get(newId);
		assertNotNull(set, "the saved set is in the library");
		assertEquals("Cow fixes", set.name());
		assertEquals(quick, set.entries());
		assertTrue(QuickActions.isMuted(saved, EVERYWHERE, COW), "the mute still applies from the library set");

		Workspace next = apply(saved, EVERYWHERE, PIG, new EntryEdit.MuteWithCue(true));
		assertEquals(List.of(SoundSet.QUICK_ID, newId, QUIET_PIGS.id()), ids(next.profile().everywhere()),
				"the next quick action starts a new Quick changes set, pinned above the saved set");
		assertEquals(1, next.profile().everywhere().quickEntries().size());
	}

	@Test
	void saveToLibraryUniqueName() {
		SoundSet existing = new SoundSet("set-old", "Quick changes", List.of(SetEntry.of("e1", PIG).withVolume(10)));
		Workspace start = toggle(ws(PlaceSets.EMPTY, existing), EVERYWHERE, COW);
		Workspace saved = QuickChanges.saveToLibrary(start, EVERYWHERE, "Quick changes");
		SoundSet set = saved.library().get(saved.profile().everywhere().attached().get(0).setId());
		assertEquals("Quick changes (2)", set.name());
		assertEquals(existing, saved.library().get("set-old"));
	}

	@Test
	void saveToLibraryInAZoneLeavesEverywhereAlone() {
		Workspace start = toggle(toggle(ws(PlaceSets.EMPTY, PlaceSets.EMPTY), EVERYWHERE, PIG), PEN, COW);
		Workspace saved = QuickChanges.saveToLibrary(start, PEN, "Pen");
		assertEquals(start.profile().everywhere(), saved.profile().everywhere());
		assertNull(saved.profile().zone("pen").sets().quickChanges());
		assertEquals(1, saved.library().size());
	}

	@Test
	void nothingToSaveIsAProblemAndChangesNothing() {
		Workspace start = ws(PlaceSets.EMPTY.attach(QUIET_PIGS.id()), QUIET_PIGS);
		assertEquals("soundsculptor.sets.error.no_quick_changes", QuickChanges.canSave(start, EVERYWHERE).key());
		assertSame(start, QuickChanges.saveToLibrary(start, EVERYWHERE, "Nothing"));
		assertEquals("soundsculptor.sets.error.no_quick_changes", QuickChanges.canSave(start, PlaceRef.zone("gone")).key(),
				"a zone deleted since the dialog opened has nothing to save");
		assertSame(start, QuickChanges.saveToLibrary(start, PlaceRef.zone("gone"), "Nothing"));
	}

	@Test
	void fullLibraryIsAProblemAndChangesNothing() {
		List<SoundSet> sets = new ArrayList<>();
		for (int i = 0; i < SetLibrary.MAX_SETS; i++) {
			sets.add(new SoundSet("s" + i, "Set " + i, List.of()));
		}
		Workspace start = toggle(ws(PlaceSets.EMPTY, sets.toArray(SoundSet[]::new)), EVERYWHERE, COW);
		Message problem = QuickChanges.canSave(start, EVERYWHERE);
		assertEquals("soundsculptor.sets.error.library_full", problem.key());
		assertEquals(List.of(SetLibrary.MAX_SETS), problem.args());
		assertSame(start, QuickChanges.saveToLibrary(start, EVERYWHERE, "One too many"));
	}

	@Test
	void fullPlaceIsAProblemAndChangesNothing() {
		List<SoundSet> sets = new ArrayList<>();
		PlaceSets everywhere = PlaceSets.EMPTY;
		for (int i = 0; i < PlaceSets.MAX_SETS; i++) {
			sets.add(new SoundSet("s" + i, "Set " + i, List.of()));
			everywhere = everywhere.attach("s" + i);
		}
		Workspace start = toggle(ws(everywhere, sets.toArray(SoundSet[]::new)), EVERYWHERE, COW);
		assertEquals(PlaceSets.MAX_SETS + 1, start.profile().everywhere().attached().size(), "Quick changes is not counted");
		Message problem = QuickChanges.canSave(start, EVERYWHERE);
		assertEquals("soundsculptor.sets.error.place_full", problem.key());
		assertSame(start, QuickChanges.saveToLibrary(start, EVERYWHERE, "One too many"));
	}

	// ---- Editing the single-sound entry (FR-1.5) ----

	@Test
	void anExistingSingleSoundEntryIsEditedAndTurnedOnInstead() {
		SetEntry old = SetEntry.of("mine", COW).withVolume(50).withEnabled(false);
		Workspace start = ws(PlaceSets.EMPTY, PlaceSets.EMPTY.withQuickEntries(List.of(old)));
		Workspace after = apply(start, PEN, COW, new EntryEdit.Mute(true));
		assertEquals(List.of(old.withEnabled(true).withMute(true)), after.profile().zone("pen").sets().quickEntries());
	}

	@Test
	void volumeAndLimitAndCueEditTheSameEntry() {
		Workspace ws = ws(PlaceSets.EMPTY, PlaceSets.EMPTY);
		ws = apply(ws, PEN, COW, new EntryEdit.Volume(30));
		ws = apply(ws, PEN, COW, new EntryEdit.SetLimit(2, 1000, false));
		ws = apply(ws, PEN, COW, new EntryEdit.Cue(true));
		SetEntry entry = quickEntry(ws, PEN, COW);
		assertEquals(30, entry.volume());
		assertEquals(new Limit(2, 1000, false), entry.limit());
		assertEquals(Boolean.TRUE, entry.cue());
		assertEquals(1, ws.profile().zone("pen").sets().quickEntries().size());
	}

	@Test
	void newEntriesTakeTheFirstFreeEntryId() {
		Workspace ws = ws(PlaceSets.EMPTY.withQuickEntries(List.of(SetEntry.of("e1", "*villager*").withVolume(40))));
		ws = apply(ws, EVERYWHERE, COW, new EntryEdit.Mute(true));
		assertEquals("e2", quickEntry(ws, EVERYWHERE, COW).id());
	}

	@Test
	void unmuteKeepsAnEntryThatStillSetsSomethingAndOtherQuickEntries() {
		SetEntry other = SetEntry.of("e1", "*villager*").withVolume(40);
		Workspace start = ws(PlaceSets.EMPTY.withQuickEntries(List.of(other)));
		Workspace muted = toggle(start, EVERYWHERE, COW);
		assertEquals(start, toggle(muted, EVERYWHERE, COW), "the cue the mute added goes with it");

		SetEntry old = SetEntry.of("mine", COW).withMute(true).withVolume(50).withEnabled(false);
		Workspace disabled = ws(PlaceSets.EMPTY.withQuickEntries(List.of(old)));
		Workspace after = apply(disabled, EVERYWHERE, COW, new EntryEdit.Mute(null));
		assertEquals(List.of(SetEntry.of("mine", COW).withVolume(50).withEnabled(false)), after.profile().everywhere().quickEntries(),
				"clearing never turns an entry on");
	}

	@Test
	void anEditThatChangesNothingOffersNoUndo() {
		Workspace start = ws(PlaceSets.EMPTY);
		QuickActionResult clear = QuickActions.apply(start, EVERYWHERE, COW, new EntryEdit.Mute(null));
		assertTrue(clear.ok());
		assertSame(start, clear.after());
		assertFalse(clear.changed());

		Workspace muted = apply(start, EVERYWHERE, COW, new EntryEdit.Mute(true));
		QuickActionResult again = QuickActions.apply(muted, EVERYWHERE, COW, new EntryEdit.Mute(true));
		assertSame(muted, again.after());
		assertFalse(again.changed());
	}

	@Test
	void aDisabledMutingEntryDoesNotCountAsMuted() {
		Workspace ws = ws(PlaceSets.EMPTY.withQuickEntries(List.of(SetEntry.of("e1", COW).withMute(true).withEnabled(false))));
		assertFalse(QuickActions.isMuted(ws, EVERYWHERE, COW));
		assertEquals(new EntryEdit.MuteWithCue(false), QuickActions.toggleMute(ws, EVERYWHERE, COW, false));
	}

	@Test
	void quickMuteStartsTheEntryWithTheCueDefaultAndKeepsACueItSets() {
		for (boolean cueDefault : new boolean[] {true, false}) {
			Workspace ws = ws(PlaceSets.EMPTY);
			ws = apply(ws, EVERYWHERE, COW, QuickActions.toggleMute(ws, EVERYWHERE, COW, cueDefault));
			assertEquals(cueDefault, quickEntry(ws, EVERYWHERE, COW).cue());
		}
		Workspace cueOff = ws(PlaceSets.EMPTY.withQuickEntries(List.of(SetEntry.of("e1", COW).withCue(false).withVolume(50))));
		cueOff = toggle(cueOff, EVERYWHERE, COW);
		assertEquals(Boolean.FALSE, quickEntry(cueOff, EVERYWHERE, COW).cue());
	}

	@Test
	void outOfRangeValuesAreRefusedWithTheEditorMessage() {
		Workspace start = ws(PlaceSets.EMPTY);
		QuickActionResult result = QuickActions.apply(start, EVERYWHERE, COW, new EntryEdit.Volume(250));
		assertEquals(TraceEditError.VOLUME_OUT_OF_RANGE.message(), result.error());
		assertEquals(List.of(SetEntry.MIN_VOLUME, SetEntry.MAX_VOLUME), result.error().args());
		assertSame(start, result.after());
		assertFalse(result.changed());
	}

	@Test
	void aZoneDeletedSinceTheListOpenedIsRefused() {
		Workspace start = ws(PlaceSets.EMPTY);
		PlaceRef gone = PlaceRef.zone("gone");
		assertEquals(TraceEditError.ZONE_NOT_FOUND.message(), QuickActions.apply(start, gone, COW, new EntryEdit.Mute(true)).error());
		assertNull(quickEntry(start, gone, COW));
		assertFalse(QuickActions.isMuted(start, gone, COW));
	}

	@Test
	void aFullQuickChangesRefusesNewEntriesButOtherPlacesStillWork() {
		List<SetEntry> entries = new ArrayList<>();
		for (int i = 0; i < SoundSet.MAX_ENTRIES; i++) {
			entries.add(SetEntry.of("g" + i, "minecraft:sound." + i).withVolume(50));
		}
		Workspace full = ws(PlaceSets.EMPTY.withQuickEntries(entries));
		QuickActionResult refused = QuickActions.apply(full, EVERYWHERE, COW, new EntryEdit.Mute(true));
		assertEquals("soundsculptor.sets.error.entries_full", refused.error().key());
		assertSame(full, refused.after());
		assertTrue(QuickActions.apply(full, EVERYWHERE, "minecraft:sound.7", new EntryEdit.Mute(true)).ok(),
				"an existing entry can still change");
		assertTrue(QuickActions.apply(full, PEN, COW, new EntryEdit.Mute(true)).ok());
	}
}
