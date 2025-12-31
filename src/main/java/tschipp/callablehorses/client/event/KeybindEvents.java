package tschipp.callablehorses.client.event;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent.PlayerTickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import tschipp.callablehorses.CallableHorses;
import tschipp.callablehorses.client.keybinds.KeybindManager;
import tschipp.callablehorses.common.config.Configs;
import tschipp.callablehorses.network.PressKeyPacket;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(value = Dist.CLIENT, modid = CallableHorses.MODID)
public class KeybindEvents
{
	private static final long DEBOUNCE_MS = 500;
	private static long lastCallHorsePressTime = 0;
	private static long lastCallHorseRidePressTime = 0;
	private static long lastSetHorsePressTime = 0;
	private static long lastShowStatsPressTime = 0;

	@SubscribeEvent
	@OnlyIn(Dist.CLIENT)
	public static void onPlayerTick(PlayerTickEvent event)
	{
		Player player = event.player;

		if (player != null && event.side == LogicalSide.CLIENT)
		{
			boolean callHorse = KeybindManager.callHorse.consumeClick();
			boolean callHorseRide = KeybindManager.callHorseRide.consumeClick();
			boolean setHorse = KeybindManager.setHorse.consumeClick();
			boolean showStats = Configs.SERVER.enableStatsViewer.get() && KeybindManager.showStats.consumeClick();

			if (callHorse)
			{
				long now = System.currentTimeMillis();
				if (now - lastCallHorsePressTime > DEBOUNCE_MS)
				{
					lastCallHorsePressTime = now;
					CallableHorses.network.sendToServer(new PressKeyPacket(0));
				}
			}

			if (callHorseRide)
			{
				long now = System.currentTimeMillis();
				if (now - lastCallHorseRidePressTime > DEBOUNCE_MS)
				{
					lastCallHorseRidePressTime = now;
					CallableHorses.network.sendToServer(new PressKeyPacket(3));
				}
			}

			if (setHorse)
			{
				long now = System.currentTimeMillis();
				if (now - lastSetHorsePressTime > DEBOUNCE_MS)
				{
					lastSetHorsePressTime = now;
					CallableHorses.network.sendToServer(new PressKeyPacket(1));
				}
			}
			
			if (showStats)
			{
				long now = System.currentTimeMillis();
				if (now - lastShowStatsPressTime > DEBOUNCE_MS)
				{
					lastShowStatsPressTime = now;
					CallableHorses.network.sendToServer(new PressKeyPacket(2));
				}
			}
		}
	}

}
