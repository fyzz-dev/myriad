package dev.myriad.essentials.modules.render;

import dev.myriad.api.Myriad;
import dev.myriad.api.event.Subscribe;
import dev.myriad.api.event.events.PacketEvent;
import dev.myriad.api.event.events.Render2DEvent;
import dev.myriad.api.event.events.Render3DEvent;
import dev.myriad.api.event.events.TickEvent;
import dev.myriad.api.event.events.WorldEvent;
import dev.myriad.api.module.Categories;
import dev.myriad.api.module.Module;
import dev.myriad.api.render.Canvas;
import dev.myriad.api.render.Projection;
import dev.myriad.api.render.Renderer3D;
import dev.myriad.api.setting.BoolSetting;
import dev.myriad.api.setting.ColorSetting;
import dev.myriad.api.setting.DoubleSetting;
import dev.myriad.api.setting.EnumSetting;
import dev.myriad.api.setting.IntSetting;
import dev.myriad.api.setting.RegistryListSetting;
import dev.myriad.api.setting.SettingColor;
import dev.myriad.api.setting.SettingGroup;
import dev.myriad.api.util.ColorUtil;
import dev.myriad.api.util.Entities;
import dev.myriad.api.render.WorldLabel;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.network.packet.s2c.play.BlockBreakingProgressS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Highlights things through walls. Each section can be turned on separately:
 * <ul>
 * <li>Entities: 3D hitboxes or 2D screen boxes with health bars, coloured by kind from your theme.</li>
 * <li>Items: name tags over dropped items, grouped when they lie together.</li>
 * <li>Pearls: who threw each ender pearl.</li>
 * <li>Blocks: every block of the chosen types in loaded chunks (ender chests, spawners, …).</li>
 * <li>Holes: safe bedrock and obsidian holes around you.</li>
 * <li>Mining: blocks other players are breaking, with who and how far along.</li>
 * </ul>
 */
public class ESP extends Module {
	public enum Mode {
		HITBOX, BOX_2D
	}

	private static final long FADE_MS = 200;

	// ---- entities ----
	private final BoolSetting entities = sgGeneral.bool("Entities").defaultValue(true).build();
	private final EnumSetting<Mode> mode = sgGeneral.enumSetting("Mode", Mode.HITBOX).description("Hitbox draws 3D boxes; Box 2D draws screen-space boxes.").visible(entities::get).build();
	private final EnumSetting<Renderer3D.ShapeMode> shape = sgGeneral.enumSetting("Shape", Renderer3D.ShapeMode.BOTH).visible(entities::get).build();
	private final DoubleSetting fillOpacity = sgGeneral.doubleSetting("Fill Opacity").defaultValue(0.15).range(0, 1).decimals(2)
		.visible(() -> entities.get() && shape.get() != Renderer3D.ShapeMode.LINES).build();
	private final DoubleSetting lineWidth = sgGeneral.doubleSetting("Line Width").defaultValue(1.5).range(0.5, 5).decimals(1).visible(entities::get).build();
	private final BoolSetting rounded = sgGeneral.bool("Rounded").description("Round the corners of 2D boxes.").visible(() -> entities.get() && mode.get() == Mode.BOX_2D).build();
	private final BoolSetting healthBar = sgGeneral.bool("Health Bar").description("A health bar beside 2D boxes.").visible(() -> entities.get() && mode.get() == Mode.BOX_2D).build();
	private final BoolSetting throughWalls = sgGeneral.bool("Through Walls").defaultValue(true).visible(entities::get).build();
	private final DoubleSetting distance = sgGeneral.doubleSetting("Distance").description("0 = no limit.").defaultValue(0).range(0, 256).decimals(0).visible(entities::get).build();
	private final BoolSetting fade = sgGeneral.bool("Fade In").description("Fade boxes in when entities appear.").defaultValue(true).visible(entities::get).build();

