package tschipp.callablehorses.common;

import com.google.common.collect.ImmutableList;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.horse.AbstractChestedHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import tschipp.callablehorses.CallableHorses;
import tschipp.callablehorses.common.capabilities.horseowner.IHorseOwner;
import tschipp.callablehorses.common.capabilities.storedhorse.IStoredHorse;
import tschipp.callablehorses.common.helper.HorseHelper;
import tschipp.callablehorses.common.mount.ICallableHorsesMountCallbacks;
import tschipp.callablehorses.common.worlddata.StoredHorsesWorldData;
import tschipp.callablehorses.network.OwnerSyncShowStatsPacket;
import tschipp.callablehorses.network.PlayWhistlePacket;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static tschipp.callablehorses.common.config.Configs.SERVER;

public class HorseManager
{

	public static boolean callHorse(Player player)
	{
		if (player instanceof ServerPlayer serverPlayer)
		{
			return callHorseInternal(serverPlayer, false, false) != null;
		}

		return false;
	}

	public static boolean callHorseAndRide(ServerPlayer player)
	{
		return callHorseInternal(player, true, true) != null;
	}

	@Nullable
	private static LivingEntity callHorseInternal(ServerPlayer player, boolean autoRide, boolean ignoreLimits)
	{
		IHorseOwner horseOwner = HorseHelper.getOwnerCap(player);
		if (horseOwner == null)
		{
			player.displayClientMessage(Component.translatable("callablehorses.error.nocap_owner").withStyle(ChatFormatting.RED), true);
			return null;
		}

		if (horseOwner.getHorseNBT().isEmpty())
		{
			player.displayClientMessage(Component.translatable("callablehorses.error.nohorse").withStyle(ChatFormatting.RED), true);
			return null;
		}

		if (!ignoreLimits && !canCallHorse(player))
			return null;

		if (autoRide && player.isDeadOrDying())
			return null;

		if (autoRide && player.isPassenger())
			player.stopRiding();

		if (autoRide && player.isSleeping())
			player.stopSleeping();

		if (autoRide && player.isUsingItem())
			player.stopUsingItem();

		Random rand = new Random();
		player.level().playSound(player, player.blockPosition(), WhistleSounds.WHISTLE.get(), SoundSource.PLAYERS, 1f, (float) (1.4 + rand.nextGaussian() / 3));
		CallableHorses.network.send(PacketDistributor.PLAYER.with(() -> player), new PlayWhistlePacket());

		LivingEntity targetHorse = null;

		LivingEntity existing = findHorseWithStorageID(horseOwner.getStorageUUID(), player.level());
		if (existing != null)
		{
			IStoredHorse stored = HorseHelper.getHorseCap(existing);
			if (stored != null && stored.getStorageUUID().equals(horseOwner.getStorageUUID()))
			{
				if (existing.level().dimension().equals(player.level().dimension()))
				{
					existing.ejectPassengers();

					if (autoRide)
					{
						Vec3 callPos = SERVER.checkForSpace.get() ? findSafeCallPosition(player, existing) : player.position();
						if (callPos == null && ignoreLimits)
							callPos = player.position();
						if (callPos == null)
						{
							player.displayClientMessage(Component.translatable("callablehorses.error.nospace").withStyle(ChatFormatting.RED), true);
							return null;
						}
						existing.teleportTo(callPos.x, callPos.y, callPos.z);
						existing.setDeltaMovement(Vec3.ZERO);
						existing.refreshDimensions();
						if (existing instanceof ICallableHorsesMountCallbacks callbacks)
							callbacks.callablehorses$afterCallToPlayer(player);

						double distanceToPlayer = existing.position().distanceTo(player.position());
						if (distanceToPlayer > 16.0D)
						{
							CallableHorses.LOGGER.warn("Failed to summon mount {} to player {} (distance still {}). Recreating mount from NBT instead.", existing, player.getGameProfile().getName(), distanceToPlayer);
							HorseManager.saveHorse(existing);
							existing.discard();
						}
						else
						{
							targetHorse = existing;
						}
					}
					else if (existing.position().distanceTo(player.position()) <= SERVER.horseWalkRange.get())
					{
						// Horse walks //Follow range attribute
						if (existing instanceof Mob mob)
						{
							if (mob.getAttribute(Attributes.FOLLOW_RANGE) != null)
								mob.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(SERVER.horseWalkRange.get());
							mob.getNavigation().moveTo(player, SERVER.horseWalkSpeed.get());
						}
						else
						{
							Vec3 callPos = SERVER.checkForSpace.get() ? findSafeCallPosition(player, existing) : player.position();
							if (callPos == null)
							{
								player.displayClientMessage(Component.translatable("callablehorses.error.nospace").withStyle(ChatFormatting.RED), true);
								return null;
							}
							existing.setPos(callPos.x, callPos.y, callPos.z);
							existing.refreshDimensions();
							if (existing instanceof ICallableHorsesMountCallbacks callbacks)
								callbacks.callablehorses$afterCallToPlayer(player);
						}
						targetHorse = existing;
					}
					else
					{
						// TP-ing the horse
						Vec3 callPos = SERVER.checkForSpace.get() ? findSafeCallPosition(player, existing) : player.position();
						if (callPos == null)
						{
							player.displayClientMessage(Component.translatable("callablehorses.error.nospace").withStyle(ChatFormatting.RED), true);
							return null;
						}
						existing.setPos(callPos.x, callPos.y, callPos.z);
						existing.refreshDimensions();
						if (existing instanceof ICallableHorsesMountCallbacks callbacks)
							callbacks.callablehorses$afterCallToPlayer(player);
						if (existing instanceof Mob mob)
							mob.getNavigation().moveTo(player, SERVER.horseWalkSpeed.get());
						targetHorse = existing;
					}

					HorseHelper.setHorseLastSeen(player);
					HorseHelper.sendHorseUpdateInRange(existing);
				}
				else
				{
					// Removing any loaded horses in other dims when a new one is spawned
					HorseManager.saveHorse(existing);
					existing.setPos(existing.getX(), -200, existing.getZ());
					existing.discard();
				}
			}
		}

		if (targetHorse == null)
		{
			// Spawning a new horse with a new num
			LivingEntity newHorse = horseOwner.createHorseEntity(player.level());
			if (newHorse == null)
				return null;

			Vec3 callPos = SERVER.checkForSpace.get() ? findSafeCallPosition(player, newHorse) : player.position();
			if (callPos == null && ignoreLimits)
				callPos = player.position();
			if (callPos == null)
			{
				player.displayClientMessage(Component.translatable("callablehorses.error.nospace").withStyle(ChatFormatting.RED), true);
				return null;
			}

			newHorse.setPos(callPos.x, callPos.y, callPos.z);
			newHorse.refreshDimensions();
			player.level().addFreshEntity(newHorse);
			if (newHorse instanceof ICallableHorsesMountCallbacks callbacks)
				callbacks.callablehorses$afterCallToPlayer(player);

			IStoredHorse stored = HorseHelper.getHorseCap(newHorse);
			if (stored != null)
			{
				HorseHelper.setHorseNum((ServerLevel) newHorse.level(), stored.getStorageUUID(), stored.getHorseNum());
				HorseHelper.sendHorseUpdateInRange(newHorse);
			}

			HorseHelper.setHorseLastSeen(player);
			targetHorse = newHorse;
		}

		if (autoRide && targetHorse != null)
		{
			boolean mounted = forceRide(player, targetHorse);
			if (!mounted)
				player.displayClientMessage(Component.translatable("callablehorses.error.cannotride").withStyle(ChatFormatting.RED), true);
		}

		return targetHorse;
	}

