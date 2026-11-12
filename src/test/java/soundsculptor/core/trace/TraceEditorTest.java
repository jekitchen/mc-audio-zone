package soundsculptor.core.trace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import soundsculptor.core.Message;
import soundsculptor.core.profile.Profile;
import soundsculptor.core.quick.QuickActions;
import soundsculptor.core.sets.Limit;
import soundsculptor.core.sets.PlaceRef;
import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.SetLibrary;
import soundsculptor.core.sets.SoundPattern;
import soundsculptor.core.sets.SoundSet;
import soundsculptor.core.sets.Workspace;
import soundsculptor.core.undo.UndoSlot;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;

import org.junit.jupiter.api.Test;

/** PRD FR-14.4: editing Sound Set entries and zones straight from the sound trace. */
class TraceEditorTest {
	private static final String WORLD = "play.example.net";
	private static final String OVERWORLD = "minecraft:overworld";
	private static final String AMBIENT = "minecraft:entity.villager.ambient";
	private static final LastPlay PLAY = new LastPlay(true, WORLD, OVERWORLD, 2, 0, 0, 50, false, false);
	private static final PlaceRef HALL = PlaceRef.zone("hall");

	private static SoundSet farm(SetEntry... entries) {
		return new SoundSet("farm", "Farm animals", entries.length == 0
				? List.of(SetEntry.of("e1", "*villager*").withVolume(50)) : List.of(entries));
	}

	private static PlaceSets attached(String... setIds) {
		PlaceSets place = PlaceSets.EMPTY;
		for (String id : setIds) {
			place = place.attach(id);
		}
		return place;
	}

	private static Zone sphere(String id, double radius, PlaceSets sets) {
		return new Zone(id, id, true, WORLD, OVERWORLD, new Point(0, 0, 0), new Shape.Sphere(radius), sets);
	}

	private static Workspace ws(List<SoundSet> sets, PlaceSets everywhere, Zone... zones) {
		return new Workspace(Profile.empty("Test").withEverywhere(everywhere).withZones(List.of(zones)), new SetLibrary(sets));
	}

	/** Farm animals ({@code *villager*} at 50%) attached to Everywhere only. */
	private static Workspace farmEverywhere() {
		return ws(List.of(farm()), attached("farm"));
	}

	private static EntryLocation inLibrary(PlaceRef place, String setId, String entryId) {
		return new EntryLocation(place, setId, entryId);
	}

	private static EntryLocation inQuick(PlaceRef place, String entryId) {
		return new EntryLocation(place, SoundSet.QUICK_ID, entryId);
	}

	private static TraceEditContext context(LastPlay play) {
		return new TraceEditContext(AMBIENT, play, false, false);
	}

	private static TraceEditResult ok(TraceEditResult result) {
		assertTrue(result.ok(), () -> "edit refused: " + result.error());
		return result;
	}

	private static SetEntry libraryEntry(Workspace ws, String setId, String entryId) {
		return ws.library().get(setId).entry(entryId);
	}

	// --- FR-14 acceptance checks ---

	@Test
	void editWinningEntryLevelUpdatesPrediction() {
		Workspace start = ws(List.of(farm()), PlaceSets.EMPTY, sphere("hall", 10, attached("farm")));
		TraceEditContext ctx = context(PLAY);
		assertEquals(50, ctx.trace(start).volumePercent(), 1e-6);

		TraceEditResult result = ok(TraceEditor.edit(ctx, start, inLibrary(HALL, "farm", "e1"), new EntryEdit.Volume(20)));

		assertEquals(20, result.predicted().volumePercent(), 1e-6);
		assertTrue(result.predictedForNextPlay(), "labelled 'Predicted for next play' until the sound plays again");
		assertEquals(20, libraryEntry(result.after(), "farm", "e1").volume(), "the library set changes");
		assertEquals(start.profile(), result.after().profile(), "the profile only refers to the set");

		UndoSlot<Workspace> undo = new UndoSlot<>();
		undo.offer(result.before(), result.after(), result.toastKey(), result.toastArgs(), 1000);
		Workspace restored = undo.undo(result.after(), 2000).orElseThrow();
		assertSame(start, restored);
		assertEquals(50, ctx.trace(restored).volumePercent(), 1e-6);
	}

