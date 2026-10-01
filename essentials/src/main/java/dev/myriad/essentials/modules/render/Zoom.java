package dev.myriad.essentials.modules.render;

import dev.myriad.api.event.Subscribe;
import dev.myriad.api.event.events.MouseLookEvent;
import dev.myriad.api.event.events.MouseScrollEvent;
import dev.myriad.api.event.events.TickEvent;
import dev.myriad.api.module.Categories;
import dev.myriad.api.module.Module;
import dev.myriad.api.module.Modules;
import dev.myriad.api.setting.BoolSetting;
import dev.myriad.api.setting.DoubleSetting;
import net.minecraft.util.math.MathHelper;

/**
 * Zooms the camera by dividing the field of view, easing in and out. Mouse turning slows down to match, and you can
 * scroll to change the zoom while it's on. Works best as a hold bind. Applied by this addon's GameRendererMixin.
 */
public class Zoom extends Module {

	private final DoubleSetting factor = sgGeneral.doubleSetting("Factor").description("How much the field of view is divided.").defaultValue(4).range(1.5, 12).decimals(1).build();
	private final BoolSetting scroll = sgGeneral.bool("Scroll").description("Scroll to zoom further in or out while zoomed.").defaultValue(true).build();
	private final BoolSetting smooth = sgGeneral.bool("Smooth").description("Ease in and out of the zoom.").defaultValue(true).build();
	private final DoubleSetting speed = sgGeneral.doubleSetting("Speed").defaultValue(14).range(1, 30).decimals(0).visible(smooth::get).build();
	private final BoolSetting smoothCamera = sgGeneral.bool("Smooth Camera").description("Use Minecraft's cinematic camera while zoomed.").build();
	private final DoubleSetting smoothCameraSpeed = sgGeneral.doubleSetting("Smooth Camera Speed").description("Look speed multiplier with the smooth camera.").defaultValue(1)
		.range(0.1, 3).decimals(1).visible(smoothCamera::get).build();

	private float progress;
	private long lastUpdate;
	private double scrollFactor = 1;
	private Boolean previousSmoothCamera;

	public Zoom() {
		super(Categories.RENDER, "Zoom", "Zooms the camera in.");
	}

	@Override
	public String hudInfo() {
		return String.format("%.1fx", zoomFactor());
	}

	@Override
	protected void onEnable() {
		scrollFactor = 1;
		lastUpdate = System.currentTimeMillis();
		if (!smooth.get()) progress = 1;
		applySmoothCamera();
	}

	@Override
	protected void onDisable() {
		lastUpdate = System.currentTimeMillis();
		if (!smooth.get()) progress = 0;
		restoreSmoothCamera();
	}

	private double zoomFactor() {
		return Math.max(1, factor.get() * scrollFactor);
	}

	@Subscribe
	private void onTick(TickEvent.Post e) {
		applySmoothCamera();
	}

	@Subscribe
	private void onScroll(MouseScrollEvent e) {
		if (!scroll.get() || mc.currentScreen != null) return;
		scrollFactor = MathHelper.clamp(scrollFactor * (e.vertical() > 0 ? 1.2 : 1 / 1.2), 1 / factor.get(), 50 / factor.get());
		e.cancel();
	}

	@Subscribe
	private void onLook(MouseLookEvent e) {
		double s = MathHelper.lerp(update(), 1, 1 / zoomFactor());
		if (smoothCamera.get()) s *= smoothCameraSpeed.get();
		e.set(e.deltaX() * s, e.deltaY() * s);
	}

	/** Applies the zoom to a field of view; also eases out after the module is turned off. */
	public static float apply(float fov) {
		// Not Modules.active: the zoom keeps easing out for a moment after the module is turned off.
		Zoom m = Modules.get(Zoom.class);
		if (m == null) return fov;
		float p = m.update();
		if (p <= 0.001f) return fov;
		return (float) MathHelper.lerp(p, fov, Math.max(1, fov / m.zoomFactor()));
	}

	private float update() {
		float target = isEnabled() ? 1 : 0;
		long now = System.currentTimeMillis();
		if (!smooth.get()) {
			progress = target;
		} else {
			float dt = Math.min(0.1f, (now - lastUpdate) / 1000f);
			progress += (target - progress) * Math.min(1, dt * speed.getFloat());
			if (Math.abs(target - progress) < 0.001f) progress = target;
		}
		lastUpdate = now;
		return progress;
	}

	private void applySmoothCamera() {
		if (!smoothCamera.get()) {
			restoreSmoothCamera();
			return;
		}
		if (previousSmoothCamera == null) previousSmoothCamera = mc.options.smoothCameraEnabled;
		mc.options.smoothCameraEnabled = true;
	}

	private void restoreSmoothCamera() {
		if (previousSmoothCamera != null) mc.options.smoothCameraEnabled = previousSmoothCamera;
		previousSmoothCamera = null;
	}
}
