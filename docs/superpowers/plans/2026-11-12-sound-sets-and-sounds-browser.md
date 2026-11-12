# Sound Sets (M8c) and Sounds Browser (M8d) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the rule lists (one global list, one list per zone) with a shared library of Sound Sets attached in order to Everywhere and to each zone. Then merge the Recently Played and Search tabs into one Sounds browser, which also works as the picker for adding sounds to a set.

**Architecture:**
- A place's attached sets are flattened into one ordered resolver per place, as layers in list order with each set's entries sorted by specificity. The first entry that sets a property wins. This implements FR-4.5 steps 1 and 2 with the existing per-ID cache, so the hot path and its budgets stay as they are.
- Profiles reference library sets by ID. The library lives in one file per set.
- The screen code works on a `Workspace` (profile plus library), which is swapped as one immutable snapshot.

**Tech Stack:** Java 25, Fabric (Minecraft 26.2 and 26.3, one jar each), Gson, JUnit 5, Fabric client game tests.

**Spec:** `docs/PRD.md` at e040f55: FR-1, FR-3, FR-4, FR-8.6, FR-8.10, FR-9, FR-11, FR-12.1, FR-14, sections 7.4–7.5, 10.1 (M8c, M8d) and 11.4 (P13–P16).

## Global Constraints

- Commit only as `jekitchen <245499875+jekitchen@users.noreply.github.com>`. Never use `--no-verify`.
- Worktrees live in `C:\Claude\Projects\mc-audio-zone\.worktrees\<branch>`.
- Every PR builds and passes the game tests on both versions:
  - 26.3: `./gradlew build`
  - 26.2: `./gradlew build -Pminecraft_version=26.2 -Pfabric_api_version=0.161.0+26.2`
  - Game tests: `CI=1 ./gradlew runClientGameTest`
  - Merge when all 8 CI checks pass.
- Names:
  - Packages `soundsculptor.core.*` (pure Java, no Minecraft imports) and `soundsculptor.mc.*`.
  - UI kit classes are prefixed `Sculpt`.
  - Lang keys are `soundsculptor.*` in `src/main/resources/assets/soundsculptor/lang/en_us.json`. `LangKeysTest` must stay green.
- Limits:
  - 256 sets in the library.
  - 512 entries per set.
  - 32 sets attached to one place. A place's Quick changes set does not count toward the 32.
  - 64 zones.
  - Level 0–100% in MVP. Above 100% stays behind the existing boost confirm.
  - Set name at most 64 characters.
- Files:
  - `config/soundsculptor/profiles/<key>.json` (schema 3)
  - `config/soundsculptor/sets/<set id>.json` (schema 3)
  - `config/soundsculptor/sets/deleted.json` (names of deleted sets, see Task 4)
  - `config/soundsculptor/exports/`, `config/soundsculptor/imports/`
  - Writes are atomic with a `.bak`. A file that fails to load is renamed `.broken` and a toast explains.
  - Profiles in schema 1 or 2 are not migrated (Decisions Log 2026-11-11). They load as broken, with the message "This profile was made by an earlier test build of Sound Sculptor and can't be read."
- Hot path:
  - 5 µs typical and 10 µs worst case per sound start. No allocation per sound.
  - Flattening happens only when a set, an attachment or a place changes (PRD 7.4).
- UI:
  - Editors use Save and Cancel, with the discard dialog (FR-12.4).
  - Quick actions apply at once with a 5-second Undo toast (FR-12.5).
  - Status always uses text and an icon, never color alone.
  - Every control is reachable with Tab, arrows and Enter, and is read by the narrator.
  - Layouts must work at 854 × 480 and GUI scales 1–4.
- Ownership: Core tasks are mine (Claude). MC tasks are Max's area. Max must review this plan before Task 1, because the rename and the model switch touch his code. Agree on each MC task's builder before it starts.

## Review Focus

1. **The same entries in two layers.** A set and its Duplicate attached to one place share `SetEntry` instances, because records are copied by reference. Each layer must still resolve and trace as its own set. Test: `EntryResolverTest.duplicateAttachedBelowOriginalIsItsOwnLayer` (Task 3).
2. **A set disappears under a profile.** This covers a set deleted elsewhere, and a set file that failed to load. The profile loads, the reference is dropped, one note names the set if its name is known, and nothing crashes. Tests: `SetStoreTest.brokenSetFileIsSetAsideAndOthersLoad` (Task 4), `ProfilePruneTest.missingSetIsDroppedWithOneNote` (Task 5).
3. **Unmute when the mute comes from several sets.** Unmute on a sound muted only by Quick changes clears that setting. Unmute on a sound muted by a library set writes "Not muted". Unmute when both mute it writes "Not muted", and Undo restores both. Tests in `QuickChangesTest` (Task 7).
4. **Import name clashes.** The file's set may be different from the library set with the same ID, and another library set may already be called "\<name> (imported)". Keep both must still give a unique name and a new ID. Test: `SetImportPlanTest.keepBothNameClashGetsNumberSuffix` (Task 9).
5. **Limits are reached by a player, not by code.** A 33rd attach, a 257th set, a 513th entry, or picker Add going over 512 must show a message and change nothing; none of them may throw. Tests: `PlaceSetsTest.attachBeyond32ReturnsProblem`, `SetLibraryTest.createBeyond256ReturnsProblem` (Task 2), and `PickerModelTest.addOverLimitIsRefused` (Task 17).

---

## PR plan

| PR | Tasks | Ends with |
| --- | --- | --- |
| A | 1–6 | Sets model, layered resolver, set storage and profile schema 3. The old Rules UI keeps working through a shim that edits each place's Quick changes set. |
| B | 7–10 | Quick changes, usage counts, delete, Copy from, export/import and trace in set terms (core, plus the minimal screen wiring each task names) |
| C | 11–12 | Sound Sets tab, set editor, entry editor, Attached to |
| D | 13 | Attached sets on Everywhere and on zones; the Rules tab and the shim removed |
| E | 14–16 | Profiles tab and import dialog, trace screen, and the M8c test pass (end of M8c) |
| F | 17–21 | Sounds browser, picker, Like this, and the M8d layout pass (end of M8d) |

---

## M8c: Sound Sets

### Task 1: Rename the rule types to entry types (Core + MC, mechanical)

The PRD's word for one line of a set is "entry". This task renames only the types that survive into the new model. The types that later tasks delete keep their old names until then: `RuleTarget`, `RuleListPanel`, `ZoneRulesScreen`, `RuleCopyScreen`, `EditableRules`, `ExactRules`, `RuleLocation`, `ListTrace`. Lang keys are not touched here.

**Files:**
- Create: `scratchpad/rename_entries.py` (not committed). Same approach as the Sound Sculptor rename: `git mv`, then whole-word replacement in `.java` files.
- Modify: every file under `src/` that uses the names below.

| Old | New |
| --- | --- |
| package `soundsculptor.core.rules` | `soundsculptor.core.sets` |
| `Rule` | `SetEntry` |
| `RuleResolver` | `EntryResolver` |
| `RuleResolution` | `EntryResolution` |
| `RuleDraft` | `EntryDraft` |
| `RuleSummary` | `EntrySummary` |
| `RuleEdit` | `EntryEdit` |
| `RuleWinner` / `RuleWinReason` | `EntryWinner` / `EntryWinReason` |
| `RuleEditorScreen` | `EntryEditorScreen` |
| matching `*Test` classes | same renames |

- [ ] **Step 1:** Run the script, using the regex `\b<Old>\b` per row and longest names first, so that `RuleResolver` is replaced before `Rule`.
- [ ] **Step 2:** Run `./gradlew build` and `./gradlew build -Pminecraft_version=26.2 -Pfabric_api_version=0.161.0+26.2`. Expected: BUILD SUCCESSFUL, with the same test count as `main`.
- [ ] **Step 3:** Run `git grep -nw "Rule\|RuleResolver\|RuleResolution" -- src`. Expected: no Java hits, only comments that describe history.
- [ ] **Step 4:** Commit with the message "Rename rule types to Sound Set entry types (no behavior change)".

