package soundsculptor.core.sets;

/**
 * The winning entry for each property of one sound ID within one place's Sound Sets (PRD FR-4.5). A {@code null}
 * winner means no entry sets that property, so vanilla behavior applies (FR-9 step 7).
 *
 * <p>Each winner comes with the ID of the set (layer) it won in: {@code ""} for a resolver built by
 * {@link EntryResolver#of}, and {@code null} when there is no winner. The ID is taken from the layer, not looked up
 * from the entry, because a set and its duplicate share entry instances.
 *
 * <p>Zones and Everywhere are combined later by the precedence step (FR-9, issue #7).
 */
public record EntryResolution(SetEntry loudness, SetEntry limit, SetEntry cue,
		String loudnessSet, String limitSet, String cueSet) {
	/** Shared result for a sound no entry sets anything for. */
	public static final EntryResolution NONE = new EntryResolution(null, null, null, null, null, null);

	public boolean muted() {
		return loudness != null && Boolean.TRUE.equals(loudness.mute());
	}

	/** The entry's volume setting, kept while muted so unmuting restores it (FR-5.1). 100 when unset. */
	public int volumePercent() {
		return loudness == null || loudness.volume() == null ? 100 : loudness.volume();
	}

	/** What the sound actually plays at: 0 when muted, otherwise {@link #volumePercent()}. */
	public int effectiveVolumePercent() {
		return muted() ? 0 : volumePercent();
	}

	public boolean cueOn() {
		return cue != null && cue.cue();
	}
}
