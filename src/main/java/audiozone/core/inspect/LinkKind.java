package audiozone.core.inspect;

/** How sure the inspector is that a sound came from an entity (PRD FR-2.3). Every link shows its label. */
public enum LinkKind {
	/** Certain: the sound instance was attached to the entity, or the server sent it with the entity's ID. */
	LINKED("linked"),
	/** Guessed: a plain positional sound that started within 1 block of the entity. */
	LIKELY("likely"),
	/** Guessed, but another candidate stood within 0.25 blocks of the chosen one. */
	UNCERTAIN("uncertain");

	private final String key;

	LinkKind(String key) {
		this.key = key;
	}

	/** Key in {@code assets/audiozone/lang/en_us.json}. */
	public String translationKey() {
		return "audiozone.inspect.link." + key;
	}
}