	private final SettingGroup sgTargets = settings.group("Targets");
	private final BoolSetting players = sgTargets.bool("Players").defaultValue(true).build();
	private final BoolSetting self = sgTargets.bool("Self").description("Yourself in third person.").build();
	private final BoolSetting hostiles = sgTargets.bool("Hostiles").defaultValue(true).build();
	private final BoolSetting passives = sgTargets.bool("Passives").build();
	private final BoolSetting items = sgTargets.bool("Items").build();
	private final RegistryListSetting<net.minecraft.entity.EntityType<?>> others = sgTargets.entityTypes("Others").description("Any other entity types to show.").build();

	private final SettingGroup sgColors = settings.group("Colors");
	private final ColorSetting playerColor = sgColors.color("Players").defaultValue(SettingColor.role(SettingColor.Mode.ACCENT)).build();
	private final ColorSetting friendColor = sgColors.color("Friends").defaultValue(SettingColor.role(SettingColor.Mode.CYAN)).build();
	private final ColorSetting hostileColor = sgColors.color("Hostiles").defaultValue(SettingColor.role(SettingColor.Mode.RED)).build();
	private final ColorSetting passiveColor = sgColors.color("Passives").defaultValue(SettingColor.role(SettingColor.Mode.GREEN)).build();
	private final ColorSetting itemColor = sgColors.color("Items").defaultValue(SettingColor.role(SettingColor.Mode.YELLOW)).build();
	private final ColorSetting otherColor = sgColors.color("Others").defaultValue(SettingColor.role(SettingColor.Mode.MAGENTA)).build();

	// ---- item names ----
	private final SettingGroup sgItems = settings.group("Item Names");
	private final BoolSetting itemNames = sgItems.bool("Item Names").description("Name tags over dropped items.").defaultValue(true).build();
	private final BoolSetting grouping = sgItems.bool("Grouping").description("Merge items lying together into one tag per item type.").defaultValue(true).build();
	private final DoubleSetting itemNameSize = sgItems.doubleSetting("Name Size").defaultValue(1).range(0.3, 3).decimals(1).build();
	private final ColorSetting itemNameColor = sgItems.color("Name Color").defaultValue(SettingColor.role(SettingColor.Mode.TEXT)).build();
	private final BoolSetting itemNameBackground = sgItems.bool("Background").defaultValue(true).build();
	private final DoubleSetting itemDistance = sgItems.doubleSetting("Item Distance").defaultValue(32).range(1, 128).decimals(0).build();

	// ---- pearl owners ----
	private final SettingGroup sgPearls = settings.group("Pearls");
	private final BoolSetting pearls = sgPearls.bool("Pearl Owners").description("Show who threw each ender pearl.").defaultValue(true).build();
	private final DoubleSetting pearlNameSize = sgPearls.doubleSetting("Name Size").defaultValue(1).range(0.3, 3).decimals(1).build();
	private final ColorSetting pearlNameColor = sgPearls.color("Name Color").defaultValue(SettingColor.role(SettingColor.Mode.TEXT)).build();

	// ---- blocks ----
	private final SettingGroup sgBlocks = settings.group("Blocks");
	private final BoolSetting blocks = sgBlocks.bool("Block ESP").build();
	private final RegistryListSetting<Block> blockList = sgBlocks.blocks("Blocks").defaultValue(Blocks.ENDER_CHEST, Blocks.SPAWNER).onChanged(v -> rescanBlocks()).build();
	private final IntSetting blockRange = sgBlocks.intSetting("Range").description("Chunks around you.").defaultValue(8).range(1, 20).build();
	private final IntSetting maxBlocks = sgBlocks.intSetting("Max Blocks").description("Cap on boxes drawn per frame.").defaultValue(2048).range(128, 8192).build();
	private final EnumSetting<Renderer3D.ShapeMode> blockShape = sgBlocks.enumSetting("Shape", Renderer3D.ShapeMode.BOTH).build();
	private final ColorSetting blockFill = sgBlocks.color("Fill").defaultValue(SettingColor.role(SettingColor.Mode.ACCENT, 40)).build();
	private final ColorSetting blockLine = sgBlocks.color("Outline").defaultValue(SettingColor.role(SettingColor.Mode.ACCENT)).build();
	private final DoubleSetting blockLineWidth = sgBlocks.doubleSetting("Line Width").defaultValue(1.5).range(0.5, 5).decimals(1).build();
	private final BoolSetting notifyFound = sgBlocks.bool("Notify Found").description("Tell you when a chunk with these blocks loads.").build();