	private static boolean forceRide(ServerPlayer player, Entity vehicle)
	{
		try
		{
			if (player.isPassenger())
				player.stopRiding();

			if (player.startRiding(vehicle, true))
				return true;
		}
		catch (Throwable t)
		{
			CallableHorses.LOGGER.error("Failed to startRiding() for {} -> {}", player.getGameProfile().getName(), vehicle, t);
		}

		try
		{
			return forceMountViaReflection(player, vehicle);
		}
		catch (Throwable t)
		{
			CallableHorses.LOGGER.error("Failed to force-mount via reflection for {} -> {}", player.getGameProfile().getName(), vehicle, t);
			return false;
		}
	}

	private static boolean forceMountViaReflection(ServerPlayer player, Entity vehicle) throws ReflectiveOperationException
	{
		// Mirrors Entity#startRiding but bypasses the vehicle's canBeRidden check.
		player.setPose(Pose.STANDING);

		var vehicleField = ObfuscationReflectionHelper.findField(Entity.class, "f_19824_");
		vehicleField.setAccessible(true);
		vehicleField.set(player, vehicle);

		var addPassenger = ObfuscationReflectionHelper.findMethod(Entity.class, "m_20348_", Entity.class);
		addPassenger.setAccessible(true);
		addPassenger.invoke(vehicle, player);
		return player.getVehicle() == vehicle;
	}