### Task 2: Sound Set model, place attachments and library (Core)

**Files:**
- Create in `src/main/java/soundsculptor/core/sets/`: `SoundSet.java`, `Attachment.java`, `PlaceSets.java`, `PlaceRef.java`, `SetLibrary.java`, `Workspace.java`.
- Test in `src/test/java/soundsculptor/core/sets/`: `SoundSetTest.java`, `PlaceSetsTest.java`, `SetLibraryTest.java`.

**Interfaces (produces):**

```java
public record SoundSet(String id, String name, List<SetEntry> entries) {
  public static final String QUICK_ID = "quick";
  public static final int MAX_ENTRIES = 512, MAX_NAME_LENGTH = 64;
  // ctor: id/name non-null, entries copied, entry IDs unique, size <= MAX_ENTRIES (else IllegalArgumentException)
  public static SoundSet quick(List<SetEntry> entries);       // id QUICK_ID, name ""
  public boolean isQuick();
  public SetEntry entry(String entryId);                      // null if absent
  public SoundSet withName(String v);
  public SoundSet withEntries(List<SetEntry> v);
  public SoundSet putEntry(SetEntry e);                       // replace by ID or append
  public SoundSet removeEntry(String entryId);
  public SoundSet moveEntry(String entryId, int delta);
  public String newEntryId();                                 // "e1", "e2", … first free
  public Message canAdd(int count);                           // null, or soundsculptor.sets.error.entries_full {max}
  public boolean sameContents(SoundSet o);                    // equal name and entries, in order (FR-11.5a)
}
public record Attachment(String setId, boolean enabled) {}
public record PlaceRef(String zoneId) {                       // null zoneId = Everywhere
  public static final PlaceRef EVERYWHERE = new PlaceRef(null);
  public static PlaceRef zone(String id);
  public boolean isEverywhere();
}
public record PlaceSets(List<Attachment> attached, SoundSet quickChanges) {
  public static final int MAX_SETS = 32;                      // library sets only; Quick changes not counted
  public static final PlaceSets EMPTY;
  // ctor invariants: quickChanges != null <=> attached.get(0).setId() == QUICK_ID; no duplicate set IDs;
  //                  library attachments <= MAX_SETS
  public Message canAttach(String setId);  // null, or soundsculptor.sets.error.already_attached / .place_full {max}
  public PlaceSets attach(String setId);   // appended at the bottom, enabled; returns this unchanged if canAttach != null
  public PlaceSets detach(String setId);   // detaching QUICK_ID also removes quickChanges
  public PlaceSets move(String setId, int delta);    // QUICK_ID never moves; nothing moves above it
  public PlaceSets setEnabled(String setId, boolean on);
  public PlaceSets withQuickChanges(SoundSet quick); // null removes it; non-null is pinned at index 0
  public PlaceSets quickSavedAs(String newSetId);    // quick's slot becomes newSetId (index 0); quickChanges = null
  public boolean uses(String setId);
  public PlaceSets dropMissing(Set<String> libraryIds); // removes attachments to IDs not in the library (QUICK_ID kept)
  public int librarySetCount();
}
public final class SetLibrary {
  public static final int MAX_SETS = 256;
  public static final SetLibrary EMPTY;
  public SetLibrary(Collection<SoundSet> sets);      // IllegalArgumentException on > MAX_SETS, duplicate IDs or a QUICK_ID set
  public SoundSet get(String id);
  public boolean contains(String id);
  public int size();
  public List<SoundSet> sets();                      // sorted by name, case-insensitive
  public Set<String> ids();
  public Message canCreate();                        // null, or soundsculptor.sets.error.library_full {max}
  public SetLibrary put(SoundSet s);
  public SetLibrary remove(String id);
  public String uniqueName(String wanted, String exceptId);  // "X" -> "X (2)" -> "X (3)"…, case-insensitive clash
  public String newId();                             // "set-" + 12 lowercase hex, not already in the library
}
public record Workspace(Profile profile, SetLibrary library) {}
```

- [ ] **Step 1: Write the failing tests.**
  - `SoundSetTest`:
    - `entryIdsMustBeUnique`
    - `moreThan512EntriesIsRejected`
    - `canAddReportsFullAt512`: `canAdd(1)` on 512 entries returns the key `soundsculptor.sets.error.entries_full` with arg 512.
    - `sameContentsComparesNameAndEntries`
    - `newEntryIdSkipsUsedIds`
  - `PlaceSetsTest`:
    - `quickIsPinnedFirst`: `withQuickChanges(q)` on [A, B] gives [quick, A, B].
    - `moveUpNeverPassesQuick`: `move("A", -1)` on [quick, A] is unchanged.
    - `quickNeverMoves`
    - `attachTwiceReturnsProblem`
    - `attachBeyond32ReturnsProblem`: the 33rd library attach returns `.place_full` with arg 32. With quick present, 32 library sets are still allowed.
    - `detachQuickRemovesQuickChanges`
    - `quickSavedAsTakesQuickSlot`: [quick, A] becomes [S, A] with `quickChanges` null.
    - `dropMissingKeepsQuick`
  - `SetLibraryTest`:
    - `uniqueNameAddsNumber`: "Farm animals" already exists, so the result is "Farm animals (2)". If "(2)" exists as well, the result is "(3)". With `exceptId` equal to its own ID, the same name comes back.
    - `createBeyond256ReturnsProblem`
    - `setsSortedByName`
    - `newIdIsUniqueAndWellFormed`: matches `set-[0-9a-f]{12}`.
- [ ] **Step 2:** Run `./gradlew test --tests 'soundsculptor.core.sets.*'`. Expected: FAIL (the classes do not exist yet).
- [ ] **Step 3:** Implement the interfaces above. Add the three error keys to `en_us.json`:
  - "This set is already attached here."
  - "A place can have up to %s Sound Sets."
  - "The library can hold up to %s Sound Sets."
  - "A Sound Set can have up to %s entries."
- [ ] **Step 4:** Run the same command. Expected: PASS.
- [ ] **Step 5:** Commit with the message "Sound Set model: sets, attachments, places and the library".

### Task 3: Layered resolver per place (Core)

**Files:**
- Modify: `core/sets/EntryResolver.java`, `core/sets/EntryResolution.java`.
- Create: `core/sets/Layer.java`.
- Test: `src/test/java/soundsculptor/core/sets/EntryResolverTest.java` (existing cases now build through `EntryResolver.of`), and a new `LayeredResolverTest.java`.

**Interfaces:**
- Consumes: `PlaceSets`, `SetLibrary`, `SoundSet.QUICK_ID` (Task 2).
- Produces:

```java
public record Layer(String setId, List<SetEntry> entries) {}
public EntryResolver(List<Layer> layers, CacheBudget budget); // order: layers top-first; within a layer, stable sort by specificity
public static EntryResolver of(List<SetEntry> entries);        // one layer, setId "" (tests, and the "Partly overridden" check)
public static EntryResolver forPlace(PlaceSets place, SetLibrary library, CacheBudget budget);
    // skips disabled attachments and IDs missing from the library; QUICK_ID resolves to place.quickChanges()
public List<Layer> layers();
public record EntryResolution(SetEntry loudness, SetEntry limit, SetEntry cue,
                              String loudnessSet, String limitSet, String cueSet) { /* existing helpers kept */ }
```

The winner per property is the first entry in the flattened order that is on, matches the sound, and sets that property. This is the existing `compute` loop over the new ordering. Results stay pooled by the winners' indexes, so the set IDs cost no allocation.

