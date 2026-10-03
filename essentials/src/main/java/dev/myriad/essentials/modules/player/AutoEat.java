package dev.myriad.essentials.modules.player;

import dev.myriad.api.Myriad;
import dev.myriad.api.event.Subscribe;
import dev.myriad.api.event.events.TickEvent;
import dev.myriad.api.module.Categories;
import dev.myriad.api.module.Module;
import dev.myriad.api.setting.BoolSetting;
import dev.myriad.api.setting.IntSetting;
import dev.myriad.api.setting.RegistryListSetting;
import dev.myriad.api.util.Baritone;
import dev.myriad.api.util.Interactions;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Eats the most saturating food in your hotbar when your health or hunger drops below a threshold, then switches
 * back. Keeps eating while a screen is open, and pauses Baritone (when it's installed) until it's done.
 */
public class AutoEat extends Module {
	private static final int ABORT_TICKS = 100;

	private final BoolSetting health = sgGeneral.bool("Health").description("Eat when health is low.").defaultValue(true).build();
	private final IntSetting healthThreshold = sgGeneral.intSetting("Health Threshold").description("Health (including absorption) to eat at.").defaultValue(6).range(0, 36).visible(health::get).build();
	private final BoolSetting hunger = sgGeneral.bool("Hunger").description("Eat when hunger is low.").defaultValue(true).build();
	private final IntSetting hungerThreshold = sgGeneral.intSetting("Hunger Threshold").description("Food level to eat at (20 is full).").defaultValue(6).range(0, 20).visible(hunger::get).build();
	private final RegistryListSetting<Item> blacklist = sgGeneral.items("Blacklist").description("Foods never to eat.")
		.defaultValue(Items.ROTTEN_FLESH, Items.SPIDER_EYE, Items.POISONOUS_POTATO, Items.PUFFERFISH, Items.CHORUS_FRUIT, Items.SUSPICIOUS_STEW)
		.filter(i -> i.components().has(DataComponents.FOOD)).build();
	private final BoolSetting pauseBaritone = sgGeneral.bool("Pause Baritone").description("Stop Baritone walking while you eat.").defaultValue(true).build();

	private boolean eating;
	private int previousSlot = -1, foodSlot = -1, ticks;

	public AutoEat() {
		super(Categories.PLAYER, "Auto Eat", "Eats when your health or hunger gets low.");
	}

	@Override
	public String hudInfo() {
		return eating ? "Eating" : null;
	}

	@Override
	protected void onDisable() {
		finish();
	}

	@Subscribe
	private void onTick(TickEvent.Pre e) {
		if (!inGame()) {
			finish();
			return;
		}
		if (eating) {
			tickEating();
			return;
		}
		var p = mc.player;
		boolean shouldEat = health.get() && p.getHealth() + p.getAbsorptionAmount() <= healthThreshold.get();
		shouldEat |= hunger.get() && p.getFoodData().getFoodLevel() <= hungerThreshold.get();
		if (!shouldEat || p.isUsingItem()) return;
		int slot = bestFood();
		if (slot < 0) return;
		previousSlot = p.getInventory().getSelectedSlot();
		foodSlot = slot;
		ticks = 0;
		Myriad.inventory().select(slot);
		Interactions.useItem(InteractionHand.MAIN_HAND);
		if (p.isUsingItem()) {
			mc.options.keyUse.setDown(true);
			eating = true;
			if (pauseBaritone.get()) Baritone.pause(this);
		} else {
			finish();
		}
	}

	private void tickEating() {
		var p = mc.player;
		boolean still = !p.isDeadOrDying() && p.isUsingItem() && p.getUseItem().has(DataComponents.FOOD) && ticks++ < ABORT_TICKS;
		if (!still) {
			finish();
			return;
		}
		if (p.getInventory().getSelectedSlot() != foodSlot) Myriad.inventory().select(foodSlot);
		mc.options.keyUse.setDown(true);
	}

	/** The most saturating food in the hotbar, then the most filling. */
	private int bestFood() {
		return Myriad.inventory().bestInHotbar(s -> {
			FoodProperties food = s.get(DataComponents.FOOD);
			if (food == null || blacklist.contains(s.getItem())) return 0;
			return 1 + food.saturation() * 1000 + food.nutrition();
		});
	}

	private void finish() {
		if (!eating && previousSlot < 0) return;
		mc.options.keyUse.setDown(false);
		if (mc.player != null && mc.player.isUsingItem() && mc.player.getUseItem().has(DataComponents.FOOD)) {
			mc.gameMode.releaseUsingItem(mc.player);
		}
		if (previousSlot >= 0 && mc.player != null) Myriad.inventory().select(previousSlot);
		Baritone.resume(this);
		eating = false;
		previousSlot = foodSlot = -1;
		ticks = 0;
	}
}
