package dev.myriad.essentials;

import dev.myriad.api.addon.AddonContext;
import dev.myriad.api.addon.MyriadAddon;
import dev.myriad.essentials.hud.ArmorPanel;
import dev.myriad.essentials.hud.OffhandPanel;
import dev.myriad.essentials.modules.misc.MiddleClick;
import dev.myriad.essentials.modules.player.AntiAFK;
import dev.myriad.essentials.modules.player.SpeedMine;
import dev.myriad.essentials.modules.player.XCarry;
import dev.myriad.essentials.modules.render.LogoutSpots;
import dev.myriad.essentials.modules.render.NameProtect;
import dev.myriad.essentials.modules.render.Nametags;
import dev.myriad.essentials.modules.render.ShulkerPreview;
import dev.myriad.essentials.modules.render.Swing;
import dev.myriad.essentials.modules.render.Zoom;
import dev.myriad.essentials.hud.CoordinatesPanel;
import dev.myriad.essentials.hud.InfoPanel;
import dev.myriad.essentials.hud.ModuleListPanel;
import dev.myriad.essentials.hud.WatermarkPanel;
import dev.myriad.essentials.modules.combat.AutoTotem;
import dev.myriad.essentials.modules.movement.ChestSwap;
import dev.myriad.essentials.modules.combat.Criticals;
import dev.myriad.essentials.modules.combat.KillAura;
import dev.myriad.essentials.modules.misc.AutoReconnect;
import dev.myriad.essentials.modules.movement.ElytraBounce;
import dev.myriad.essentials.modules.movement.NoSlow;
import dev.myriad.essentials.modules.movement.Sprint;
import dev.myriad.essentials.modules.movement.Scaffold;
import dev.myriad.essentials.modules.movement.Step;
import dev.myriad.essentials.modules.movement.Velocity;
import dev.myriad.essentials.modules.player.AirPlace;
import dev.myriad.essentials.modules.player.AutoEat;
import dev.myriad.essentials.modules.player.FastUse;
import dev.myriad.essentials.modules.render.Chams;
import dev.myriad.essentials.modules.render.ESP;
import dev.myriad.essentials.modules.render.FreeLook;
import dev.myriad.essentials.modules.render.Freecam;
import dev.myriad.essentials.modules.render.Fullbright;
import dev.myriad.essentials.modules.render.NoRender;
import dev.myriad.essentials.modules.render.Storage;
import dev.myriad.essentials.modules.render.Tracers;
import dev.myriad.essentials.modules.render.ViewModel;

import static dev.myriad.api.ui.PanelType.Anchor.TOP_LEFT;
import static dev.myriad.api.ui.PanelType.Anchor.TOP_RIGHT;
import static dev.myriad.api.ui.PanelType.Anchor.BOTTOM_LEFT;

/**
 * The stock module set. It is an ordinary Myriad addon: it uses nothing but the public API, so removing this jar
 * leaves Myriad fully working with no modules.
 */
public final class Essentials implements MyriadAddon {
	@Override
	public void initialize(AddonContext ctx) {
		ctx.registerModules(
			// Combat
			new KillAura(), new AutoTotem(), new Criticals(), new ChestSwap(),
			// Movement
			new Sprint(), new Velocity(), new NoSlow(), new Step(), new Scaffold(), new ElytraBounce(),
			// Player
			new FastUse(), new AutoEat(), new AirPlace(), new SpeedMine(), new XCarry(), new AntiAFK(),
			// Render
			new ESP(), new Storage(), new Tracers(), new Fullbright(), new NoRender(), new Chams(), new Freecam(), new FreeLook(), new ViewModel(),
			new LogoutSpots(), new ShulkerPreview(), new Zoom(), new Nametags(), new NameProtect(), new Swing(),
			// Misc
			new AutoReconnect(), new MiddleClick()
		);

		// HUD elements. The ones with a position are placed on a fresh install; the rest are added from the HUD workspace.
		ctx.registerHud("Watermark", "\uf02b", WatermarkPanel::new, TOP_LEFT, 0, 0);
		ctx.registerHud("Module List", "\uf03a", ModuleListPanel::new, TOP_RIGHT, 0, 0);
		ctx.registerHud("Coordinates", "\uf124", CoordinatesPanel::new, BOTTOM_LEFT, 0, 0);
		ctx.registerHud("Info", "\uf05a", InfoPanel::new, BOTTOM_LEFT, 0, -12);
		ctx.registerHud("Armor", "\uf132", ArmorPanel::new);
		ctx.registerHud("Off Hand", "\uf256", OffhandPanel::new);
	}
}
