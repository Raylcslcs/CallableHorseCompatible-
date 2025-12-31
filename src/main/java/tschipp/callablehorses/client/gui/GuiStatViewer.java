package tschipp.callablehorses.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.WoolCarpetBlock;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import tschipp.callablehorses.CallableHorses;
import tschipp.callablehorses.common.capabilities.horseowner.HorseOwner;
import tschipp.callablehorses.common.capabilities.horseowner.IHorseOwner;
import tschipp.callablehorses.common.helper.HorseHelper;

import javax.annotation.Nullable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class GuiStatViewer extends Screen
{

	private int xSize = 176;
	private int ySize = 138;
	private static final int MODEL_BOX_X = 9;
	private static final int MODEL_BOX_Y = 9;
	private static final int MODEL_BOX_SIZE = 69;
	private static final int MODEL_BOX_FOOT_OFFSET = 4;

	private static final ResourceLocation TEXTURE = new ResourceLocation(CallableHorses.MODID, "textures/gui/horse_stat_viewer.png");
	private IHorseOwner owner;
	private LivingEntity horse;
	private Component errorText;

	private Component speedText;
	private Component jumpHeightText;
	private Component healthText;
	private Vec3 lastPos;
	private ResourceKey<Level> lastDim;

	@Nullable
	private static final Method LLAMA_SET_SWAG = findLlamaSetSwagMethod();

	private Minecraft mc = Minecraft.getInstance();

	public GuiStatViewer(Player player)
	{
		super(Component.translatable("callablehorses.stats.title"));
		this.owner = HorseHelper.getOwnerCap(player);
		init(player);
	}

	public GuiStatViewer(Player player, @Nullable CompoundTag ownerNbt)
	{
		super(Component.translatable("callablehorses.stats.title"));
		if (ownerNbt != null)
		{
			HorseOwner snapshot = new HorseOwner();
			try
			{
				HorseOwner.readNBT(snapshot, ownerNbt);
				this.owner = snapshot;
			}
			catch (Exception e)
			{
				CallableHorses.LOGGER.error("Failed to read owner NBT for stats viewer", e);
				this.owner = null;
			}
		}
		else
		{
			this.owner = HorseHelper.getOwnerCap(player);
		}

		init(player);
	}

	private void init(Player player)
	{
		this.speedText = Component.translatable("callablehorses.stats.na");
		this.jumpHeightText = Component.translatable("callablehorses.stats.na");
		this.healthText = Component.translatable("callablehorses.stats.na");
		this.lastPos = Vec3.ZERO;
		this.lastDim = player.level().dimension();

		if (owner == null)
		{
			this.errorText = Component.translatable("callablehorses.error.nocap_owner");
			this.horse = null;
			return;
		}

		if (owner.getHorseNBT() == null || owner.getHorseNBT().isEmpty())
		{
			this.errorText = Component.translatable("callablehorses.error.nohorse");
			this.horse = null;
			return;
		}

		this.horse = owner.createHorseEntity(player.level(), false);
		if (horse == null)
		{
			this.errorText = Component.literal("Failed to create mount preview from stored NBT.");
			return;
		}

		horse.getAttributes().load(owner.getHorseNBT().getList("Attributes", 10)); // Read
					
		// attributes		
		this.horse.load(owner.getHorseNBT());

		LazyOptional<IItemHandler> cap = horse.getCapability(ForgeCapabilities.ITEM_HANDLER, null);
		cap.ifPresent(horseInventory -> {
			if (horse instanceof AbstractHorse abstractHorse)
			{
				if (horseInventory.getStackInSlot(0).isEmpty() && abstractHorse.isSaddleable())
					abstractHorse.equipSaddle(null);// Set saddled

				if (abstractHorse instanceof Llama llama)
				{
					ItemStack stack = horseInventory.getStackInSlot(1);
					if (!stack.isEmpty() && abstractHorse.isArmor(stack))
					{
						Item item = stack.getItem();
						if (item instanceof BlockItem blockItem && blockItem.getBlock() instanceof WoolCarpetBlock carpet)
							setLlamaSwag(llama, carpet.getColor());
						else
							setLlamaSwag(llama, null);
					}
					else
					{
						setLlamaSwag(llama, null);
					}
				}

			}

		});

		float health = (float) (Math.floor(horse.getHealth()));
		float maxHealth = (float) (Math.floor(horse.getMaxHealth() * 10) / 10);
		this.healthText = Component.literal(health + "/" + maxHealth);

		if (horse.getAttribute(Attributes.MOVEMENT_SPEED) != null)
			this.speedText = Component.literal((float) (Math.floor(horse.getAttribute(Attributes.MOVEMENT_SPEED).getValue() * 100) / 10) + "");
		else
			this.speedText = Component.translatable("callablehorses.stats.na");

		if (horse instanceof AbstractHorse abstractHorse)
			this.jumpHeightText = Component.literal((float) (Math.floor(abstractHorse.getCustomJump() * 100) / 10) + "");
		else
			this.jumpHeightText = Component.translatable("callablehorses.stats.na");

		this.lastPos = owner.getLastSeenPosition();
		this.lastDim = owner.getLastSeenDim();
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks)
	{
		this.renderBackground(graphics);

		// GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
		int i = (this.width - this.xSize) / 2;
		int j = (this.height - this.ySize) / 2;
		graphics.blit(TEXTURE, i, j, 0, 0, this.xSize, this.ySize, 256, 256);

		super.render(graphics, mouseX, mouseY, partialTicks);

		if (this.horse == null)
		{
			if (this.errorText != null)
				graphics.drawString(mc.font, this.errorText, i + 84, j + 30, DyeColor.LIGHT_GRAY.getTextColor());
			return;
		}

		int boxLeft = i + MODEL_BOX_X;
		int boxTop = j + MODEL_BOX_Y;
		int boxRight = boxLeft + MODEL_BOX_SIZE;
		int boxBottom = boxTop + MODEL_BOX_SIZE;

		int modelCenterX = boxLeft + (MODEL_BOX_SIZE / 2);
		int modelFeetY = boxBottom - MODEL_BOX_FOOT_OFFSET;
		int scale = EntityRenderScaling.computeScale(this.horse.getBbWidth(), this.horse.getBbHeight(), MODEL_BOX_SIZE);

		graphics.enableScissor(boxLeft, boxTop, boxRight, boxBottom);
		try
		{
			InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, modelCenterX, modelFeetY, scale, (float) modelCenterX - mouseX, (float) (modelFeetY - 50) - mouseY, this.horse);
		}
		finally
		{
			graphics.disableScissor();
		}

		graphics.drawString(mc.font, this.horse.getName(), i + 84, j + 10, DyeColor.WHITE.getTextColor());

		graphics.drawString(mc.font, Component.translatable("callablehorses.stats.health"), i + 84, j + 30, DyeColor.LIGHT_GRAY.getTextColor());
		graphics.drawString(mc.font, healthText, i + 120, j + 30, DyeColor.WHITE.getTextColor());

		graphics.drawString(mc.font, Component.translatable("callablehorses.stats.speed"), i + 84, j + 45, DyeColor.LIGHT_GRAY.getTextColor());
		graphics.drawString(mc.font, speedText, i + 120, j + 45, DyeColor.WHITE.getTextColor());

		graphics.drawString(mc.font, Component.translatable("callablehorses.stats.jump"), i + 84, j + 60, DyeColor.LIGHT_GRAY.getTextColor());
		graphics.drawString(mc.font, jumpHeightText, i + 148, j + 60, DyeColor.WHITE.getTextColor());

		graphics.drawString(mc.font, Component.translatable("callablehorses.stats.last_pos"), i + 8, j + 84, DyeColor.LIGHT_GRAY.getTextColor());
		graphics.drawString(mc.font, lastPos.equals(Vec3.ZERO) ? Component.translatable("callablehorses.stats.unknown") : Component.translatable("callablehorses.stats.pos", lastPos.x(), lastPos.y(), lastPos.z()), i + 8, j + 94, DyeColor.WHITE.getTextColor());

		graphics.drawString(mc.font, Component.translatable("callablehorses.stats.last_dim"), i + 8, j + 110, DyeColor.LIGHT_GRAY.getTextColor());
		graphics.drawString(mc.font, Component.literal(this.lastDim.location().toString()), i + 8, j + 120, DyeColor.WHITE.getTextColor());

	}
	@Override
	public boolean isPauseScreen()
	{
		return false;
	}
	
	@Override
	public boolean shouldCloseOnEsc()
	{
		return true;
	}
	
	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers)
	{		
		if (this.mc.options.keyInventory.isActiveAndMatches(InputConstants.getKey(keyCode, scanCode)))
		{
			this.onClose();
			return true;
		}
		
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	private static void setLlamaSwag(Llama llama, @Nullable DyeColor color)
	{
		if (LLAMA_SET_SWAG == null)
			return;

		try
		{
			LLAMA_SET_SWAG.invoke(llama, color);
		}
		catch (IllegalAccessException | InvocationTargetException e)
		{
			CallableHorses.LOGGER.error("Failed to apply llama carpet color for stats viewer", e);
		}
	}

	@Nullable
	private static Method findLlamaSetSwagMethod()
	{
		try
		{
			Method method = Llama.class.getDeclaredMethod("setSwag", DyeColor.class);
			method.setAccessible(true);
			return method;
		}
		catch (NoSuchMethodException ignored)
		{
		}

		for (Method method : Llama.class.getDeclaredMethods())
		{
			if (method.getReturnType() == void.class
					&& method.getParameterCount() == 1
					&& method.getParameterTypes()[0] == DyeColor.class)
			{
				method.setAccessible(true);
				return method;
			}
		}

		return null;
	}

}
