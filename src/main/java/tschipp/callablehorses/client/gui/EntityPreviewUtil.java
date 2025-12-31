package tschipp.callablehorses.client.gui;

final class EntityPreviewUtil
{
	private EntityPreviewUtil()
	{
	}

	static int computeScale(double entityWidth, double entityHeight, int boxSize, int padding, int minScale, int maxScale)
	{
		double safeWidth = entityWidth > 0 ? entityWidth : 0.001;
		double safeHeight = entityHeight > 0 ? entityHeight : 0.001;

		int safeBoxSize = Math.max(1, boxSize);
		int safePadding = Math.max(0, padding);

		int available = Math.max(1, safeBoxSize - (safePadding * 2));

		int scale = (int) Math.floor(Math.min(available / safeWidth, available / safeHeight));
		if (scale < minScale)
			scale = minScale;
		if (scale > maxScale)
			scale = maxScale;

		return scale;
	}
}

