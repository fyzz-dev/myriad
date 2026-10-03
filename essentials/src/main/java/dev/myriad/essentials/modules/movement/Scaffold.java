package dev.myriad.essentials.modules.movement;

import dev.myriad.api.Myriad;
import dev.myriad.api.event.Subscribe;
import dev.myriad.api.event.events.InputEvent;
import dev.myriad.api.event.events.Render3DEvent;
import dev.myriad.api.event.events.TickEvent;
import dev.myriad.api.module.Categories;
import dev.myriad.api.module.Module;
import dev.myriad.api.render.Renderer3D;
import dev.myriad.api.service.Placement;
import dev.myriad.api.setting.BoolSetting;
import dev.myriad.api.setting.ColorSetting;
import dev.myriad.api.setting.DoubleSetting;
import dev.myriad.api.setting.EnumSetting;
import dev.myriad.api.setting.IntSetting;
import dev.myriad.api.setting.RegistryListSetting;
import dev.myriad.api.setting.SettingColor;
import dev.myriad.api.setting.SettingGroup;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Places blocks under (and a little ahead of) you while you walk. Bridge mode keeps a one-wide path; Platformer
 * fills a square around your feet. Uses Myriad's shared placement service.
 */
public class Scaffold extends Module {
	public enum Mode {
		BRIDGE, PLATFORMER
	}

	public enum Priority {
		NEAREST, FURTHEST
	}

	private final EnumSetting<Mode> mode = sgGeneral.enumSetting("Mode", Mode.BRIDGE).build();
	private final EnumSetting<Priority> priority = sgGeneral.enumSetting("Priority", Priority.NEAREST).description("Which candidate to place first.").build();
	private final DoubleSetting extend = sgGeneral.doubleSetting("Extend").description("How far ahead to place while moving (blocks).").defaultValue(0.8).range(0, 4).decimals(1).build();
	private final IntSetting blocksPerTick = sgGeneral.intSetting("Blocks Per Tick").defaultValue(1).range(1, 8).build();
	private final IntSetting delay = sgGeneral.intSetting("Delay").description("Ticks between placements.").defaultValue(0).range(0, 10).build();
	private final DoubleSetting range = sgGeneral.doubleSetting("Range").defaultValue(4.5).range(1, 6).decimals(1).build();
	private final BoolSetting rotate = sgGeneral.bool("Rotate").description("Face each block as it's placed.").defaultValue(true).build();
	private final BoolSetting safeWalk = sgGeneral.bool("Safe Walk").description("Sneak at edges so you never walk off.").defaultValue(true).build();
	private final BoolSetting onlyMoving = sgGeneral.bool("Only While Moving").description("Except when towering or descending.").build();
	private final BoolSetting tower = sgGeneral.bool("Tower").description("Place below you while holding jump.").defaultValue(true).build();
	private final BoolSetting descend = sgGeneral.bool("Descend").description("Place one lower while holding sneak.").defaultValue(true).build();
	private final RegistryListSetting<Block> blocks = sgGeneral.blocks("Blocks").description("Only use these blocks (empty = any full block).").build();

	private final SettingGroup sgPlatform = settings.group("Platformer");
	private final IntSetting radius = sgPlatform.intSetting("Radius").defaultValue(1).range(1, 5).visible(() -> mode.get() == Mode.PLATFORMER).build();
	private final DoubleSetting ahead = sgPlatform.doubleSetting("Ahead").description("How far the platform reaches in front while moving.").defaultValue(1.0).range(0, 4).decimals(1).visible(() -> mode.get() == Mode.PLATFORMER).build();
	private final BoolSetting corners = sgPlatform.bool("Corners").description("Square platform instead of a diamond.").defaultValue(true).visible(() -> mode.get() == Mode.PLATFORMER).build();

	private final SettingGroup sgRender = settings.group("Render");
	private final BoolSetting render = sgRender.bool("Render").defaultValue(true).build();
	private final BoolSetting renderQueue = sgRender.bool("Show Queue").description("Also outline upcoming targets.").defaultValue(true).visible(render::get).build();
	private final ColorSetting targetColor = sgRender.color("Target").defaultValue(SettingColor.role(SettingColor.Mode.ACCENT)).visible(render::get).build();
	private final ColorSetting queueColor = sgRender.color("Queue").defaultValue(SettingColor.role(SettingColor.Mode.SECONDARY, 120)).visible(render::get).build();

