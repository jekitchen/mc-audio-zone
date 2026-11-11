package soundsculptor.core.profile;

import soundsculptor.core.outline.OutlineMode;
import soundsculptor.core.zones.ZoneEdge;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Profile settings (PRD FR-11: cue defaults, outline mode, default edge, show distance). Keybinds and UI preferences
 * are app-wide and live elsewhere.
 *
 * @param showDistance FR-1.4: show the distance band in the recently played list (default on)
 * @param cueDefault   FR-7.6: the Cue setting new mute rules start with (default on)
 * @param outlines     FR-10.1: "Show zone outlines" (default only while editing)
 * @param defaultEdge  FR-8.5: the edge of zones that use the profile default; Hard or Fade, never
 *                     {@link ZoneEdge.Mode#DEFAULT} (default Hard)
 */
public record ProfileSettings(boolean showDistance, boolean cueDefault, OutlineMode outlines, ZoneEdge defaultEdge) {
	public static final ProfileSettings DEFAULTS = new ProfileSettings(true, true, OutlineMode.DEFAULT, ZoneEdge.hard());

	public ProfileSettings {
		Objects.requireNonNull(outlines, "outlines");
		Objects.requireNonNull(defaultEdge, "defaultEdge");
		if (defaultEdge.mode() == ZoneEdge.Mode.DEFAULT) {
			throw new IllegalArgumentException("the profile's default edge must be hard or fade");
		}
	}

	/** Only "show distance" set; every other setting at its default. */
	public ProfileSettings(boolean showDistance) {
		this(showDistance, true, OutlineMode.DEFAULT, ZoneEdge.hard());
	}

	public ProfileSettings withShowDistance(boolean value) {
		return new ProfileSettings(value, cueDefault, outlines, defaultEdge);
	}

	public ProfileSettings withCueDefault(boolean value) {
		return new ProfileSettings(showDistance, value, outlines, defaultEdge);
	}

	public ProfileSettings withOutlines(OutlineMode value) {
		return new ProfileSettings(showDistance, cueDefault, value, defaultEdge);
	}

	public ProfileSettings withDefaultEdge(ZoneEdge value) {
		return new ProfileSettings(showDistance, cueDefault, outlines, value);
	}

	/** Whether this value is at its default, so its reset button has nothing to do. */
	public boolean isDefault(ProfileSetting setting) {
		return switch (setting) {
			case SHOW_DISTANCE -> showDistance == DEFAULTS.showDistance;
			case CUE_DEFAULT -> cueDefault == DEFAULTS.cueDefault;
			case OUTLINES -> outlines == DEFAULTS.outlines;
			case DEFAULT_EDGE -> defaultEdge.equals(DEFAULTS.defaultEdge);
		};
	}

	/** The values that differ from their defaults. */
	public Set<ProfileSetting> changed() {
		Set<ProfileSetting> changed = EnumSet.noneOf(ProfileSetting.class);
		for (ProfileSetting setting : ProfileSetting.values()) {
			if (!isDefault(setting)) {
				changed.add(setting);
			}
		}
		return changed;
	}

	/** One value's reset-to-default button (PRD FR-12.6); the other values stay as they are. */
	public ProfileSettings reset(ProfileSetting setting) {
		return switch (setting) {
			case SHOW_DISTANCE -> withShowDistance(DEFAULTS.showDistance);
			case CUE_DEFAULT -> withCueDefault(DEFAULTS.cueDefault);
			case OUTLINES -> withOutlines(DEFAULTS.outlines);
			case DEFAULT_EDGE -> withDefaultEdge(DEFAULTS.defaultEdge);
		};
	}

	/** "Reset page": every profile setting back to its default (PRD FR-12.6). */
	public ProfileSettings resetPage() {
		return DEFAULTS;
	}
}
