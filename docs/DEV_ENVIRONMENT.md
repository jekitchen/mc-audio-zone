# Development environment

This page lists everything needed to build, test and run Audio Zone from source. Update it whenever a tool or version is added or changed.

Last verified: 2026-11-08 on Windows 11. Both Minecraft targets build, and the unit tests and client game tests pass on both.

## Tools to install

| Tool | Version | Required? | Why | Install |
| --- | --- | --- | --- | --- |
| JDK | **25** (Eclipse Temurin 25.0.5 used) | Required | Minecraft 26.2 and 26.3 require Java 25 (Mojang `javaVersion.majorVersion = 25`). Gradle, Loom and the dev client all run on it. | Windows: `winget install EclipseAdoptium.Temurin.25.JDK` · macOS: `brew install --cask temurin@25` · Linux: [adoptium.net](https://adoptium.net/) or your package manager |
| Git | any recent | Required | Source control | [git-scm.com](https://git-scm.com/) |
| Gradle | 9.8.2 | **Do not install** | Supplied by the Gradle wrapper (`gradlew` / `gradlew.bat`), which downloads the exact version on first run | — |
| IntelliJ IDEA (Community is fine) | 2025.3+ | Recommended | Best Fabric support; imports the Gradle project directly | [jetbrains.com/idea](https://www.jetbrains.com/idea/) |
| Minecraft Development plugin for IntelliJ | latest | Recommended | Mixin target checking, Fabric templates, `fabric.mod.json` support | IntelliJ → Settings → Plugins → "Minecraft Development" |
| GitHub CLI (`gh`) | latest | Optional | Opening PRs and reading CI results from the terminal | `winget install GitHub.cli` / `brew install gh`, then `gh auth login` |
| Python | 3.x | Optional | Only to regenerate the GUI icons with `tools/make_icons.py` (no packages needed) | [python.org](https://www.python.org/) |

You do not need a Minecraft install or account to build. `runClient` downloads the game and starts it in offline dev mode.

### Point Gradle at JDK 25

Gradle uses `JAVA_HOME`. If an older Java (8, 17 or 21) is first on your `PATH`, set `JAVA_HOME` to the JDK 25 folder:

- Windows (PowerShell, current user): `[Environment]::SetEnvironmentVariable('JAVA_HOME', 'C:\Program Files\Eclipse Adoptium\jdk-25.0.5.8-hotspot', 'User')`, then open a new terminal.
- macOS/Linux: `export JAVA_HOME=$(/usr/libexec/java_home -v 25)` (macOS) or the JDK path (Linux) in your shell profile.

In IntelliJ, also set File → Project Structure → SDK and Settings → Build Tools → Gradle → Gradle JVM to JDK 25.

## Libraries and versions (pinned in the repo)

All versions live in [`gradle.properties`](../gradle.properties), except the Gradle version, which lives in [`gradle/wrapper/gradle-wrapper.properties`](../gradle/wrapper/gradle-wrapper.properties). Check new versions at [fabricmc.net/develop](https://fabricmc.net/develop).

| Component | Version | Notes |
| --- | --- | --- |
| Minecraft | 26.3 (default), 26.2 | Unobfuscated, so code uses Mojang's official names (PRD A1) |
| Fabric Loader | 0.20.1 | |
| Fabric Loom (Gradle plugin) | 1.19.4 | Plugin id `net.fabricmc.fabric-loom`: the non-remapping Loom used for unobfuscated 26.x |
| Fabric API | 0.165.2+26.3 / 0.161.0+26.2 | One build per Minecraft version |
| Mixin | bundled with Fabric Loader | Config: `src/main/resources/audiozone.mixins.json`, package `audiozone.mc.mixin` |
| JUnit Jupiter | 6.1.3 | Pure-logic tests that run without Minecraft (PRD 7.6) |
| Gradle | 9.8.2 | Wrapper jar SHA-256 checked against Gradle's published checksum; CI re-validates it on every run |
| Mod Menu | not added yet | Optional "suggests" dependency, added at M8 (repo `https://maven.terraformersmc.com/`) |

## Common commands

Run from the repo root. On Windows use `gradlew` (cmd/PowerShell) or `./gradlew` (Git Bash).

| Task | Command |
| --- | --- |
| Build and test (Minecraft 26.3) | `./gradlew build` |
| Build and test for Minecraft 26.2 | `./gradlew build -Pminecraft_version=26.2 -Pfabric_api_version=0.161.0+26.2` |
| Unit tests only | `./gradlew test` |
| Launch the game with the mod (dev client) | `./gradlew runClient` |
| Run the in-game milestone tests (opens a game window, needs a display) | `./gradlew runClientGameTest` (add the `-P` flags above for 26.2) |
| Same, on a machine without an audio device | Set the environment variable `ALSOFT_DRIVERS=null` first (OpenAL Soft's silent output), as CI does |
| Turn the dev client's per-sound log off/on | JVM property `-Daudiozone.logSounds=false` / `true` (on by default in the dev client, off in release jars) |
| Generate Minecraft sources for browsing | `./gradlew genSources` |
| Clean | `./gradlew clean` |

Output jars go to `build/libs/` as `audiozone-<version>+<minecraft>.jar`. The dev client's game folder is `run/` (git-ignored); its log is `run/logs/latest.log`.

The first build downloads Minecraft and the dependencies (about 1–2 minutes). Later builds use the cache in `~/.gradle`.

## Continuous integration

[`.github/workflows/build.yml`](../.github/workflows/build.yml) runs on every push and pull request. It validates the Gradle wrapper, sets up Temurin JDK 25, and builds and tests against Minecraft 26.2 and 26.3 in a matrix. The jars are uploaded as workflow artifacts. A second matrix job runs the client game tests (`runClientGameTest`) with Mesa software graphics. Loom starts the game under `xvfb-run` by itself when the `CI` variable is set, so the workflow only installs `xvfb` and the Mesa OpenGL and Vulkan drivers. The job has a 20-minute timeout, because a game that cannot create a window hangs instead of exiting. When a supported Minecraft version is added or a Fabric API version is bumped, update `gradle.properties`, both matrices and [`docs/PORTING.md`](PORTING.md).

## Project layout

```
build.gradle, settings.gradle, gradle.properties   Gradle build (Loom)
gradlew, gradlew.bat, gradle/wrapper/              Gradle wrapper (do not edit by hand)
src/main/java/audiozone/                           Mod code; client entry point AudioZoneClient
src/main/java/audiozone/mc/                        Version-sensitive code only (Mixins, adapters), PRD 7.6
src/main/java/audiozone/mc/ui/                     UI kit (PRD 6.2); all drawing goes through Draw.java
src/main/java/audiozone/core/                      Pure Java logic with unit tests (no Minecraft classes)
src/main/versions/<minecraft_version>/java/        Classes whose source differs per Minecraft version (see PORTING.md)
src/main/resources/assets/audiozone/textures/gui/sprites/icon/   GUI icons (generated by tools/make_icons.py)
tools/                                             Developer scripts
src/main/resources/fabric.mod.json                 Mod metadata (client-only, MIT)
src/main/resources/audiozone.mixins.json           Mixin config
src/main/resources/assets/audiozone/lang/          Translations (en_us.json)
src/test/java/audiozone/                           JUnit tests for pure logic
src/gametest/                                      Client game tests (Fabric client game-test API), one per milestone
docs/PRD.md                                        Requirements (source of truth)
docs/DEV_ENVIRONMENT.md                            This page
docs/PORTING.md                                    Version-sensitive touch points and 26.2/26.3 differences
```

## Git identity

Commit with your GitHub **noreply** email so your personal address never shows up in history:

```
git config --local user.name  "<github-username>"
git config --local user.email "<id>+<github-username>@users.noreply.github.com"
git config --local user.useConfigOnly true
```

Your noreply address is listed in GitHub → Settings → Emails. Also turn on "Keep my email addresses private" and "Block command line pushes that expose my email" there. Those settings make merges and edits done on github.com use the noreply address too.

## Verifying a fresh setup

1. `java -version` (with `JAVA_HOME` set) reports 25.
2. `./gradlew build` ends with `BUILD SUCCESSFUL`, and `build/libs/` contains the jar.
3. `./gradlew runClient` opens Minecraft, and the log shows `Loading N mods: - audiozone …` and `Audio Zone initialized` with no errors from Audio Zone. Press `;` in a world: the Audio Zone screen opens.
4. `./gradlew runClientGameTest` ends with `BUILD SUCCESSFUL`. Screenshots are saved to `build/run/clientGameTest/screenshots/`.
