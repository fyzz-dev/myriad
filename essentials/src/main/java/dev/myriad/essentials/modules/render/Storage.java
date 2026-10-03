package dev.myriad.essentials.modules.render;

import dev.myriad.api.event.Subscribe;
import dev.myriad.api.event.events.Render3DEvent;
import dev.myriad.api.module.Categories;
import dev.myriad.api.module.Module;
import dev.myriad.api.render.MeshBuilder;
import dev.myriad.api.render.Renderer3D;
import dev.myriad.api.render.ShapeBuilder;
import dev.myriad.api.setting.BoolSetting;
import dev.myriad.api.setting.ColorSetting;
import dev.myriad.api.setting.DoubleSetting;
import dev.myriad.api.setting.EnumSetting;
import dev.myriad.api.setting.IntSetting;
import dev.myriad.api.setting.SettingColor;
import dev.myriad.api.setting.SettingGroup;
import dev.myriad.api.util.ColorUtil;
import dev.myriad.api.util.Entities;
import dev.myriad.api.world.ChunkCache;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.MinecartChest;
import net.minecraft.world.entity.vehicle.minecart.MinecartHopper;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.TrappedChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * Highlights storage around you: chests, ender chests, barrels, shulker boxes, hoppers, dispensers and droppers,
 * furnaces, and chest and hopper minecarts. Each kind can be turned off and has its own colour; double chests are
 * drawn as one box.
 * <p>
 * Block entities are found per chunk with a {@link ChunkCache}: a chunk is scanned when it loads and again only when a
 * block in it changes, and its boxes stay on the GPU. Settings only re-mesh. Minecarts move, so they and the tracers
 * (which start at the camera) are drawn each frame.
 */
public class Storage extends Module {
	private final IntSetting range = sgGeneral.intSetting("Range").description("Chunks around you.").defaultValue(8).range(1, 32).build();
	private final EnumSetting<Renderer3D.ShapeMode> shape = sgGeneral.enumSetting("Shape", Renderer3D.ShapeMode.BOTH).build();
	private final DoubleSetting fillOpacity = sgGeneral.doubleSetting("Fill Opacity").defaultValue(0.2).range(0, 1).decimals(2)
		.visible(() -> shape.get() != Renderer3D.ShapeMode.LINES).build();
	private final DoubleSetting lineWidth = sgGeneral.doubleSetting("Line Width").defaultValue(1.5).range(0.5, 5).decimals(1)
		.visible(() -> shape.get() != Renderer3D.ShapeMode.FILL).build();
	private final BoolSetting throughWalls = sgGeneral.bool("Through Walls").defaultValue(true).build();
	private final BoolSetting tracers = sgGeneral.bool("Tracers").description("A line from the crosshair to each one.").build();

	private final SettingGroup sgKinds = settings.group("Storage");
	private final Kind chests = kind("Chests", true, SettingColor.role(SettingColor.Mode.YELLOW));
	private final Kind trappedChests = kind("Trapped Chests", true, SettingColor.role(SettingColor.Mode.RED));
	private final Kind enderChests = kind("Ender Chests", true, SettingColor.role(SettingColor.Mode.MAGENTA));
	private final Kind barrels = kind("Barrels", true, SettingColor.role(SettingColor.Mode.YELLOW));
	private final Kind shulkers = kind("Shulker Boxes", true, SettingColor.role(SettingColor.Mode.ACCENT));
	private final Kind hoppers = kind("Hoppers", false, SettingColor.role(SettingColor.Mode.TEXT));
	private final Kind dispensers = kind("Dispensers & Droppers", false, SettingColor.role(SettingColor.Mode.TEXT));
	private final Kind furnaces = kind("Furnaces", false, SettingColor.role(SettingColor.Mode.SECONDARY));
	private final BoolSetting minecarts = sgKinds.bool("Minecarts").description("Chest and hopper minecarts, in the chest and hopper colours.").defaultValue(true).build();

	/** Every storage block per chunk, whether or not its kind is shown: toggling a kind only re-meshes. */
	private final ChunkCache<List<Found>> found = ChunkCache.of(this, this::scan)
		.range(range::get)
		.mesh(this::mesh)
		.build();

	private record Kind(BoolSetting enabled, ColorSetting color) {
	}

