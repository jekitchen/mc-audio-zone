# Changelog

All notable changes to Sound Sculptor. Versions follow `major.minor.patch`, with `-alpha` or `-beta` for test releases.
Each release ships one jar per supported Minecraft version.

## Unreleased (1.0)

### Changes
- **New name: Sound Sculptor** (was Audio Zone during development). Settings now live in `config/soundsculptor/`.
- **Rules are now Sound Sets.** A Sound Set is a named list of entries, each for one sound or a group of sounds
  (`minecraft:entity.villager.*`), that mutes them or sets their level. Your sets live in a library on the new
  **Sound Sets** tab (New, Duplicate, Rename, Delete) and work in every world. Attach sets to **Everywhere** or to any
  zone, as many as you like and in the order you choose: the higher set wins where two disagree, and inside a set the
  more specific entry wins. A set attached in several places is linked: edit it once and every place changes. The set
  editor's **Attached to** panel shows and changes where a set is used, and Save says which places it will change.
  Deleting a set lists every place it is removed from first, other worlds included (by name only).
- **Quick changes:** Mute and Edit entry on Recently Played and Search go into the place's own **Quick changes**, a set
  that belongs to Everywhere or to one zone and always comes first there. Save Quick changes to the library to reuse them.
- **"Why does it sound like this?" in sets:** the trace lists, for Everywhere and each zone, the sets with a matching
  entry, which entry won and why ("Higher in the list", "Exact sound ID"), and how many places use a linked set.
  **Add to Quick changes** is offered at every place whose Quick changes has no entry for the sound, so a mute from a
  library set can be lifted in one place without changing the set.
- **Profiles, export and import carry Sound Sets:** profile rows count Sound Sets and zones; Copy from copies the sets on
  Everywhere; an export includes the sets it uses, and importing adds them to your library, asking for a new name
  when one is taken. Profiles from earlier test builds can't be read and are set aside with a message.
- Long lists of places in the delete warning and the Save confirmation scroll, so Delete and Save stay on screen.
- A save that Windows holds back for a moment (a virus scanner or the search indexer reading the file) is retried, and
  is written at the latest when the game closes.

### Features
- **Profiles tab:** every server and world with a profile, with its name, address or world, Sound Set and zone counts
  and when you last joined. Copy another server's Sound Sets on Everywhere, personal bubbles and settings into this
  world (fixed zones are never copied), rename, reset or delete a profile. The profile of the world you are in can be reset but not
  deleted. In singleplayer, "Give this copy its own profile" for a copied world.
- **Export and import:** Export saves a profile, fixed zones included, to `config/soundsculptor/exports/`. Import a file by
  dropping it on the Profiles tab or from `config/soundsculptor/imports/`. Files are checked first, and a dialog says what
  will change; fixed zones made for another server are skipped. A broken or out-of-range file changes nothing.
- **Undo:** Mute and Unmute on Recently Played and Search, On/Off, Move and Detach in a place's Sound Sets, New,
  Duplicate, Rename and Delete on the Sound Sets tab, and On/Off and Delete on the Zones list apply at once and show a
  5-second toast with **Undo**. Press <kbd>Ctrl</kbd>+<kbd>Z</kbd> (<kbd>Cmd</kbd>+<kbd>Z</kbd>
  on Mac) or Tab to the button. The toast stays while Undo has focus. A new mute entry starts with the profile's Cue
  default.
- **Apply to:** Recently Played and Search have an "Apply to" selector: Everywhere, or a zone you are standing in. With a
  zone chosen, Mute and Edit entry work on that zone's Quick changes, so a zone entry can be made straight from a search.
- **Edit from "Why?":** every entry line in the sound trace has On/Off, Mute and Level, every zone line has On/Off,
  and each opens its full editor. Editing an entry of a linked set changes every place that uses it, and says so.
  Changes save at once, the trace updates straight away ("Predicted for next play") and a toast offers Undo.

## 0.1.0-alpha (unreleased)

First public alpha, for Minecraft Java Edition 26.2 and 26.3 with Fabric.

### Features
- **Recently Played:** every sound you can hear, live, with its subtitle (or a readable name), ID, category, play count,
  a Close/Near/Far distance band (can be hidden) and status (Playing, Muted). Pause, sort and filter; Mute, Edit and
  "Why?" on every row. Press <kbd>;</kbd> to open (rebindable).
- **Search:** every sound the game knows, including mods and resource packs, with plain words, `|` for either, `-` to
  exclude, `"phrases"` and `*` wildcards. Same quick actions as Recently Played.
- **Rules:** mute or turn down (0 to 100%) one sound or a whole family with wildcard patterns such as
  `minecraft:entity.villager.*`. More specific rules win. The editor previews the matching sounds and says when a rule
  is partly overridden. Type plain words in the pattern field (`villager hurt`) to find a sound, then pick **This sound** or
  **Like this** for its family.
- **Zones:** boxes you draw by marking two corners in the world, then fine-tune side by side (Grow, Shrink), with the
  size shown on screen while you mark. Each zone has its own rules (new or copied from existing ones). Where zones overlap, the closest center wins. Outlines in the zone's color with its name, shown always,
  only while editing, or never.
- **Why does it sound like this?** For any sound: every rule and zone that touched it, which one won and why, and the
  final volume. Copy as text.
- **A profile per server and world,** created on first join and kept across restarts. Outside a world nothing changes.
- **Bypass** (<kbd>F10</kbd>) turns every effect off at once; a broken setting or an error never stops a sound.
- **Diagnostics:** live sounds per second, time per sound, cache hit rate, memory, and Copy report.
- **Mod Menu** (optional) opens the Sound Sculptor screen.
- Keyboard-only use, narrator support, and layouts checked at every GUI scale.

### Fair play
Sound Sculptor only changes how loud sounds are on your own computer, and never shows more than vanilla sounds and subtitles
do. It sends nothing to servers. See "For server admins" in the README.

### Not yet included (planned for 1.0)
Volume boost above 100%, on-screen cues for muted sounds, repeat limits, the point-at-a-mob inspector, Outside zones,
fade edges, cylinders and personal bubbles, copying settings between servers, and import/export.
