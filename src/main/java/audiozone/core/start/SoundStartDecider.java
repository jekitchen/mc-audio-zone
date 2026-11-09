package audiozone.core.start;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import audiozone.core.SoundRange;
import audiozone.core.cue.CueRules;
import audiozone.core.cue.CueSettings;
import audiozone.core.limit.RepeatLimiter;
import audiozone.core.recent.PlayStatus;
import audiozone.core.rules.Rule;
import audiozone.core.trace.LastPlays;
import audiozone.core.zones.Precedence;
import audiozone.core.zones.SoundDecision;
import audiozone.core.zones.Zone;

/**
 * Decides one sound start from end to end (PRD section 7 flowchart), so the sound hook makes one call:
 * <ol>
 *   <li><b>Range gate:</b> whether the listener is in vanilla hearing range, from the un-boosted volume
 *       ({@link SoundRange}). Out-of-range sounds are still given their volume, but they never use up a repeat limit,
 *       never show a cue and are not recorded for the trace: not heard, not logged.</li>
 *   <li><b>Decide:</b> global rules, zones and precedence ({@link Precedence}), then the winning rule's repeat limit
 *       ({@link RepeatLimiter}).</li>
 *   <li><b>Drop</b> (over the limit, FR-6.8: status Limited, no cue), <b>mute</b> (status Muted, cue if due, FR-7.1)
 *       or <b>play</b> at 0 to 200% (status Boosted above 100%, FR-5.4).</li>
 *   <li><b>Record</b> the play for the sound trace ({@link LastPlays}, FR-14.6).</li>
 * </ol>
 * Bypass, vanilla-covered sounds (Music, UI, Voice/Speech) and the time before a profile is loaded leave the sound as
 * vanilla plays it.
 *
 * <p>Each rule has its own repeat-limit counter (FR-6.7). Rule IDs are unique only within one list, so a zone rule's
 * counter is keyed by its zone as well ({@link #limiterKey}); a global {@code r1} and zone {@code a}'s {@code r1}
 * never share a count. Counters survive profile saves that keep the rule.
 *
 * <p>Allocates nothing once each sound ID and rule has been seen. Not thread-safe: call everything on the thread that
 * starts sounds.
 */
public final class SoundStartDecider {
	/** Separates the zone ID from the rule ID in a zone rule's limiter key. IDs never contain a line break. */
	private static final char KEY_SEPARATOR = '\n';

	private final LastPlays lastPlays;
	private final RepeatLimiter limiter = new RepeatLimiter();
	private final SoundDecision decision = new SoundDecision();
	private Precedence precedence;
	private Map<Rule, String> globalKeys = Map.of();
	private Map<Zone, Map<Rule, String>> zoneKeys = Map.of();
	private CueSettings cueSettings = CueSettings.DEFAULT;
	private boolean boostSupported = true;

	/** @param lastPlays where each sound's last heard play is recorded for its trace */
	public SoundStartDecider(LastPlays lastPlays) {
		this.lastPlays = lastPlays;
	}

	/**
	 * Uses a new profile snapshot from now on, e.g. after the player saves. Repeat-limit counters of rules that still
	 * exist keep counting; the rest are forgotten.
	 */
	public void setProfile(Precedence precedence) {
		Map<Rule, String> global = new IdentityHashMap<>();
		Set<String> keys = new HashSet<>();
		for (Rule rule : precedence.globalRules().rulesInOrder()) {
			String key = limiterKey(null, rule.id());
			global.put(rule, key);
			keys.add(key);
		}
		Map<Zone, Map<Rule, String>> zones = new IdentityHashMap<>();
		List<Zone> list = precedence.zones();
		for (int i = 0; i < list.size(); i++) {
			Zone zone = list.get(i);
			Map<Rule, String> rules = new IdentityHashMap<>();
			for (Rule rule : precedence.zoneRules(i).rulesInOrder()) {
				String key = limiterKey(zone, rule.id());
				rules.put(rule, key);
				keys.add(key);
			}
			zones.put(zone, rules);
		}
		limiter.retain(keys);
		this.globalKeys = global;
		this.zoneKeys = zones;
		this.precedence = precedence;
	}

