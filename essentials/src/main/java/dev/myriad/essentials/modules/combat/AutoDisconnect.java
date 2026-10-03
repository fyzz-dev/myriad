package dev.myriad.essentials.modules.combat;

import dev.myriad.api.Myriad;
import dev.myriad.api.event.Subscribe;
import dev.myriad.api.event.events.TickEvent;
import dev.myriad.api.module.Categories;
import dev.myriad.api.module.Module;
import dev.myriad.api.setting.BoolSetting;
import dev.myriad.api.setting.DoubleSetting;
import dev.myriad.api.setting.IntSetting;
import dev.myriad.api.setting.SettingGroup;
import dev.myriad.essentials.util.Threats;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * Leaves the server before you die: at low health, after too many totem pops or with too few totems left, when a
 * player you haven't friended comes close, or when a bed, respawn anchor, end crystal or creeper nearby could kill you.
 * The disconnect screen says why. It turns itself off afterwards (Auto Disable), so you can rejoin without leaving again
 * straight away. Singleplayer is left alone.
 */
public class AutoDisconnect extends Module {
	private final SettingGroup sgHealth = settings.group("Health");
	private final BoolSetting health = sgHealth.bool("Health").description("Leave at low health (absorption included).").defaultValue(true).build();
	private final DoubleSetting healthThreshold = sgHealth.doubleSetting("Threshold").defaultValue(6).range(1, 36).decimals(1).visible(health::get).build();
	private final BoolSetting pops = sgHealth.bool("Totem Pops").description("Leave after this many totems pop in one life.").build();
	private final IntSetting popCount = sgHealth.intSetting("Pops").defaultValue(3).range(1, 20).visible(pops::get).build();
	private final BoolSetting totems = sgHealth.bool("Totems Left").description("Leave when you have this many totems or fewer.").build();
	private final IntSetting totemCount = sgHealth.intSetting("Totems").defaultValue(0).range(0, 20).visible(totems::get).build();

	private final SettingGroup sgDanger = settings.group("Danger");
	private final BoolSetting players = sgDanger.bool("Players").description("Leave when a player who isn't a friend comes close.").build();
	private final DoubleSetting playerRange = sgDanger.doubleSetting("Player Range").defaultValue(16).range(1, 128).decimals(0).visible(players::get).build();
	private final BoolSetting beds = sgDanger.bool("Beds").description("Leave when a bed nearby could kill you (outside the Overworld).").defaultValue(true).build();
	private final BoolSetting anchors = sgDanger.bool("Anchors").description("Leave when a charged respawn anchor nearby could kill you (outside the Nether).").defaultValue(true).build();
	private final BoolSetting crystals = sgDanger.bool("Crystals").description("Leave when end crystals nearby could kill you.").build();
	private final BoolSetting creepers = sgDanger.bool("Creepers").description("Leave when a creeper comes close.").build();
	private final DoubleSetting creeperRange = sgDanger.doubleSetting("Creeper Range").defaultValue(5).range(1, 16).decimals(1).visible(creepers::get).build();

	private final BoolSetting autoDisable = sgGeneral.bool("Auto Disable").description("Turn this off after leaving.").defaultValue(true).build();
	private final BoolSetting copyCoords = sgGeneral.bool("Copy Coords").description("Copy where you were to the clipboard.").build();

	public AutoDisconnect() {
		super(Categories.COMBAT, "Auto Disconnect", "Leaves the server when you're about to die.");
	}

	@Override
	public String hudInfo() {
		return mc.player == null ? null : String.format("%.1f", Threats.health());
	}

	@Subscribe
	private void onTick(TickEvent.Post e) {
		if (!inGame() || mc.player.isCreative() || mc.player.isSpectator() || mc.isLocalServer() || mc.getConnection() == null) return;
		String reason = reason();
		if (reason != null) leave(reason);
	}

	private @Nullable String reason() {
		float hp = Threats.health();
		if (health.get() && hp <= healthThreshold.get()) return String.format("health %.1f", hp);
		if (pops.get()) {
			int n = Myriad.server().totemPops(mc.player);
			if (n >= popCount.get()) return n + " totem pops";
		}
		if (totems.get()) {
			int n = Myriad.inventory().count(s -> s.is(Items.TOTEM_OF_UNDYING));
			if (n <= totemCount.get()) return n + (n == 1 ? " totem" : " totems") + " left";
		}
		if (players.get()) {
			for (Player p : mc.level.players()) {
				if (p == mc.player || !p.isAlive() || p.isSpectator() || Myriad.friends().isFriend(p)) continue;
				if (mc.player.distanceTo(p) <= playerRange.get()) return p.getGameProfile().name() + " " + Math.round(mc.player.distanceTo(p)) + " blocks away";
			}
		}
		if (beds.get() && Threats.beds(8) >= hp) return "a bed could kill you";
		if (anchors.get() && Threats.anchors(8) >= hp) return "a respawn anchor could kill you";
		if (crystals.get() && Threats.crystals(12, true) >= hp) return "end crystals could kill you";
		if (creepers.get()) {
			var near = mc.level.getEntitiesOfClass(Creeper.class, mc.player.getBoundingBox().inflate(creeperRange.get()));
			if (!near.isEmpty()) return "a creeper " + Math.round(mc.player.distanceTo(near.getFirst())) + " blocks away";
		}
		return null;
	}

	private void leave(String reason) {
		if (copyCoords.get()) mc.keyboardHandler.setClipboard(String.format("%d %d %d", mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ()));
		if (autoDisable.get()) disable();
		mc.getConnection().getConnection().disconnect(Component.literal("[Auto Disconnect] " + reason));
	}
}