- [ ] **Step 1: Write the failing tests** in `LayeredResolverTest`:
  - `p13HigherSetWinsAndSwapFlips`: S1 (villagers 20%) above S2 (villagers 80%) gives `volumePercent()` 20. With the attachments swapped it gives 80.
  - `p14SingleSoundBeatsGroupInOneSet`: one set with `minecraft:entity.villager.*` muted and `minecraft:entity.villager.ambient` at 60. Ambient gives 60 and not muted; hurt gives muted.
  - `p15SettingsLayer`: S1 mutes villagers, and S2 (below it) has `Limit(2, 1000, false)`. The result is muted, with `loudnessSet` S1 and `limitSet` S2.
  - `notMutedQuickBeatsMutingLibrarySet`: Quick changes has the cow ambient sound with `mute=false`, and set A mutes `minecraft:entity.cow.*`. The result is `muted()` false.
  - `disabledAttachmentIsSkipped`
  - `missingLibrarySetIsSkipped`
  - `duplicateAttachedBelowOriginalIsItsOwnLayer`: A and A-copy share entry instances; A is off and A-copy is on. The winner's set is A-copy.
  - `cacheHitAllocatesNothing`: use the existing `Allocations` helper, as `EntryResolverTest` already does.
- [ ] **Step 2:** Run `./gradlew test --tests 'soundsculptor.core.sets.*Resolver*'`. Expected: FAIL.
- [ ] **Step 3:** Implement the interfaces above. Keep `MAX_CACHED_IDS`, `MAX_RESULTS`, `CacheBudget` and the diagnostics counters unchanged.
- [ ] **Step 4:** Run the same command, then `./gradlew test`. Expected: all PASS.
- [ ] **Step 5:** Commit with the message "Resolve a place's attached sets as ordered layers (FR-4.5)".

### Task 4: Set files and the set store (Core)

**Files:**
- Create in `core/sets/`: `SetCodec.java`, `SetStore.java`, `SetFormatException.java`.
- Test: `SetCodecTest.java`, `SetStoreTest.java`.

**Interfaces (produces):**

```java
public final class SetCodec {
  public static final int SCHEMA_VERSION = 3;
  public static String toJson(SoundSet s);   // {"schemaVersion":3,"id","name","entries":[{"id","enabled","target","mute"?,"volume"?,"limit"?,"cue"?}]}
  public static JsonObject toJsonObject(SoundSet s);           // reused by export (Task 9)
  public static SoundSet read(String text) throws SetFormatException;
  public static SoundSet read(JsonObject o, String where) throws SetFormatException; // where = "Sound Set 'X'" for messages
  public record Read(SoundSet set, List<Message> warnings) {}  // unknown fields -> warnings
}
public final class SetStore {
  public SetStore(Path dir);
  public record LoadAll(SetLibrary library, List<Message> problems) {}
  public LoadAll loadAll();                  // each bad file is renamed <id>.json.broken with one problem message; others still load
  public void save(SoundSet s) throws IOException;          // atomic temp + rename, previous kept as .bak
  public void delete(String id, String name) throws IOException; // file -> .bak; records id -> name in deleted.json
  public Map<String, String> deletedNames(); // id -> last name; capped at 1024, oldest dropped
}
```

Validation reuses the existing rules: the entry `target` must pass `SoundPattern.validate`, the level is 0–200, the limit uses the `Limit` bounds, at most 512 entries, and IDs match `[a-z0-9-]+`. Messages follow the ProfileCodec style, e.g. "Sound Set 'Farm animals', entry 3, level: 250 is above the maximum 200".

- [ ] **Step 1: Write the failing tests:**
  - `roundTripKeepsEveryField`
  - `unsetPropertiesAreOmitted`
  - `levelAbove200NamesSetAndEntry`: the message contains "Sound Set 'Farm animals', entry 3, level".
  - `newerSchemaIsRefused`
  - `unknownFieldWarns`
  - `brokenSetFileIsSetAsideAndOthersLoad`: two good files and one with bad JSON. The result is 2 sets, 1 problem, and a `.broken` file exists.
  - `saveKeepsBak`
  - `deleteRecordsName`
- [ ] **Step 2:** Run `./gradlew test --tests 'soundsculptor.core.sets.Set*'`. Expected: FAIL.
- [ ] **Step 3:** Implement the interfaces, following `ProfileStore`'s atomic write and `.broken` handling. Factor out a shared helper if one falls out naturally.
- [ ] **Step 4:** Run the same command. Expected: PASS.
- [ ] **Step 5:** Commit with the message "Store library Sound Sets one file each (FR-11.8)".

### Task 5: Switch profiles and zones to attached sets (Core + minimal MC, the breaking change)

After this task, nothing reads a rule list. The old Rules UI keeps working through a shim: `RuleTarget` now reads and writes the target place's **Quick changes** set. The Rules tab therefore edits Everywhere's Quick changes, and a zone's Rules screen edits that zone's.

**Files:**
- Modify in `core/`:
  - `profile/Profile.java`: `globalRules` becomes `PlaceSets everywhere`, and every rule method is removed.
  - `zones/Zone.java`: `rules` becomes `PlaceSets sets`, and `withRules` becomes `withSets`.
  - `profile/ProfileCodec.java` (schema 3), `profile/ProfileMigrations.java` (no steps; target 3), `profile/ProfileStore.java`
  - `zones/Precedence.java`, `start/SoundStartDecider.java`, `stress/StressProfile.java`
  - `profile/RuleTarget.java` (shim), `profile/ProfileSummary.java`, `profile/ProfileOps.java`, `profile/ImportPlan.java`
  - `trace/*`: compile only, kept to Everywhere and zone Quick changes until Task 10.
  - `quick/QuickActions.java`: compile only until Task 7.
- Modify in `mc/`: `ProfileManager.java`, `SoundHook.java`, `Diagnostics.java`, `ZoneListPanel.java` (count = `zone.sets().librarySetCount()` plus 1 if quick), `TraceScreen.java` (compile only).
- Create: `core/profile/ProfilePrune.java`.
- Test:
  - Update the existing `Profile*Test`, `ProfileCodec`-based tests, `Precedence`/decider tests, `StressProfileTest`, `HotPathBudgetTest`, `SoundStartBudgetTest`.
  - New: `ProfilePruneTest.java`, `PrecedenceSetsTest.java`.
  - Delete `ProfileMigrationsTest` V1→V2 cases, and replace them with `olderSchemaIsRefusedAsEarlierBuild`.

**Interfaces:**
- Consumes: Tasks 2–4.
- Produces:

```java
// Profile
public record Profile(String name, ProfileSettings settings, PlaceSets everywhere, List<Zone> zones,
                      Map<String,Integer> zoneColors, Instant lastJoined, boolean boostConfirmed) {
  public static final int SCHEMA_VERSION = 3;
  public PlaceSets place(PlaceRef where);              // Everywhere or the zone's sets; null for an unknown zone
  public Profile withPlace(PlaceRef where, PlaceSets v);
  public List<PlaceRef> placesUsing(String setId);     // Everywhere first, then zones in list order
  public boolean hasBoost(SetLibrary library);         // any attached or quick entry above 100
  public Profile withEverywhere(PlaceSets v);
  // zone methods unchanged
}
// Precedence
public Precedence(PlaceSets everywhere, List<Zone> zones, SetLibrary library, ZoneEdge profileEdge);
public EntryResolver everywhere();                     // was globalRules()
public EntryResolver zoneResolver(int i);              // was zoneRules(i)
// SoundStartDecider
public static String limiterKey(String zoneIdOrNull, String setId, String entryId);
    // (zoneId == null ? "everywhere" : zoneId) + KEY_SEPARATOR + setId + KEY_SEPARATOR + entryId
    // Each place has its own counters for a shared set (owner decision 2026-11-11)
// ProfilePrune
public record Pruned(Profile profile, List<Message> notes) {}
public static Pruned dropMissingSets(Profile p, SetLibrary lib, Map<String,String> deletedNames);
    // note key soundsculptor.sets.note.removed {set name, place name}: "'%s' was deleted, so it was removed from %s"
    // unknown name: soundsculptor.sets.note.removed_unnamed {place name}: "A Sound Set that no longer exists was removed from %s"
```

