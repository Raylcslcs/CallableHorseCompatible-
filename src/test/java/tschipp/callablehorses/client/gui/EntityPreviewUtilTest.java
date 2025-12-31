package tschipp.callablehorses.client.gui;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class EntityPreviewUtilTest
{
	@Test
	public void computeScaleFitsEntityInsideBox() throws Exception
	{
		Class<?> util;
		try
		{
			util = Class.forName("tschipp.callablehorses.client.gui.EntityPreviewUtil");
		}
		catch (ClassNotFoundException e)
		{
			fail("EntityPreviewUtil is missing; stats viewer cannot auto-scale models.", e);
			return;
		}

		Method method = util.getDeclaredMethod("computeScale", double.class, double.class, int.class, int.class, int.class, int.class);
		int boxSize = 69;
		int padding = 4;
		int available = boxSize - (padding * 2);

		int scale = (int) method.invoke(null, 14.0, 7.0, boxSize, padding, 1, 10_000);

		assertTrue(14.0 * scale <= available + 0.0001, "width must fit");
		assertTrue(7.0 * scale <= available + 0.0001, "height must fit");
	}

	@Test
	public void computeScaleMatchesExpectedForCommonSizes() throws Exception
	{
		Class<?> util;
		try
		{
			util = Class.forName("tschipp.callablehorses.client.gui.EntityPreviewUtil");
		}
		catch (ClassNotFoundException e)
		{
			fail("EntityPreviewUtil is missing; stats viewer cannot auto-scale models.", e);
			return;
		}

		Method method = util.getDeclaredMethod("computeScale", double.class, double.class, int.class, int.class, int.class, int.class);

		int scale = (int) method.invoke(null, 1.4, 1.6, 69, 4, 1, 10_000);
		assertEquals(38, scale);
	}
}

