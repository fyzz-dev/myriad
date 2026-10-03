package dev.myriad.impl;

import dev.myriad.api.event.events.GameReadyEvent;
import dev.myriad.api.event.events.Render2DEvent;
import dev.myriad.api.event.events.Render3DEvent;
import dev.myriad.api.render.Projection;
import dev.myriad.impl.render.MeshRenderer;
import dev.myriad.impl.render.MyriadPipelines;
import dev.myriad.impl.render.WorldRenderQueue;
import dev.myriad.api.event.events.ScreenOpenEvent;
import dev.myriad.api.event.events.ShutdownEvent;
import dev.myriad.api.event.events.TickEvent;
import dev.myriad.api.event.events.WorldEvent;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.Identifier;

/** Fabric entrypoint: boots Myriad and bridges Fabric API callbacks onto the Myriad event bus. */
public class MyriadClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		MyriadImpl myriad = MyriadImpl.bootstrap();
		MyriadPipelines.init();
		var bus = myriad.events();

		ClientTickEvents.START_CLIENT_TICK.register(mc -> bus.post(TickEvent.Pre.INSTANCE));
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			bus.post(TickEvent.Post.INSTANCE);
			myriad.windowManager().tick();
			myriad.configImpl().tick();
		});

		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("myriad", "hud"), (ctx, tickCounter) -> {
			if (bus.hasListeners(Render2DEvent.class)) {
				myriad.windowManager().draw(ctx, canvas -> bus.post(new Render2DEvent(ctx, canvas, tickCounter.getGameTimeDeltaPartialTick(false))));
			}
			myriad.windowManager().renderHud(ctx);
		});

		// The camera is settled once the level is extracted, before the HUD is: labels projected from the HUD use it.
		LevelExtractionEvents.END_EXTRACTION.register(ctx -> {
			var camera = ctx.levelState().cameraRenderState;
			Projection.update(camera.viewRotationMatrix, camera.projectionMatrix, camera.pos);
		});
		LevelRenderEvents.COLLECT_SUBMITS.register(ctx -> {
			Minecraft mc = Minecraft.getInstance();
			var camera = mc.gameRenderer.mainCamera();
			WorldRenderQueue.INSTANCE.begin(ctx.levelState().cameraRenderState.pos);
			if (bus.hasListeners(Render3DEvent.class)) {
				// Same tick delta the entities were drawn with this frame.
				float tickDelta = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
				bus.post(new Render3DEvent(ctx.poseStack(), camera, tickDelta, ctx.submitNodeCollector()));
			}
			WorldRenderQueue.INSTANCE.submit(ctx.submitNodeCollector(), ctx.poseStack(), camera);
		});

		// Retained meshes (WorldMesh, ChunkCache) draw straight after the per-frame shapes, in the same place in the frame.
		LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(ctx -> MeshRenderer.draw(ctx.levelState().cameraRenderState));

		ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> bus.post(WorldEvent.Join.INSTANCE));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> bus.post(WorldEvent.Leave.INSTANCE));

		ClientLifecycleEvents.CLIENT_STARTED.register(mc -> bus.post(GameReadyEvent.INSTANCE));

		// Dev convenience: -Dmyriad.openDesktop=true opens the desktop the first time the title screen appears.
		if (Boolean.getBoolean("myriad.openDesktop")) {
			boolean[] done = {false};
			bus.listen(ScreenOpenEvent.class, e -> {
				if (!done[0] && e.screen() instanceof TitleScreen) {
					done[0] = true;
					Minecraft.getInstance().schedule(() -> myriad.windowManager().open());
				}
			});
		}
		// Dev convenience: -Dmyriad.screenshotEvery=N saves a screenshot every N seconds (screenshots/myriad-dev-*.png),
		// to check rendering without touching the window.
		int screenshotEvery = Integer.getInteger("myriad.screenshotEvery", 0);
		if (screenshotEvery > 0) {
			int[] ticks = {0, 0};
			ClientTickEvents.END_CLIENT_TICK.register(mc -> {
				if (++ticks[0] % (screenshotEvery * 20) != 0) return;
				Screenshot.grab(mc.gameDirectory, "myriad-dev-" + ticks[1]++ + ".png", mc.gameRenderer.mainRenderTarget(), 1, message -> {
				});
			});
		}
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> {
			bus.post(ShutdownEvent.INSTANCE);
			myriad.configImpl().shutdown();
		});
	}
}
