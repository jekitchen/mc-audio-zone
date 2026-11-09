package audiozone.core.trace;

import java.util.Objects;

/**
 * Where a rule shown in the trace lives: the global list, or a zone's own list.
 *
 * @param zoneId the zone's ID, or {@code null} for the global list
 * @param ruleId the rule's ID
 */
public record RuleLocation(String zoneId, String ruleId) {
	public RuleLocation {
		Objects.requireNonNull(ruleId, "ruleId");
	}

	public static RuleLocation global(String ruleId) {
		return new RuleLocation(null, ruleId);
	}

	public static RuleLocation inZone(String zoneId, String ruleId) {
		return new RuleLocation(Objects.requireNonNull(zoneId, "zoneId"), ruleId);
	}

	public boolean isGlobal() {
		return zoneId == null;
	}
}