- Profile JSON follows PRD 7.5 exactly:
  - `everywhere: {sets: [{set, enabled}], quickChanges?: {id: "quick", entries: [...]}}`
  - Zones get `sets` and an optional `quickChanges`.
  - Codec validation enforces quick-first, no duplicates, at most 32 library sets per place, and at most 512 entries in a quick set. It reuses `SetCodec` for the entries.
  - Schema below 3 throws `ProfileFormatException` with the message key `soundsculptor.profile.error.old_build`. `ProfileStore` then sets the file aside as `.broken`, as it already does.
- `ProfileManager`:
  - The snapshot becomes `Snapshot(String key, Profile profile, SetLibrary library, Precedence precedence)`.
  - The library is loaded once in `init(Path)` from `config/soundsculptor/sets`. Its problems go to `Notices`.
  - `join` runs `ProfilePrune.dropMissingSets` and shows each note as a toast. A changed profile is saved.
  - New: `public static SetLibrary library()`, `public static Workspace workspace()`, and `public static synchronized void change(UnaryOperator<Workspace> f)`. `change` swaps the snapshot, then saves the profile and any library sets that changed or were removed (by identity against the previous library) on the existing `SAVER` thread.
  - `update(UnaryOperator<Profile>)` stays and delegates to `change`.
- `SoundHook.applyVolume` reads `ProfileManager.precedence().everywhere()`. This replaces `rules()`, which is removed.
- `Diagnostics` and its Copy report show library set, attached set, entry and zone counts (the FR-13 AC), in place of rule counts.
- `StressProfile`:
  - Everywhere gets 2 library sets, 25 entries each, mixing single sounds and groups.
  - The 64 zones attach 1–3 sets from a pool of 8, so some sets are shared by several zones.
  - Add `public static SetLibrary library()`.

- [ ] **Step 1: Write the failing tests:**
  - `PrecedenceSetsTest`:
    - `p16LinkedSetChangesBothZones`: zones A and B attach "Farm animals" (cow ambient at 30%). After the library's set is replaced with 60% and the Precedence rebuilt, a cow in A decides 60%.
    - `p1ThroughP5StillHold`: port the existing P1–P5 cases to sets.
    - `limiterKeysDifferPerPlaceForSharedSet`
  - `ProfilePruneTest`:
    - `missingSetIsDroppedWithOneNote`: deleted name known, so one note with the args ("Farm animals", "Main base").
    - `unknownMissingSetUsesUnnamedNote`
    - `nothingMissingNoNotes`
  - Codec:
    - `schema3RoundTrip`
    - `quickNotFirstIsRejected`
    - `thirtyThreeSetsInAPlaceIsRejected`
    - `olderSchemaIsRefusedAsEarlierBuild`
  - Budgets: `HotPathBudgetTest` and `SoundStartBudgetTest` pass unchanged against the new `StressProfile`.
- [ ] **Step 2:** Run `./gradlew test`. Expected: FAIL to compile. That is expected, because this task is the switch.
- [ ] **Step 3:** Implement the switch:
  - **Shim.** `RuleTarget.rules(p)` returns the place's quick entries, or an empty list. `put`, `remove` and `move` edit the quick set through `PlaceSets.withQuickChanges`, creating it on first put and removing it when it becomes empty. `otherRules` lists the other places' quick entries.
  - **Trace and quick actions.** Make them compile against the Quick changes sets only. Mark each such shim with `// M8c shim: removed in Task 13` (or Task 7 or 10, whichever replaces it).
- [ ] **Step 4:** Run `./gradlew test`, then both version builds, then `CI=1 ./gradlew runClientGameTest`. Expected: all PASS. Game tests that call `Profile.isExactMuted` or `globalRules` move to `RuleTarget.GLOBAL.rules(...)`.
- [ ] **Step 5:** Commit with the message "Profiles and zones attach Sound Sets; schema 3; library loaded at start".

### Task 6: Undo and saving over the workspace (MC)

**Files:**
- Modify: `mc/QuickUndo.java` (snapshots become `Workspace`), `mc/ProfileManager.java` (the `change` save path from Task 5, which this task tests).
- Test: update `UndoClientGameTest`, and add `undoRestoresLibrarySetEdit`.

**Interfaces (produces):**
- `QuickUndo.apply(SculptScreen host, Runnable refresh, UnaryOperator<Workspace> change, String toastKey, String... args)`
- `QuickUndo.offer(SculptScreen, Runnable, Workspace before, Workspace after, String toastKey, List<String> args)`
- The existing `UnaryOperator<Profile>` overload stays and wraps the new one.

- [ ] **Step 1:** Write the failing game test `undoRestoresLibrarySetEdit`:
  1. Put "Farm animals" in the library, with cow ambient at 30%.
  2. Through `QuickUndo.apply`, change it to 60%.
  3. Press Undo.
  4. Expected: `ProfileManager.library().get(id)` shows 30 again, and after a flush the set file on disk says 30.
- [ ] **Step 2:** Run `CI=1 ./gradlew runClientGameTest`. Expected: FAIL.
- [ ] **Step 3:** Implement the interfaces above.
- [ ] **Step 4:** Run it again. Expected: PASS.
- [ ] **Step 5:** Commit with the message "Undo and saves cover library Sound Sets". **Open PR A.**

### Task 7: Quick changes (Core, plus the Recent and Search tabs' wiring)

**Files:**
- Modify: `core/quick/QuickActions.java`, `core/quick/QuickActionResult.java`, `mc/SoundSculptorScreen.java` (`toggleMute`, `muteButton`).
- Create: `core/sets/QuickChanges.java`.
- Test: `src/test/java/soundsculptor/core/quick/QuickChangesTest.java`, replacing the rule-based cases in `QuickActionsTest`.

**Interfaces (produces):**

```java
public final class QuickActions {
  public static boolean isMuted(Workspace ws, PlaceRef where, String soundId);  // actual state: EntryResolver.forPlace(...).resolve(id).muted()
  public static EntryEdit toggleMute(Workspace ws, PlaceRef where, String soundId, boolean cueDefault);
  public static QuickActionResult apply(Workspace ws, PlaceRef where, String soundId, EntryEdit edit);
      // edits the single-sound entry (target == soundId) in the place's Quick changes set, creating set/entry;
      // an entry left setting nothing is removed; an empty Quick changes set is removed
}
public final class QuickChanges {
  public static Message canSave(Workspace ws, PlaceRef where);   // null, or "no quick changes" / library full
  public static Workspace saveToLibrary(Workspace ws, PlaceRef where, String name);
      // new library set (newId, uniqueName(name)), entries = quick entries; place.quickSavedAs(newId)
}
```

`ApplyTarget` (UI) maps to `PlaceRef` through a new `ApplyTarget.place()` method.

Unmute follows FR-4.10:
- If the sound is muted only by its Quick changes entry, clear `mute` on that entry.
- If any other set at the place mutes it, set `mute=false` ("Not muted").

