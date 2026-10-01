package dev.myriad.essentials.hud;

import dev.myriad.api.Myriad;
import dev.myriad.api.setting.BoolSetting;
import dev.myriad.api.setting.EnumSetting;
import dev.myriad.api.ui.hud.HudStyle;
import dev.myriad.api.ui.hud.TextHudPanel;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.world.World;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** FPS, ping, speed, TPS, server and more, one "label value" pair per line. */
public final class InfoPanel extends TextHudPanel {
	public enum SpeedUnit {
		BLOCKS_PER_SECOND, KMH
	}

	private final BoolSetting fps = sgGeneral.bool("FPS").defaultValue(true).build();
	private final BoolSetting ping = sgGeneral.bool("Ping").defaultValue(true).build();
	private final BoolSetting speed = sgGeneral.bool("Speed").defaultValue(true).build();
	private final EnumSetting<SpeedUnit> unit = sgGeneral.enumSetting("Speed Unit", SpeedUnit.BLOCKS_PER_SECOND).visible(speed::get).build();
	private final BoolSetting tps = sgGeneral.bool("TPS").description("The server's ticks per second.").build();
	private final BoolSetting server = sgGeneral.bool("Server").build();
	private final BoolSetting dimension = sgGeneral.bool("Dimension").build();
	private final BoolSetting time = sgGeneral.bool("Time").description("Your local time.").build();
	private final BoolSetting durability = sgGeneral.bool("Durability").description("Durability left on the held item.").build();

	public InfoPanel() {
		super("Info", "", HudStyle.Mode.TEXT);
	}

	@Override
	protected void lines(Lines out) {
		if (fps.get()) out.add("FPS", String.valueOf(mc.getCurrentFps()));
		if (ping.get() && mc.player != null && mc.getNetworkHandler() != null) {
			PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
			out.add("Ping", (e == null ? 0 : e.getLatency()) + "ms");
		}
		if (speed.get() && mc.player != null) {
			double dx = mc.player.getX() - mc.player.prevX, dz = mc.player.getZ() - mc.player.prevZ;
			double bps = Math.sqrt(dx * dx + dz * dz) * 20;
			out.add("Speed", unit.get() == SpeedUnit.KMH ? String.format("%.1f km/h", bps * 3.6) : String.format("%.1f b/s", bps));
		}
		if (tps.get()) out.add("TPS", String.format("%.1f", Myriad.server().tps()));
		if (server.get()) {
			var entry = mc.getCurrentServerEntry();
			out.add("Server", entry == null ? (mc.isInSingleplayer() ? "Singleplayer" : "-") : entry.address);
		}
		if (dimension.get() && mc.world != null) {
			var key = mc.world.getRegistryKey();
			out.add("Dimension", key == World.NETHER ? "Nether" : key == World.END ? "End" : key == World.OVERWORLD ? "Overworld" : key.getValue().getPath());
		}
		if (time.get()) out.add("Time", LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
		if (durability.get() && mc.player != null && mc.player.getMainHandStack().isDamageable()) {
			var s = mc.player.getMainHandStack();
			out.add("Durability", String.valueOf(s.getMaxDamage() - s.getDamage()));
		}
	}
}
