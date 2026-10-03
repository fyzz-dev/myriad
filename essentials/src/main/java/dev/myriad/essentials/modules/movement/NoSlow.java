package dev.myriad.essentials.modules.movement;

import com.mojang.blaze3d.platform.InputConstants;
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
import dev.myriad.api.util.Interactions;
import dev.myriad.api.util.Packets;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
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
		if (m == null || p == null || p.isPassenger() || p.isShiftKeyDown() || !p.isUsingItem()) return false;
		return switch (m.items.get()) {
			case OFF -> false;
			case NORMAL, GRIM_V2 -> true;
			case PULSE -> !p.isVisuallyCrawling() && p.getUseItemRemainingTicks() < 5 || (p.getTicksUsingItem() > 1 && p.getTicksUsingItem() % 2 != 0);
		};
	}

	public static boolean skipSneakSlow(boolean sneaking, boolean crawling) {
		NoSlow m = Modules.active(NoSlow.class);
		return m != null && ((crawling && m.crawling.get()) || (sneaking && !crawling && m.sneaking.get()));
	}

	public static boolean skipClimb() {
		NoSlow m = Modules.active(NoSlow.class);
		if (m == null || !m.climbing.get()) return false;
		return !m.grim.get() || !mc.player.getDeltaMovement().equals(Vec3.ZERO);
	}

	/** The web/berry bush multiplier to use instead of vanilla's, or null to keep vanilla's. */
	public static Vec3 webMultiplier(BlockState state, Vec3 original) {
		NoSlow m = Modules.active(NoSlow.class);
		if (m == null || !m.webs.get()) return null;
		if (!(state.getBlock() instanceof WebBlock) && !(state.getBlock() instanceof SweetBerryBushBlock)) return null;
		double speed = m.webSpeed.get();
		// A zero multiplier means "no slowdown" to the movement code.
		return speed >= 1 ? Vec3.ZERO : original.scale(speed);
	}

	@Subscribe
	private void onTick(TickEvent.Pre e) {
		if (!inGame()) return;
		var p = mc.player;
		if (jumpDelay.get()) Interactions.setJumpCooldown(0);
		if (items.get() == ItemsMode.GRIM_V2 && p.isUsingItem() && !p.isShiftKeyDown()) {
			if (p.getUsedItemHand() == InteractionHand.OFF_HAND && reusable(p.getMainHandItem())) {
				Packets.sendSequenced(id -> new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, id, p.getYRot(), p.getXRot()));
			} else if (reusable(p.getOffhandItem())) {
				Packets.sendSequenced(id -> new ServerboundUseItemPacket(InteractionHand.OFF_HAND, id, p.getYRot(), p.getXRot()));
			}
		}
		if (inventoryMove.get() && screenAllowsMove()) {
			var handle = mc.getWindow();
			for (KeyMapping k : new KeyMapping[]{mc.options.keyJump, mc.options.keyUp, mc.options.keyDown, mc.options.keyRight, mc.options.keyLeft, mc.options.keySprint}) {
				InputConstants.Key bound = InputConstants.getKey(k.saveString());
				k.setDown(bound.getType() == InputConstants.Type.KEYSYM && InputConstants.isKeyDown(handle, bound.getValue()));
			}
			if (arrowLook.get()) {
				float yaw = p.getYRot(), pitch = p.getXRot();
				if (InputConstants.isKeyDown(handle, GLFW.GLFW_KEY_UP)) pitch -= 3;
				if (InputConstants.isKeyDown(handle, GLFW.GLFW_KEY_DOWN)) pitch += 3;
				if (InputConstants.isKeyDown(handle, GLFW.GLFW_KEY_LEFT)) yaw -= 3;
				if (InputConstants.isKeyDown(handle, GLFW.GLFW_KEY_RIGHT)) yaw += 3;
				p.setYRot(yaw);
				p.setXRot(Mth.clamp(pitch, -90, 90));
			}
		}
		if (grim.get() && webs.get()) {
			for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(p.getBoundingBox().inflate(1).getMinPosition()), BlockPos.containing(p.getBoundingBox().inflate(1).getMaxPosition()))) {
				if (mc.level.getBlockState(pos).getBlock() instanceof WebBlock) {
					mc.getConnection().send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, pos.immutable(), Direction.DOWN));
				}
			}
		}
	}

	private static boolean reusable(ItemStack stack) {
		return !stack.has(DataComponents.FOOD) && !stack.is(Items.BOW) && !stack.is(Items.CROSSBOW) && !stack.is(Items.SHIELD);
	}

	/** Screens where moving is safe: not chat, signs, death, or Myriad's own menu (which uses the keyboard). */
	private boolean screenAllowsMove() {
		var s = mc.gui.screen();
		return s != null && !(s instanceof ChatScreen) && !(s instanceof AbstractSignEditScreen) && !(s instanceof DeathScreen) && !Myriad.ui().isOpen();
	}
}
