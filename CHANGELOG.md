# Changelog

All notable changes to Audio Zone. Versions follow `major.minor.patch`, with `-alpha` or `-beta` for test releases.
Each release ships one jar per supported Minecraft version.

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
  is partly overridden.
- **Zones:** spheres and boxes, centered on you or the block you look at, each with its own rules (new or copied from
  existing ones). Where zones overlap, the closest center wins. Outlines in the zone's color with its name, shown always,
  only while editing, or never.
- **Why does it sound like this?** For any sound: every rule and zone that touched it, which one won and why, and the
  final volume. Copy as text.
- **A profile per server and world,** created on first join and kept across restarts. Outside a world nothing changes.
- **Bypass** (<kbd>F10</kbd>) turns every effect off at once; a broken setting or an error never stops a sound.
- **Diagnostics:** live sounds per second, time per sound, cache hit rate, memory, and Copy report.
- **Mod Menu** (optional) opens the Audio Zone screen.
- Keyboard-only use, narrator support, and layouts checked at every GUI scale.

### Fair play
Audio Zone only changes how loud sounds are on your own computer, and never shows more than vanilla sounds and subtitles
do. It sends nothing to servers. See "For server admins" in the README.

### Not yet included (planned for 1.0)
Volume boost above 100%, on-screen cues for muted sounds, repeat limits, the point-at-a-mob inspector, Outside zones,
fade edges, cylinders and personal bubbles, copying settings between servers, and import/export.
