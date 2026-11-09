package audiozone.core.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A parsed search box query (PRD FR-3.5).
 *
 * <table>
 *   <caption>Syntax</caption>
 *   <tr><td>{@code villager}</td><td>contains "villager"</td></tr>
 *   <tr><td>{@code villager hurt}</td><td>contains all words (AND)</td></tr>
 *   <tr><td>{@code villager|zombie}</td><td>contains either (OR); {@code |} splits into groups first</td></tr>
 *   <tr><td>{@code -ambient}</td><td>must not contain "ambient"</td></tr>
 *   <tr><td>{@code "glass break"}</td><td>exact phrase, spaces included</td></tr>
 *   <tr><td>{@code block.*.step}</td><td>{@code *} matches any characters</td></tr>
 * </table>
 *
 * <p>Matching ignores case. Each word may match the sound ID or its subtitle text; an excluded word must appear in
 * neither. Inside quotes, spaces and {@code |} are literal; an unclosed quote runs to the end. A lone {@code -},
 * empty quotes and empty groups are ignored, and an empty query matches everything. Regex ({@code /.../}) is a
 * Future feature and is not parsed here.
 */
public final class SearchQuery {
	private final List<Group> groups;

	private SearchQuery(List<Group> groups) {
		this.groups = groups;
	}

	public static SearchQuery parse(String raw) {
		Parser parser = new Parser();
		String s = raw.toLowerCase(Locale.ROOT);
		for (int i = 0; i < s.length(); i++) {
			parser.accept(s.charAt(i));
		}
		parser.endGroup();
		return new SearchQuery(List.copyOf(parser.groups));
	}

	/** True when the query has no words, so everything matches. */
	public boolean isEmpty() {
		return groups.isEmpty();
	}

	public boolean matches(SoundEntry entry) {
		if (groups.isEmpty()) {
			return true;
		}
		for (Group group : groups) {
			if (group.matches(entry)) {
				return true;
			}
		}
		return false;
	}

	private record Group(List<Term> include, List<Term> exclude) {
		boolean matches(SoundEntry entry) {
			for (Term term : include) {
				if (!term.matches(entry)) {
					return false;
				}
			}
			for (Term term : exclude) {
				if (term.matches(entry)) {
					return false;
				}
			}
			return true;
		}
	}

	/** One word or phrase, already lowercased. Words with {@code *} become a glob that may match anywhere. */
	private record Term(String text, String glob) {
		static Term of(String text) {
			return new Term(text, text.indexOf('*') >= 0 ? "*" + text + "*" : null);
		}

		boolean matches(SoundEntry entry) {
			return matchesText(entry.lowerId) || (entry.lowerSubtitle != null && matchesText(entry.lowerSubtitle));
		}

		private boolean matchesText(String haystack) {
			return glob == null ? haystack.contains(text) : Glob.matches(glob, haystack);
		}
	}

	private static final class Parser {
		final List<Group> groups = new ArrayList<>();
		private List<Term> include = new ArrayList<>();
		private List<Term> exclude = new ArrayList<>();
		private final StringBuilder token = new StringBuilder();
		private boolean negate;
		private boolean inQuote;

		void accept(char c) {
			if (c == '"') {
				inQuote = !inQuote;
			} else if (inQuote) {
				token.append(c);
			} else if (c == '|') {
				endGroup();
			} else if (Character.isWhitespace(c)) {
				endToken();
			} else if (c == '-' && token.isEmpty() && !negate) {
				negate = true;
			} else {
				token.append(c);
			}
		}

		void endToken() {
			if (!token.isEmpty()) {
				(negate ? exclude : include).add(Term.of(token.toString()));
			}
			token.setLength(0);
			negate = false;
		}

		void endGroup() {
			endToken();
			if (!include.isEmpty() || !exclude.isEmpty()) {
				groups.add(new Group(List.copyOf(include), List.copyOf(exclude)));
			}
			include = new ArrayList<>();
			exclude = new ArrayList<>();
		}
	}
}