- [ ] **Step 1: Write the failing tests:**
  - `muteWritesQuickEntryEvenWhenLibrarySaysNotMuted`
  - `unmuteMutedOnlyByQuickClearsSetting`: afterwards the entry is gone and the quick set is removed.
  - `unmuteMutedByLibraryWritesNotMuted`: the library set is unchanged.
  - `unmuteMutedByBothWritesNotMuted`
  - `zoneTargetEditsZoneQuickNotEverywhere`
  - `saveToLibraryKeepsPlaceAndFreesPin`: [quick, A] becomes [S, A], and S has the quick entries. A following `apply` creates a new quick set pinned at index 0 above S.
  - `saveToLibraryUniqueName`
- [ ] **Step 2:** Run `./gradlew test --tests 'soundsculptor.core.quick.*'`. Expected: FAIL.
- [ ] **Step 3:** Implement the interfaces above, and switch `SoundSculptorScreen.toggleMute` and `muteButton` to `Workspace` and `PlaceRef`.
- [ ] **Step 4:** Run `./gradlew test`, then `CI=1 ./gradlew runClientGameTest`; `ApplyToClientGameTest` and `M6ClientGameTest` now assert entries in the place's Quick changes set. Expected: PASS.
- [ ] **Step 5:** Commit with the message "Quick actions edit the place's pinned Quick changes set (FR-4.10)".

### Task 8: Usage counts and library operations (Core, plus a background index in MC)

**Files:**
- Create: `core/sets/SetUsage.java`, `core/sets/LibraryOps.java`, `mc/SetUsageIndex.java`.
- Test: `SetUsageTest.java`, `LibraryOpsTest.java`.

**Interfaces (produces):**

```java
public record SetUsage(Map<String, List<Use>> bySet) {
  public record Use(String worldKey, String profileName, List<PlaceRef> places) {}
  public static SetUsage of(Map<String, Profile> profilesByKey);
  public List<PlaceRef> placesHere(String setId, String currentKey);
  public List<String> otherProfileNames(String setId, String currentKey);  // display names, sorted
  public int otherCount(String setId, String currentKey);
}
public final class LibraryOps {
  public static Workspace create(Workspace ws, String name);     // empty set, uniqueName
  public static Workspace duplicate(Workspace ws, String setId); // "<name> (copy)" via uniqueName, new IDs, attached nowhere
  public static Workspace rename(Workspace ws, String setId, String name);  // ProfileOps.nameProblem rules, uniqueName
  public record DeletePlan(String setId, String name, List<PlaceRef> placesHere, List<String> otherProfiles) {}
  public static DeletePlan planDelete(Workspace ws, SetUsage usage, String currentKey, String setId);
  public static Workspace delete(Workspace ws, String setId);    // removes from library and from every place in the loaded profile
}
```

`SetUsageIndex` (MC):
- Rebuilds `SetUsage` on a background thread: on `ProfileManager.init`, after each save of another profile, and after `join`.
- `current()` returns the last finished index. It never blocks the game thread (FR-4.9).
- The current profile always comes from the live snapshot, so "places here" is never stale.
- `ProfileManager.change` calls `SetStore.delete(id, name)` for every set the library lost.

- [ ] **Step 1: Write the failing tests:**
  - `SetUsageTest`:
    - `countsPlacesHereAndOtherProfiles`
    - `otherNamesAreDisplayNamesOnly`
  - `LibraryOpsTest`:
    - `duplicateIsIndependentAndUnattached`
    - `deletePlanListsZonesHereAndOtherProfileNames`: the FR-4.11 AC. Two zones here plus one other world gives `placesHere` [A, B] and `otherProfiles` ["Creative test world"].
    - `deleteDetachesEverywhereInLoadedProfile`
    - `renameKeepsId`
    - `createBeyond256Refused`
- [ ] **Step 2:** Run `./gradlew test --tests 'soundsculptor.core.sets.*'`. Expected: FAIL.
- [ ] **Step 3:** Implement the interfaces above.
- [ ] **Step 4:** Run it again. Expected: PASS.
- [ ] **Step 5:** Commit with the message "Library operations and cross-profile usage (FR-4.7, FR-4.9, FR-4.11)".

### Task 9: Copy from, export and import with sets (Core, plus `ProfileTransfer` wiring)

**Files:**
- Modify: `core/profile/ProfileOps.java` (`copyFrom`), `core/profile/ImportPlan.java`, `core/profile/ProfileImport.java`, `mc/ProfileTransfer.java`.
- Create: `core/profile/ExportCodec.java`, `core/sets/SetImportPlan.java`.
- Test: `ProfileOpsTest`, `ImportPlanTest`, `ProfileImportTest` (all updated), plus new `ExportCodecTest` and `SetImportPlanTest`.

**Interfaces (produces):**

```java
// Copy from (FR-11.2)
public record Copy(Profile result, int setCount, int bubbleCount, int fixedKept, Message problem);
public static Copy copyFrom(Profile source, Profile target);
    // Everywhere's attachments (same library IDs, linked) and its Quick changes (copied as is),
    // bubbles with their sets; fixed zones of target kept
// Export (FR-11.4)
public final class ExportCodec {
  public static String toJson(Profile p, String worldKey, SetLibrary lib); // profile JSON + "librarySets": [SetCodec objects of every attached set]
  public record Read(Profile profile, List<SoundSet> sets, List<Message> warnings) {}
  public static Read read(String text) throws ProfileFormatException;     // FR-11.6 limits incl. 256 library sets in the file
}
// Sets on import (FR-11.5a)
public enum Status { NEW, SAME, DIFFERENT }
public enum Choice { KEEP_BOTH, USE_MINE, REPLACE }               // default KEEP_BOTH
public record SetImportPlan(Map<String, Status> statusById, int missingRefs) {
  public static SetImportPlan of(List<SoundSet> fileSets, Profile fileProfile, SetLibrary lib);
  public Applied apply(Profile fileProfile, List<SoundSet> fileSets, SetLibrary lib, Map<String, Choice> choices);
  public record Applied(Profile profile, SetLibrary library) {}
      // KEEP_BOTH: add as uniqueName(name + " (imported)") with newId(); the file profile's refs are rewritten to the new ID
      // REPLACE: library set overwritten; USE_MINE: library kept; refs to IDs in neither file nor library dropped
}
public static ImportPlan of(Profile current, ExportCodec.Read file, SetLibrary lib, String worldKey, Map<String, Choice> choices);
    // existing counts plus setsAdded, setsMissing ("1 Sound Set not found, skipped")
```

`ProfileTransfer.export` uses `ExportCodec.toJson`. Import builds `SetImportPlan` for the dialog (the clash UI is Task 14) and applies it with `ProfileManager.change`, so the profile and the library change together. Until Task 14, the import uses the default choice, Keep both, for every clash.

- [ ] **Step 1: Write the failing tests:**
  - `copyFromLinksLibrarySetsAndCopiesQuick`
  - `copyFromKeepsTargetFixedZones`
  - `exportCarriesEveryAttachedSet`
  - `exportReadRejects257Sets`
  - `exportReadRejectsLevel250WithLocation`
  - `SetImportPlanTest`:
    - `newSetIsAdded`
    - `sameContentsIsNotDuplicated`
    - `differentKeepBothAddsImportedCopyAndRewritesRefs`
    - `keepBothNameClashGetsNumberSuffix`: "Farm animals (imported)" already exists, so the result is "Farm animals (imported) (2)".
    - `useMineAttachesLibraryVersion`
    - `replaceOverwritesLibrary`
    - `missingRefIsDroppedAndCounted`
  - `ImportPlanTest.fixedZonesOtherServerSkippedWithSets`
- [ ] **Step 2:** Run `./gradlew test --tests 'soundsculptor.core.profile.*' --tests 'soundsculptor.core.sets.*'`. Expected: FAIL.
- [ ] **Step 3:** Implement the interfaces above.
- [ ] **Step 4:** Run it again, then `CI=1 ./gradlew runClientGameTest` (`ImportExportClientGameTest`, `ProfilesClientGameTest`, updated to sets). Expected: PASS.
- [ ] **Step 5:** Commit with the message "Copy from, export and import carry Sound Sets (FR-11.2, 11.4, 11.5a)".

