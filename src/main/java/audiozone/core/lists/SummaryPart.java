package audiozone.core.lists;

import java.util.List;

import audiozone.core.Translator;

/**
 * One chip of a list row summary: a translation key and its already-formatted arguments. Each chip is a whole
 * phrase, so translators never have to fit words around other chips (PRD 9.3).
 */
public record SummaryPart(String key, List<String> args) {
	public SummaryPart {
		args = List.copyOf(args);
	}

	public static SummaryPart of(String key, String... args) {
		return new SummaryPart(key, List.of(args));
	}

	public String text(Translator translator) {
		return translator.translate(key, args.toArray());
	}
}
