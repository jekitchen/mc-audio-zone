package soundsculptor.core.browser;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

/** PRD FR-4.8: "Like this" lists a sound's groups, closest first, with how many sounds each matches. */
class LikeThisTest {
	private static final List<String> KNOWN = List.of(
			"minecraft:entity.villager.ambient",
			"minecraft:entity.villager.hurt",
			"minecraft:entity.villager.death",
			"minecraft:entity.zombie.ambient",
			"minecraft:block.stone.step",
			"mod:entity.villager.ambient");

	@Test
	void likeThisClosestFirstWithCounts() {
		List<LikeThis.Group> groups = LikeThis.groups("minecraft:entity.villager.ambient", KNOWN);
		assertEquals(List.of(
				new LikeThis.Group("minecraft:entity.villager.*", 3),
				new LikeThis.Group("minecraft:entity.*.ambient", 2),
				new LikeThis.Group("minecraft:entity.*", 4),
				new LikeThis.Group("minecraft:*", 5)), groups);
	}
}