	// ---- holes ----
	private final SettingGroup sgHoles = settings.group("Holes");
	private final BoolSetting holes = sgHoles.bool("Hole ESP").build();
	private final IntSetting holeRange = sgHoles.intSetting("Range").defaultValue(12).range(1, 30).build();
	private final BoolSetting doubleHoles = sgHoles.bool("Double Holes").defaultValue(true).build();
	private final BoolSetting ignoreOwn = sgHoles.bool("Ignore Own").description("Skip the hole you're standing in.").defaultValue(true).build();
	private final BoolSetting holeFade = sgHoles.bool("Fade").description("Fade holes out with distance.").defaultValue(true).build();
	private final DoubleSetting holeHeight = sgHoles.doubleSetting("Height").defaultValue(1).range(-3, 3).decimals(1).build();
	private final BoolSetting bedrock = sgHoles.bool("Bedrock").defaultValue(true).build();
	private final ColorSetting bedrockColor = sgHoles.color("Bedrock Color").defaultValue(SettingColor.role(SettingColor.Mode.GREEN, 70)).visible(bedrock::get).build();
	private final BoolSetting obsidian = sgHoles.bool("Obsidian").defaultValue(true).build();
	private final ColorSetting obsidianColor = sgHoles.color("Obsidian Color").defaultValue(SettingColor.role(SettingColor.Mode.YELLOW, 70)).visible(obsidian::get).build();
	private final DoubleSetting holeLineOpacity = sgHoles.doubleSetting("Line Opacity").defaultValue(0.6).range(0, 1).decimals(2).build();

	// ---- mining ----
	private final SettingGroup sgMining = settings.group("Mining");
	private final BoolSetting mining = sgMining.bool("Mining ESP").description("Blocks other players are breaking.").build();
	private final IntSetting miningRange = sgMining.intSetting("Range").defaultValue(64).range(1, 128).build();
	private final BoolSetting miningIgnoreFriends = sgMining.bool("Ignore Friends").build();
	private final BoolSetting miningNames = sgMining.bool("Names").description("Show who is mining.").defaultValue(true).build();
	private final BoolSetting miningPercent = sgMining.bool("Percentage").defaultValue(true).build();
	private final ColorSetting miningFill = sgMining.color("Fill").defaultValue(SettingColor.role(SettingColor.Mode.RED, 40)).build();
	private final ColorSetting miningLine = sgMining.color("Line").defaultValue(SettingColor.role(SettingColor.Mode.RED)).build();

	private final Map<Integer, Long> appeared = new HashMap<>();
	private final Map<Long, List<BlockPos>> chunkBlocks = new ConcurrentHashMap<>();
	private final ArrayDeque<Long> scanQueue = new ArrayDeque<>();
	private final Set<Long> dirty = ConcurrentHashMap.newKeySet();
	private final List<Hole> holeList = new ArrayList<>();
	private final Map<Integer, Breaking> breaking = new ConcurrentHashMap<>();
	private int holeTimer;

	private record Hole(Box box, boolean bedrockHole) {
	}

	private record Breaking(BlockPos pos, int stage, long time) {
	}

	public ESP() {
		super(Categories.RENDER, "ESP", "Highlights entities, items, blocks and holes through walls.");
	}

	@Override
	protected void onEnable() {
		appeared.clear();
		breaking.clear();
		rescanBlocks();
	}

	@Override
	protected void onDisable() {
		chunkBlocks.clear();
		scanQueue.clear();
		holeList.clear();
	}

	@Subscribe
	private void onWorld(WorldEvent e) {
		chunkBlocks.clear();
		scanQueue.clear();
		breaking.clear();
		appeared.clear();
	}

