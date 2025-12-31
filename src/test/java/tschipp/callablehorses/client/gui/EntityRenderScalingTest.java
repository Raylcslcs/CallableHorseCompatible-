package tschipp.callablehorses.client.gui;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class EntityRenderScalingTest
{
	@Test
	public void computesReasonableScaleForCommonEntities() throws Exception
	{
		Class<?> scaling = Class.forName("tschipp.callablehorses.client.gui.EntityRenderScaling");
		Method computeScale = scaling.getMethod("computeScale", float.class, float.class, int.class);

		int horseLike = (int) computeScale.invoke(null, 1.4f, 1.6f, 69);
		assertTrue(horseLike >= 10 && horseLike <= 40, "Horse-like entities should render at a readable scale in a 69x69 box.");

		int llamaLike = (int) computeScale.invoke(null, 0.9f, 1.87f, 69);
		assertTrue(llamaLike >= 10 && llamaLike <= 40, "Llama-like entities should render at a readable scale in a 69x69 box.");

		int huge = (int) computeScale.invoke(null, 3.0f, 5.0f, 69);
		assertTrue(huge < horseLike, "Huge entities should be scaled down compared to horse-like entities.");

		int tiny = (int) computeScale.invoke(null, 0.4f, 0.4f, 69);
		assertTrue(tiny <= 40, "Tiny entities should be capped to avoid overflowing the box.");
	}
}