	/** The cue settings from the Settings page (FR-7.2, FR-7.7). */
	public void setCueSettings(CueSettings settings) {
		this.cueSettings = settings;
	}

	/** False when boost is off or the audio device ignores a raised gain cap: volumes then stop at 100% (section 7.2). */
	public void setBoostSupported(boolean supported) {
		this.boostSupported = supported;
	}

	/**
	 * The repeat-limit counter key of a rule: its ID for a global rule, its zone's ID and its ID for a zone rule. For
	 * {@link RepeatLimiter#droppedCount} in Diagnostics.
	 *
	 * @param zone the rule's zone, or {@code null} for a global rule
	 */
	public static String limiterKey(Zone zone, String ruleId) {
		return zone == null ? ruleId : zone.id() + KEY_SEPARATOR + ruleId;
	}

	/** The repeat-limit counters, for Diagnostics. */
	public RepeatLimiter limiter() {
		return limiter;
	}

	/**
	 * Estimated bytes of the repeat-limit counters and the current profile's per-ID rule caches (global and every
	 * zone), for the Diagnostics memory readout (PRD 9.1): {@code meter.add("Rules and limits", decider::estimatedBytes)}.
	 * Walks them, so let {@code MemoryMeter} call it, not every sound.
	 */
	public long estimatedBytes() {
		Precedence p = precedence;
		return limiter.estimatedBytes() + (p == null ? 0 : p.estimatedBytes());
	}

	/** Decides {@code in} and writes what to do into {@code out}. */
	public void start(SoundStart in, SoundStartResult out) {
		out.reset();
		boolean audible = !in.positional || SoundRange.isAudible(in.range, in.distanceSq);
		out.audible = audible;
		Precedence p = precedence;
		if (in.vanillaCovered || p == null) {
			return;
		}
		if (in.bypass) {
			if (audible) {
				record(in, 100, false, false);
			}
			return;
		}

		SoundDecision d = decision;
		p.decide(in.soundId, in.positional, in.worldKey, in.dimension, in.x, in.y, in.z, in.px, in.py, in.pz, d);
		float volume = d.volumePercent();
		out.handled = true;
		out.decision = d;
		out.volumePercent = volume;

		if (audible && d.limit() != null && !limiter.tryStart(keyOf(d.limitZone(), d.limitRule()), d.limit(), in.x, in.y,
				in.z, in.nowMs)) {
			out.status = PlayStatus.LIMITED;
			out.factor = 0;
			out.cancel = true;
			out.zone = d.limitZone();
			record(in, volume, true, false);
			return;
		}

		out.zone = d.loudnessZone();
		if (volume <= 0) {
			out.status = PlayStatus.MUTED;
			out.factor = 0;
			out.cancel = !(in.positional && p.anyZoneCouldSetLoudness(in.soundId, in.worldKey, in.dimension));
			out.cue = CueRules.shouldCue(in.hasSubtitle, volume, d.loudnessRule() != null, d.cueOn(), audible, cueSettings);
			out.cueUsesSoundId = out.cue && !in.hasSubtitle;
		} else {
			float capped = Math.min(volume, boostSupported ? 200F : 100F);
			out.factor = capped / 100F;
			out.status = capped > 100 ? PlayStatus.BOOSTED : PlayStatus.PLAYING;
		}
		if (audible) {
			record(in, volume, false, out.cue);
		}
	}

	private String keyOf(Zone zone, Rule rule) {
		Map<Rule, String> keys = zone == null ? globalKeys : zoneKeys.get(zone);
		String key = keys == null ? null : keys.get(rule);
		// Not in the snapshot's maps can only mean a rule from another snapshot: key it the same way (allocates).
		return key != null ? key : limiterKey(zone, rule.id());
	}

	private void record(SoundStart in, float volume, boolean dropped, boolean cueShown) {
		lastPlays.record(in.soundId, in.positional, in.worldKey, in.dimension, in.x, in.y, in.z, in.px, in.py, in.pz, volume,
				dropped, cueShown);
	}
}