	// ---- entity colours ---------------------------------------------------------------------------------------------

	/** The colour to draw {@code e} with, or 0 to skip it. */
	int colorFor(Entity e) {
		if (e instanceof ItemEntity) return items.get() ? itemColor.argb() : 0;
		int c = switch (Entities.kind(e)) {
			case PLAYER -> players.get() ? (Entities.isFriend(e) ? friendColor.argb() : playerColor.argb()) : 0;
			case HOSTILE -> hostiles.get() ? hostileColor.argb() : 0;
			case PASSIVE -> passives.get() ? passiveColor.argb() : 0;
			case OTHER -> 0;
		};
		if (c == 0 && others.contains(e.getType())) c = otherColor.argb();
		return c;
	}

	private boolean skip(Entity e) {
		if (e == mc.player) return !self.get() || mc.options.getPerspective().isFirstPerson();
		return distance.get() > 0 && mc.player.squaredDistanceTo(e) > distance.get() * distance.get();
	}

	private float fadeFor(Entity e, Set<Integer> seen) {
		seen.add(e.getId());
		if (!fade.get()) return 1;
		long now = System.currentTimeMillis();
		return MathHelper.clamp((now - appeared.computeIfAbsent(e.getId(), id -> now)) / (float) FADE_MS, 0, 1);
	}

	private static int scaleAlpha(int c, float f) {
		return ColorUtil.withAlpha(c, (int) (ColorUtil.alpha(c) * f));
	}

	// ---- 3D ---------------------------------------------------------------------------------------------------------

	@Subscribe
	private void onRender3D(Render3DEvent e) {
		if (!inGame()) return;
		if (entities.get() && mode.get() == Mode.HITBOX) {
			Renderer3D.lineWidth(lineWidth.getFloat());
			Set<Integer> seen = new HashSet<>();
			for (Entity entity : mc.world.getEntities()) {
				if (skip(entity)) continue;
				int c = colorFor(entity);
				if (c == 0) continue;
				float f = fadeFor(entity, seen);
				int line = scaleAlpha(c, f);
				int fill = ColorUtil.withAlpha(c, (int) (ColorUtil.alpha(c) * fillOpacity.get() * f));
				Renderer3D.box(Entities.lerpedBox(entity, e.tickDelta()), fill, line, shape.get(), throughWalls.get());
			}
			appeared.keySet().retainAll(seen);
		}
		if (blocks.get()) renderBlocks();
		if (holes.get()) renderHoles();
		if (mining.get()) renderMining3D();
	}

	private void renderBlocks() {
		Renderer3D.lineWidth(blockLineWidth.getFloat());
		ChunkPos center = mc.player.getChunkPos();
		int range = blockRange.get(), budget = maxBlocks.get(), drawn = 0;
		List<Map.Entry<Long, List<BlockPos>>> chunks = new ArrayList<>(chunkBlocks.entrySet());
		chunks.sort(Comparator.comparingInt(en -> {
			ChunkPos cp = new ChunkPos(en.getKey());
			return (cp.x - center.x) * (cp.x - center.x) + (cp.z - center.z) * (cp.z - center.z);
		}));
		for (Map.Entry<Long, List<BlockPos>> en : chunks) {
			ChunkPos cp = new ChunkPos(en.getKey());
			if (Math.abs(cp.x - center.x) > range || Math.abs(cp.z - center.z) > range) continue;
			for (BlockPos p : en.getValue()) {
				if (drawn >= budget) return;
				BlockState state = mc.world.getBlockState(p);
				if (!blockList.contains(state.getBlock())) continue;
				Box box;
				if (state.isOpaqueFullCube()) box = new Box(p);
				else {
					VoxelShape s = state.getOutlineShape(mc.world, p);
					box = s.isEmpty() ? new Box(p) : s.getBoundingBox().offset(p);
				}
				Renderer3D.box(box, blockFill.argb(), blockLine.argb(), blockShape.get(), true);
				drawn++;
			}
		}
	}

