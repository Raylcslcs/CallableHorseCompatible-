package tschipp.callablehorses.common.loot;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HorseDropModifierJsonTest
{
	private static final Pattern ENTITY_THIS = Pattern.compile("\"entity\"\\s*:\\s*\"this\"");
	private static final Pattern ENTITY_THIS_UPPER = Pattern.compile("\"entity\"\\s*:\\s*\"THIS\"");

	@Test
	public void horseDropLootModifierUsesLowercaseThisEntityTarget() throws IOException
	{
		String json = readResource("data/callablehorses/loot_modifiers/horse_drop.json");
		assertNotNull(json);
		assertFalse(ENTITY_THIS_UPPER.matcher(json).find(), "horse_drop.json must not use \"entity\": \"THIS\" (invalid in 1.20.1)");
		assertTrue(ENTITY_THIS.matcher(json).find(), "horse_drop.json must use \"entity\": \"this\"");
	}

	private static String readResource(String path) throws IOException
	{
		try (InputStream input = HorseDropModifierJsonTest.class.getClassLoader().getResourceAsStream(path))
		{
			if (input == null)
				return null;
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}

