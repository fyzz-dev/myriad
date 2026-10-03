package dev.myriad.essentials.modules.misc;

import dev.myriad.api.event.Subscribe;
import dev.myriad.api.event.events.ScreenOpenEvent;
import dev.myriad.api.event.events.TickEvent;
import dev.myriad.api.event.events.WorldEvent;
import dev.myriad.api.module.Categories;
import dev.myriad.api.module.Module;
import dev.myriad.api.setting.DoubleSetting;
import dev.myriad.api.setting.IntSetting;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

/** Rejoins the last server after you're disconnected, up to a number of attempts. */
public class AutoReconnect extends Module {
	private final DoubleSetting delay = sgGeneral.doubleSetting("Delay").description("Seconds to wait on the disconnect screen.").defaultValue(5).range(0.5, 60).decimals(1).build();
	private final IntSetting attempts = sgGeneral.intSetting("Attempts").description("Give up after this many tries in a row. 0 = never.").defaultValue(0).range(0, 50).build();

	private ServerData lastServer;
	private long disconnectedAt = -1;
	private int attemptCount;

	public AutoReconnect() {
		super(Categories.MISC, "Auto Reconnect", "Rejoins the server after you get disconnected.");
	}

	@Override
	public String hudInfo() {
		return disconnectedAt < 0 ? null : String.format("%.1fs", remaining());
	}

	@Override
	protected void onDisable() {
		disconnectedAt = -1;
	}

	@Subscribe
	private void onJoin(WorldEvent.Join e) {
		ServerData info = mc.getCurrentServer();
		if (info != null) lastServer = info;
		attemptCount = 0;
		disconnectedAt = -1;
	}

	@Subscribe
	private void onScreen(ScreenOpenEvent e) {
		if (e.screen() instanceof DisconnectedScreen) {
			boolean canRetry = lastServer != null && (attempts.get() == 0 || attemptCount < attempts.get());
			disconnectedAt = canRetry ? System.currentTimeMillis() : -1;
		}
	}

	private double remaining() {
		return Math.max(0, delay.get() - (System.currentTimeMillis() - disconnectedAt) / 1000.0);
	}

	@Subscribe
	private void onTick(TickEvent.Post e) {
		if (disconnectedAt < 0) return;
		if (!(mc.gui.screen() instanceof DisconnectedScreen)) {
			disconnectedAt = -1;
			return;
		}
		if (remaining() > 0) return;
		disconnectedAt = -1;
		attemptCount++;
		ConnectScreen.startConnecting(new JoinMultiplayerScreen(new TitleScreen()), mc, ServerAddress.parseString(lastServer.ip), lastServer, false, null);
	}
}