	private void renderHoles() {
		Renderer3D.lineWidth(1.5f);
		Vec3d eye = mc.player.getPos();
		for (Hole h : holeList) {
			if (ignoreOwn.get() && h.box.intersects(mc.player.getBoundingBox())) continue;
			float f = 1;
			if (holeFade.get()) {
				f = (float) MathHelper.clamp(1 - eye.distanceTo(h.box.getCenter()) / holeRange.get(), 0, 1);
				f *= f;
			}
			int base = h.bedrockHole ? bedrockColor.argb() : obsidianColor.argb();
			double hgt = holeHeight.get();
			Box box = new Box(h.box.minX, hgt >= 0 ? h.box.minY : h.box.minY + hgt, h.box.minZ, h.box.maxX, hgt >= 0 ? h.box.minY + hgt : h.box.minY, h.box.maxZ);
			int fill = scaleAlpha(base, f);
			int line = ColorUtil.withAlpha(base, (int) (255 * holeLineOpacity.get() * f));
			Renderer3D.box(box, fill, line, Renderer3D.ShapeMode.BOTH, false);
		}
	}

	private void renderMining3D() {
		Renderer3D.lineWidth(1.5f);
		long now = System.currentTimeMillis();
		breaking.values().removeIf(b -> now - b.time > 10_000 || mc.world.getBlockState(b.pos).isAir());
		for (Map.Entry<Integer, Breaking> en : breaking.entrySet()) {
			if (!showMining(en.getKey(), en.getValue())) continue;
			Breaking b = en.getValue();
			double s = (b.stage + 1) / 10.0;
			Vec3d c = Vec3d.ofCenter(b.pos);
			Box box = new Box(c.x - s / 2, c.y - s / 2, c.z - s / 2, c.x + s / 2, c.y + s / 2, c.z + s / 2);
			Renderer3D.box(box, miningFill.argb(), miningLine.argb(), Renderer3D.ShapeMode.BOTH, true);
		}
	}

	private boolean showMining(int entityId, Breaking b) {
		if (entityId == mc.player.getId()) return false;
		if (mc.player.getPos().distanceTo(Vec3d.ofCenter(b.pos)) > miningRange.get()) return false;
		Entity miner = mc.world.getEntityById(entityId);
		return !(miningIgnoreFriends.get() && miner instanceof PlayerEntity p && Myriad.friends().isFriend(p));
	}

	// ---- 2D ---------------------------------------------------------------------------------------------------------

	@Subscribe
	private void onRender2D(Render2DEvent e) {
		if (!inGame()) return;
		Canvas c = e.canvas();
		if (entities.get() && mode.get() == Mode.BOX_2D) {
			Set<Integer> seen = new HashSet<>();
			for (Entity entity : mc.world.getEntities()) {
				if (skip(entity)) continue;
				int color = colorFor(entity);
				if (color == 0) continue;
				float[] r = project(Entities.lerpedBox(entity, e.tickDelta()));
				if (r == null) continue;
				drawBox2D(c, entity, r, scaleAlpha(color, fadeFor(entity, seen)));
			}
			appeared.keySet().retainAll(seen);
		}
		if (itemNames.get()) renderItemNames(c, e.tickDelta());
		if (pearls.get()) renderPearlOwners(c, e.tickDelta());
		if (mining.get() && (miningNames.get() || miningPercent.get())) renderMiningLabels(c);
	}

