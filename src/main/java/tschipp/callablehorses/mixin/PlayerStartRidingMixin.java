package tschipp.callablehorses.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tschipp.callablehorses.common.helper.HorseHelper;
import tschipp.callablehorses.common.capabilities.storedhorse.IStoredHorse;
import tschipp.callablehorses.common.capabilities.horseowner.IHorseOwner;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Mixin(Entity.class)
public abstract class PlayerStartRidingMixin
{
	@Unique
	private static final double CALLABLEHORSES_MAX_RIDE_DISTANCE = 10.0D;

	@Unique
	private static final Set<UUID> callablehorses$pendingRide = new HashSet<>();

	@Inject(method = "startRiding(Lnet/minecraft/world/entity/Entity;Z)Z", at = @At("HEAD"), cancellable = true)
	private void callablehorses$teleportMountBeforeRiding(Entity vehicle, boolean force, CallbackInfoReturnable<Boolean> cir)
	{
		Entity self = (Entity) (Object) this;

		if (!(self instanceof ServerPlayer player))
			return;

		if (callablehorses$pendingRide.contains(player.getUUID()))
		{
			callablehorses$pendingRide.remove(player.getUUID());
			return;
		}

		if (!(vehicle instanceof LivingEntity livingVehicle))
			return;

		IStoredHorse storedHorse = HorseHelper.getHorseCap(vehicle);
		if (storedHorse == null || !storedHorse.isOwned())
			return;

		IHorseOwner owner = HorseHelper.getOwnerCap(player);
		if (owner == null || !storedHorse.getStorageUUID().equals(owner.getStorageUUID()))
			return;

		double distance = player.position().distanceTo(vehicle.position());
		if (distance > CALLABLEHORSES_MAX_RIDE_DISTANCE)
		{
			Vec3 targetPos = player.position();

			vehicle.moveTo(targetPos.x, targetPos.y, targetPos.z, vehicle.getYRot(), vehicle.getXRot());
			vehicle.setDeltaMovement(Vec3.ZERO);
			vehicle.hurtMarked = true;
			livingVehicle.refreshDimensions();

			cir.setReturnValue(false);

			callablehorses$pendingRide.add(player.getUUID());
			player.getServer().execute(() -> {
				player.startRiding(vehicle, true);
			});
		}
	}
}
