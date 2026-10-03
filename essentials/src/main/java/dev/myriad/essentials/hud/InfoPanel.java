package dev.myriad.essentials.hud;

import dev.myriad.api.Myriad;
import dev.myriad.api.setting.BoolSetting;
import dev.myriad.api.setting.EnumSetting;
import dev.myriad.api.ui.hud.HudStyle;
import dev.myriad.api.ui.hud.TextHudPanel;
import dev.myriad.api.util.ItemInfo;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import net.minecraft.world.level.Level;

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
		if (fps.get()) out.add("FPS", String.valueOf(mc.getFps()));
		if (ping.get() && mc.player != null) out.add("Ping", Myriad.server().ping() + "ms");
		if (speed.get() && mc.player != null) {
			double dx = mc.player.getX() - mc.player.xo, dz = mc.player.getZ() - mc.player.zo;
			double bps = Math.sqrt(dx * dx + dz * dz) * 20;
			out.add("Speed", unit.get() == SpeedUnit.KMH ? String.format("%.1f km/h", bps * 3.6) : String.format("%.1f b/s", bps));
		}
		if (tps.get()) out.add("TPS", String.format("%.1f", Myriad.server().tps()));
		if (server.get()) {
			var entry = mc.getCurrentServer();
			out.add("Server", entry == null ? (mc.isLocalServer() ? "Singleplayer" : "-") : entry.ip);
		}
		if (dimension.get() && mc.level != null) {
			var key = mc.level.dimension();
			out.add("Dimension", key == Level.NETHER ? "Nether" : key == Level.END ? "End" : key == Level.OVERWORLD ? "Overworld" : key.identifier().getPath());
		}
		if (time.get()) out.add("Time", LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
		if (durability.get() && mc.player != null && mc.player.getMainHandItem().isDamageableItem()) {
			out.add("Durability", String.valueOf(ItemInfo.durability(mc.player.getMainHandItem())));
		}
	}
}