	private final List<BlockPos> candidates = new ArrayList<>();
	private BlockPos target;
	private int cooldown, placed;
	private String info = "";
	private boolean sneakAtEdge;

	public Scaffold() {
		super(Categories.MOVEMENT, "Scaffold", "Places blocks under you as you walk.");
	}

	@Override
	protected void onEnable() {
		candidates.clear();
		target = null;
		cooldown = placed = 0;
	}

	@Override
	public String hudInfo() {
		return info;
	}

	/** Safe walk: hold sneak while standing at an edge (only for this tick's movement). */
	@Subscribe
	private void onInput(InputEvent e) {
		if (sneakAtEdge && !e.sneak) e.sneak = true;
	}

	@Subscribe
	private void onTick(TickEvent.Pre e) {
		sneakAtEdge = false;
		if (!inGame() || mc.gameMode == null || mc.player.isSpectator()) return;
		sneakAtEdge = safeWalk.get() && mc.player.onGround() && !descending() && moving() && atEdge();

		int slot = bestSlot();
		int count = blockCount();
		if (slot < 0) {
			info = "No blocks";
			target = null;
			candidates.clear();
			return;
		}
		info = String.valueOf(count);
		if (cooldown > 0) {
			cooldown--;
			return;
		}
		if (onlyMoving.get() && !moving() && !towering() && !descending()) {
			target = null;
			candidates.clear();
			return;
		}

		Placement.Options options = new Placement.Options(rotate.get(), false, true, range.get());
		List<BlockPos> targets = findTargets(options);
		target = targets.isEmpty() ? null : targets.getFirst();
		int limit = blocksPerTick.get();
		int done = 0;
		for (BlockPos pos : targets) {
			if (done >= limit) break;
			if (Myriad.placement().place(pos, slot, options)) done++;
		}
		if (done > 0) {
			placed += done;
			cooldown = delay.get();
		}
	}

	@Subscribe
	private void onRender(Render3DEvent e) {
		if (!render.get() || !inGame()) return;
		if (renderQueue.get()) {
			int n = 0;
			for (BlockPos pos : candidates) {
				if (pos.equals(target)) continue;
				int c = queueColor.argb();
				Renderer3D.box(new AABB(pos), dev.myriad.api.util.ColorUtil.fade(c, 0.25f), c, Renderer3D.ShapeMode.BOTH, false);
				if (++n >= 12) break;
			}
		}
		if (target != null) {
			int c = targetColor.argb();
			Renderer3D.box(new AABB(target), dev.myriad.api.util.ColorUtil.fade(c, 0.2f), c, Renderer3D.ShapeMode.BOTH, false);
		}
	}

	// ---- targets ------------------------------------------------------------------------------------------------

	private List<BlockPos> findTargets(Placement.Options options) {
		candidates.clear();
		List<BlockPos> out = new ArrayList<>();
		for (BlockPos pos : mode.get() == Mode.PLATFORMER ? platformCandidates() : bridgeCandidates()) {
			if (!Myriad.placement().canPlace(pos, options)) continue;
			if (candidates.size() < 12) candidates.add(pos);
			out.add(pos);
		}
		return out;
	}

	private int targetY() {
		int y = Mth.floor(mc.player.getBoundingBox().minY) - 1;
		return descending() ? y - 1 : y;
	}

	private List<BlockPos> bridgeCandidates() {
		Set<BlockPos> positions = new LinkedHashSet<>();
		AABB box = mc.player.getBoundingBox();
		Vec3 dir = movementDirection();
		double max = moving() && !towering() ? extend.get() : 0;
		for (double d = 0; d <= max + 0.001; d += 0.35) footprint(positions, box, targetY(), dir.x * d, dir.z * d);
		Vec3 eyes = mc.player.getEyePosition();
		Comparator<BlockPos> byDistance = Comparator.comparingDouble(p -> eyes.distanceToSqr(Vec3.atCenterOf(p)));
		return positions.stream().sorted(priority.get() == Priority.FURTHEST ? byDistance.reversed() : byDistance).toList();
	}

