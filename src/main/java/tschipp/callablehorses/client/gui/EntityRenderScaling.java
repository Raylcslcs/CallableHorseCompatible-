package tschipp.callablehorses.client.gui;

import net.minecraft.util.Mth;

public final class EntityRenderScaling
{
	private static final int MIN_SCALE = 1;
	private static final int MAX_SCALE = 40;
	private static final int DEFAULT_PADDING = 4;

	private EntityRenderScaling()
	{
	}

	public static int computeScale(float entityBbWidth, float entityBbHeight, int boxSize)
	{
		float safeWidth = entityBbWidth > 0.0f ? entityBbWidth : 0.001f;
		float safeHeight = entityBbHeight > 0.0f ? entityBbHeight : 0.001f;

		int safeBoxSize = Math.max(1, boxSize);
		int available = Math.max(1, safeBoxSize - (DEFAULT_PADDING * 2));

		int scale = Mth.floor(Math.min(available / safeWidth, available / safeHeight));
		return Mth.clamp(scale, MIN_SCALE, MAX_SCALE);
	}
}