	public static void setHorse(Player player)
	{
		if (player != null)
		{
			Entity vehicle = player.getVehicle();
			if (vehicle == null)
			{
				player.displayClientMessage(Component.translatable("callablehorses.error.notriding").withStyle(ChatFormatting.RED), true);
				return;
			}

			// Prefer the root vehicle (seat entities are common in mods), but fall back to any living entity in the chain.
			Entity mount = vehicle;
			while (mount.getVehicle() != null)
			{
				mount = mount.getVehicle();
			}

			LivingEntity livingMount = null;
			if (mount instanceof LivingEntity mountLiving && !(mount instanceof Player))
			{
				livingMount = mountLiving;
			}
			else
			{
				Entity scan = vehicle;
				while (scan != null)
				{
					if (scan instanceof LivingEntity scanLiving && !(scan instanceof Player))
					{
						livingMount = scanLiving;
						break;
					}
					scan = scan.getVehicle();
				}
			}

			if (livingMount == null)
			{
				player.displayClientMessage(Component.translatable("callablehorses.error.notlivingmount").withStyle(ChatFormatting.RED), true);
				return;
			}

			if (!canSetHorse(player, livingMount))
				return;

			IStoredHorse storedHorse = HorseHelper.getHorseCap(livingMount);
			if (storedHorse == null)
			{
				player.displayClientMessage(Component.translatable("callablehorses.error.nocap").withStyle(ChatFormatting.RED), true);
				CallableHorses.LOGGER.error("Failed to bind mount for {}: missing stored_horse capability on {}", player.getGameProfile().getName(), livingMount);
				return;
			}

			IHorseOwner horseOwner = HorseHelper.getOwnerCap(player);
			if (horseOwner == null)
			{
				player.displayClientMessage(Component.translatable("callablehorses.error.nocap_owner").withStyle(ChatFormatting.RED), true);
				CallableHorses.LOGGER.error("Failed to bind mount for {}: missing horse_owner capability", player.getGameProfile().getName());
				return;
			}

			// Marking any old mounts as disbanded for this player
			String ownedID = horseOwner.getStorageUUID();
			if (!ownedID.isEmpty())
			{
				Entity ent = findHorseWithStorageID(ownedID, player.level());
				if (ent != null)
				{
					clearHorse(HorseHelper.getHorseCap(ent));
				}
				else
				{
					player.level().getServer().getAllLevels().forEach(serverworld -> {
						StoredHorsesWorldData data = HorseHelper.getWorldData(serverworld);
						data.disbandHorse(ownedID);
					});
				}
			}

			horseOwner.clearHorse();

			// Force-clear any previous ownership info on the mount to avoid "already owned" deadlocks.
			if (storedHorse.isOwned())
			{
				clearHorse(storedHorse);
			}

			// Setting the new mount
			horseOwner.setHorse(livingMount, player);
			HorseHelper.setHorseLastSeen(player);

			HorseHelper.setHorseNum((ServerLevel) livingMount.level(), storedHorse.getStorageUUID(), storedHorse.getHorseNum());
			player.displayClientMessage(Component.translatable("callablehorses.success"), true);
			HorseHelper.sendHorseUpdateInRange(livingMount);
		}
	}

	public static void showHorseStats(ServerPlayer player)
	{
		IHorseOwner owner = HorseHelper.getOwnerCap(player);
		if (owner == null)
		{
			player.displayClientMessage(Component.translatable("callablehorses.error.nocap_owner").withStyle(ChatFormatting.RED), true);
			CallableHorses.LOGGER.error("Failed to show stats for {}: missing horse_owner capability", player.getGameProfile().getName());
			return;
		}

		if (owner.getHorseNBT().isEmpty())
		{
			player.displayClientMessage(Component.translatable("callablehorses.error.nohorse").withStyle(ChatFormatting.RED), true);
			return;
		}

		Entity e = findHorseWithStorageID(owner.getStorageUUID(), player.level());
		if (e != null)
		{
			HorseManager.saveHorse(e);
		}

		CallableHorses.network.send(PacketDistributor.PLAYER.with(() -> player), new OwnerSyncShowStatsPacket(owner));
	}