	@Test
	void editingALibraryEntryChangesEveryPlaceThatUsesTheSet() {
		// Sets are linked (FR-4.9): an edit shown on Everywhere's line also changes the zone that uses the set.
		Workspace start = ws(List.of(farm()), attached("farm"), sphere("hall", 10, attached("farm")));
		TraceEditResult result = ok(TraceEditor.edit(context(PLAY), start, inLibrary(PlaceRef.EVERYWHERE, "farm", "e1"),
				new EntryEdit.Volume(20)));
		SetEntry inZone = result.predicted().zones().get(0).sets().winners().get(TraceProperty.LOUDNESS).entry();
		assertEquals(20, inZone.volume());
		assertEquals(20, result.predicted().volumePercent(), 1e-6);
	}

	@Test
	void turnOffWinnerNextInLineWins() {
		SetEntry wildcard = SetEntry.of("w", "*villager*").withVolume(30);
		SetEntry exact = SetEntry.of("e", AMBIENT).withVolume(60);
		Workspace start = ws(List.of(farm(wildcard, exact)), attached("farm"));
		assertEquals(60, context(PLAY).trace(start).volumePercent(), 1e-6);

		TraceEditResult result = ok(TraceEditor.edit(context(PLAY), start, inLibrary(PlaceRef.EVERYWHERE, "farm", "e"),
				new EntryEdit.Enabled(false)));

		EntryWinner winner = result.predicted().everywhere().winners().get(TraceProperty.LOUDNESS);
		assertEquals("w", winner.entry().id());
		assertEquals("farm", winner.setId());
		assertEquals(30, result.predicted().volumePercent(), 1e-6);
		assertFalse(libraryEntry(result.after(), "farm", "e").enabled(), "the entry is kept, only turned off");
	}

	@Test
	void addToQuickChangesOnEverywhereMutesAndIsListedFirst() {
		Workspace start = farmEverywhere();

		TraceEditResult result = ok(TraceEditor.addToQuickChanges(context(PLAY), start, PlaceRef.EVERYWHERE, new EntryEdit.Mute(true)));

		assertEquals(0, result.predicted().volumePercent(), 1e-6);
		List<SetLine> sets = result.predicted().everywhere().sets();
		assertTrue(sets.get(0).quick(), "Quick changes is the first set listed: " + sets);
		assertEquals("farm", sets.get(1).setId());
		SetEntry added = QuickActions.quickEntry(result.after(), PlaceRef.EVERYWHERE, AMBIENT);
		assertEquals("e1", added.id(), "a new Quick changes entry gets the set's next entry ID, not an exact: ID");
		assertTrue(added.pattern().isExact());
		assertEquals(Boolean.TRUE, added.mute());
		assertSame(start.library(), result.after().library(), "the library is not touched");
		assertEquals(TraceEditor.TOAST_ADDED, result.toastKey());
		assertEquals(List.of(AMBIENT), result.toastArgs());
	}

	@Test
	void unmuteOnQuickLineOverLibraryMuteWritesNotMuted() {
		SoundSet hush = new SoundSet("hush", "Hush", List.of(SetEntry.of("e1", "*villager*").withMute(true)));
		PlaceSets everywhere = attached("hush").withQuickEntries(List.of(SetEntry.of("e1", AMBIENT).withCue(true)));
		Workspace start = ws(List.of(hush), everywhere);
		assertEquals(0, context(PLAY).trace(start).volumePercent(), 1e-6, "the library set mutes it");

		EntryEdit unmute = QuickActions.toggleMute(start, PlaceRef.EVERYWHERE, AMBIENT, true);
		assertEquals(new EntryEdit.Mute(false), unmute, "Unmute over a library mute writes Not muted");
		TraceEditResult result = ok(TraceEditor.addToQuickChanges(context(PLAY), start, PlaceRef.EVERYWHERE, unmute));

		SetEntry quick = QuickActions.quickEntry(result.after(), PlaceRef.EVERYWHERE, AMBIENT);
		assertEquals("e1", quick.id());
		assertEquals(Boolean.FALSE, quick.mute());
		assertEquals(Boolean.TRUE, quick.cue(), "the entry keeps its other settings");
		assertEquals(100, result.predicted().volumePercent(), 1e-6);
		assertSame(start.library(), result.after().library(), "the library set still mutes everywhere else");
		assertEquals(TraceEditor.TOAST_CHANGED, result.toastKey());
	}

