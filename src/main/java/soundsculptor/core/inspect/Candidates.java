package soundsculptor.core.inspect;

/**
 * The entities near a positional sound, gathered by the sound hook and passed to
 * {@link EntitySounds#recordPositional}. A reusable buffer: {@link #clear()} it before each sound, so the sound path
 * allocates nothing. The hook may add every entity in a small box around the sound; entities more than 1 block away
 * are ignored.
 */
public final class Candidates {
	/** More entities than this within a block of one sound are dropped; a pen that full is beyond guessing anyway. */
	public static final int CAPACITY = 32;

	final int[] ids = new int[CAPACITY];
	final String[] types = new String[CAPACITY];
	final double[] xs = new double[CAPACITY];
	final double[] ys = new double[CAPACITY];
	final double[] zs = new double[CAPACITY];
	final boolean[] inRange = new boolean[CAPACITY];
	int size;

	public void clear() {
		for (int i = 0; i < size; i++) {
			types[i] = null;
		}
		size = 0;
	}

	/**
	 * @param entityId the entity's network ID
	 * @param typeId   its type, e.g. {@code minecraft:villager}
	 * @param inRange  whether the entity is within vanilla hearing range of the player (FR-2.2). Entities that are
	 *                 not are never linked and never make a link uncertain.
	 */
	public void add(int entityId, String typeId, double x, double y, double z, boolean inRange) {
		if (size == CAPACITY) {
			return;
		}
		ids[size] = entityId;
		types[size] = typeId;
		xs[size] = x;
		ys[size] = y;
		zs[size] = z;
		this.inRange[size] = inRange;
		size++;
	}

	public int size() {
		return size;
	}
}