	private void drawBox2D(Canvas c, Entity entity, float[] r, int color) {
		float x = r[0], y = r[1], w = r[2] - r[0], h = r[3] - r[1];
		float rad = rounded.get() ? Math.min(3, Math.min(w, h) / 4) : 0;
		if (shape.get() != Renderer3D.ShapeMode.LINES) c.roundRect(x, y, w, h, rad, ColorUtil.withAlpha(color, (int) (ColorUtil.alpha(color) * fillOpacity.get())));
		if (shape.get() != Renderer3D.ShapeMode.FILL) {
			c.outline(x - 0.5f, y - 0.5f, w + 1, h + 1, rad, lineWidth.getFloat() + 1, ColorUtil.withAlpha(0xFF000000, ColorUtil.alpha(color) / 2));
			c.outline(x, y, w, h, rad, lineWidth.getFloat(), color);
		}
		if (healthBar.get() && entity instanceof LivingEntity living) {
			float hp = MathHelper.clamp(living.getHealth() / living.getMaxHealth(), 0, 1);
			float bx = x - 3.5f;
			c.rect(bx - 0.5f, y - 0.5f, 2, h + 1, 0x99000000);
			c.rect(bx, y + h * (1 - hp), 1, h * hp, ColorUtil.lerp(0xFFFF5555, 0xFF55FF55, hp));
		}
	}

	/** Screen rect [left, top, right, bottom] around a world box, or null when it's off screen. */
	private static float[] project(Box b) {
		float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
		var window = mc.getWindow();
		float sw = window.getScaledWidth(), sh = window.getScaledHeight();
		boolean any = false;
		for (int i = 0; i < 8; i++) {
			Vec3d s = Projection.toScreen(new Vec3d((i & 1) == 0 ? b.minX : b.maxX, (i & 2) == 0 ? b.minY : b.maxY, (i & 4) == 0 ? b.minZ : b.maxZ));
			// Corners right by the near plane project far off screen; skip them.
			if (s == null || s.x < -sw * 2 || s.x > sw * 3 || s.y < -sh * 2 || s.y > sh * 3) continue;
			minX = Math.min(minX, (float) s.x);
			minY = Math.min(minY, (float) s.y);
			maxX = Math.max(maxX, (float) s.x);
			maxY = Math.max(maxY, (float) s.y);
			any = true;
		}
		if (!any) return null;
		minX = Math.max(0, minX);
		minY = Math.max(0, minY);
		maxX = Math.min(sw, maxX);
		maxY = Math.min(sh, maxY);
		return maxX <= minX || maxY <= minY ? null : new float[]{minX, minY, maxX, maxY};
	}

	private void renderItemNames(Canvas c, float tickDelta) {
		double maxSq = itemDistance.get() * itemDistance.get();
		List<List<ItemEntity>> groups = new ArrayList<>();
		for (Entity entity : mc.world.getEntities()) {
			if (!(entity instanceof ItemEntity item) || mc.player.squaredDistanceTo(entity) > maxSq) continue;
			List<ItemEntity> target = null;
			if (grouping.get()) {
				for (List<ItemEntity> g : groups) {
					if (g.getFirst().squaredDistanceTo(item) < 4) {
						target = g;
						break;
					}
				}
			}
			if (target == null) groups.add(target = new ArrayList<>());
			target.add(item);
		}
		int color = itemNameColor.argb();
		int bg = itemNameBackground.get() ? 0x64000000 : 0;
		for (List<ItemEntity> g : groups) {
			double x = 0, y = 0, z = 0;
			for (ItemEntity it : g) {
				Box box = Entities.lerpedBox(it, tickDelta);
				x += box.getCenter().x;
				y += box.maxY;
				z += box.getCenter().z;
			}
			x /= g.size();
			y /= g.size();
			z /= g.size();
			Map<String, Integer> counts = new LinkedHashMap<>();
			for (ItemEntity it : g) counts.merge(it.getStack().getName().getString(), it.getStack().getCount(), Integer::sum);
			int line = 0;
			for (Map.Entry<String, Integer> en : counts.entrySet()) {
				String text = en.getValue() > 1 ? en.getKey() + " x" + en.getValue() : en.getKey();
				Vec3d at = new Vec3d(x, y + 0.3 + line++ * 0.28, z);
				WorldLabel.draw(c, at, itemNameSize.getFloat() * WorldLabel.distanceScale(at), List.of(new WorldLabel.Segment(text, color)),
					bg != 0 ? WorldLabel.Background.ROUNDED : WorldLabel.Background.NONE, bg, 0, true);
			}
		}
	}

