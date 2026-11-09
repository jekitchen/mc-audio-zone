package audiozone.core.trace;

/** Why a rule won a setting within one list (PRD FR-14.2 item 1, mirroring the FR-4.5 steps). */
public enum RuleWinReason {
	/** No other matching rule sets this. */
	ONLY_MATCH("only_match"),
	/** An exact sound ID beats any pattern. */
	EXACT_ID("exact_id"),
	/** More literal characters: a more specific pattern. */
	MORE_LITERAL("more_literal"),
	/** Same literal characters, fewer {@code *}. */
	FEWER_STARS("fewer_stars"),
	/** Equally specific; higher in the list. */
	LIST_ORDER("list_order");

	private final String key;

	RuleWinReason(String key) {
		this.key = key;
	}

	/** Key in {@code assets/audiozone/lang/en_us.json}. */
	public String translationKey() {
		return "audiozone.trace.rule_reason." + key;
	}
}
