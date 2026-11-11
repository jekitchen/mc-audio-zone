package soundsculptor.core.lists;

import soundsculptor.core.Translator;

/**
 * A zone's location label: "Here", "120 blocks away", "Other world" or "Other dimension".
 *
 * @param blocks whole blocks from the player to the zone's anchor; only used for {@link LocationKind#AWAY}
 */
public record ZoneLocation(LocationKind kind, long blocks) {
	public SummaryPart part() {
		return kind == LocationKind.AWAY
				? SummaryPart.of(kind.translationKey(), Long.toString(blocks))
				: SummaryPart.of(kind.translationKey());
	}

	public String text(Translator translator) {
		return part().text(translator);
	}
}