	private void renderPearlOwners(Canvas c, float tickDelta) {
		for (Entity entity : mc.world.getEntities()) {
			if (!(entity instanceof EnderPearlEntity pearl) || pearl.getOwner() == null) continue;
			Box box = Entities.lerpedBox(entity, tickDelta);
			Vec3d at = new Vec3d(box.getCenter().x, box.maxY + 0.25, box.getCenter().z);
			WorldLabel.draw(c, at, pearlNameSize.getFloat() * WorldLabel.distanceScale(at),
				List.of(new WorldLabel.Segment(pearl.getOwner().getNameForScoreboard(), pearlNameColor.argb())), WorldLabel.Background.ROUNDED, 0x64000000, 0, true);
		}
	}

	private void renderMiningLabels(Canvas c) {
		for (Map.Entry<Integer, Breaking> en : breaking.entrySet()) {
			if (!showMining(en.getKey(), en.getValue())) continue;
			List<WorldLabel.Segment> segs = new ArrayList<>();
			Entity miner = mc.world.getEntityById(en.getKey());
			if (miningNames.get() && miner != null) segs.add(new WorldLabel.Segment(miner.getNameForScoreboard(), 0xFFFFFFFF));
			if (miningPercent.get()) segs.add(new WorldLabel.Segment((en.getValue().stage + 1) * 10 + "%", miningLine.argb()));
			if (segs.isEmpty()) continue;
			Vec3d at = Vec3d.ofCenter(en.getValue().pos).add(0, 0.7, 0);
			WorldLabel.draw(c, at, WorldLabel.distanceScale(at), segs, WorldLabel.Background.ROUNDED, 0x64000000, 0, true);
		}
	}

	// ---- block scanning ----------------------------------------------------------------------------------------------

	@Subscribe
	private void onTick(TickEvent.Post e) {
		if (!inGame()) return;
		if (blocks.get()) tickBlockScan();
		if (holes.get() && --holeTimer <= 0) {
			holeTimer = 5;
			findHoles();
		} else if (!holes.get()) holeList.clear();
	}

	@Subscribe
	private void onPacket(PacketEvent.Receive e) {
		if (e.packet() instanceof BlockBreakingProgressS2CPacket p) {
			if (p.getProgress() < 0 || p.getProgress() > 9) breaking.remove(p.getEntityId());
			else breaking.put(p.getEntityId(), new Breaking(p.getPos(), p.getProgress(), System.currentTimeMillis()));
			return;
		}
		if (!blocks.get()) return;
		if (e.packet() instanceof ChunkDataS2CPacket p) dirty.add(ChunkPos.toLong(p.getChunkX(), p.getChunkZ()));
		else if (e.packet() instanceof BlockUpdateS2CPacket p) markDirty(p.getPos(), p.getState());
		else if (e.packet() instanceof ChunkDeltaUpdateS2CPacket p) p.visitUpdates(this::markDirty);
	}

