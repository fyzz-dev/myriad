package dev.myriad.essentials.modules.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.myriad.api.Myriad;
import dev.myriad.api.event.Subscribe;
import dev.myriad.api.event.events.PacketEvent;
import dev.myriad.api.event.events.Render2DEvent;
import dev.myriad.api.event.events.Render3DEvent;
import dev.myriad.api.event.events.WorldEvent;
import dev.myriad.api.module.Categories;
import dev.myriad.api.module.Module;
import dev.myriad.api.render.Canvas;
import dev.myriad.api.render.ModelShapes;
import dev.myriad.api.render.Projection;
import dev.myriad.api.render.RenderStates;
import dev.myriad.api.render.Renderer3D;
import dev.myriad.api.render.WorldLabel;
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
import dev.myriad.api.world.BlockScan;
import dev.myriad.api.world.ChunkCache;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Highlights things through walls. Each section can be turned on separately:
 * <ul>
 * <li>Entities: 3D hitboxes, 2D screen boxes with health bars, or a wireframe of the model (Complex), coloured by kind
 * from your theme.</li>
 * <li>Items: name tags over dropped items, grouped when they lie together.</li>
 * <li>Pearls: who threw each ender pearl.</li>
 * <li>Holes: safe bedrock and obsidian holes around you.</li>
 * <li>Mining: blocks other players are breaking, with who and how far along.</li>
 * </ul>
 */
public class ESP extends Module {
	public enum Mode {
		HITBOX, BOX_2D, COMPLEX
	}

	private static final long FADE_MS = 200;

	// ---- entities ----
	private final BoolSetting entities = sgGeneral.bool("Entities").defaultValue(true).build();
	private final EnumSetting<Mode> mode = sgGeneral.enumSetting("Mode", Mode.HITBOX).description("Hitbox: 3D boxes. Box 2D: screen-space boxes. Complex: a wireframe of the model.")
		.visible(entities::get).build();
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
	private final RegistryListSetting<net.minecraft.world.entity.EntityType<?>> others = sgTargets.entityTypes("Others").description("Any other entity types to show.").build();

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

	/** When each entity id was first drawn, for the fade-in; ids not drawn this frame are dropped. */
	private final Int2LongOpenHashMap appeared = new Int2LongOpenHashMap();
	private final IntOpenHashSet seen = new IntOpenHashSet();
	private final Map<Integer, Breaking> breaking = new ConcurrentHashMap<>();

	/**
	 * Holes per chunk, found when a chunk loads or changes rather than rescanned around you every few ticks. A hole looks
	 * one block past its own chunk, so changes at a chunk's edge recompute its neighbour too. Drawn each frame, since
	 * they fade with your distance.
	 */
	private final ChunkCache<List<Hole>> holeCache = ChunkCache.of(this, this::findHoles)
		.range(() -> holes.get() ? (holeRange.get() + 15) / 16 : 0)
		.neighbours()
		.build();

	private record Hole(AABB box, boolean bedrockHole) {
	}

	private record Breaking(BlockPos pos, int stage, long time) {
	}

	public ESP() {
		super(Categories.RENDER, "ESP", "Highlights entities, items and holes through walls.");
		for (var s : List.of(holes, doubleHoles, bedrock, obsidian)) s.onChanged(v -> holeCache.invalidateAll());
	}

	@Override
	protected void onEnable() {
		appeared.clear();
		breaking.clear();
	}

	@Subscribe
	private void onWorld(WorldEvent e) {
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
		if (e == mc.player) return !self.get() || mc.options.getCameraType().isFirstPerson();
		return distance.get() > 0 && mc.player.distanceToSqr(e) > distance.get() * distance.get();
	}

	private float fadeFor(Entity e, long now) {
		seen.add(e.getId());
		if (!fade.get()) return 1;
		long since = appeared.get(e.getId());
		if (since == 0) appeared.put(e.getId(), since = now);
		return Mth.clamp((now - since) / (float) FADE_MS, 0, 1);
	}

	/** The fade-in of an entity already seen this frame (see {@link #fadeFor}). */
	private float fadeOf(Entity e) {
		if (!fade.get()) return 1;
		long since = appeared.get(e.getId());
		return since == 0 ? 0 : Mth.clamp((System.currentTimeMillis() - since) / (float) FADE_MS, 0, 1);
	}

	private void forgetUnseen() {
		appeared.keySet().retainAll(seen);
		seen.clear();
	}

