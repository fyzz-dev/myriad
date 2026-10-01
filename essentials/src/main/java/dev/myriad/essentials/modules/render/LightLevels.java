package dev.myriad.essentials.modules.render;

import dev.myriad.api.event.Subscribe;
import dev.myriad.api.event.events.Render3DEvent;
import dev.myriad.api.event.events.TickEvent;
import dev.myriad.api.module.Categories;
import dev.myriad.api.module.Module;
import dev.myriad.api.render.Renderer3D;
import dev.myriad.api.setting.BoolSetting;
import dev.myriad.api.setting.ColorSetting;
import dev.myriad.api.setting.DoubleSetting;
import dev.myriad.api.setting.IntSetting;
import dev.myriad.api.setting.SettingColor;
import dev.myriad.api.util.ColorUtil;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.LightType;
import net.minecraft.world.RaycastContext;

/** Marks the tops of blocks around you where hostile mobs can spawn (block light 0), and optionally the safe ones. */
public class LightLevels extends Module {
	private final IntSetting range = sgGeneral.intSetting("Range").description("Blocks around the camera.").defaultValue(16).range(1, 32).build();
	private final DoubleSetting opacity = sgGeneral.doubleSetting("Opacity").description("Fill opacity.").defaultValue(0.25).range(0, 1).decimals(2).build();
	private final DoubleSetting lineWidth = sgGeneral.doubleSetting("Line Width").defaultValue(1).range(0.5, 5).decimals(1).build();
	private final BoolSetting throughWalls = sgGeneral.bool("Through Walls").description("Show surfaces you can't see directly.").defaultValue(true).build();
	private final BoolSetting spawnableOnly = sgGeneral.bool("Spawnable Only").description("Only mark where mobs can spawn.").build();
	private final ColorSetting spawnable = sgGeneral.color("Spawnable").defaultValue(SettingColor.role(SettingColor.Mode.RED)).build();
	private final ColorSetting safe = sgGeneral.color("Safe").defaultValue(SettingColor.role(SettingColor.Mode.GREEN)).visible(() -> !spawnableOnly.get()).build();

	private record Surface(BlockPos pos, Box top, boolean spawnable) {
	}

	private final java.util.List<Surface> surfaces = new java.util.ArrayList<>();
	private int timer;

	public LightLevels() {
		super(Categories.RENDER, "Light Levels", "Shows where mobs can spawn.");
	}

	@Override
	protected void onEnable() {
		timer = 0;
	}

	@Subscribe
	private void onRender(Render3DEvent e) {
		if (!inGame()) return;
		Renderer3D.lineWidth(lineWidth.getFloat());
		Vec3d cam = e.camera().getPos();
		for (Surface s : surfaces) {
			if (!throughWalls.get() && !visible(cam, s.pos, s.top)) continue;
			int c = s.spawnable ? spawnable.argb() : safe.argb();
			Renderer3D.box(s.top, ColorUtil.withAlpha(c, (int) (255 * opacity.get())), c, Renderer3D.ShapeMode.BOTH, throughWalls.get());
		}
	}

	/** Rescans a few times a second; light and blocks rarely change faster than that. */
	@Subscribe
	private void onTick(TickEvent.Post e) {
		if (!inGame() || --timer > 0) return;
		timer = 5;
		surfaces.clear();
		Vec3d cam = mc.gameRenderer.getCamera().getPos();
		int r = range.get();
		double rangeSq = r * r;
		BlockPos.Mutable pos = new BlockPos.Mutable();
		int bx = MathHelper.floor(cam.x), by = MathHelper.floor(cam.y), bz = MathHelper.floor(cam.z);
		for (int x = bx - r; x <= bx + r; x++) {
			for (int z = bz - r; z <= bz + r; z++) {
				for (int y = by - r; y <= by + r; y++) {
					if (cam.squaredDistanceTo(x + 0.5, y + 0.5, z + 0.5) > rangeSq) continue;
					pos.set(x, y, z);
					BlockState state = mc.world.getBlockState(pos);
					Box top = topFace(pos, state);
					if (top == null) continue;
					boolean canSpawn = state.allowsSpawning(mc.world, pos, EntityType.ZOMBIE) && mc.world.getLightLevel(LightType.BLOCK, pos.up()) == 0;
					if (spawnableOnly.get() && !canSpawn) continue;
					surfaces.add(new Surface(pos.toImmutable(), top, canSpawn));
				}
			}
		}
	}

	private Box topFace(BlockPos pos, BlockState state) {
		if (state.isAir()) return null;
		BlockState above = mc.world.getBlockState(pos.up());
		if (!above.isAir() || !above.getFluidState().isEmpty()) return null;
		VoxelShape shape = state.getCollisionShape(mc.world, pos);
		if (shape.isEmpty() || shape.getMax(Direction.Axis.Y) <= 0) return null;
		Box b = shape.getBoundingBox();
		double top = pos.getY() + b.maxY, thick = Math.min(0.02, b.maxY);
		return new Box(pos.getX() + b.minX, top - thick, pos.getZ() + b.minZ, pos.getX() + b.maxX, top, pos.getZ() + b.maxZ);
	}

	private boolean visible(Vec3d from, BlockPos pos, Box top) {
		Vec3d target = new Vec3d((top.minX + top.maxX) / 2, top.maxY - 0.001, (top.minZ + top.maxZ) / 2);
		BlockHitResult hit = mc.world.raycast(new RaycastContext(from, target, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
		return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos);
	}
}
