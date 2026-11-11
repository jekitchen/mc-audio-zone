package soundsculptor.core.editor;

/** A field of an editor draft. Text fields take typed text; the others are toggles and choices with their own setters. */
public interface DraftField {
	/** Whether the player types this value (so {@link Draft#set} accepts it). */
	boolean isText();
}
