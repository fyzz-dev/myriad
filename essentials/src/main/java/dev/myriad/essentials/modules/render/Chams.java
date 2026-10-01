package dev.myriad.essentials.modules.render;

import dev.myriad.api.module.Categories;
import dev.myriad.api.module.Module;
import dev.myriad.api.module.Modules;
import dev.myriad.api.setting.BoolSetting;
import dev.myriad.api.setting.ColorSetting;
import dev.myriad.api.setting.DoubleSetting;
import dev.myriad.api.setting.SettingColor;
import dev.myriad.api.setting.SettingGroup;
import dev.myriad.api.util.Entities;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

/**
 * Colours entity models: a flat translucent fill (optionally visible through walls, over or instead of the normal
 * texture) and/or the vanilla glowing outline in a theme colour. Applied by this addon's render mixins.
 */
public class Chams extends Module {

	private final SettingGroup sgTargets = settings.group("Targets");
	private final BoolSetting players = sgTargets.bool("Players").defaultValue(true).build();
	private final BoolSetting hostiles = sgTargets.bool("Hostiles").build();
	private final BoolSetting passives = sgTargets.bool("Passives").build();
	private final BoolSetting self = sgTargets.bool("Self").description("Also colour yourself (visible in third person).").build();
	private final BoolSetting friends = sgTargets.bool("Friends").description("Also colour friends.").defaultValue(true).build();
	private final DoubleSetting range = sgTargets.doubleSetting("Range").defaultValue(64).range(4, 256).decimals(0).build();

	private final SettingGroup sgFill = settings.group("Fill");
	private final BoolSetting fill = sgFill.bool("Fill").description("Draw the model in a flat colour.").defaultValue(true).build();
	private final BoolSetting throughWalls = sgFill.bool("Through Walls").defaultValue(true).visible(fill::get).build();
	private final BoolSetting texture = sgFill.bool("Keep Texture").description("Draw the normal model too, under the fill.").defaultValue(true).visible(fill::get).build();
	private final ColorSetting fillColor = sgFill.color("Color").defaultValue(SettingColor.role(SettingColor.Mode.ACCENT, 90)).visible(fill::get).build();
	private final ColorSetting friendColor = sgFill.color("Friend Color").defaultValue(SettingColor.role(SettingColor.Mode.CYAN, 90)).visible(fill::get).build();

	private final SettingGroup sgGlow = settings.group("Glow");
	private final BoolSetting glow = sgGlow.bool("Glow").description("Vanilla glowing outline, visible through walls.").build();
	private final ColorSetting glowColor = sgGlow.color("Glow Color").defaultValue(SettingColor.role(SettingColor.Mode.ACCENT)).visible(glow::get).build();

	private Entity current;

	public Chams() {
		super(Categories.RENDER, "Chams", "Colour entity models, optionally through walls.");
	}

	/** Whether chams apply to this entity right now. */
	public boolean appliesTo(Entity e) {
		if (!(e instanceof LivingEntity) || !e.isAlive()) return false;
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null) return false;
		if (e == mc.player) return self.get();
		if (mc.player.squaredDistanceTo(e) > range.get() * range.get()) return false;
		if (Entities.isFriend(e)) {
			current = e;
			return friends.get();
		}
		current = e;
		return switch (Entities.kind(e)) {
			case PLAYER -> players.get();
			case HOSTILE -> hostiles.get();
			case PASSIVE -> passives.get();
			case OTHER -> false;
		};
	}

	public boolean glowsFor(Entity e) {
		return isEnabled() && glow.get() && appliesTo(e);
	}

	public boolean fill() {
		return fill.get();
	}

	public boolean throughWalls() {
		return throughWalls.get();
	}

	public boolean keepTexture() {
		return !fill.get() || texture.get();
	}

	/** Fill colour for the entity last passed to {@link #appliesTo}. */
	public int fillColor() {
		return current != null && Entities.isFriend(current) ? friendColor.argb() : fillColor.argb();
	}

	public int glowColor() {
		return glowColor.argb();
	}
}
