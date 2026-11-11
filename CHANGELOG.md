# Changelog

All notable changes to Sound Sculptor. Versions follow `major.minor.patch`, with `-alpha` or `-beta` for test releases.
Each release ships one jar per supported Minecraft version.

## Unreleased (1.0)

### Changes
- **New name: Sound Sculptor** (was Audio Zone during development). Settings now live in `config/soundsculptor/`.

### Features
- **Profiles tab:** every server and world with a profile, with its name, address or world, rule and zone counts and
  when you last joined. Copy another server's global rules, personal bubbles and settings into this world (fixed zones
  are never copied), rename, reset or delete a profile. The profile of the world you are in can be reset but not
  deleted. In singleplayer, "Give this copy its own profile" for a copied world.
- **Export and import:** Export saves a profile, fixed zones included, to `config/soundsculptor/exports/`. Import a file by
  dropping it on the Profiles tab or from `config/soundsculptor/imports/`. Files are checked first, and a dialog says what
  will change; fixed zones made for another server are skipped. A broken or out-of-range file changes nothing.
- **Undo:** Mute and Unmute on Recently Played and Search, and On/Off, Move and Delete on the Rules and Zones lists,
  apply at once and show a 5-second toast with **Undo**. Press <kbd>Ctrl</kbd>+<kbd>Z</kbd> (<kbd>Cmd</kbd>+<kbd>Z</kbd>
  on Mac) or Tab to the button. The toast stays while Undo has focus. A new mute rule starts with the profile's Cue
  default.
- **Apply to:** Recently Played and Search have an "Apply to" selector: Everywhere, or a zone you are standing in. With a
  zone chosen, Mute and Edit work on that zone's rule for the sound, so a zone rule can be made straight from a search.
- **Edit from "Why?":** every rule line in the sound trace has On/Off, Mute and Volume, every zone line has On/Off,
  and each opens its full editor. Where no rule matches, "Add rule" adds one for this exact sound, globally or in a
  zone. Changes save at once, the trace updates straight away ("Predicted for next play") and a toast offers Undo.

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
