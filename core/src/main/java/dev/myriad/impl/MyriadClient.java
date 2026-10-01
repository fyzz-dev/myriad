package dev.myriad.impl;

import dev.myriad.api.event.events.GameReadyEvent;
import dev.myriad.api.event.events.Render2DEvent;
import dev.myriad.api.event.events.ScreenOpenEvent;
import dev.myriad.api.event.events.ShutdownEvent;
import dev.myriad.api.event.events.TickEvent;
import dev.myriad.api.event.events.WorldEvent;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;

/** Fabric entrypoint: boots Myriad and bridges Fabric API callbacks onto the Myriad event bus. */
public class MyriadClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		MyriadImpl myriad = MyriadImpl.bootstrap();
		var bus = myriad.events();

		ClientTickEvents.START_CLIENT_TICK.register(mc -> bus.post(TickEvent.Pre.INSTANCE));
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			bus.post(TickEvent.Post.INSTANCE);
			myriad.windowManager().tick();
			myriad.configImpl().tick();
		});

		HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
			if (bus.hasListeners(Render2DEvent.class)) {
				myriad.windowManager().draw(ctx, canvas -> bus.post(new Render2DEvent(ctx, canvas, tickCounter.getTickDelta(false))));
			}
			myriad.windowManager().renderHud(ctx);
		});

		// Render3DEvent is posted from GameRendererMixin, right after the world renders.

		ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> bus.post(WorldEvent.Join.INSTANCE));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> bus.post(WorldEvent.Leave.INSTANCE));

		ClientLifecycleEvents.CLIENT_STARTED.register(mc -> bus.post(GameReadyEvent.INSTANCE));

		// Dev convenience: -Dmyriad.openDesktop=true opens the desktop the first time the title screen appears.
		if (Boolean.getBoolean("myriad.openDesktop")) {
			boolean[] done = {false};
			bus.listen(ScreenOpenEvent.class, e -> {
				if (!done[0] && e.screen() instanceof TitleScreen) {
					done[0] = true;
					MinecraftClient.getInstance().send(() -> myriad.windowManager().open());
				}
			});
		}
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> {
			bus.post(ShutdownEvent.INSTANCE);
			myriad.configImpl().shutdown();
		});
	}
}
