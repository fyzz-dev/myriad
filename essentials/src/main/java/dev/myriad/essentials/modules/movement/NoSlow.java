package dev.myriad.essentials.modules.movement;

import dev.myriad.api.Myriad;
import dev.myriad.api.event.Subscribe;
import dev.myriad.api.event.events.TickEvent;
import dev.myriad.api.module.Categories;
import dev.myriad.api.module.Module;
import dev.myriad.api.module.Modules;
import dev.myriad.api.setting.BoolSetting;
import dev.myriad.api.setting.DoubleSetting;
import dev.myriad.api.setting.EnumSetting;
import dev.myriad.api.setting.SettingGroup;
import dev.myriad.essentials.mixin.LivingEntityAccessor;
import net.minecraft.block.BlockState;
import net.minecraft.block.CobwebBlock;
import net.minecraft.block.SweetBerryBushBlock;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

/**
 * Stops things from slowing you down: using items (eating, blocking, drawing a bow), sneaking, crawling, climbing,
 * cobwebs and berry bushes. Inventory Move lets you walk (and turn with the arrow keys) while a screen is open.
 * Item modes: Normal removes the slowdown outright; Grim V2 re-uses the other hand each tick so Grim accepts it;
 * Pulse only removes it on alternate ticks near the end of an item use.
 */
public class NoSlow extends Module {

	public enum ItemsMode {
		OFF, NORMAL, GRIM_V2, PULSE
	}

	private final EnumSetting<ItemsMode> items = sgGeneral.enumSetting("Items", ItemsMode.NORMAL).description("How item-use slowdown is removed.").build();
	public final BoolSetting sneaking = sgGeneral.bool("Sneaking").description("Full speed while sneaking.").build();
	public final BoolSetting crawling = sgGeneral.bool("Crawling").description("Full speed while crawling.").build();
	private final BoolSetting climbing = sgGeneral.bool("Climbing").description("Don't get slowed on ladders and vines.").build();
	private final BoolSetting webs = sgGeneral.bool("Webs").description("Move through cobwebs and berry bushes.").build();
	private final DoubleSetting webSpeed = sgGeneral.doubleSetting("Web Speed").description("1 = no slowdown at all.").defaultValue(1).range(0.01, 1).decimals(2).visible(webs::get).build();
	private final BoolSetting grim = sgGeneral.bool("Grim").description("Strict-server tweaks: only skip climbing while moving, and break webs you're in.").build();

	private final SettingGroup sgInventory = settings.group("Inventory Move");
	private final BoolSetting inventoryMove = sgInventory.bool("Inventory Move").description("Walk while inventories and other screens are open.").defaultValue(true).build();
	private final BoolSetting arrowLook = sgInventory.bool("Arrow Look").description("Turn with the arrow keys while a screen is open.").visible(inventoryMove::get).build();
	private final BoolSetting jumpDelay = sgInventory.bool("No Jump Delay").description("Jump again as soon as you land.").build();

	public NoSlow() {
		super(Categories.MOVEMENT, "No Slow", "Stops actions from slowing you down.");
	}

	/** Whether item-use slowdown should be skipped this tick (read by this addon's player mixin). */
	public static boolean skipItemSlow() {
		NoSlow m = Modules.active(NoSlow.class);
		var p = mc.player;
		if (m == null || p == null || p.hasVehicle() || p.isSneaking() || !p.isUsingItem()) return false;
		return switch (m.items.get()) {
			case OFF -> false;
			case NORMAL, GRIM_V2 -> true;
			case PULSE -> !p.isCrawling() && p.getItemUseTimeLeft() < 5 || (p.getItemUseTime() > 1 && p.getItemUseTime() % 2 != 0);
		};
	}

	public static boolean skipSneakSlow(boolean sneaking, boolean crawling) {
		NoSlow m = Modules.active(NoSlow.class);
		return m != null && ((crawling && m.crawling.get()) || (sneaking && !crawling && m.sneaking.get()));
	}

