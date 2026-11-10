# Mod page text

The description for Modrinth and CurseForge, kept in the repository so it stays accurate and reviewable. Copy the part
between the lines into the project's description on each site. Keep it in step with [CHANGELOG.md](../CHANGELOG.md)
and the README's "For server admins" section.

**Summary** (Modrinth's one-line summary and CurseForge's short description, under 256 characters):

> Mute or turn down any sound, everywhere or only inside zones you draw, without ever hearing more than vanilla allows.
> Client-only, nothing sent to servers.

---

# Audio Zone

Too many villagers? A noisy farm next to your base? An ambient sound that drives you up the wall? **Audio Zone** lets
you mute or turn down any individual sound, everywhere or only inside areas you choose, and it never lets you hear more
than vanilla Minecraft would.

Press <kbd>;</kbd> in game to open it.

## What you can do

- **See what you're hearing.** *Recently Played* lists every sound around you as it plays, with its subtitle, how often
  it played, and whether it's Close, Near or Far. Mute it with one click.
- **Find any sound.** *Search* covers every sound in the game, mods and resource packs included: type `villager`,
  `villager|zombie`, `villager -ambient` or `block.*.step`.
- **Write rules.** Mute or turn down one sound or a whole family (`minecraft:entity.villager.*`). The editor shows which
  sounds a rule matches and warns when a more specific rule overrides it.
- **Draw zones.** A sphere or box around your trading hall, farm or base, with its own rules. Outlines show where your
  zones are, in their own colors and with their names.
- **Ask why.** "Why does it sound like this?" shows every rule and zone that touched a sound, which one won, and why.
- **One profile per server and world**, created automatically and saved across restarts.
- **Bypass** with <kbd>F10</kbd> to hear plain vanilla sound at any time, without losing a single setting.

Everything works with the keyboard alone and with the narrator, and the screens are tested at every GUI scale.

## For server admins: fair play

Audio Zone only changes how loud sounds are on the player's own computer. It never tells a player anything that vanilla
Minecraft's sounds and subtitles don't already tell them. This is a hard rule of the project, not a setting.

- **Nothing sent to your server.** No custom packets, no plugin channel, no client brand change: your server sees a
  vanilla client. Verify it with any packet logger.
- **Mute and volume only remove sound**, like vanilla's own volume sliders, just per sound.
- **Recently Played lists only sounds the player was close enough to hear in vanilla.** Distance is Close, Near or Far,
  never blocks and never a direction, and players can hide it.
- **Zone outlines show only zones the player drew.**

Coming in 1.0, with the same rule: a volume boost that is still silent wherever vanilla is silent, on-screen cues only
where vanilla shows subtitles, and an inspector that only selects mobs in clear sight.

Questions or concerns: open an issue with "fair play" in the title at
<https://github.com/jekitchen/mc-audio-zone/issues>.

## Requirements

- Minecraft Java Edition **26.2** or **26.3** (download the file for your version)
- **Fabric Loader** 0.20.1 or newer and **Fabric API**
- Optional: **Mod Menu**, for a config button
- Client only: don't install it on a server.

## Compatibility

- Tested with Mod Menu installed and without it.
- Not yet verified: Sodium, Iris with shader packs, and Sound Physics Remastered. Please report any problem.

## Status

This is an **alpha**: the features above are complete and tested, but expect rough edges. Planned for 1.0: volume boost
above 100%, cues for muted sounds, repeat limits, the mob inspector, more zone shapes and modes, and copying settings
between servers.

Audio Zone is open source under the MIT license: <https://github.com/jekitchen/mc-audio-zone>.

---
