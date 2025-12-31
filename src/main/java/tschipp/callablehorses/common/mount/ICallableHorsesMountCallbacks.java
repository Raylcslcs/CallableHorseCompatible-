package tschipp.callablehorses.common.mount;

import net.minecraft.world.entity.player.Player;

public interface ICallableHorsesMountCallbacks
{
	default void callablehorses$afterLoadFromNBT()
	{
	}

	default void callablehorses$afterCallToPlayer(Player player)
	{
	}
}