### Task 10: Trace in set terms (Core)

**Files:**
- Modify: `core/trace/SoundTracer.java`, `SoundTrace.java`, `ZoneLine.java`, `TraceEditor.java`, `TraceEditContext.java`, `TraceText.java`, `EntryWinReason.java`.
- Replace: `ListTrace` with `PlaceTrace`, `RuleLocation` with `EntryLocation`, `EditableRules` with `Workspace`, and `ExactRules` with `QuickActions` (Task 7).
- Modify (MC): `mc/TraceScreen.java`, wiring only.
- Test: rewrite `SoundTracerTest`, `TraceEditorTest`, `TraceTextTest` and `TraceTextLinesTest` in set terms.

**Interfaces (produces):**

```java
public record EntryLocation(PlaceRef place, String setId, String entryId) {}
public record SetLine(String setId, String setName, boolean quick, List<SetEntry> matched, int placesUsing) {}
public record PlaceTrace(List<SetLine> sets, Map<TraceProperty, EntryWinner> winners) {
  public static final PlaceTrace EMPTY;
}
public record EntryWinner(SetEntry entry, String setId, EntryWinReason reason) {}
public enum EntryWinReason { ONLY_MATCH, EXACT_ID, MORE_LITERAL, FEWER_STARS, LIST_ORDER, HIGHER_SET }
    // HIGHER_SET -> "higher in the list"
// SoundTrace: global -> everywhere (PlaceTrace); ZoneLine(zone, weight, distance, PlaceTrace sets)
public static SoundTrace trace(Precedence p, SetLibrary lib, String soundId, LastPlay play, boolean bypass, boolean vanillaCovered);
public static TraceEditResult edit(TraceEditContext ctx, Workspace ws, EntryLocation where, EntryEdit edit);
    // library set -> library changes (linked, FR-4.9); QUICK_ID -> place's Quick changes
public static TraceEditResult addToQuickChanges(TraceEditContext ctx, Workspace ws, PlaceRef where, EntryEdit edit);
// TraceEditResult.after() is now a Workspace
```

The "used in N places" count on a line comes from `Profile.placesUsing(setId).size()` plus `SetUsage.otherCount`. When the usage index has not finished yet, only the local count is shown.

- [ ] **Step 1: Write the failing tests** (the FR-14 AC):
  - `ambientSingleBeatsVillagerGroupInOneSet`: final volume 60.
  - `twoSetsBothSettingCowsMarksHigherSet`: reason `HIGHER_SET`.
  - `overlappingZonesCloserWins`: the existing case, ported.
  - `editWinningEntryLevelUpdatesPrediction`
  - `turnOffWinnerNextInLineWins`
  - `addToQuickChangesOnEverywhereMutesAndIsListedFirst`
  - `unmuteOnQuickLineOverLibraryMuteWritesNotMuted`
  - `level250Refused`
  - `lineOfSetUsedInTwoZonesSaysTwoPlaces`
- [ ] **Step 2:** Run `./gradlew test --tests 'soundsculptor.core.trace.*'`. Expected: FAIL.
- [ ] **Step 3:** Implement the interfaces above, and wire `TraceScreen` to `Workspace`, `EntryLocation` and `ProfileManager.change`.
- [ ] **Step 4:** Run `./gradlew test`, then `CI=1 ./gradlew runClientGameTest` (`TraceEditClientGameTest`, `M5bClientGameTest`, `M7ClientGameTest`). Expected: PASS.
- [ ] **Step 5:** Commit with the message "Sound trace in Sound Set terms (FR-14)". **Open PR B.**

### Task 11: Sound Sets tab (MC)

**Files:**
- Create: `mc/SetLibraryPanel.java`, `mc/SetDeleteDialog.java`.
- Modify: `mc/SoundSculptorScreen.java`. `TAB_RULES` becomes `TAB_SETS` with the label "Sound Sets", and the tab's body becomes `new SetLibraryPanel(this)`. The `en_us.json` keys are `soundsculptor.sets.*`.
- Test: `src/gametest/java/soundsculptor/gametest/SoundSetsTabClientGameTest.java`.

**Interfaces:**
- Consumes: `LibraryOps`, `SetUsageIndex.current()`, `ProfileManager.change`.
- Produces:
  - `SetLibraryPanel(SculptScreen host)`, with `build(Body)` and `refresh()`.
  - Public fields `list`, `newButton`.
  - `openEditor(String setId)`, implemented in Task 12.

- Each row shows the name, the entry count, and where the set is used ("3 places in this world, 2 other worlds", or "Not attached"). Each row has Edit, Duplicate, Rename (reusing the dialog pattern of `ProfileRenameScreen`) and Delete.
- Delete opens `SetDeleteDialog`:
  - Title: "Delete '%s'?"
  - "Used here in: Everywhere, Main base"
  - "Also removed from: Hypixel Skyblock, Creative test world". These are profile names only, never zones.
  - Delete and Cancel buttons.
  - For an unattached set, the text is just "This set is not attached anywhere."

- [ ] **Step 1: Write the failing game test:**
  - `newDuplicateRenameDelete`: create "Farm animals", then Duplicate it, which gives "Farm animals (copy)" and leaves it unattached.
  - `deleteWarningListsPlacesAndOtherProfiles`:
    1. Seed a second profile file that attaches the set.
    2. The dialog text contains both zone names and the other profile's name, and none of its zones.
    3. Cancel changes nothing.
    4. Delete removes the set from both zones.
  - `rowShowsUsage`
- [ ] **Step 2:** Run `CI=1 ./gradlew runClientGameTest`. Expected: FAIL.
- [ ] **Step 3:** Implement the panel and the dialog as described above.
- [ ] **Step 4:** Run it again. Expected: PASS.
- [ ] **Step 5:** Commit with the message "Sound Sets tab with library actions and delete warning (FR-4.7, FR-4.11)".

### Task 12: Set editor, entry editor and Attached to (MC, plus core drafts)

**Files:**
- Create: `core/editor/SetDraft.java`, `mc/SetEditorScreen.java`, `mc/AttachedToPanel.java`.
- Modify:
  - `mc/EntryEditorScreen.java`: the entry belongs to a `SoundSet` draft, not to a `RuleTarget`. The "Partly overridden by" line is computed within the set using `EntryResolver.of(set entries)`. The inherited-value line is removed.
  - `core/editor/EntryDraft.java`: the field `pattern` becomes `target`, with the same validation.
- Test: `SetDraftTest.java`, and a new `SetEditorClientGameTest.java`.

**Interfaces (produces):**
- `SetDraft`:
  - `SetDraft.of(SoundSet)` and `SetDraft.blank(String id, String name)`
  - `putEntry(SetEntry)`, `removeEntry(String)`, `setEntryEnabled(String, boolean)`
  - `SoundSet build()`, `boolean changed()`
- `SetEditorScreen`:
  - `SetEditorScreen.edit(Screen parent, String setId)`
  - `SetEditorScreen.forQuickChanges(Screen parent, PlaceRef where)`: edits a place's Quick changes set, and has no Attached to panel or rename.
- `AttachedToPanel(SculptScreen host, String setId)` lists the places in this profile, with Detach on each line and "Attach to…" choosing among this profile's places that don't have the set. It also shows "Also used in N other worlds or servers", with the names in a tooltip.

Editor behavior:
- **Save** writes the set through `ProfileManager.change`.
- When the set is used in more than one place, Save first confirms with: "This changes Main base, Mob farm and 1 other world."
- Attach and Detach in the panel apply when the player presses Save, together with the set's own changes.
- **Add sounds…** opens the entry editor for a new entry (typed pattern, or plain-word search, as built in M7) until Task 19 replaces it with the picker.