	private void markDirty(BlockPos pos, BlockState state) {
		long key = ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4);
		if (blockList.contains(state.getBlock())) dirty.add(key);
		else {
			List<BlockPos> found = chunkBlocks.get(key);
			if (found != null && found.contains(pos)) dirty.add(key);
		}
	}

	private void rescanBlocks() {
		chunkBlocks.clear();
		scanQueue.clear();
		if (!inGame()) return;
		ChunkPos center = mc.player.getChunkPos();
		int r = blockRange.get();
		for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) scanQueue.add(ChunkPos.toLong(center.x + dx, center.z + dz));
	}

	private void tickBlockScan() {
		for (Long key : dirty) {
			if (!scanQueue.contains(key)) scanQueue.add(key);
		}
		dirty.clear();
		ChunkPos center = mc.player.getChunkPos();
		int r = blockRange.get();
		chunkBlocks.keySet().removeIf(k -> {
			ChunkPos cp = new ChunkPos(k);
			return Math.abs(cp.x - center.x) > r + 2 || Math.abs(cp.z - center.z) > r + 2;
		});
		// Pick up chunks that came into range as you moved.
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				long key = ChunkPos.toLong(center.x + dx, center.z + dz);
				if (!chunkBlocks.containsKey(key) && !scanQueue.contains(key)) scanQueue.add(key);
			}
		}
		for (int i = 0; i < 4 && !scanQueue.isEmpty(); i++) scanChunk(scanQueue.poll());
	}

	private void scanChunk(long key) {
		ChunkPos cp = new ChunkPos(key);
		if (!mc.world.getChunkManager().isChunkLoaded(cp.x, cp.z)) return;
		WorldChunk chunk = mc.world.getChunk(cp.x, cp.z);
		Set<Block> targets = blockList.get();
		List<BlockPos> found = new ArrayList<>();
		ChunkSection[] sections = chunk.getSectionArray();
		for (int si = 0; si < sections.length; si++) {
			ChunkSection section = sections[si];
			if (section == null || section.isEmpty() || !section.hasAny(s -> targets.contains(s.getBlock()))) continue;
			int baseY = chunk.sectionIndexToCoord(si) << 4;
			for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
				if (targets.contains(section.getBlockState(x, y, z).getBlock())) found.add(new BlockPos(cp.getStartX() + x, baseY + y, cp.getStartZ() + z));
			}
		}
		List<BlockPos> previous = found.isEmpty() ? chunkBlocks.remove(key) : chunkBlocks.put(key, found);
		if (notifyFound.get() && !found.isEmpty() && (previous == null || previous.isEmpty())) {
			info("Found " + found.size() + " block" + (found.size() == 1 ? "" : "s") + " in chunk " + cp.x + ", " + cp.z);
		}
	}

	// ---- holes ------------------------------------------------------------------------------------------------------

	private void findHoles() {
		holeList.clear();
		BlockPos center = mc.player.getBlockPos();
		int r = holeRange.get(), ry = Math.min(r, 6);
		Set<BlockPos> doubled = new HashSet<>();
		for (BlockPos p : BlockPos.iterate(center.add(-r, -ry, -r), center.add(r, ry, r))) {
			if (!isAirColumn(p)) continue;
			int kind = enclosed(p, null);
			if (kind > 0) {
				addHole(new Box(p), kind == 2);
				continue;
			}
			if (!doubleHoles.get()) continue;
			for (Direction d : new Direction[]{Direction.EAST, Direction.SOUTH}) {
				BlockPos q = p.offset(d);
				if (doubled.contains(p) || !isAirColumn(q)) continue;
				int a = enclosed(p, d), b = enclosed(q, d.getOpposite());
				if (a > 0 && b > 0) {
					doubled.add(p.toImmutable());
					doubled.add(q.toImmutable());
					addHole(new Box(p).union(new Box(q)), a == 2 && b == 2);
				}
			}
		}
	}

	private void addHole(Box box, boolean bedrockHole) {
		if (bedrockHole ? bedrock.get() : obsidian.get()) holeList.add(new Hole(box, bedrockHole));
	}

	private boolean isAirColumn(BlockPos p) {
		return mc.world.getBlockState(p).isAir() && mc.world.getBlockState(p.up()).isAir() && mc.world.getBlockState(p.up(2)).isAir();
	}

	/** 2 = all bedrock, 1 = blast-proof mix, 0 = not a hole. {@code open} is a side left open for a double hole. */
	private int enclosed(BlockPos p, Direction open) {
		boolean allBedrock = true;
		for (Direction d : new Direction[]{Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
			if (d == open) continue;
			Block b = mc.world.getBlockState(p.offset(d)).getBlock();
			if (b == Blocks.BEDROCK) continue;
			if (b == Blocks.OBSIDIAN || b == Blocks.CRYING_OBSIDIAN || b == Blocks.RESPAWN_ANCHOR || b == Blocks.ENDER_CHEST || b == Blocks.NETHERITE_BLOCK) {
				allBedrock = false;
				continue;
			}
			return 0;
		}
		return allBedrock ? 2 : 1;
	}
}
