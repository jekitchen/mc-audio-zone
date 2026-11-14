package soundsculptor.mc.compat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Finds Minecraft fields and methods by name while the game runs, so one jar works on every supported Minecraft
 * version (docs/PORTING.md, "One jar for every version"). Callers look up once, when their class loads, and keep the
 * result: nothing here runs per frame or per sound. Every failure is an {@link IllegalStateException} that names the
 * class and the member, so a version this jar does not support fails loudly instead of misbehaving.
 *
 * <p>Pure Java (no Minecraft types), so it is unit tested.
 */
public final class RuntimeLookup {
	private static final MethodHandles.Lookup PUBLIC = MethodHandles.publicLookup();

	private RuntimeLookup() {
	}

	/** The public static field {@code owner.name}. */
	public static Field staticField(Class<?> owner, String name) {
		Field field;
		try {
			field = owner.getField(name);
		} catch (NoSuchFieldException e) {
			throw new IllegalStateException(owner.getName() + " has no public field " + name
					+ ": this Minecraft version is not supported", e);
		}
		if (!Modifier.isStatic(field.getModifiers())) {
			throw new IllegalStateException(owner.getName() + "." + name + " is not static: this Minecraft version is not supported");
		}
		return field;
	}

	/** The value of the public static field {@code owner.name}. */
	public static Object staticValue(Class<?> owner, String name) {
		Field field = staticField(owner, name);
		try {
			return field.get(null);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException("cannot read " + owner.getName() + "." + name, e);
		}
	}

	/**
	 * The value of the public static {@code int} field {@code owner.name}, as the running game defines it. Use this for
	 * Minecraft's {@code static final int} constants: javac copies a constant's value into the class that uses it, so
	 * using the constant directly would fix the value of the version the jar was compiled against.
	 */
	public static int staticInt(Class<?> owner, String name) {
		Field field = staticField(owner, name);
		if (field.getType() != int.class) {
			throw new IllegalStateException(owner.getName() + "." + name + " is a " + field.getType().getName()
					+ ", not an int: this Minecraft version is not supported");
		}
		try {
			return field.getInt(null);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException("cannot read " + owner.getName() + "." + name, e);
		}
	}

	/** The public static method {@code owner.name} of the given type. */
	public static MethodHandle staticMethod(Class<?> owner, String name, MethodType type) {
		try {
			return PUBLIC.findStatic(owner, name, type);
		} catch (NoSuchMethodException | IllegalAccessException e) {
			throw new IllegalStateException(owner.getName() + " has no public static method " + name + type
					+ ": this Minecraft version is not supported", e);
		}
	}

	/** The public instance method {@code owner.name} of the given type (the receiver is not part of {@code type}). */
	public static MethodHandle virtualMethod(Class<?> owner, String name, MethodType type) {
		try {
			return PUBLIC.findVirtual(owner, name, type);
		} catch (NoSuchMethodException | IllegalAccessException e) {
			throw new IllegalStateException(owner.getName() + " has no public method " + name + type
					+ ": this Minecraft version is not supported", e);
		}
	}
}