	public static boolean skipClimb() {
		NoSlow m = Modules.active(NoSlow.class);
		if (m == null || !m.climbing.get()) return false;
		return !m.grim.get() || !mc.player.getVelocity().equals(Vec3d.ZERO);
	}

	/** The web/berry bush multiplier to use instead of vanilla's, or null to keep vanilla's. */
	public static Vec3d webMultiplier(BlockState state, Vec3d original) {
		NoSlow m = Modules.active(NoSlow.class);
		if (m == null || !m.webs.get()) return null;
		if (!(state.getBlock() instanceof CobwebBlock) && !(state.getBlock() instanceof SweetBerryBushBlock)) return null;
		double speed = m.webSpeed.get();
		// A zero multiplier means "no slowdown" to the movement code.
		return speed >= 1 ? Vec3d.ZERO : original.multiply(speed);
	}

	@Subscribe
	private void onTick(TickEvent.Pre e) {
		if (!inGame()) return;
		var p = mc.player;
		if (jumpDelay.get()) ((LivingEntityAccessor) p).myriad$setJumpingCooldown(0);
		if (items.get() == ItemsMode.GRIM_V2 && p.isUsingItem() && !p.isSneaking()) {
			if (p.getActiveHand() == Hand.OFF_HAND && reusable(p.getMainHandStack())) {
				mc.interactionManager.sendSequencedPacket(mc.world, id -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, id, p.getYaw(), p.getPitch()));
			} else if (reusable(p.getOffHandStack())) {
				mc.interactionManager.sendSequencedPacket(mc.world, id -> new PlayerInteractItemC2SPacket(Hand.OFF_HAND, id, p.getYaw(), p.getPitch()));
			}
		}
		if (inventoryMove.get() && screenAllowsMove()) {
			long handle = mc.getWindow().getHandle();
			for (KeyBinding k : new KeyBinding[]{mc.options.jumpKey, mc.options.forwardKey, mc.options.backKey, mc.options.rightKey, mc.options.leftKey, mc.options.sprintKey}) {
				InputUtil.Key bound = InputUtil.fromTranslationKey(k.getBoundKeyTranslationKey());
				k.setPressed(bound.getCategory() == InputUtil.Type.KEYSYM && InputUtil.isKeyPressed(handle, bound.getCode()));
			}
			if (arrowLook.get()) {
				float yaw = p.getYaw(), pitch = p.getPitch();
				if (InputUtil.isKeyPressed(handle, GLFW.GLFW_KEY_UP)) pitch -= 3;
				if (InputUtil.isKeyPressed(handle, GLFW.GLFW_KEY_DOWN)) pitch += 3;
				if (InputUtil.isKeyPressed(handle, GLFW.GLFW_KEY_LEFT)) yaw -= 3;
				if (InputUtil.isKeyPressed(handle, GLFW.GLFW_KEY_RIGHT)) yaw += 3;
				p.setYaw(yaw);
				p.setPitch(MathHelper.clamp(pitch, -90, 90));
			}
		}
		if (grim.get() && webs.get()) {
			for (BlockPos pos : BlockPos.iterate(BlockPos.ofFloored(p.getBoundingBox().expand(1).getMinPos()), BlockPos.ofFloored(p.getBoundingBox().expand(1).getMaxPos()))) {
				if (mc.world.getBlockState(pos).getBlock() instanceof CobwebBlock) {
					mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos.toImmutable(), Direction.DOWN));
				}
			}
		}
	}

	private static boolean reusable(ItemStack stack) {
		return !stack.contains(DataComponentTypes.FOOD) && !stack.isOf(Items.BOW) && !stack.isOf(Items.CROSSBOW) && !stack.isOf(Items.SHIELD);
	}

	/** Screens where moving is safe: not chat, signs, death, or Myriad's own menu (which uses the keyboard). */
	private boolean screenAllowsMove() {
		var s = mc.currentScreen;
		return s != null && !(s instanceof ChatScreen) && !(s instanceof AbstractSignEditScreen) && !(s instanceof DeathScreen) && !Myriad.ui().isOpen();
	}
}
