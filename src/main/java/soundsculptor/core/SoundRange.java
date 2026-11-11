package soundsculptor.core;

/**
 * Vanilla hearing range (PRD section 7, "range gate"). Pure Java so it can be unit-tested without Minecraft.
 *
 * <p>Mirrors Minecraft 26.2 and 26.3 exactly: {@code SoundEngine.play} computes
 * {@code max(instanceVolume, 1) * attenuationDistance}, or infinity for relative sounds and sounds with
 * attenuation {@code NONE}; the subtitle overlay then treats a sound as audible when the listener is strictly
 * closer than that range ({@code Vec3.closerThan}). The range always uses the sound instance's own volume, never
 * a volume changed by Sound Sculptor, so rules and boost cannot change which sounds reach the player.
 */
public final class SoundRange {
	private SoundRange() {
	}

	/**
	 * @param instanceVolume      the sound instance's own volume ({@code SoundInstance.getVolume()})
	 * @param attenuationDistance the resolved sound's attenuation distance in blocks (16 unless the sound sets one)
	 * @param relative            whether the sound plays relative to the listener (UI clicks, music)
	 * @param noAttenuation       whether the sound's attenuation is {@code NONE}
	 * @return the hearing range in blocks, or {@link Float#POSITIVE_INFINITY} when distance does not matter
	 */
	public static float vanillaRange(float instanceVolume, int attenuationDistance, boolean relative, boolean noAttenuation) {
		if (relative || noAttenuation) {
			return Float.POSITIVE_INFINITY;
		}
		return Math.max(instanceVolume, 1.0F) * (float) attenuationDistance;
	}

	/** Whether a listener at squared distance {@code distanceSq} hears a sound with this range (strictly closer). */
	public static boolean isAudible(float range, double distanceSq) {
		if (Float.isInfinite(range)) {
			return true;
		}
		double r = range;
		return distanceSq < r * r;
	}
}