- [ ] **Step 1: Write the failing tests:**
  - `SetDraftTest`:
    - `changedAfterEntryEdit`
    - `buildKeepsEntryOrder`
    - `refusesEntry513`
  - `SetEditorClientGameTest`:
    - `farmAnimalsLinkedAcrossZones`: the FR-4 AC. Attach the set to Main base and Mob farm, edit cows to 60%, and the save confirmation names both zones. Then the decision for a cow in each zone is 60%.
    - `partlyOverriddenByShownInEntryEditor`
    - `entryOffStopsEffectAfterSave`
    - `uppercaseGroupLowercasedAndBadCharBlocksSave`
- [ ] **Step 2:** Run `./gradlew test --tests 'soundsculptor.core.editor.*'` and `CI=1 ./gradlew runClientGameTest`. Expected: FAIL.
- [ ] **Step 3:** Implement the drafts and screens as described above.
- [ ] **Step 4:** Run both commands again. Expected: PASS.
- [ ] **Step 5:** Commit with the message "Set editor with entries and Attached to (FR-4.3, FR-4.9)". **Open PR C.**

### Task 13: Attached sets on every place; remove the Rules UI (MC)

**Files:**
- Create: `mc/PlaceSetsPanel.java`, `mc/PlaceSetsScreen.java`, `mc/AttachSetScreen.java`.
- Modify: `mc/ZoneListPanel.java`, `mc/ZoneEditorScreen.java`, `mc/SoundSculptorScreen.java`.
- Delete: `mc/RuleListPanel.java`, `mc/ZoneRulesScreen.java`, `mc/RuleCopyScreen.java`, `core/profile/RuleTarget.java`, the `soundsculptor.rules.*`, `rule_copy.*` and `zone_rules.*` lang keys, `CopyRuleClientGameTest`, and every `// M8c shim` marker.
- Test: replace `M7bClientGameTest`'s zone rules cases, and add `PlaceSetsClientGameTest.java`.

**Interfaces:**
- Consumes: `PlaceSets` operations (Task 2), `QuickChanges.saveToLibrary` (Task 7), `SetEditorScreen` (Task 12).
- Produces:
  - `PlaceSetsPanel(SculptScreen host, PlaceRef where)`
  - `PlaceSetsScreen(Screen parent, PlaceRef where)`
  - `AttachSetScreen(Screen parent, PlaceRef where)`: lists the library sets not attached here, plus New set.

Each line follows FR-8.6: name, entry count, "Also used in N places", and the buttons On/Off, Edit, Move up, Move down, Detach.
- The Quick changes line has no move buttons. It has **Save to library**, which opens a name dialog (default name "Quick changes"), and Edit (`SetEditorScreen.forQuickChanges`).
- Changes apply at once, with an Undo toast (they are list quick actions, FR-12.5).

Places:
- **Zones tab.** The first row is **Everywhere**: no outline, color, Edit shape or Delete; only a "Sound Sets" button that opens `PlaceSetsScreen(EVERYWHERE)`. Zone rows show "N sets", and their Sets button opens `PlaceSetsScreen(zone)`.
- **Zone editor.** The rules button becomes "Sound Sets…".
- **Edit… on a sound row.** `EntryEditorScreen.forSound(Screen parent, PlaceRef where, String soundId)` opens that sound's single-sound entry in the place's Quick changes set, and creates the entry on Save. This replaces the `RuleTarget`-based overload.

- [ ] **Step 1: Write the failing game tests:**
  - `quickLineHasNoMoveAndNothingPassesIt`
  - `swapOrderFlipsResult`: P13 in game.
  - `saveToLibraryKeepsAttachedAndNextMuteMakesNewQuick`
  - `attachSameSetTwiceNotOffered`
  - `everywhereRowFirstAndNotEditable`
  - `thirtyThirdAttachShowsMessage`
- [ ] **Step 2:** Run `CI=1 ./gradlew runClientGameTest`. Expected: FAIL.
- [ ] **Step 3:** Implement the panel and screens as described above, then delete the shim and the old screens.
- [ ] **Step 4:** Run `git grep -n "RuleTarget\|M8c shim" src`. Expected: no hits.
- [ ] **Step 5:** Run `./gradlew test`, both version builds, and `CI=1 ./gradlew runClientGameTest`. Expected: PASS.
- [ ] **Step 6:** Commit with the message "Attach Sound Sets to Everywhere and zones; remove the Rules tab (FR-8.6, FR-8.10)". **Open PR D.**

### Task 14: Profiles tab and import dialog with sets (MC)

**Files:**
- Modify: `mc/ProfileListPanel.java`, `mc/ProfileTransfer.java`, `core/profile/ProfileSummary.java` (`ruleCount` becomes `setCount`).
- Create: `mc/SetClashScreen.java`.
- Test: update `ProfilesClientGameTest` and `ImportExportClientGameTest`.

Behavior:
- Profile rows show Sound Set and zone counts.
- **Copy from** dialog text: "4 Sound Sets on Everywhere, 2 bubble zones, settings. This server's fixed zones are kept."
- **Reset and Delete** confirmations add: "Library Sound Sets are not deleted."
- **Import** with any `DIFFERENT` set opens `SetClashScreen`:
  - One line per set: the name, "Used in N places here and M other worlds", and a choice of Keep both (default), Use mine or Replace with the file's.
  - Replace adds: "This changes every place that uses '%s'."
  - The plan message adds "%s Sound Sets added" and "%s Sound Set not found, skipped".

- [ ] **Step 1: Write the failing game tests:**
  - `exportImportOnEmptyLibraryRestoresSets`
  - `changedSetOffersThreeChoicesAndKeepBothAddsImported`
  - `resetKeepsLibrary`
  - `copyFromLinksSets`: after copying, detach a set on A; B still has it.
- [ ] **Step 2:** Run `CI=1 ./gradlew runClientGameTest`. Expected: FAIL.
- [ ] **Step 3:** Implement the rows, dialogs and `SetClashScreen` as described above.
- [ ] **Step 4:** Run it again. Expected: PASS.
- [ ] **Step 5:** Commit with the message "Profiles tab and import dialog handle Sound Sets (FR-11)".

### Task 15: Trace screen in set terms (MC)

**Files:**
- Modify: `mc/TraceScreen.java`.
- Test: update `TraceEditClientGameTest`.

Behavior:
- Under each place, lines are grouped by set. The Quick changes line is always first.
- A set used in more than one place shows "Farm animals · used in 3 places", with the places in a tooltip.
- "Add to Quick changes" replaces "Add rule".
- "Open full editor" opens `SetEditorScreen` at the entry.
- The "higher in the list" reason is shown.

- [ ] **Step 1:** Write the failing game tests:
  - `twoSetsOnEverywhereNamesHigherSet`
  - `addToQuickChangesMutesNextPlay`
  - `unmuteOnQuickLineOverLibraryMute`
  - `usedInTwoPlacesShown`
- [ ] **Step 2:** Run `CI=1 ./gradlew runClientGameTest`. Expected: FAIL.
- [ ] **Step 3:** Implement the changes above.
- [ ] **Step 4:** Run it again. Expected: PASS.
- [ ] **Step 5:** Commit with the message "Trace screen shows Sound Sets and edits linked sets (FR-14.4)".

### Task 16: M8c test pass (MC)

**Files:**
- Modify: `M8StressClientGameTest` (stress profile with sets and shared sets), `AccessibilityClientGameTest` (Sound Sets tab, set editor, place sets, delete dialog, clash screen), `M5ClientGameTest` and `M8ReviewClientGameTest` (moved onto sets).
- Create: `PrecedenceSetsClientGameTest` (P13, P14, P16 in game, with a cow in a boat as in 11.4).

