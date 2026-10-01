package dev.myriad.essentials.modules.render;

import dev.myriad.api.event.Priority;
import dev.myriad.api.event.Subscribe;
import dev.myriad.api.event.events.PacketEvent;
import dev.myriad.api.event.events.Render2DEvent;
import dev.myriad.api.event.events.Render3DEvent;
import dev.myriad.api.event.events.TickEvent;
import dev.myriad.api.event.events.WorldEvent;
import dev.myriad.api.module.Categories;
import dev.myriad.api.module.Module;
import dev.myriad.api.render.Renderer3D;
import dev.myriad.api.setting.BoolSetting;
import dev.myriad.api.setting.ColorSetting;
import dev.myriad.api.setting.DoubleSetting;
import dev.myriad.api.setting.EnumSetting;
import dev.myriad.api.setting.SettingColor;
import dev.myriad.api.setting.SettingGroup;
import dev.myriad.api.Myriad;
import dev.myriad.api.service.ServerStats;
import dev.myriad.api.render.WorldLabel;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Marks where players logged out: a frozen copy of their model and/or their hitbox, with a label showing their
 * name, how long ago they left and how many totems they had popped. Optionally tells you when they come back.
 */
public class LogoutSpots extends Module {
	public enum Mode {
		FILL, OUTLINE, BOTH
	}

	private final BoolSetting renderModel = sgGeneral.bool("Render Model").description("Draw a frozen copy of the player's model.").defaultValue(true).build();
	private final EnumSetting<Mode> mode = sgGeneral.enumSetting("Box", Mode.BOTH).description("How to draw the hitbox.").build();
	private final ColorSetting fillColor = sgGeneral.color("Fill Color").defaultValue(SettingColor.role(SettingColor.Mode.ACCENT, 30)).visible(() -> mode.get() != Mode.OUTLINE).build();
	private final ColorSetting outlineColor = sgGeneral.color("Outline Color").defaultValue(SettingColor.role(SettingColor.Mode.ACCENT)).visible(() -> mode.get() != Mode.FILL).build();
	private final DoubleSetting lineWidth = sgGeneral.doubleSetting("Line Width").defaultValue(1.5).range(0.5, 5).decimals(1).visible(() -> mode.get() != Mode.FILL).build();
	private final BoolSetting unlimited = sgGeneral.bool("Unlimited").description("Show spots at any distance.").defaultValue(true).build();
	private final DoubleSetting distance = sgGeneral.doubleSetting("Distance").defaultValue(100).range(1, 500).decimals(0).visible(() -> !unlimited.get()).build();
	private final BoolSetting ignoreNaked = sgGeneral.bool("Ignore Naked").description("Skip players with no armour.").build();

	private final SettingGroup sgLabel = settings.group("Label");
	private final BoolSetting label = sgLabel.bool("Label").defaultValue(true).build();
	private final BoolSetting totemPops = sgLabel.bool("Totem Pops").description("Show how many totems they popped before leaving.").build();
	private final DoubleSetting scale = sgLabel.doubleSetting("Scale").defaultValue(1).range(0.3, 3).decimals(1).build();
	private final ColorSetting background = sgLabel.color("Background").defaultValue(0x64000000).build();

	private final SettingGroup sgNotify = settings.group("Notify");
	private final BoolSetting notifyRejoin = sgNotify.bool("Login Notifier").description("Tell you when a logged-out player comes back.").defaultValue(true).build();
	private final BoolSetting showCoords = sgNotify.bool("Show Coords").description("Include where they logged out.").defaultValue(true).visible(notifyRejoin::get).build();

	private final Map<UUID, Ghost> ghosts = new ConcurrentHashMap<>();
	/** Players seen in the world during the last few ticks. */
	private final Map<UUID, Seen> seen = new ConcurrentHashMap<>();

	private record Seen(PlayerEntity player, long time) {
	}

	private record Ghost(UUID id, String name, PlayerEntity player, Vec3d pos, Box box, RegistryKey<World> dimension, long time, int pops) {
	}

	public LogoutSpots() {
		super(Categories.RENDER, "Logout Spots", "Marks where players logged out.");
	}

	@Override
	protected void onEnable() {
		ghosts.clear();
		seen.clear();
	}

	@Override
	public String hudInfo() {
		return inGame() ? String.valueOf(ghosts.values().stream().filter(this::shown).count()) : null;
	}

	@Subscribe
	private void onWorld(WorldEvent e) {
		ghosts.clear();
		seen.clear();
	}

	@Subscribe(priority = Priority.LOWEST)
	private void onTick(TickEvent.Pre e) {
		if (!inGame()) return;
		long now = System.currentTimeMillis();
		seen.values().removeIf(s -> now - s.time > 150);
		for (PlayerEntity p : mc.world.getPlayers()) {
			if (p == mc.player || (ignoreNaked.get() && naked(p))) continue;
			seen.put(p.getUuid(), new Seen(p, now));
		}
	}

