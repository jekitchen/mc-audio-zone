package soundsculptor.core.trace;

import soundsculptor.core.rules.Rule;

/** The rule that decided one setting within a list, and why. */
public record RuleWinner(Rule rule, RuleWinReason reason) {
}
