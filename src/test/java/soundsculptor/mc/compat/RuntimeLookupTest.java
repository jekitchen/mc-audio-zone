package soundsculptor.mc.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import org.junit.jupiter.api.Test;

/** Runtime lookups that let one jar run on every supported Minecraft version (docs/PORTING.md). */
class RuntimeLookupTest {
	/** Stands in for a Minecraft class such as {@code InputConstants}. */
	public static final class Fixture {
		public static final int KEY_TAB = 258;
		public static final String NAME = "fixture";
		public static final Integer BOXED = 7;
		public final int instanceField = 1;
		static final int HIDDEN = 3;

		public static int twice(int value) {
			return value * 2;
		}

		private Fixture() {
		}
	}

	@Test
	void readsAPublicStaticIntByName() {
		assertEquals(258, RuntimeLookup.staticInt(Fixture.class, "KEY_TAB"));
	}

	@Test
	void aMissingFieldFailsNamingTheClassAndTheField() {
		IllegalStateException e = assertThrows(IllegalStateException.class, () -> RuntimeLookup.staticInt(Fixture.class, "KEY_NOPE"));
		assertTrue(e.getMessage().contains("KEY_NOPE"), e.getMessage());
		assertTrue(e.getMessage().contains(Fixture.class.getName()), e.getMessage());
	}

	@Test
	void aFieldThatIsNotAPublicStaticIntIsRefused() {
		assertThrows(IllegalStateException.class, () -> RuntimeLookup.staticInt(Fixture.class, "NAME"));
		assertThrows(IllegalStateException.class, () -> RuntimeLookup.staticInt(Fixture.class, "BOXED"));
		assertThrows(IllegalStateException.class, () -> RuntimeLookup.staticInt(Fixture.class, "instanceField"));
		IllegalStateException hidden = assertThrows(IllegalStateException.class, () -> RuntimeLookup.staticInt(Fixture.class, "HIDDEN"));
		assertTrue(hidden.getMessage().contains("HIDDEN"), hidden.getMessage());
	}

	@Test
	void readsAPublicStaticValueOfAnyType() {
		assertSame(Fixture.NAME, RuntimeLookup.staticValue(Fixture.class, "NAME"));
		assertEquals(String.class, RuntimeLookup.staticField(Fixture.class, "NAME").getType());
		IllegalStateException e = assertThrows(IllegalStateException.class, () -> RuntimeLookup.staticValue(Fixture.class, "instanceField"));
		assertTrue(e.getMessage().contains("instanceField"), e.getMessage());
	}

	@Test
	void findsAStaticMethodOrSaysWhichOneIsMissing() throws Throwable {
		MethodHandle twice = RuntimeLookup.staticMethod(Fixture.class, "twice", MethodType.methodType(int.class, int.class));
		assertEquals(42, (int) twice.invokeExact(21));
		IllegalStateException e = assertThrows(IllegalStateException.class,
				() -> RuntimeLookup.staticMethod(Fixture.class, "thrice", MethodType.methodType(int.class, int.class)));
		assertTrue(e.getMessage().contains("thrice"), e.getMessage());
		assertTrue(e.getMessage().contains(Fixture.class.getName()), e.getMessage());
	}

	@Test
	void findsAnInstanceMethodOrSaysWhichOneIsMissing() throws Throwable {
		MethodHandle length = RuntimeLookup.virtualMethod(String.class, "length", MethodType.methodType(int.class));
		assertEquals(5, (int) length.invokeExact("hello"));
		IllegalStateException e = assertThrows(IllegalStateException.class,
				() -> RuntimeLookup.virtualMethod(String.class, "lengthy", MethodType.methodType(int.class)));
		assertTrue(e.getMessage().contains("lengthy"), e.getMessage());
	}
}