	private record Found(AABB box, Kind kind) {
	}

	public Storage() {
		super(Categories.RENDER, "Storage", "Highlights chests, shulker boxes and other storage through walls.");
		settings.onAnyChanged(s -> found.remeshAll());
	}

	private Kind kind(String name, boolean on, SettingColor color) {
		BoolSetting enabled = sgKinds.bool(name).defaultValue(on).build();
		ColorSetting c = sgKinds.color(name + " Color").defaultValue(color).visible(enabled::get).build();
		return new Kind(enabled, c);
	}

	@Override
	public String hudInfo() {
		int[] count = {0};
		found.forEach(list -> {
			for (Found f : list) if (f.kind.enabled.get()) count[0]++;
		});
		return count[0] == 0 ? null : String.valueOf(count[0]);
	}

	// ---- per chunk --------------------------------------------------------------------------------------------------

	private List<Found> scan(LevelChunk chunk) {
		List<Found> list = null;
		for (BlockEntity be : chunk.getBlockEntities().values()) {
			Kind k = kindOf(be);
			if (k == null) continue;
			AABB box = boxOf(be);
			if (box == null) continue;
			if (list == null) list = new ArrayList<>();
			list.add(new Found(box, k));
		}
		return list;
	}

	private void mesh(List<Found> list, MeshBuilder mesh) {
		mesh.lineWidth(lineWidth.getFloat());
		for (Found f : list) if (f.kind.enabled.get()) draw(mesh, f.box, f.kind.color.argb());
	}

	private Kind kindOf(BlockEntity be) {
		if (be instanceof TrappedChestBlockEntity) return trappedChests;
		if (be instanceof ChestBlockEntity) return chests;
		if (be instanceof EnderChestBlockEntity) return enderChests;
		if (be instanceof BarrelBlockEntity) return barrels;
		if (be instanceof ShulkerBoxBlockEntity) return shulkers;
		if (be instanceof HopperBlockEntity) return hoppers;
		if (be instanceof DispenserBlockEntity) return dispensers;
		if (be instanceof AbstractFurnaceBlockEntity) return furnaces;
		return null;
	}

	/** The block's outline as one box (both halves of a double chest), or null for the half that isn't drawn. */
	private AABB boxOf(BlockEntity be) {
		BlockPos pos = be.getBlockPos();
		BlockState state = be.getBlockState();
		AABB box = shapeBox(state, pos);
		if (be instanceof ChestBlockEntity && state.hasProperty(ChestBlock.TYPE) && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
			// Draw a double chest once, from its left half.
			if (state.getValue(ChestBlock.TYPE) == ChestType.RIGHT) return null;
			BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state));
			box = box.minmax(shapeBox(mc.level.getBlockState(other), other));
		}
		return box;
	}

	private AABB shapeBox(BlockState state, BlockPos pos) {
		VoxelShape shape = state.getShape(mc.level, pos);
		return shape.isEmpty() ? new AABB(pos) : shape.bounds().move(pos);
	}

	// ---- per frame --------------------------------------------------------------------------------------------------

	@Subscribe
	private void onRender3D(Render3DEvent e) {
		if (!inGame()) return;
		ShapeBuilder shapes = e.shapes();
		shapes.lineWidth(lineWidth.getFloat());
		if (tracers.get()) {
			found.forEach(list -> {
				for (Found f : list) if (f.kind.enabled.get()) Renderer3D.tracer(f.box.getCenter(), f.kind.color.argb());
			});
		}
		if (minecarts.get()) {
			for (Entity entity : mc.level.entitiesForRendering()) {
				Kind k = entity instanceof MinecartChest ? chests : entity instanceof MinecartHopper ? hoppers : null;
				if (k == null || !k.enabled.get()) continue;
				AABB box = Entities.lerpedBox(entity, e.tickDelta());
				draw(shapes, box, k.color.argb());
				if (tracers.get()) Renderer3D.tracer(box.getCenter(), k.color.argb());
			}
		}
	}

	private void draw(ShapeBuilder shapes, AABB box, int color) {
		int fill = ColorUtil.withAlpha(color, (int) (ColorUtil.alpha(color) * fillOpacity.get()));
		shapes.box(box, fill, color, shape.get(), throughWalls.get());
	}
}
