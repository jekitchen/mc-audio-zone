package audiozone.core.search;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Readable names for sounds without a vanilla subtitle (M8 review). */
class SoundNamesTest {
	@Test
	void groupComesLastAndWordsAreSpaced() {
		assertEquals("Basalt deltas: additions (ambient)", SoundNames.readable("minecraft:ambient.basalt_deltas.additions"));
		assertEquals("Stone: step (block)", SoundNames.readable("minecraft:block.stone.step"));
		assertEquals("Parrot: imitate: zombie villager (entity)", SoundNames.readable("minecraft:entity.parrot.imitate.zombie_villager"));
	}

	@Test
	void shortIdsStillReadWell() {
		assertEquals("Thunder (weather)", SoundNames.readable("minecraft:weather.thunder"));
		assertEquals("Click", SoundNames.readable("minecraft:click"));
	}

	@Test
	void modSoundsNameTheirMod() {
		assertEquals("Fan: whoosh (block, create)", SoundNames.readable("create:block.fan.whoosh"));
		assertEquals("Boing (mymod)", SoundNames.readable("mymod:boing"));
	}

	@Test
	void oddIdsAreLeftAlone() {
		assertEquals("", SoundNames.readable(""));
		assertEquals("minecraft:", SoundNames.readable("minecraft:"));
		assertEquals("A: b (x)", SoundNames.readable("minecraft:x..a__.b"));
	}
}
