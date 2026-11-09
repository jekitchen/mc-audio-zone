package audiozone.mc;

import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;

/**
 * One sound start, as seen by the sound hook. A single instance is reused for every sound so the hot path does
 * not allocate (PRD 7.4); observers must copy any field they want to keep.
 */
public final class SoundObservation {
	Identifier id;
	SoundSource category;
	double x;
	double y;
	double z;
	float volume;
	float range;
	double distance;
	boolean relative;
	boolean audible;

	public Identifier id() {
		return id;
	}

	public SoundSource category() {
		return category;
	}

	public double x() {
		return x;
	}

	public double y() {
		return y;
	}

	public double z() {
		return z;
	}

	/** The sound instance's own (vanilla) volume, before category sliders and before any Audio Zone rule. */
	public float volume() {
		return volume;
	}

	/** Vanilla hearing range in blocks; {@link Float#POSITIVE_INFINITY} for relative and non-attenuated sounds. */
	public float range() {
		return range;
	}

	/** Distance from the audio listener in blocks; 0 for relative sounds. */
	public double distance() {
		return distance;
	}

	/** Whether the sound plays relative to the listener (UI clicks, music) rather than at a world position. */
	public boolean relative() {
		return relative;
	}

	/** Whether vanilla would treat the sound as heard (same rule as subtitles). */
	public boolean audible() {
		return audible;
	}
}