- [ ] **Step 1:** Run `CI=1 ./gradlew runClientGameTest` and both version builds. Expected: all PASS. Per-sound cost in the stress test stays under 5 µs average.
- [ ] **Step 2:** Build both jars and hand them to the owner for the manual FR-4, FR-8.10, FR-11 and FR-14 AC. New jars use `config/soundsculptor/`.
- [ ] **Step 3:** Update `CHANGELOG.md` (Unreleased: "Rules are now Sound Sets…") and the M8c row status. Commit with the message "M8c: Sound Sets test pass". **Open PR E.**

## M8d: Sounds browser

### Task 17: Browser and picker models (Core)

**Files:**
- Create: `core/browser/SoundBrowser.java`, `core/browser/BrowserRow.java`, `core/browser/PickerModel.java`, `core/browser/LikeThis.java`.
- Test: `SoundBrowserTest.java`, `PickerModelTest.java`, `LikeThisTest.java`.

**Interfaces (produces):**

```java
public record BrowserRow(String soundId, String subtitle, RecentRow recent /* null if not recently played */) {}
public final class SoundBrowser {
  public static List<BrowserRow> rows(List<RecentRow> recent, List<SoundEntry> all, SearchQuery query,
                                      boolean recentOnly, SortOrder sort, Set<String> seenExcluded);
      // recentOnly: recent rows matching query, sorted by sort (FR-1.3); else every known sound matching (FR-3.1),
      // carrying recent data when present; empty query = everything the filter allows
}
public final class PickerModel {
  public PickerModel(SoundSet target);
  public boolean inSet(String soundId);                // a single-sound entry already targets it (FR-1.7)
  public boolean groupInSet(String pattern);
  public void toggleSound(String soundId);
  public void toggleGroup(String pattern);
  public int pickedCount();
  public Message canAdd();                             // null, or entries_full when the set would exceed 512
  public SoundSet addTo(SoundSet set);                 // one entry per pick, no settings, newEntryId order
}
public final class LikeThis {
  public record Group(String pattern, int count) {}
  public static List<Group> groups(String soundId, Collection<String> knownIds); // PatternSuggestions.suggest order (closest first) with match counts
}
```

- [ ] **Step 1: Write the failing tests:**
  - `filterOnShowsOnlyRecentMatches`
  - `filterOffListsEveryKnownMatch`
  - `emptyQueryShowsAll`
  - `recentDataCarriedWhenFilterOff`
  - `inSetMarksExistingSingleSounds`
  - `addCreatesOneEntryPerPick`
  - `addOverLimitIsRefused`: 510 entries plus 3 picks gives `entries_full`.
  - `likeThisClosestFirstWithCounts`: villager ambient gives `minecraft:entity.villager.*` first, and its count equals the villager IDs in the known list.
- [ ] **Step 2:** Run `./gradlew test --tests 'soundsculptor.core.browser.*'`. Expected: FAIL.
- [ ] **Step 3:** Implement the interfaces above.
- [ ] **Step 4:** Run it again. Expected: PASS.
- [ ] **Step 5:** Commit with the message "Sounds browser and picker models (FR-1, FR-1.7, FR-3, FR-4.8)".

### Task 18: Sounds tab (MC)

**Files:**
- Create: `mc/SoundsPanel.java`.
- Modify: `mc/SoundSculptorScreen.java`. The tabs become Sounds, Inspect (if present), Sound Sets, Zones, Profiles and Settings, and the `buildRecent`/`buildSearch` code moves into `SoundsPanel`.
- Test: rewrite `M3ClientGameTest` and `M6ClientGameTest` against the Sounds tab, and update `ApplyToClientGameTest` and `UndoClientGameTest`.

Behavior:
- One search field, with a help button, and a **Recently played** toggle. The toggle is on when the tab first opens in a game run, then remembered as the player left it, in a static field.
- The result count shows "42 sounds".
- The Apply to dropdown, Pause (P while the list is focused) and Sort are shown while the filter is on.
- Rows show the FR-1.2 columns when they have recent data.
- Quick actions: Mute/Unmute (the actual state), Volume, Why?, Edit…, and **Add to set…**. Add to set… chooses a library set or New set, then This sound or one of the Like this groups.
- The list refreshes at most 4 times a second, and search waits 150 ms after typing stops.

- [ ] **Step 1: Write the failing game tests:**
  - `villagerFilterOnThenOff`: the FR-1 AC.
  - `orAndNotSyntax`: the FR-3 AC.
  - `muteRowWritesEverywhereQuick`
  - `zoneApplyToWritesZoneQuick`
  - `addToSetCreatesEntryInChosenSet`
  - `filterStateRememberedAcrossReopen`
- [ ] **Step 2:** Run `CI=1 ./gradlew runClientGameTest`. Expected: FAIL.
- [ ] **Step 3:** Implement `SoundsPanel` and the tab change as described above.
- [ ] **Step 4:** Run it again. Expected: PASS.
- [ ] **Step 5:** Commit with the message "One Sounds tab replaces Recently Played and Search (FR-1, FR-3, FR-12.1)".

### Task 19: Picker mode (MC)

**Files:**
- Create: `mc/SoundPickerScreen.java`, which reuses `SoundsPanel` in picker mode: a checkbox per row, Like this…, a footer reading "%s picked", and Add and Cancel.
- Modify: `mc/SetEditorScreen.java` (Add sounds… opens the picker), `mc/SoundsPanel.java` (a mode flag).
- Test: `PickerClientGameTest.java`.

- [ ] **Step 1: Write the failing game tests:**
  - `tickThreeCowsAddsThreeEntries`
  - `reopenMarksThemInThisSet`: this is the FR-1 AC.
  - `likeThisAddsGroupEntry`
  - `overLimitShowsMessage`
- [ ] **Step 2:** Run `CI=1 ./gradlew runClientGameTest`. Expected: FAIL.
- [ ] **Step 3:** Implement the picker screen and wiring as described above.
- [ ] **Step 4:** Run it again. Expected: PASS.
- [ ] **Step 5:** Commit with the message "Sounds picker for Sound Sets (FR-1.7)".

### Task 20: Find a sound or group in the entry editor (MC)

**Files:**
- Modify: `mc/EntryEditorScreen.java`. Plain-word search results get **This sound** and **Like this** buttons, and Like this lists `LikeThis.groups` with counts.
- Test: update `FindSoundClientGameTest`.

- [ ] **Step 1:** Write the failing game test `villagerHurtThisSoundAndCowLikeThis`, which is the FR-4.8 AC:
  - This sound gives `minecraft:entity.villager.hurt`, with "Matches 1 sound".
  - Like this on a cow sound gives `minecraft:entity.cow.*`, and the preview lists only cow sounds.
- [ ] **Step 2:** Run `CI=1 ./gradlew runClientGameTest`. Expected: FAIL.
- [ ] **Step 3:** Implement the buttons and the Like this list.
- [ ] **Step 4:** Run it again. Expected: PASS.
- [ ] **Step 5:** Commit with the message "Entry editor: This sound and Like this (FR-4.8)".

### Task 21: M8d layout and accessibility pass (MC)

**Files:**
- Modify: `AccessibilityClientGameTest`, adding the Sounds tab in both modes and the picker at 854 × 480 and GUI scales 1–4. Also modify any screen that the test shows truncating or overlapping.

- [ ] **Step 1:** Run `CI=1 ./gradlew runClientGameTest`, both version builds and the pseudo-language check. Expected: PASS, with no new pseudo-language issues beyond the current 314.
- [ ] **Step 2:** Build both jars for the owner's manual FR-1 and FR-3 AC run.
- [ ] **Step 3:** Update `CHANGELOG.md`. Commit with the message "M8d: Sounds browser layout pass". **Open PR F.** M8e (alpha release) follows as its own milestone.
