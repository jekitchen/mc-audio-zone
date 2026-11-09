# Porting checklist

PRD section 7.6 requires this list. It names every place Audio Zone touches version-specific Minecraft code. When porting to a new Minecraft version (PRD 10.5), work through it top to bottom, then re-run the listed tests on every supported version.

Supported versions: **26.2, 26.3**. Each version gets its own jar from one source tree (Decisions Log, M0):

```
./gradlew build runClientGameTest                                                         # 26.3 (default)
./gradlew build runClientGameTest -Pminecraft_version=26.2 -Pfabric_api_version=0.161.0+26.2
```

## Version-sensitive touch points

Add a row whenever you add a Mixin, an access widener entry or a call into version-sensitive Minecraft API.

| Since | Touch point | Minecraft API used | Re-test |
| --- | --- | --- | --- |
| M0 | Open-screen key (`AudioZoneClient`) | `KeyMapping(String, int, KeyMapping.Category)`, `KeyMapping.Category.register(Identifier)`, Fabric `KeyMappingHelper`, `InputConstants.KEY_*` | M0 game test |
| M0 | Opening a screen (`AudioZoneClient.onEndTick`) | `Minecraft.gui.screen()`, `Minecraft.gui.setScreen(Screen)` | M0 game test |
| M0 | Main screen (`audiozone.mc.AudioZoneScreen`) | `Screen.extractRenderState(GuiGraphicsExtractor, …)`, `GuiGraphicsExtractor.centeredText` | M0 game test (screenshot) |

## Known differences between 26.2 and 26.3

Found at M0 by comparing the two game jars. Code that touches any of these needs a per-version implementation, or must use an API that exists in both.

| Area | 26.2 | 26.3 | Affects |
| --- | --- | --- | --- |
| Hearing range | Computed inline in `SoundEngine.play`: `max(volume, 1) × Sound.getAttenuationDistance()`, infinite if relative or attenuation `NONE` | Same formula, moved into `private static float SoundEngine.getRange(boolean, Attenuation, float)` | M1 range gate, Mixin targets in `play` |
| Subtitle listeners | Notified inline in `play` | Moved into `SoundEngine.notifyListeners(SoundInstance, WeighedSoundEvents, float)` | M1 log/range consistency, M9 cues |
| Resolving a sound | `SoundInstance.resolve(SoundManager)` | Renamed `getOrResolve(SoundManager)`; new `getSoundEvent()` | Anything reading the resolved sound |
| Key types | `InputConstants.Type.KEYSYM`, `SCANCODE`, `MOUSE` | `KEYBOARD`, `MOUSE` (`SCANCODE` removed) | Use `KeyMapping(String, int, Category)` and `InputConstants.getKey(String)`, which exist in both |
| Screen narration | `updateNarratorStatus(boolean)` | Takes a `NarrationTrigger` argument; new `scheduleNarration()` | M2 UI kit (narration) |
| World rendering | — | `LevelRenderer` changed substantially (75 signature lines differ) | M7 zone outlines |

Unchanged between the two versions: `SoundEngine.play(SoundInstance)` and both `calculateVolume` overloads, `SoundSource` (categories), `ChannelAccess`, `SoundEngineExecutor`, `com.mojang.blaze3d.audio.Channel`/`Library`/`Listener`, `KeyMapping` signatures, `GuiGraphicsExtractor`.

### How to compare two versions

Loom caches each game jar at `~/.gradle/caches/fabric-loom/<version>/minecraft-merged.jar` after a build for that version. Compare class signatures with `javap` from JDK 25:

```
javap -p -cp ~/.gradle/caches/fabric-loom/26.2/minecraft-merged.jar net.minecraft.client.sounds.SoundEngine > a.txt
javap -p -cp ~/.gradle/caches/fabric-loom/26.3/minecraft-merged.jar net.minecraft.client.sounds.SoundEngine > b.txt
diff a.txt b.txt
```

Add `-c` to compare bytecode, for example to see whether a method body you inject into changed.