	@Test
	void level250Refused() {
		Workspace start = farmEverywhere();

		TraceEditResult result = TraceEditor.edit(context(PLAY), start, inLibrary(PlaceRef.EVERYWHERE, "farm", "e1"),
				new EntryEdit.Volume(250));

		assertFalse(result.ok());
		assertEquals(TraceEditError.VOLUME_OUT_OF_RANGE.message(), result.error());
		assertEquals("soundsculptor.trace.edit.error.volume_out_of_range", result.error().key());
		assertSame(start, result.after(), "nothing changes");
		assertSame(start, result.before());
		assertEquals(50, result.predicted().volumePercent(), 1e-6);

		TraceEditResult added = TraceEditor.addToQuickChanges(context(PLAY), start, PlaceRef.EVERYWHERE, new EntryEdit.Volume(250));
		assertEquals(TraceEditError.VOLUME_OUT_OF_RANGE.message(), added.error());
		assertSame(start, added.after());
	}

	@Test
	void lineOfSetUsedInTwoZonesSaysTwoPlaces() {
		Workspace start = ws(List.of(farm()), PlaceSets.EMPTY, sphere("a", 10, attached("farm")), sphere("b", 20, attached("farm")));

		SoundTrace t = context(PLAY).trace(start);

		assertEquals(2, t.zones().size());
		for (ZoneLine zone : t.zones()) {
			assertEquals(2, zone.sets().sets().get(0).placesUsing(), zone.zone().id());
		}
		// Another world's profile using the set in 3 places adds those 3 places.
		TraceEditContext withOthers = new TraceEditContext(AMBIENT, PLAY, false, false, setId -> setId.equals("farm") ? 3 : 0);
		assertEquals(5, withOthers.trace(start).zones().get(0).sets().sets().get(0).placesUsing(),
				"places in other profiles count too");
	}

	// --- Entry line controls ---

	@Test
	void entryLineControlsSetEachProperty() {
		Workspace start = ws(List.of(farm(SetEntry.of("e1", AMBIENT))), attached("farm"));
		TraceEditContext ctx = context(PLAY);
		EntryLocation e = inLibrary(PlaceRef.EVERYWHERE, "farm", "e1");

		Workspace w = ok(TraceEditor.edit(ctx, start, e, new EntryEdit.Mute(true))).after();
		assertEquals(Boolean.TRUE, libraryEntry(w, "farm", "e1").mute());
		w = ok(TraceEditor.edit(ctx, w, e, new EntryEdit.Cue(false))).after();
		assertEquals(Boolean.FALSE, libraryEntry(w, "farm", "e1").cue());
		w = ok(TraceEditor.edit(ctx, w, e, new EntryEdit.SetLimit(3, 1000, true))).after();
		assertEquals(new Limit(3, 1000, true), libraryEntry(w, "farm", "e1").limit());
		TraceEditResult limited = TraceEditor.edit(ctx, w, e, new EntryEdit.Volume(70));
		assertEquals(new Limit(3, 1000, true), limited.predicted().limit());
		w = ok(TraceEditor.edit(ctx, w, e, EntryEdit.ClearLimit.INSTANCE)).after();
		assertNull(libraryEntry(w, "farm", "e1").limit());
		w = ok(TraceEditor.edit(ctx, w, e, new EntryEdit.Mute(null))).after();
		assertNull(libraryEntry(w, "farm", "e1").mute(), "Mute off clears it, so the entry no longer sets loudness");
		assertEquals(1, w.library().get("farm").entries().size(), "an entry edited on its line stays, even setting little");
	}

