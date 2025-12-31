package tschipp.callablehorses;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public interface IProxy
{
	public Level getWorld();
	
	public Player getPlayer();
	
	public void displayStatViewer();

	default void displayStatViewer(CompoundTag ownerNbt)
	{
		displayStatViewer();
	}
}
