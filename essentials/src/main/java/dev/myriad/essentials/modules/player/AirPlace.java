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
import dev.myriad.api.util.Interactions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

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
		if (!inGame() || mc.gameMode == null || mc.player.isSpectator()) {
			renderPos = null;
			return;
		}
		if (airPlaceTicks > 0) airPlaceTicks--;

		BlockHitResult hit = findAirPlaceHit();
		renderPos = hit == null ? null : BlockPos.containing(hit.getLocation());

		if (!mc.options.keyUse.isDown() || hit == null || mc.player.isUsingItem()) return;
		if (Interactions.itemUseCooldown() != 0 || airPlaceTicks != 0) return;

		Interactions.setItemUseCooldown(PLACE_COOLDOWN_TICKS);
		airPlaceTicks = PLACE_COOLDOWN_TICKS;
		cancelVanillaUse = true;

		mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(hit.getLocation(), hit.getDirection(), renderPos, false));
		mc.player.swing(InteractionHand.MAIN_HAND);
	}

	@Subscribe
	private void onRender(Render3DEvent e) {
		if (!render.get() || renderPos == null || !inGame()) return;
		Renderer3D.lineWidth(lineWidth.getFloat());
		Renderer3D.box(new AABB(renderPos), fillColor.argb(), lineColor.argb(), Renderer3D.ShapeMode.BOTH, false);
	}

	private BlockHitResult findAirPlaceHit() {
		// Looking at a real block: that's normal placement, not air-placing.
		if (mc.hitResult instanceof BlockHitResult solid && !mc.level.getBlockState(solid.getBlockPos()).canBeReplaced()) return null;
		ItemStack stack = mc.player.getMainHandItem();
		if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem)) return null;

		HitResult result = mc.player.pick(distance.get(), 1.0f, true);
		if (!(result instanceof BlockHitResult blockHit)) return null;

		BlockPos pos = BlockPos.containing(blockHit.getLocation());
		if (!mc.level.getBlockState(pos).canBeReplaced()) return null;
		if (!mc.level.getEntities(mc.player, new AABB(pos), entity -> entity.isAlive() && entity.isPickable()).isEmpty()) return null;
		return blockHit;
	}
}
