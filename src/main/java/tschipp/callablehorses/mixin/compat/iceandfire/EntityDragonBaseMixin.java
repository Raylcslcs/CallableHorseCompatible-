package tschipp.callablehorses.mixin.compat.iceandfire;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import tschipp.callablehorses.common.mount.ICallableHorsesMountCallbacks;

@Pseudo
@Mixin(targets = "com.github.alexthe666.iceandfire.entity.EntityDragonBase", remap = false)
public abstract class EntityDragonBaseMixin extends TamableAnimal implements ICallableHorsesMountCallbacks
{
	protected EntityDragonBaseMixin(EntityType<? extends TamableAnimal> type, Level world)
	{
		super(type, world);
	}

	@Shadow
	public abstract void updateParts();

	@Override
	public void callablehorses$afterLoadFromNBT()
	{
		this.refreshDimensions();
		this.updateParts();
	}

	@Override
	public void callablehorses$afterCallToPlayer(Player player)
	{
		this.refreshDimensions();
		this.updateParts();
	}
}