	@Test
	void limitOutOfRangeIsRefused() {
		Workspace start = farmEverywhere();
		EntryLocation e = inLibrary(PlaceRef.EVERYWHERE, "farm", "e1");
		assertEquals(TraceEditError.LIMIT_COUNT_OUT_OF_RANGE.message(),
				TraceEditor.edit(context(PLAY), start, e, new EntryEdit.SetLimit(0, 1000, false)).error());
		assertEquals(TraceEditError.LIMIT_COUNT_OUT_OF_RANGE.message(),
				TraceEditor.edit(context(PLAY), start, e, new EntryEdit.SetLimit(Limit.MAX_COUNT + 1, 1000, false)).error());
		assertEquals(TraceEditError.LIMIT_WINDOW_OUT_OF_RANGE.message(),
				TraceEditor.edit(context(PLAY), start, e, new EntryEdit.SetLimit(3, 10, false)).error());
		assertEquals(TraceEditError.VOLUME_OUT_OF_RANGE.message(),
				TraceEditor.edit(context(PLAY), start, e, new EntryEdit.Volume(-1)).error());
	}

	@Test
	void volumeEdgesAreAccepted() {
		Workspace start = farmEverywhere();
		EntryLocation e = inLibrary(PlaceRef.EVERYWHERE, "farm", "e1");
		assertEquals(0, libraryEntry(ok(TraceEditor.edit(context(PLAY), start, e, new EntryEdit.Volume(0))).after(), "farm", "e1").volume());
		assertEquals(200, libraryEntry(ok(TraceEditor.edit(context(PLAY), start, e, new EntryEdit.Volume(200))).after(), "farm", "e1").volume());
	}

	@Test
	void unknownSetEntryOrZoneIsRefused() {
		Workspace start = ws(List.of(farm()), attached("farm"), sphere("hall", 10, PlaceSets.EMPTY));
		Message noEntry = TraceEditError.RULE_NOT_FOUND.message();
		Message noZone = TraceEditError.ZONE_NOT_FOUND.message();
		assertEquals(noEntry, TraceEditor.edit(context(PLAY), start, inLibrary(PlaceRef.EVERYWHERE, "gone", "e1"),
				new EntryEdit.Mute(true)).error(), "the set was deleted since the panel opened");
		assertEquals(noEntry, TraceEditor.edit(context(PLAY), start, inLibrary(PlaceRef.EVERYWHERE, "farm", "gone"),
				new EntryEdit.Mute(true)).error());
		assertEquals(noEntry, TraceEditor.edit(context(PLAY), start, inQuick(PlaceRef.EVERYWHERE, "e1"), new EntryEdit.Mute(true)).error(),
				"Everywhere has no Quick changes");
		assertEquals(noZone, TraceEditor.edit(context(PLAY), start, inQuick(PlaceRef.zone("nope"), "e1"), new EntryEdit.Mute(true)).error());
		assertEquals(noZone, TraceEditor.setZoneEnabled(context(PLAY), start, "nope", false).error());
		assertEquals(noZone, TraceEditor.addToQuickChanges(context(PLAY), start, PlaceRef.zone("nope"), new EntryEdit.Mute(true)).error());
	}

	@Test
	void editingAQuickEntryChangesOnlyThatPlace() {
		PlaceSets everywhere = PlaceSets.EMPTY.withQuickEntries(List.of(SetEntry.of("e1", AMBIENT).withVolume(60)));
		Zone hall = sphere("hall", 10, PlaceSets.EMPTY.withQuickEntries(List.of(SetEntry.of("e1", "*villager*").withVolume(50))));
		Workspace start = ws(List.of(farm()), everywhere, hall);

		Workspace after = ok(TraceEditor.edit(context(PLAY), start, inQuick(PlaceRef.EVERYWHERE, "e1"), new EntryEdit.Volume(40))).after();

		assertEquals(40, after.profile().everywhere().quickEntries().get(0).volume());
		assertEquals(hall, after.profile().zone("hall"));
		assertSame(start.library(), after.library());
	}

	@Test
	void editsLeaveTheOtherEntriesAndSetsAlone() {
		SetEntry cows = SetEntry.of("e2", "*cow*").withMute(true);
		SoundSet birds = new SoundSet("birds", "Birds", List.of(SetEntry.of("e1", "*parrot*").withMute(true)));
		Zone hall = sphere("hall", 10, attached("farm", "birds"));
		Workspace start = ws(List.of(farm(SetEntry.of("e1", "*villager*").withVolume(50), cows), birds), PlaceSets.EMPTY, hall);

		Workspace after = ok(TraceEditor.edit(context(PLAY), start, inLibrary(HALL, "farm", "e1"), new EntryEdit.Volume(20))).after();

		assertSame(cows, libraryEntry(after, "farm", "e2"));
		assertSame(birds, after.library().get("birds"));
		assertEquals("Farm animals", after.library().get("farm").name());
		assertEquals(start.profile(), after.profile());
	}

