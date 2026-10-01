package dev.myriad.essentials.modules.player;

import dev.myriad.api.event.Subscribe;
import dev.myriad.api.event.events.Render3DEvent;
import dev.myriad.api.event.events.TickEvent;
import dev.myriad.api.module.Categories;
import dev.myriad.api.module.Module;
import dev.myriad.api.module.Modules;
import dev.myriad.api.render.Renderer3D;
import dev.myriad.api.setting.BoolSetting;
import dev.myriad.api.setting.ColorSetting;
import dev.myriad.api.setting.DoubleSetting;
import dev.myriad.api.setting.SettingColor;
import dev.myriad.essentials.mixin.MinecraftClientAccessor;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

/**
 * Place blocks in midair: hold right-click while looking at an empty (or replaceable) cell within range. A preview
 * box shows the target while a block is in your main hand.
 */
public class AirPlace extends Module {
	private static final int PLACE_COOLDOWN_TICKS = 4;

	private final DoubleSetting distance = sgGeneral.doubleSetting("Distance").description("How far away you can air-place.").defaultValue(4.0).range(1, 6).decimals(1).build();
	private final BoolSetting render = sgGeneral.bool("Render").description("Show the block you'd place.").defaultValue(true).build();
	private final ColorSetting fillColor = sgGeneral.color("Fill").defaultValue(SettingColor.role(SettingColor.Mode.ACCENT, 35)).visible(render::get).build();
	private final ColorSetting lineColor = sgGeneral.color("Line").defaultValue(SettingColor.role(SettingColor.Mode.ACCENT)).visible(render::get).build();
	private final DoubleSetting lineWidth = sgGeneral.doubleSetting("Line Width").defaultValue(1.0).range(1, 5).decimals(1).visible(render::get).build();

	private int airPlaceTicks;
	private boolean cancelVanillaUse;
	private BlockPos renderPos;

	public AirPlace() {
		super(Categories.PLAYER, "Air Place", "Place blocks in midair.");
	}

	@Override
	protected void onDisable() {
		airPlaceTicks = 0;
		cancelVanillaUse = false;
		renderPos = null;
	}

	/** True right after an air-place, so vanilla's item use can't place a second block that tick (see the mixin). */
	public static boolean shouldCancelVanillaUse() {
		AirPlace m = Modules.active(AirPlace.class);
		return m != null && m.cancelVanillaUse;
	}

	@Subscribe
	private void onTick(TickEvent.Pre e) {
		cancelVanillaUse = false;
		if (!inGame() || mc.interactionManager == null || mc.player.isSpectator()) {
			renderPos = null;
			return;
		}
		if (airPlaceTicks > 0) airPlaceTicks--;

		BlockHitResult hit = findAirPlaceHit();
		renderPos = hit == null ? null : BlockPos.ofFloored(hit.getPos());

		if (!mc.options.useKey.isPressed() || hit == null || mc.player.isUsingItem()) return;
		MinecraftClientAccessor acc = (MinecraftClientAccessor) mc;
		if (acc.myriad$getItemUseCooldown() != 0 || airPlaceTicks != 0) return;

		acc.myriad$setItemUseCooldown(PLACE_COOLDOWN_TICKS);
		airPlaceTicks = PLACE_COOLDOWN_TICKS;
		cancelVanillaUse = true;

		mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(hit.getPos(), hit.getSide(), renderPos, false));
		mc.player.swingHand(Hand.MAIN_HAND);
	}

	@Subscribe
	private void onRender(Render3DEvent e) {
		if (!render.get() || renderPos == null || !inGame()) return;
		Renderer3D.lineWidth(lineWidth.getFloat());
		Renderer3D.box(new Box(renderPos), fillColor.argb(), lineColor.argb(), Renderer3D.ShapeMode.BOTH, false);
	}

	private BlockHitResult findAirPlaceHit() {
		// Looking at a real block: that's normal placement, not air-placing.
		if (mc.crosshairTarget instanceof BlockHitResult solid && !mc.world.getBlockState(solid.getBlockPos()).isReplaceable()) return null;
		ItemStack stack = mc.player.getMainHandStack();
		if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem)) return null;

		HitResult result = mc.player.raycast(distance.get(), 1.0f, true);
		if (!(result instanceof BlockHitResult blockHit)) return null;

		BlockPos pos = BlockPos.ofFloored(blockHit.getPos());
		if (!mc.world.getBlockState(pos).isReplaceable()) return null;
		if (!mc.world.getOtherEntities(mc.player, new Box(pos), entity -> entity.isAlive() && entity.canHit()).isEmpty()) return null;
		return blockHit;
	}
}
