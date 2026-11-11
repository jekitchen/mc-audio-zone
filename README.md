# Sound Sculptor

Sound Sculptor is a client-only Fabric mod for Minecraft: Java Edition that lets you mute, turn down, boost or rate-limit any sound, everywhere or only inside areas you choose, without ever hearing more than vanilla allows.

By maxismagical and Sciencode. MIT licensed.

## For server admins

Sound Sculptor only changes how loud sounds are on the player's own computer. It never tells a player anything that vanilla Minecraft's sounds and subtitles don't already tell them. This is a hard rule of the project, not a setting.

**Nothing on your server, nothing sent to it.** Sound Sculptor is installed on the client only. It sends no custom packets, opens no plugin channel and does not change the client brand, so your server sees a vanilla client. You can check this with any packet logger.

**What it can and cannot do**

| Feature | Could it give an advantage? | How Sound Sculptor rules that out |
| --- | --- | --- |
| Mute, turn down, repeat limits | No: they only remove sound | Same effect as vanilla's own volume sliders, just per sound |
| Volume boost (up to 200%) | Hearing sounds from farther away | Boost makes a sound louder only where vanilla already plays it. Hearing range is always worked out from the original vanilla volume, so at the edge of vanilla range a boosted sound is silent, exactly as in vanilla |
| On-screen cues for muted sounds | More detail than subtitles | A cue shows only for sounds within the range at which vanilla shows subtitles, with the vanilla subtitle's text and the same left/right arrow, and no distance |
| Recently played list | Detecting unseen mobs, or exact distance | Lists only sounds the player was in vanilla range to hear. Distance is shown as Close, Near or Far, never in blocks, and never with a direction. Players can hide the distance column |
| Point-at-entity inspector | Finding mobs behind walls | Only selects an entity the player can see in a clear line of sight, and never highlights anything through walls |
| Zone outlines | No | They show only zones the player drew themselves |

**How it is checked.** These limits have automated tests that run on every build, for example that a 200% boost is silent at exactly the distance vanilla is. Before each release they are also tested in game, as described in the [PRD](docs/PRD.md) (section 11).

**Status.** The first alpha, 0.1.0-alpha, has mute, volume (0 to 100%), rules, zones with outlines, the recently played list and search. Boost, cues, repeat limits and the inspector, listed above with their safeguards, follow in 1.0. See the [changelog](CHANGELOG.md).

**Questions or concerns.** If you think any feature gives more information than vanilla, please [open an issue](https://github.com/jekitchen/mc-sound-sculptor/issues) and mention "fair play" in the title. We aim to look at these within 7 days.

## Specification

The [Product Requirements Document](docs/PRD.md) is the source of truth for the project's specifications. Changes to requirements are made there, through pull requests.

## Releasing

[docs/RELEASING.md](docs/RELEASING.md) has the release steps, and [docs/MOD_PAGE.md](docs/MOD_PAGE.md) the Modrinth and CurseForge description.

## Building

Requires JDK 25. Run `./gradlew build` to build and test, or `./gradlew runClient` to launch the game with the mod. See [docs/DEV_ENVIRONMENT.md](docs/DEV_ENVIRONMENT.md) for the full toolchain, versions and setup steps.