	@Test
	void unchangedEditChangesNothing() {
		Workspace start = farmEverywhere();
		TraceEditResult result = ok(TraceEditor.edit(context(PLAY), start, inLibrary(PlaceRef.EVERYWHERE, "farm", "e1"),
				new EntryEdit.Volume(50)));
		assertSame(start, result.after(), "nothing to save or undo");
	}

	// --- Zone line controls ---

	@Test
	void disablingAZoneLineDropsItFromThePrediction() {
		Zone hall = sphere("hall", 10, attached("farm"));
		Workspace start = ws(List.of(farm()), PlaceSets.EMPTY, hall);

		TraceEditResult result = ok(TraceEditor.setZoneEnabled(context(PLAY), start, "hall", false));

		Zone off = result.after().profile().zone("hall");
		assertFalse(off.enabled());
		assertTrue(result.predicted().zones().isEmpty());
		assertEquals(100, result.predicted().volumePercent(), 1e-6);
		assertEquals(hall.sets(), off.sets());
		assertEquals(TraceEditor.TOAST_CHANGED, result.toastKey());
		assertEquals(List.of("hall"), result.toastArgs());
	}

	@Test
	void disabledZoneKeepsItsKind() {
		Zone bubble = sphere("bubble", 8, attached("farm")).asBubble();
		Workspace start = ws(List.of(farm()), PlaceSets.EMPTY, bubble);
		Zone off = ok(TraceEditor.setZoneEnabled(context(PLAY), start, "bubble", false)).after().profile().zone("bubble");
		assertTrue(off.isBubble());
		assertEquals(bubble.mode(), off.mode());
		assertEquals(bubble.edge(), off.edge());
	}

	// --- Add to Quick changes ---

	@Test
	void addToQuickChangesInAZoneCreatesASingleSoundEntryThere() {
		Zone hall = sphere("hall", 10, attached("farm"));
		Workspace start = ws(List.of(farm()), PlaceSets.EMPTY, hall);

		TraceEditResult result = ok(TraceEditor.addToQuickChanges(context(PLAY), start, HALL, new EntryEdit.Volume(25)));

		PlaceSets sets = result.after().profile().zone("hall").sets();
		assertEquals(1, sets.quickEntries().size());
		SetEntry added = sets.quickEntries().get(0);
		assertEquals(AMBIENT, added.pattern().text());
		assertEquals(25, added.volume());
		assertNull(result.after().profile().everywhere().quickChanges());
		assertEquals(25, result.predicted().volumePercent(), 1e-6);
		assertTrue(result.predicted().zones().get(0).sets().sets().get(0).quick());
	}

	@Test
	void addToQuickChangesReusesTheSoundsSingleEntryEvenWithAnOldExactId() {
		// Saved data from earlier builds names the single-sound entry "exact:<id>"; it is found by its pattern.
		SetEntry existing = new SetEntry("exact:" + AMBIENT, false, SoundPattern.of(AMBIENT), null, 60, null, null);
		Workspace start = ws(List.of(), PlaceSets.EMPTY.withQuickEntries(List.of(existing)));

		TraceEditResult result = ok(TraceEditor.addToQuickChanges(context(PLAY), start, PlaceRef.EVERYWHERE, new EntryEdit.Mute(true)));

		List<SetEntry> entries = result.after().profile().everywhere().quickEntries();
		assertEquals(1, entries.size());
		SetEntry e = entries.get(0);
		assertEquals("exact:" + AMBIENT, e.id());
		assertTrue(e.enabled(), "the reused entry is turned on, or the edit would have no effect");
		assertEquals(Boolean.TRUE, e.mute());
		assertEquals(60, e.volume());
		assertEquals(0, result.predicted().volumePercent(), 1e-6);
		assertEquals(TraceEditor.TOAST_CHANGED, result.toastKey());
	}