	@Subscribe
	private void onPacket(PacketEvent.Receive e) {
		if (e.packet() instanceof PlayerRemoveS2CPacket p) {
			List<UUID> ids = new ArrayList<>(p.profileIds());
			mc.execute(() -> ids.forEach(this::left));
		} else if (e.packet() instanceof PlayerListS2CPacket p && p.getActions().contains(PlayerListS2CPacket.Action.ADD_PLAYER)) {
			List<PlayerListS2CPacket.Entry> entries = new ArrayList<>(p.getPlayerAdditionEntries());
			mc.execute(() -> entries.forEach(entry -> joined(entry.profileId())));
		}
	}

	private void left(UUID id) {
		Seen s = seen.get(id);
		if (s == null || ghosts.containsKey(id) || mc.world == null) return;
		PlayerEntity p = s.player;
		ghosts.put(id, new Ghost(id, p.getGameProfile().getName(), p, p.getPos(), p.getBoundingBox(), mc.world.getRegistryKey(), System.currentTimeMillis(), Myriad.server().totemPops(p)));
	}

	private void joined(UUID id) {
		if (id == null) return;
		Ghost g = ghosts.remove(id);
		if (g == null || !notifyRejoin.get()) return;
		String msg = g.name + " logged back in";
		if (showCoords.get()) msg += String.format(" at %.0f, %.0f, %.0f", g.pos.x, g.pos.y, g.pos.z);
		info(msg);
	}

	private boolean shown(Ghost g) {
		if (mc.world == null || g.dimension != mc.world.getRegistryKey()) return false;
		return unlimited.get() || mc.player.getPos().distanceTo(g.pos) <= distance.get();
	}

	private static boolean naked(PlayerEntity p) {
		for (ItemStack s : p.getArmorItems()) if (!s.isEmpty()) return false;
		return true;
	}

	@Subscribe
	private void onRender3D(Render3DEvent e) {
		if (!inGame()) return;
		MatrixStack matrices = e.matrices();
		Vec3d cam = e.camera().getPos();
		VertexConsumerProvider.Immediate vcp = renderModel.get() ? mc.getBufferBuilders().getEntityVertexConsumers() : null;
		Renderer3D.lineWidth(lineWidth.getFloat());
		for (Ghost g : ghosts.values()) {
			if (!shown(g)) continue;
			if (vcp != null) renderModel(g, matrices, cam, vcp);
			Renderer3D.box(g.box, fillColor.argb(), outlineColor.argb(), switch (mode.get()) {
				case FILL -> Renderer3D.ShapeMode.FILL;
				case OUTLINE -> Renderer3D.ShapeMode.LINES;
				case BOTH -> Renderer3D.ShapeMode.BOTH;
			}, false);
		}
		if (vcp != null) vcp.draw();
	}

	private void renderModel(Ghost g, MatrixStack matrices, Vec3d cam, VertexConsumerProvider vcp) {
		PlayerEntity p = g.player;
		// Freeze the copy where it logged out: no interpolation, no death or hurt animation.
		p.setPosition(g.pos);
		p.lastRenderX = p.prevX = g.pos.x;
		p.lastRenderY = p.prevY = g.pos.y;
		p.lastRenderZ = p.prevZ = g.pos.z;
		p.hurtTime = 0;
		p.deathTime = 0;
		if (p.getPose() == EntityPose.DYING) p.setPose(EntityPose.STANDING);
		try {
			mc.getEntityRenderDispatcher().render(p, g.pos.x - cam.x, g.pos.y - cam.y, g.pos.z - cam.z, 1f, matrices, vcp, LightmapTextureManager.MAX_LIGHT_COORDINATE);
		} catch (RuntimeException ignored) {
			// A renderer that can't draw a detached entity just skips the model.
		}
	}

	@Subscribe
	private void onRender2D(Render2DEvent e) {
		if (!inGame() || !label.get()) return;
		for (Ghost g : ghosts.values()) {
			if (!shown(g)) continue;
			Vec3d top = new Vec3d(g.pos.x, g.box.maxY + 0.5, g.pos.z);
			List<WorldLabel.Segment> segs = new ArrayList<>();
			segs.add(new WorldLabel.Segment(g.name, 0xFFFFFFFF));
			segs.add(new WorldLabel.Segment(ago(g.time), 0xFFAAAAAA));
			if (totemPops.get() && g.pops > 0) segs.add(new WorldLabel.Segment("-" + g.pops, ServerStats.popColor(g.pops)));
			WorldLabel.draw(e.canvas(), top, scale.getFloat() * WorldLabel.distanceScale(top), segs, WorldLabel.Background.ROUNDED, background.argb(), 0, true);
		}
	}

	private static String ago(long time) {
		int s = (int) ((System.currentTimeMillis() - time) / 1000);
		int h = s / 3600, m = s % 3600 / 60;
		if (h > 0) return String.format("%dh %02dm", h, m);
		if (m > 0) return String.format("%dm %02ds", m, s % 60);
		return s + "s";
	}
}