	/** Complex mode: called as an entity's model is submitted, to draw it again as a wireframe of its boxes and a fill. */
	@SuppressWarnings("unchecked")
	public void submitModel(SubmitNodeCollector submits, Model<?> model, LivingEntityRenderState state, PoseStack poseStack) {
		if (!isEnabled() || !entities.get() || mode.get() != Mode.COMPLEX || mc.player == null) return;
		Entity e = RenderStates.entity(state);
		if (e == null || skip(e)) return;
		int c = colorFor(e);
		if (c == 0) return;
		float f = fadeOf(e);
		Model<LivingEntityRenderState> m = (Model<LivingEntityRenderState>) model;
		int fill = ColorUtil.withAlpha(c, (int) (ColorUtil.alpha(c) * fillOpacity.get() * f));
		if (ColorUtil.alpha(fill) > 0 && shape.get() != Renderer3D.ShapeMode.LINES) {
			ModelShapes.fill(submits, poseStack, m, state, fill, throughWalls.get());
		}
		if (shape.get() != Renderer3D.ShapeMode.FILL) {
			ModelShapes.wireframe(submits, poseStack, m, state, scaleAlpha(c, f), lineWidth.getFloat(), throughWalls.get());
		}
	}

	private static int scaleAlpha(int c, float f) {
		return ColorUtil.withAlpha(c, (int) (ColorUtil.alpha(c) * f));
	}

	// ---- 3D ---------------------------------------------------------------------------------------------------------

	@Subscribe
	private void onRender3D(Render3DEvent e) {
		if (!inGame()) return;
		if (entities.get() && mode.get() != Mode.BOX_2D) {
			Renderer3D.lineWidth(lineWidth.getFloat());
			long now = System.currentTimeMillis();
			for (Entity entity : mc.level.entitiesForRendering()) {
				if (skip(entity)) continue;
				int c = colorFor(entity);
				if (c == 0) continue;
				float f = fadeFor(entity, now);
				// Complex: models draw themselves (submitModel); anything without one falls back to its box.
				if (mode.get() == Mode.COMPLEX && entity instanceof LivingEntity) continue;
				int line = scaleAlpha(c, f);
				int fill = ColorUtil.withAlpha(c, (int) (ColorUtil.alpha(c) * fillOpacity.get() * f));
				Renderer3D.box(Entities.lerpedBox(entity, e.tickDelta()), fill, line, shape.get(), throughWalls.get());
			}
			forgetUnseen();
		}
		if (holes.get()) renderHoles();
		if (mining.get()) renderMining3D();
	}

	private void renderHoles() {
		Renderer3D.lineWidth(1.5f);
		Vec3 eye = mc.player.position();
		int r = holeRange.get(), ry = Math.min(r, 6);
		holeCache.forEach(list -> {
			for (Hole h : list) drawHole(h, eye, r, ry);
		});
	}

	private void drawHole(Hole h, Vec3 eye, int r, int ry) {
		AABB b = h.box;
		if (b.maxX < eye.x - r || b.minX > eye.x + r || b.maxZ < eye.z - r || b.minZ > eye.z + r || Math.abs(b.minY - eye.y) > ry) return;
		if (ignoreOwn.get() && b.intersects(mc.player.getBoundingBox())) return;
		float f = 1;
		if (holeFade.get()) {
			f = (float) Mth.clamp(1 - eye.distanceTo(b.getCenter()) / r, 0, 1);
			f *= f;
		}
		int base = h.bedrockHole ? bedrockColor.argb() : obsidianColor.argb();
		double hgt = holeHeight.get();
		AABB box = new AABB(b.minX, hgt >= 0 ? b.minY : b.minY + hgt, b.minZ, b.maxX, hgt >= 0 ? b.minY + hgt : b.minY, b.maxZ);
		int fill = scaleAlpha(base, f);
		int line = ColorUtil.withAlpha(base, (int) (255 * holeLineOpacity.get() * f));
		Renderer3D.box(box, fill, line, Renderer3D.ShapeMode.BOTH, false);
	}

