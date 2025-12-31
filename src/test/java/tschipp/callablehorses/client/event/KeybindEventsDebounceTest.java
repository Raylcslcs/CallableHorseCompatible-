package tschipp.callablehorses.client.event;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class KeybindEventsDebounceTest
{
	@Test
	public void usesPerActionDebounceFields() throws Exception
	{
		Class<?> clazz = Class.forName("tschipp.callablehorses.client.event.KeybindEvents");
		Set<String> fieldNames = Arrays.stream(clazz.getDeclaredFields()).map(Field::getName).collect(Collectors.toSet());

		assertFalse(fieldNames.contains("lastPressTime"), "KeybindEvents should not use a shared debounce timer for all actions.");
		assertTrue(fieldNames.contains("lastCallHorsePressTime"), "KeybindEvents should debounce call-horse independently.");
		assertTrue(fieldNames.contains("lastSetHorsePressTime"), "KeybindEvents should debounce set-horse independently.");
		assertTrue(fieldNames.contains("lastShowStatsPressTime"), "KeybindEvents should debounce show-stats independently.");
	}
}