	public static void clearHorse(IStoredHorse horse)
	{
		horse.setOwned(false);
		horse.setHorseNum(0);
		horse.setOwnerUUID("");
		horse.setStorageUUID("");
	}

	@Nullable
	public static LivingEntity findHorseWithStorageID(String id, Level world)
	{
		MinecraftServer server = world.getServer();
		List<Entity> entities = new ArrayList<Entity>();

		for (ServerLevel w : server.getAllLevels())
			entities.addAll(ImmutableList.copyOf(w.getAllEntities()));

		for (Entity e : entities)
		{
			if (e instanceof LivingEntity && !(e instanceof Player))
			{
				IStoredHorse horse = HorseHelper.getHorseCap(e);
				if (horse != null && horse.getStorageUUID().equals(id))
					return (LivingEntity) e;

			}
		}

		return null;
	}

	// Clear armor, saddle, and any chest items
	public static void prepDeadHorseForRespawning(Entity e)
	{
		LazyOptional<IItemHandler> cap = e.getCapability(ForgeCapabilities.ITEM_HANDLER, null);
		cap.ifPresent(itemHandler -> {
			for (int i = 0; i < itemHandler.getSlots(); i++)
			{
				itemHandler.extractItem(i, 64, false);
			}
		});

		if (e instanceof AbstractChestedHorse)
		{
			((AbstractChestedHorse) e).setChest(false);
		}

		e.clearFire();
		((LivingEntity) e).setHealth(((LivingEntity) e).getMaxHealth());

	}

	@SuppressWarnings("deprecation")
	public static boolean canCallHorse(Player player)
	{
		if (player.getVehicle() != null)
		{
			player.displayClientMessage(Component.translatable("callablehorses.error.riding").withStyle(ChatFormatting.RED), true);
			return false;
		}

		if (SERVER.checkForSpace.get())
		{
			IHorseOwner owner = HorseHelper.getOwnerCap(player);
			LivingEntity previewHorse = owner != null ? owner.createHorseEntity(player.level(), false) : null;
			if (previewHorse == null || findSafeCallPosition(player, previewHorse) == null)
			{
				player.displayClientMessage(Component.translatable("callablehorses.error.nospace").withStyle(ChatFormatting.RED), true);
				return false;
			}
		}

		if (!SERVER.callableInEveryDimension.get())
		{
			List<? extends String> allowedDims = SERVER.callableDimsWhitelist.get();
			ResourceKey<Level> playerDim = player.level().dimension();

			for (int i = 0; i < allowedDims.size(); i++)
			{
				if (allowedDims.get(i).equals(playerDim.location().toString()))
					return true;
			}
			player.displayClientMessage(Component.translatable("callablehorses.error.dim").withStyle(ChatFormatting.RED), true);
			return false;
		}

		int maxDistance = SERVER.maxCallingDistance.get();
		if (maxDistance != -1)
		{
			IHorseOwner owner = HorseHelper.getOwnerCap(player);
			Vec3 lastSeenPos = owner.getLastSeenPosition();
			ResourceKey<Level> lastSeenDim = owner.getLastSeenDim();

			if (lastSeenPos.equals(Vec3.ZERO))
				return true;

			MinecraftServer server = player.level().getServer();

			Entity livingHorse = findHorseWithStorageID(owner.getStorageUUID(), player.level());
			if (livingHorse != null)
			{
				lastSeenPos = livingHorse.position();
				lastSeenDim = livingHorse.level().dimension(); // Dimension
																	// registry
																	// key
			}

			double movementFactorHorse = server.getLevel(lastSeenDim).dimensionType().coordinateScale(); // getDimensionType,
																										// getMovementFactor
			double movementFactorOwner = player.level().dimensionType().coordinateScale();

			double movementFactorTotal = movementFactorHorse > movementFactorOwner ? movementFactorHorse / movementFactorOwner : movementFactorOwner / movementFactorHorse;

			double distance = lastSeenPos.distanceTo(player.position()) / movementFactorTotal;
			if (distance <= maxDistance)
				return true;

			player.displayClientMessage(Component.translatable("callablehorses.error.range").withStyle(ChatFormatting.RED), true);
			return false;
		}

		return true;
	}