	private List<BlockPos> platformCandidates() {
		Set<BlockPos> positions = new LinkedHashSet<>();
		AABB box = mc.player.getBoundingBox();
		Vec3 dir = movementDirection();
		double max = moving() && !towering() ? ahead.get() : 0;
		double cx = (box.minX + box.maxX) / 2, cz = (box.minZ + box.maxZ) / 2;
		int y = targetY(), r = radius.get();
		for (double d = 0; d <= max + 0.001; d += 0.5) {
			int bx = Mth.floor(cx + dir.x * d), bz = Mth.floor(cz + dir.z * d);
			for (int x = -r; x <= r; x++) {
				for (int z = -r; z <= r; z++) {
					if (!corners.get() && Math.abs(x) + Math.abs(z) > r) continue;
					positions.add(new BlockPos(bx + x, y, bz + z));
				}
			}
		}
		footprint(positions, box, y, 0, 0);
		double px = mc.player.getX(), pz = mc.player.getZ();
		Comparator<BlockPos> byDistance = Comparator.comparingDouble(p -> {
			double dx = p.getX() + 0.5 - px, dz = p.getZ() + 0.5 - pz;
			return dx * dx + dz * dz;
		});
		return positions.stream().sorted(priority.get() == Priority.FURTHEST ? byDistance.reversed() : byDistance).toList();
	}

	/** The blocks under the player's hitbox (centre and corners), shifted by an offset. */
	private static void footprint(Set<BlockPos> out, AABB box, int y, double ox, double oz) {
		double pad = 0.05;
		double minX = box.minX + pad + ox, maxX = box.maxX - pad + ox, minZ = box.minZ + pad + oz, maxZ = box.maxZ - pad + oz;
		out.add(new BlockPos(Mth.floor((minX + maxX) / 2), y, Mth.floor((minZ + maxZ) / 2)));
		out.add(new BlockPos(Mth.floor(minX), y, Mth.floor(minZ)));
		out.add(new BlockPos(Mth.floor(minX), y, Mth.floor(maxZ)));
		out.add(new BlockPos(Mth.floor(maxX), y, Mth.floor(minZ)));
		out.add(new BlockPos(Mth.floor(maxX), y, Mth.floor(maxZ)));
	}

	// ---- movement state -------------------------------------------------------------------------------------------

	private boolean moving() {
		return mc.player.input.getMoveVector().lengthSquared() > 1e-8;
	}

	private boolean towering() {
		return tower.get() && mc.options.keyJump.isDown() && !moving();
	}

	private boolean descending() {
		return descend.get() && mc.options.keyShift.isDown();
	}

	private Vec3 movementDirection() {
		double f = mc.player.input.getMoveVector().y, s = mc.player.input.getMoveVector().x;
		double len = Math.hypot(f, s);
		if (len < 1e-4) return Vec3.ZERO;
		f /= len;
		s /= len;
		double yaw = Math.toRadians(mc.player.getYRot());
		return new Vec3(s * Math.cos(yaw) - f * Math.sin(yaw), 0, f * Math.cos(yaw) + s * Math.sin(yaw)).normalize();
	}

	private boolean atEdge() {
		AABB box = mc.player.getBoundingBox();
		int y = Mth.floor(box.minY) - 1;
		double pad = 0.08;
		BlockPos[] support = {
			new BlockPos(Mth.floor(box.minX + pad), y, Mth.floor(box.minZ + pad)),
			new BlockPos(Mth.floor(box.minX + pad), y, Mth.floor(box.maxZ - pad)),
			new BlockPos(Mth.floor(box.maxX - pad), y, Mth.floor(box.minZ + pad)),
			new BlockPos(Mth.floor(box.maxX - pad), y, Mth.floor(box.maxZ - pad))
		};
		for (BlockPos p : support) if (mc.level.getBlockState(p).canBeReplaced()) return true;
		return false;
	}

	// ---- blocks ---------------------------------------------------------------------------------------------------

	private boolean usable(ItemStack stack) {
		if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem item)) return false;
		Block block = item.getBlock();
		if (block == Blocks.COBWEB || block == Blocks.SCAFFOLDING || block instanceof FallingBlock) return false;
		if (!blocks.get().isEmpty() && !blocks.contains(block)) return false;
		BlockState state = block.defaultBlockState();
		return state.isCollisionShapeFullBlock(mc.level, BlockPos.ZERO);
	}

	/** Hotbar slot with the most usable blocks, or -1. */
	private int bestSlot() {
		return Myriad.inventory().bestInHotbar(s -> usable(s) ? s.getCount() : 0);
	}

	private int blockCount() {
		int n = 0;
		for (int i = 0; i < 9; i++) {
			ItemStack s = mc.player.getInventory().getItem(i);
			if (usable(s)) n += s.getCount();
		}
		return n;
	}
}