	private void renderMining3D() {
		Renderer3D.lineWidth(1.5f);
		long now = System.currentTimeMillis();
		breaking.values().removeIf(b -> now - b.time > 10_000 || mc.level.getBlockState(b.pos).isAir());
		for (Map.Entry<Integer, Breaking> en : breaking.entrySet()) {
			if (!showMining(en.getKey(), en.getValue())) continue;
			Breaking b = en.getValue();
			double s = (b.stage + 1) / 10.0;
			Vec3 c = Vec3.atCenterOf(b.pos);
			AABB box = new AABB(c.x - s / 2, c.y - s / 2, c.z - s / 2, c.x + s / 2, c.y + s / 2, c.z + s / 2);
			Renderer3D.box(box, miningFill.argb(), miningLine.argb(), Renderer3D.ShapeMode.BOTH, true);
		}
	}

	private boolean showMining(int entityId, Breaking b) {
		if (entityId == mc.player.getId()) return false;
		if (mc.player.position().distanceTo(Vec3.atCenterOf(b.pos)) > miningRange.get()) return false;
		Entity miner = mc.level.getEntity(entityId);
		return !(miningIgnoreFriends.get() && miner instanceof Player p && Myriad.friends().isFriend(p));
	}

	// ---- 2D ---------------------------------------------------------------------------------------------------------

	@Subscribe
	private void onRender2D(Render2DEvent e) {
		if (!inGame()) return;
		Canvas c = e.canvas();
		if (entities.get() && mode.get() == Mode.BOX_2D) {
			long now = System.currentTimeMillis();
			for (Entity entity : mc.level.entitiesForRendering()) {
				if (skip(entity)) continue;
				int color = colorFor(entity);
				if (color == 0) continue;
				float[] r = project(Entities.lerpedBox(entity, e.tickDelta()));
				if (r == null) continue;
				drawBox2D(c, entity, r, scaleAlpha(color, fadeFor(entity, now)));
			}
			forgetUnseen();
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
			float hp = Mth.clamp(living.getHealth() / living.getMaxHealth(), 0, 1);
			float bx = x - 3.5f;
			c.rect(bx - 0.5f, y - 0.5f, 2, h + 1, 0x99000000);
			c.rect(bx, y + h * (1 - hp), 1, h * hp, ColorUtil.lerp(0xFFFF5555, 0xFF55FF55, hp));
		}
	}

