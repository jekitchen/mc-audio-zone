package soundsculptor.mc;

import soundsculptor.core.search.SoundEntry;
import soundsculptor.core.search.SoundNames;
import soundsculptor.core.search.SoundSearch;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Every sound ID the client knows (PRD FR-3.1): the sound manager's list, which holds the sound event registry plus
 * every ID in the {@code sounds.json} files of loaded mods and resource packs. Used for the rule editor's preview
 * (FR-4.3) and the Rules tab's match counts. Not on the sound path.
 */
public final class KnownSounds {
	private static Collection<Identifier> source;
	private static int sourceSize = -1;
	private static List<String> ids = List.of();

	private KnownSounds() {
	}

	/**
	 * Sorted IDs, without the vanilla-covered ones (FR-4.4). Rebuilt when the sound manager's list changes (after a
	 * resource reload).
	 */
	public static synchronized List<String> ids() {
		Collection<Identifier> current = Minecraft.getInstance().getSoundManager().getAvailableSounds();
		if (current != source || current.size() != sourceSize) {
			List<String> fresh = new ArrayList<>(current.size());
			for (Identifier id : current) {
				String text = id.toString();
				if (!SoundSearch.isVanillaCovered(text)) {
					fresh.add(text);
				}
			}
			fresh.sort(null);
			source = current;
			sourceSize = current.size();
			ids = List.copyOf(fresh);
		}
		return ids;
	}

	/**
	 * Every known sound with its name for searching (FR-3.1, FR-4.8): the vanilla subtitle in the current language, or
	 * the readable name made from the ID ("Basalt deltas: additions (ambient)") for a sound without one, so plain words
	 * find those too. Built on each call (a few thousand sounds), so it follows the language and resource packs.
	 */
	public static List<SoundEntry> entries() {
		List<String> ids = ids();
		List<SoundEntry> entries = new ArrayList<>(ids.size());
		for (String id : ids) {
			entries.add(new SoundEntry(id, name(id)));
		}
		return entries;
	}

	/** The sound's subtitle in the current language, or its readable name if it has none. */
	public static String name(String soundId) {
		Identifier id = Identifier.tryParse(soundId);
		WeighedSoundEvents events = id == null ? null : Minecraft.getInstance().getSoundManager().getSoundEvent(id);
		Component subtitle = events == null ? null : events.getSubtitle();
		return subtitle != null ? subtitle.getString() : SoundNames.readable(soundId);
	}

	/** IDs that played in a vanilla-covered category this session, which no rule matches either. */
	public static Set<String> seenExcluded() {
		return RecentLog.get().sounds().seenExcludedIds();
	}
}
