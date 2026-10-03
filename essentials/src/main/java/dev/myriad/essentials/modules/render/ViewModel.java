package dev.myriad.essentials.modules.render;

import dev.myriad.api.module.Categories;
import dev.myriad.api.module.Module;
import dev.myriad.api.module.Modules;
import dev.myriad.api.setting.BoolSetting;
import dev.myriad.api.setting.DoubleSetting;
import dev.myriad.api.setting.IntSetting;
import dev.myriad.api.setting.SettingGroup;

/**
 * First-person hand options: move, rotate and scale the held item and arm, stop the camera sway, render at full
 * brightness, or hide either hand. Applied by this addon's HeldItemRendererMixin.
 */
public class ViewModel extends Module {

	public final BoolSetting brighten = sgGeneral.bool("Brighten").description("Draw the hands at full brightness.").build();
	public final BoolSetting noSway = sgGeneral.bool("No Sway").description("Stop the hands lagging behind camera movement.").build();
	public final BoolSetting hideMain = sgGeneral.bool("Hide Main Hand").build();
	public final BoolSetting hideOff = sgGeneral.bool("Hide Offhand").description("Handy for big shields and totems.").build();
	public final BoolSetting noSwitch = sgGeneral.bool("No Switch Animation").description("Show a new item straight away instead of lowering and raising the hand.").build();

	private final SettingGroup sgPosition = settings.group("Position");
	public final DoubleSetting x = sgPosition.doubleSetting("X").description("Sideways; mirrored for the other hand.").defaultValue(0).range(-2, 2).build();
	public final DoubleSetting y = sgPosition.doubleSetting("Y").defaultValue(0).range(-2, 2).build();
	public final DoubleSetting z = sgPosition.doubleSetting("Z").defaultValue(0).range(-2, 2).build();

	private final SettingGroup sgRotation = settings.group("Rotation");
	public final IntSetting rotX = sgRotation.intSetting("Pitch").defaultValue(0).range(-180, 180).build();
	public final IntSetting rotY = sgRotation.intSetting("Yaw").description("Mirrored for the other hand.").defaultValue(0).range(-180, 180).build();
	public final IntSetting rotZ = sgRotation.intSetting("Roll").description("Mirrored for the other hand.").defaultValue(0).range(-180, 180).build();

	private final SettingGroup sgScale = settings.group("Scale");
	public final DoubleSetting scale = sgScale.doubleSetting("Scale").defaultValue(1).range(0.1, 3).build();
	public final DoubleSetting offhandScale = sgScale.doubleSetting("Offhand Scale").defaultValue(1).range(0.1, 3).build();

	public ViewModel() {
		super(Categories.RENDER, "View Model", "Move, rotate, scale or hide your first-person hands.");
	}

	/** Whether the hand should skip the lower-and-raise animation when the held item changes. */
	public static boolean noSwitchAnimation() {
		ViewModel vm = Modules.active(ViewModel.class);
		return vm != null && vm.noSwitch.get();
	}

}