	/** Screen rect [left, top, right, bottom] around a world box, or null when it's off screen. */
	private static float[] project(AABB b) {
		float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
		var window = mc.getWindow();
		float sw = window.getGuiScaledWidth(), sh = window.getGuiScaledHeight();
		boolean any = false;
		for (int i = 0; i < 8; i++) {
			Vec3 s = Projection.toScreen(new Vec3((i & 1) == 0 ? b.minX : b.maxX, (i & 2) == 0 ? b.minY : b.maxY, (i & 4) == 0 ? b.minZ : b.maxZ));
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
		for (Entity entity : mc.level.entitiesForRendering()) {
			if (!(entity instanceof ItemEntity item) || mc.player.distanceToSqr(entity) > maxSq) continue;
			List<ItemEntity> target = null;
			if (grouping.get()) {
				for (List<ItemEntity> g : groups) {
					if (g.getFirst().distanceToSqr(item) < 4) {
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
				AABB box = Entities.lerpedBox(it, tickDelta);
				x += box.getCenter().x;
				y += box.maxY;
				z += box.getCenter().z;
			}
			x /= g.size();
			y /= g.size();
			z /= g.size();
			Map<String, Integer> counts = new LinkedHashMap<>();
			for (ItemEntity it : g) counts.merge(it.getItem().getHoverName().getString(), it.getItem().getCount(), Integer::sum);
			int line = 0;
			for (Map.Entry<String, Integer> en : counts.entrySet()) {
				String text = en.getValue() > 1 ? en.getKey() + " x" + en.getValue() : en.getKey();
				Vec3 at = new Vec3(x, y + 0.3 + line++ * 0.28, z);
				WorldLabel.draw(c, at, itemNameSize.getFloat() * WorldLabel.distanceScale(at), List.of(new WorldLabel.Segment(text, color)),
					bg != 0 ? WorldLabel.Background.ROUNDED : WorldLabel.Background.NONE, bg, 0, true);
			}
		}
	}

	private void renderPearlOwners(Canvas c, float tickDelta) {
		for (Entity entity : mc.level.entitiesForRendering()) {
			if (!(entity instanceof ThrownEnderpearl pearl) || pearl.getOwner() == null) continue;
			AABB box = Entities.lerpedBox(entity, tickDelta);
			Vec3 at = new Vec3(box.getCenter().x, box.maxY + 0.25, box.getCenter().z);
			WorldLabel.draw(c, at, pearlNameSize.getFloat() * WorldLabel.distanceScale(at),
				List.of(new WorldLabel.Segment(pearl.getOwner().getScoreboardName(), pearlNameColor.argb())), WorldLabel.Background.ROUNDED, 0x64000000, 0, true);
		}
	}

	private void renderMiningLabels(Canvas c) {
		for (Map.Entry<Integer, Breaking> en : breaking.entrySet()) {
			if (!showMining(en.getKey(), en.getValue())) continue;
			List<WorldLabel.Segment> segs = new ArrayList<>();
			Entity miner = mc.level.getEntity(en.getKey());
			if (miningNames.get() && miner != null) segs.add(new WorldLabel.Segment(miner.getScoreboardName(), 0xFFFFFFFF));
			if (miningPercent.get()) segs.add(new WorldLabel.Segment((en.getValue().stage + 1) * 10 + "%", miningLine.argb()));
			if (segs.isEmpty()) continue;
			Vec3 at = Vec3.atCenterOf(en.getValue().pos).add(0, 0.7, 0);
			WorldLabel.draw(c, at, WorldLabel.distanceScale(at), segs, WorldLabel.Background.ROUNDED, 0x64000000, 0, true);
		}
	}

	// ---- ticking ----------------------------------------------------------------------------------------------------

	@Subscribe
	private void onPacket(PacketEvent.Receive e) {
		if (e.packet() instanceof ClientboundBlockDestructionPacket p) {
			if (p.getProgress() < 0 || p.getProgress() > 9) breaking.remove(p.getId());
			else breaking.put(p.getId(), new Breaking(p.getPos(), p.getProgress(), System.currentTimeMillis()));
		}
	}

	// ---- holes ------------------------------------------------------------------------------------------------------

	/** The holes whose floor is in {@code chunk}: every hole stands on a blast-proof block, so only those are visited. */
	private List<Hole> findHoles(LevelChunk chunk) {
		if (!holes.get()) return null;
		List<Hole> found = new ArrayList<>();
		Set<BlockPos> doubled = new HashSet<>();
		BlockScan.forEach(chunk, ESP::isBlastProof, (floor, state) -> {
			BlockPos p = floor.above();
			if (!isAirColumn(p)) return;
			int kind = enclosed(p, null);
			if (kind > 0) {
				addHole(found, new AABB(p), kind == 2);
				return;
			}
			if (!doubleHoles.get() || doubled.contains(p)) return;
			for (Direction d : HORIZONTAL_PARTNERS) {
				BlockPos q = p.relative(d);
				if (!isAirColumn(q)) continue;
				int a = enclosed(p, d), b = enclosed(q, d.getOpposite());
				if (a > 0 && b > 0) {
					doubled.add(p);
					doubled.add(q);
					addHole(found, new AABB(p).minmax(new AABB(q)), a == 2 && b == 2);
				}
			}
		});
		return found.isEmpty() ? null : found;
	}

	private static final Direction[] HORIZONTAL_PARTNERS = {Direction.EAST, Direction.SOUTH};
	private static final Direction[] HOLE_SIDES = {Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

	private static boolean isBlastProof(BlockState state) {
		Block b = state.getBlock();
		return b == Blocks.BEDROCK || b == Blocks.OBSIDIAN || b == Blocks.CRYING_OBSIDIAN || b == Blocks.RESPAWN_ANCHOR
			|| b == Blocks.ENDER_CHEST || b == Blocks.NETHERITE_BLOCK;
	}

	private void addHole(List<Hole> found, AABB box, boolean bedrockHole) {
		if (bedrockHole ? bedrock.get() : obsidian.get()) found.add(new Hole(box, bedrockHole));
	}

	private boolean isAirColumn(BlockPos p) {
		return mc.level.getBlockState(p).isAir() && mc.level.getBlockState(p.above()).isAir() && mc.level.getBlockState(p.above(2)).isAir();
	}

	/** 2 = all bedrock, 1 = blast-proof mix, 0 = not a hole. {@code open} is a side left open for a double hole. */
	private int enclosed(BlockPos p, Direction open) {
		boolean allBedrock = true;
		for (Direction d : HOLE_SIDES) {
			if (d == open) continue;
			BlockState s = mc.level.getBlockState(p.relative(d));
			if (!isBlastProof(s)) return 0;
			if (!s.is(Blocks.BEDROCK)) allBedrock = false;
		}
		return allBedrock ? 2 : 1;
	}
}
