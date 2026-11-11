# Releasing Sound Sculptor

How to publish a release on Modrinth and CurseForge. Publishing is done by a maintainer with access to the projects on
both sites; the steps below prepare everything so that part is uploading files and pasting text.

## 1. Before the release

1. `main` is green in CI on every supported Minecraft version (build and client game tests).
2. The manual checks of the release checklist ([PRD 11.5](PRD.md)) are done for each Minecraft version. The rest of
   11.5 (layout at every GUI scale, narration, Mod Menu present and absent, keyboard tasks) is automated.
   - Grayscale screenshot check: statuses and zone outlines are still readable in grayscale.
   - Sodium; Iris with one popular shader pack; Sound Physics Remastered. Note any problem on the mod page.
   - A vanilla server and singleplayer, with nothing installed on the server; Realms if available.
   - Noisy-farm stress test in game (PRD 11.2) with FPS from F3, and the 30-minute soak:
     `./gradlew runClientGameTest -Psoak=1800`.
3. `mod_version` in [`gradle.properties`](../gradle.properties) is the version to release (e.g. `0.1.0-alpha`).
4. [CHANGELOG.md](../CHANGELOG.md) has an entry for it; replace "(unreleased)" with the date.
5. [MOD_PAGE.md](MOD_PAGE.md) matches what ships.

## 2. Tag and build

Push a tag named after the version:

```sh
git tag v0.1.0-alpha
git push origin v0.1.0-alpha
```

The [release workflow](../.github/workflows/release.yml) builds one jar per Minecraft version and creates a **draft**
GitHub release with the jars attached and the changelog entry as notes. Check the draft, then publish it.

To build by hand instead:

```sh
./gradlew build -Pminecraft_version=26.3 -Pfabric_api_version=0.165.2+26.3
./gradlew build -Pminecraft_version=26.2 -Pfabric_api_version=0.161.0+26.2
```

Each puts `soundsculptor-<version>+<minecraft>.jar` in `build/libs/` (ignore the `-sources` jar).

Smoke test each jar in a normal launcher (the official launcher with Fabric, or Prism): launch, mute a sound, make a
zone, see its outline, Bypass with F10.

## 3. Modrinth

Project settings (once): client side **required**, server side **unsupported**; license MIT; source and issue links to
the GitHub repository; icon `src/main/resources/assets/soundsculptor/icon.png`; description from MOD_PAGE.md.

For each jar, **Versions → Create version**:

| Field | Value |
| --- | --- |
| Version number | `0.1.0-alpha+26.3` (the jar's version) |
| Version title | `Sound Sculptor 0.1.0-alpha for 26.3` |
| Release channel | Alpha |
| Loaders | Fabric |
| Game versions | the jar's Minecraft version only (26.3 or 26.2) |
| Dependencies | Fabric API: required. Mod Menu: optional |
| Changelog | the CHANGELOG.md entry |

## 4. CurseForge

Project settings (once): Java Edition mod, Fabric; license MIT; source link; icon and description as above.

For each jar, **Upload file**:

| Field | Value |
| --- | --- |
| Display name | `Sound Sculptor 0.1.0-alpha for 26.3` |
| Release type | Alpha |
| Game versions | the jar's Minecraft version, plus **Fabric** and **Client** |
| Related projects | Fabric API: required dependency. Mod Menu: optional dependency |
| Changelog | the CHANGELOG.md entry (Markdown) |

## 5. After the release

1. Bump `mod_version` to the next version and add an "(unreleased)" entry to CHANGELOG.md.
2. Keep an eye on issues titled "fair play" (we aim to answer within 7 days).
3. New Minecraft versions follow [PRD 10.5](PRD.md): port at the first pre-release, ship within 7 days of the release.
