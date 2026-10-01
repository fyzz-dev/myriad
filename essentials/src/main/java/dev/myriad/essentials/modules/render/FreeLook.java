package dev.myriad.essentials.modules.render;

import dev.myriad.api.event.Subscribe;
import dev.myriad.api.event.events.CameraEvent;
import dev.myriad.api.event.events.MouseLookEvent;
import dev.myriad.api.module.Categories;
import dev.myriad.api.module.Module;
import dev.myriad.api.setting.BoolSetting;
import dev.myriad.api.setting.EnumSetting;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.MathHelper;

/**
 * Look around without turning: the camera orbits in third person while your player keeps facing (and moving) the
 * same way. Works well as a hold bind.
 */
public class FreeLook extends Module {
	public enum View {
		BEHIND, FRONT
	}

	private final EnumSetting<View> view = sgGeneral.enumSetting("View", View.BEHIND).description("Which third-person view to use.").build();
	private final BoolSetting snapBack = sgGeneral.bool("Snap Back").description("Return to your previous perspective when turned off.").defaultValue(true).build();

	private float yaw, pitch;
	private Perspective previous;

	public FreeLook() {
		super(Categories.RENDER, "Free Look", "Orbit the camera without turning your player.");
	}

	@Override
	protected void onEnable() {
		if (!inGame()) return;
		yaw = mc.player.getYaw();
		pitch = mc.player.getPitch();
		previous = mc.options.getPerspective();
		mc.options.setPerspective(view.get() == View.FRONT ? Perspective.THIRD_PERSON_FRONT : Perspective.THIRD_PERSON_BACK);
	}

	@Override
	protected void onDisable() {
		if (snapBack.get() && previous != null) mc.options.setPerspective(previous);
		previous = null;
	}

	@Subscribe
	private void onMouse(MouseLookEvent e) {
		e.cancel();
		yaw += (float) (e.deltaX() * 0.15);
		pitch = MathHelper.clamp(pitch + (float) (e.deltaY() * 0.15), -90, 90);
	}

	@Subscribe
	private void onCamera(CameraEvent.Rotation e) {
		// Front view looks back at the player, so flip the orbit.
		e.yaw = view.get() == View.FRONT ? yaw + 180 : yaw;
		e.pitch = view.get() == View.FRONT ? -pitch : pitch;
	}
}