	public static boolean canSetHorse(Player player, Entity entity)
	{
		return true;
	}

	public static void saveHorse(Entity e)
	{
		if (e instanceof LivingEntity livingEntity && !(e instanceof Player))
		{
			if (livingEntity.isDeadOrDying())
				return;

			Level world = e.level();
			IStoredHorse horse = HorseHelper.getHorseCap(e);
			if (horse != null && horse.isOwned())
			{
				String ownerid = horse.getOwnerUUID();
				Player owner = HorseHelper.getPlayerFromUUID(ownerid, world);

				if (owner != null)
				{
					// Owner is online
					IHorseOwner horseOwner = HorseHelper.getOwnerCap(owner);
					if (horseOwner != null)
					{
						CompoundTag nbt = HorseHelper.sanitizeEntityNBT(e.serializeNBT());
						horseOwner.setHorseNBT(nbt);
						horseOwner.setLastSeenDim(e.level().dimension());
						horseOwner.setLastSeenPosition(e.position());
					}
					else
					{
						world.getServer().getAllLevels().forEach(serverworld -> {
							StoredHorsesWorldData data = HorseHelper.getWorldData(serverworld);
							data.addOfflineSavedHorse(horse.getStorageUUID(), HorseHelper.sanitizeEntityNBT(e.serializeNBT()));
						});
					}
				}
				else
				{
					StoredHorsesWorldData data = HorseHelper.getWorldData((ServerLevel) world);
					data.addOfflineSavedHorse(horse.getStorageUUID(), HorseHelper.sanitizeEntityNBT(e.serializeNBT()));
				}
			}
		}
	}

	private static boolean isAreaProtected(Player player, @Nullable Entity fakeHorse)
	{
		IHorseOwner owner = HorseHelper.getOwnerCap(player);
		if (fakeHorse == null && owner != null)
			fakeHorse = owner.createHorseEntity(player.level(), false);
		if (fakeHorse == null)
			return false;
		fakeHorse.setPos(player.getX(), player.getY(), player.getZ());
		PlayerInteractEvent.EntityInteract interactEvent = new EntityInteract(player, InteractionHand.MAIN_HAND, fakeHorse);
		AttackEntityEvent attackEvent = new AttackEntityEvent(player, fakeHorse);

		MinecraftForge.EVENT_BUS.post(interactEvent);
		MinecraftForge.EVENT_BUS.post(attackEvent);

		return interactEvent.isCanceled() || attackEvent.isCanceled();
	}

	@Nullable
	private static Vec3 findSafeCallPosition(Player player, LivingEntity horse)
	{
		Level world = player.level();
		BlockPos basePos = player.blockPosition();

		int horizontalRadius = (int) Math.ceil(horse.getBbWidth() + 1.0D);
		horizontalRadius = Math.min(32, Math.max(1, horizontalRadius));

		int[] yOffsets = new int[] {0, 1, -1, 2, -2, 3, -3, 4, -4};

		for (int r = 0; r <= horizontalRadius; r++)
		{
			for (int dx = -r; dx <= r; dx++)
			{
				for (int dz = -r; dz <= r; dz++)
				{
					if (Math.abs(dx) != r && Math.abs(dz) != r)
						continue;

					for (int dy : yOffsets)
					{
						double x = basePos.getX() + 0.5D + dx;
						double y = basePos.getY() + dy;
						double z = basePos.getZ() + 0.5D + dz;

						if (canPlaceEntityAt(world, horse, x, y, z))
							return new Vec3(x, y, z);
					}
				}
			}
		}

		return null;
	}

	private static boolean canPlaceEntityAt(Level world, LivingEntity entity, double x, double y, double z)
	{
		AABB targetBox = entity.getBoundingBox().move(x - entity.getX(), y - entity.getY(), z - entity.getZ());
		return world.noCollision(entity, targetBox);
	}

}