	@Test
	void addToQuickChangesDoesNotReuseAGroupEntry() {
		Workspace start = ws(List.of(), PlaceSets.EMPTY.withQuickEntries(List.of(SetEntry.of("e1", "*villager*").withVolume(30))));
		TraceEditResult result = ok(TraceEditor.addToQuickChanges(context(PLAY), start, PlaceRef.EVERYWHERE, new EntryEdit.Volume(80)));
		List<SetEntry> entries = result.after().profile().everywhere().quickEntries();
		assertEquals(2, entries.size());
		assertEquals(30, entries.get(0).volume());
		assertEquals("e2", entries.get(1).id());
		assertEquals(80, result.predicted().volumePercent(), 1e-6);
	}

	@Test
	void addToQuickChangesRefusesPastTheEntryLimit() {
		List<SetEntry> many = new ArrayList<>();
		for (int i = 0; i < SoundSet.MAX_ENTRIES; i++) {
			many.add(SetEntry.of("r" + i, "minecraft:block.b" + i).withMute(true));
		}
		Workspace full = ws(List.of(), PlaceSets.EMPTY.withQuickEntries(many));
		TraceEditResult result = TraceEditor.addToQuickChanges(context(PLAY), full, PlaceRef.EVERYWHERE, new EntryEdit.Mute(true));
		assertEquals(Message.of("soundsculptor.sets.error.entries_full", SoundSet.MAX_ENTRIES), result.error());
		assertSame(full, result.after());
	}

	// --- Prediction and Undo ---

	@Test
	void notHeardYetEditsAreNotLabelledPredicted() {
		Workspace start = farmEverywhere();
		TraceEditResult result = ok(TraceEditor.edit(context(null), start, inLibrary(PlaceRef.EVERYWHERE, "farm", "e1"),
				new EntryEdit.Volume(40)));
		assertFalse(result.predictedForNextPlay());
		assertTrue(result.predicted().notices().contains(TraceNotice.NOT_HEARD_YET));
		assertEquals(40, result.predicted().volumePercent(), 1e-6);
	}

	@Test
	void predictionUsesThePlayersPositionForBubbles() {
		LastPlay play = new LastPlay(true, WORLD, OVERWORLD, 102, 0, 0, 100, 0, 0, 100, false, false);
		Zone bubble = sphere("bubble", 8, attached("farm")).asBubble();
		Workspace start = ws(List.of(farm(SetEntry.of("e1", "*villager*").withVolume(100))), PlaceSets.EMPTY, bubble);
		TraceEditResult result = ok(TraceEditor.edit(context(play), start, inLibrary(PlaceRef.zone("bubble"), "farm", "e1"),
				new EntryEdit.Volume(30)));
		assertEquals(30, result.predicted().volumePercent(), 1e-6, "the bubble sits on the player, 2 blocks from the sound");
	}

	@Test
	void toastNamesTheEditedPatternWithoutJoiningSentenceFragments() {
		TraceEditResult edited = ok(TraceEditor.edit(context(PLAY), farmEverywhere(), inLibrary(PlaceRef.EVERYWHERE, "farm", "e1"),
				new EntryEdit.Volume(40)));
		assertEquals("soundsculptor.trace.edit.toast.changed", edited.toastKey());
		assertEquals(List.of("*:*villager*"), edited.toastArgs());
		TraceEditResult added = ok(TraceEditor.addToQuickChanges(context(PLAY), ws(List.of(), PlaceSets.EMPTY), PlaceRef.EVERYWHERE,
				new EntryEdit.Mute(true)));
		assertEquals("soundsculptor.trace.edit.toast.added", added.toastKey());
		assertEquals(List.of(AMBIENT), added.toastArgs());
	}

	@Test
	void refusedEditOffersNoToast() {
		TraceEditResult refused = TraceEditor.edit(context(PLAY), farmEverywhere(), inLibrary(PlaceRef.EVERYWHERE, "farm", "e1"),
				new EntryEdit.Volume(999));
		assertNull(refused.toastKey());
		assertTrue(refused.toastArgs().isEmpty());
	}
}
