package tschipp.callablehorses.common.capabilities.horseowner;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;

public interface IHorseOwner {

	default LivingEntity createHorseEntity(Level world) {
		return createHorseEntity(world, true);
	}

	public LivingEntity createHorseEntity(Level world, boolean incrementHorseNum);
	
	public CompoundTag getHorseNBT();
	
	public void setHorseNBT(CompoundTag nbt);

	public void setHorse(LivingEntity horse, Player player);
	
	public void clearHorse();
	
	public int getHorseNum();
	
	public void setHorseNum(int num);
	
	public String getStorageUUID();
	
	public void setStorageUUID(String id);

	public void setLastSeenPosition(Vec3 pos);
	
	public Vec3 getLastSeenPosition();
	
	public ResourceKey<Level> getLastSeenDim();
	
	public void setLastSeenDim(ResourceKey<Level> dim);

}
