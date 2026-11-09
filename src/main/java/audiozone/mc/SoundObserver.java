package audiozone.mc;

/** Receives sound starts from {@link SoundHook}. The observation object is reused; copy what you keep. */
@FunctionalInterface
public interface SoundObserver {
	void onSound(SoundObservation observation);
}
