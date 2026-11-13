package soundsculptor.core.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A readable name for a sound that has no vanilla subtitle, made from its ID (M8 review): the path's parts with spaces
 * instead of underscores, the first part (the group, such as "ambient" or "block") last in brackets, and a mod's
 * namespace after it. {@code minecraft:ambient.basalt_deltas.additions} becomes "Basalt deltas: additions (ambient)".
 *
 * <p>Shown where a subtitle would be (the Sounds tab); the ID itself is always shown too. English word order
 * only: these are not translations.
 */
public final class SoundNames {
	private SoundNames() {
	}

	public static String readable(String soundId) {
		int colon = soundId.indexOf(':');
		String namespace = colon < 0 ? "minecraft" : soundId.substring(0, colon);
		String path = soundId.substring(colon + 1);
		List<String> parts = new ArrayList<>();
		for (String part : path.split("\\.")) {
			String words = part.replace('_', ' ').strip().replaceAll(" +", " ");
			if (!words.isEmpty()) {
				parts.add(words);
			}
		}
		if (parts.isEmpty()) {
			return soundId;
		}
		List<String> notes = new ArrayList<>();
		if (parts.size() > 1) {
			notes.add(parts.remove(0));
		}
		if (!namespace.equals("minecraft")) {
			notes.add(namespace);
		}
		String name = String.join(": ", parts);
		name = name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
		return notes.isEmpty() ? name : name + " (" + String.join(", ", notes) + ")";
	}
}
