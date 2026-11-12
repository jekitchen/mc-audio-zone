package soundsculptor.core.sets;

import java.util.List;
import java.util.Objects;

/**
 * One Sound Set's entries as a layer of a place's resolver (PRD FR-4.5). A higher layer beats a lower one for every
 * property it sets; specificity only orders entries within one layer.
 *
 * @param setId   the set the entries belong to, reported with each winner; {@code ""} for a list that is no set
 * @param entries the set's entries in list order, disabled ones included
 */
public record Layer(String setId, List<SetEntry> entries) {
	public Layer {
		Objects.requireNonNull(setId, "setId");
		entries = List.copyOf(entries);
	}
}
