package dev.myriad.essentials.modules.combat;

import dev.myriad.api.Myriad;
import dev.myriad.api.combat.Damage;
import dev.myriad.api.event.Subscribe;
import dev.myriad.api.event.events.PacketEvent;
import dev.myriad.api.event.events.TickEvent;
import dev.myriad.api.module.Categories;
import dev.myriad.api.module.Module;
import dev.myriad.api.setting.BoolSetting;
import dev.myriad.api.setting.DoubleSetting;
import dev.myriad.api.setting.EnumSetting;
import dev.myriad.api.setting.IntSetting;
import dev.myriad.api.setting.RegistryListSetting;
import dev.myriad.api.setting.SettingGroup;
import java.util.function.Predicate;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.phys.Vec3;

/**
 * Keeps a totem of undying in your off hand, and puts a fresh one there the tick one pops. Optionally:
 * <ul>
 * <li>Main Hand keeps a totem in a hotbar slot and switches to it when your health is low.</li>
 * <li>Predict Lethal estimates incoming damage (nearby crystals, beds in the Nether/End, players about to hit
 * you, falling) and forces totems whenever it could kill you.</li>
 * <li>Eating swaps food (or a golden apple when health is low) into your off hand while you hold right click.</li>
 * <li>Crystal Swap keeps an end crystal in your off hand until your health gets low.</li>
 * </ul>
 */
public class AutoTotem extends Module {
	public enum FoodHand {
		OFF_HAND, MAIN_HAND
	}

	private final IntSetting delay = sgGeneral.intSetting("Delay").description("Ticks to wait between swaps.").defaultValue(0).range(0, 20).build();
	private final BoolSetting inventoryOnly = sgGeneral.bool("Pause In Containers").description("Don't swap while a chest or other container is open.").defaultValue(true).build();

	private final SettingGroup sgMain = settings.group("Main Hand");
	private final BoolSetting mainHand = sgMain.bool("Main Hand").description("Keep a totem in a hotbar slot too.").build();
	private final IntSetting mainhandSlot = sgMain.intSetting("Slot").description("Hotbar slot (1-9) for the totem.").defaultValue(3).range(1, 9).visible(mainHand::get).build();
	private final IntSetting mainhandHealth = sgMain.intSetting("Health").description("Switch to the totem at or below this health.").defaultValue(6).range(0, 36).visible(mainHand::get).build();

	private final SettingGroup sgSafety = settings.group("Safety");
	private final BoolSetting predictLethal = sgSafety.bool("Predict Lethal").description("Force totems when incoming damage could kill you.").defaultValue(true).build();
	private final BoolSetting sumDamage = sgSafety.bool("Sum Damage").description("Add up all nearby crystals instead of taking the strongest.").defaultValue(true).visible(predictLethal::get).build();
	private final BoolSetting includeFall = sgSafety.bool("Include Fall").description("Count fall damage.").defaultValue(true).visible(predictLethal::get).build();
	private final DoubleSetting buffer = sgSafety.doubleSetting("Buffer").description("Extra health kept as a safety margin.").defaultValue(0.5).range(0, 4).decimals(1).visible(predictLethal::get).build();
	private final BoolSetting lethalMainhand = sgSafety.bool("Main Hand On Lethal").description("Also switch to the main-hand totem on lethal damage.").defaultValue(true)
		.visible(() -> predictLethal.get() && mainHand.get()).build();

	private final SettingGroup sgEating = settings.group("Eating");
	private final BoolSetting eating = sgEating.bool("Eating").description("Swap to food while you hold right click with nothing usable in hand.").build();
	private final EnumSetting<FoodHand> foodHand = sgEating.enumSetting("Hand", FoodHand.OFF_HAND).visible(eating::get).build();
	private final RegistryListSetting<Item> foods = sgEating.items("Foods").defaultValue(Items.GOLDEN_CARROT, Items.COOKED_BEEF, Items.COOKED_PORKCHOP, Items.BREAD)
		.filter(i -> i.components().has(DataComponents.FOOD)).visible(eating::get).build();
	private final IntSetting gappleHealth = sgEating.intSetting("Gapple Health").description("Eat a golden apple instead at or below this health.").defaultValue(12).range(0, 36).visible(eating::get).build();

	private final SettingGroup sgCrystal = settings.group("Crystal Swap");
	private final BoolSetting crystalSwap = sgCrystal.bool("Crystal Swap").description("Keep an end crystal in your off hand until health is low.").build();
	private final IntSetting crystalHealth = sgCrystal.intSetting("Crystal Health").description("Switch back to a totem at or below this health.").defaultValue(8).range(1, 36).visible(crystalSwap::get).build();

	private int wait, eatPrevSlot = -1;
	private volatile boolean popped;

	public AutoTotem() {
		super(Categories.COMBAT, "Auto Totem", "Keeps a totem of undying in your off hand.");
	}

	@Override
	public String hudInfo() {
		if (mc.player == null) return null;
		int n = 0;
		for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
			ItemStack s = mc.player.getInventory().getItem(i);
			if (s.is(Items.TOTEM_OF_UNDYING)) n += s.getCount();
		}
		return String.valueOf(n);
	}

	@Override
	protected void onDisable() {
		restoreEatSlot();
	}

	@Subscribe
	private void onReceive(PacketEvent.Receive e) {
		if (e.packet() instanceof ClientboundEntityEventPacket p && p.getEventId() == 35 && mc.level != null && mc.player != null && p.getEntity(mc.level) == mc.player) popped = true;
	}

	@Subscribe
	private void onTick(TickEvent.Pre e) {
		if (!inGame()) return;
		if (inventoryOnly.get() && mc.gui.screen() != null && !(mc.gui.screen() instanceof InventoryScreen) && mc.player.containerMenu != mc.player.inventoryMenu) return;
		if (popped) {
			// A totem just popped: re-arm this tick, ignoring the delay.
			popped = false;
			wait = 0;
		}
		if (wait > 0) {
			wait--;
			return;
		}
		boolean lethal = predictLethal.get() && lethalIncoming();
		float health = mc.player.getHealth() + mc.player.getAbsorptionAmount();

		if (mainHand.get()) tickMainHand(lethal, health);

		Predicate<ItemStack> want = desiredOffhand(lethal, health);
		if (want.test(mc.player.getOffhandItem())) return;
		int slot = find(want);
		if (slot < 0 && want != TOTEM) slot = find(TOTEM);
		if (slot < 0) return;
		Myriad.inventory().swapWithOffhand(slot);
		wait = delay.get();
	}

	private static final Predicate<ItemStack> TOTEM = s -> s.is(Items.TOTEM_OF_UNDYING);
	private static final Predicate<ItemStack> CRYSTAL = s -> s.is(Items.END_CRYSTAL);

	private Predicate<ItemStack> desiredOffhand(boolean lethal, float health) {
		if (lethal) {
			restoreEatSlot();
			return TOTEM;
		}
		if (eating.get() && mc.options.keyUse.isDown() && !usableInMainHand()) {
			Item food = chooseFood(health);
			if (food != null) {
				if (foodHand.get() == FoodHand.OFF_HAND) return s -> s.is(food);
				int slot = Myriad.inventory().findInHotbar(s -> s.is(food));
				if (slot >= 0 && mc.player.getInventory().getSelectedSlot() != slot) {
					if (eatPrevSlot < 0) eatPrevSlot = mc.player.getInventory().getSelectedSlot();
					Myriad.inventory().select(slot);
				}
			}
		} else {
			restoreEatSlot();
		}
		if (crystalSwap.get() && health > crystalHealth.get() && (mc.player.getOffhandItem().is(Items.END_CRYSTAL) || find(CRYSTAL) >= 0)) return CRYSTAL;
		return TOTEM;
	}

	private void restoreEatSlot() {
		if (eatPrevSlot >= 0 && mc.player != null && !mc.options.keyUse.isDown()) {
			Myriad.inventory().select(eatPrevSlot);
			eatPrevSlot = -1;
		}
	}

	/** Whether right click already does something with the main hand item (so don't hijack it for food). */
	private boolean usableInMainHand() {
		ItemStack s = mc.player.getMainHandItem();
		if (s.isEmpty()) return false;
		if (s.has(DataComponents.FOOD) || s.getItem() instanceof net.minecraft.world.item.BlockItem) return true;
		return s.is(Items.BOW) || s.is(Items.CROSSBOW) || s.is(Items.TRIDENT) || s.is(Items.SHIELD) || s.is(Items.ENDER_PEARL)
			|| s.is(Items.EXPERIENCE_BOTTLE) || s.is(Items.FIREWORK_ROCKET) || s.is(Items.END_CRYSTAL) || s.is(Items.POTION)
			|| s.is(Items.SPLASH_POTION) || s.is(Items.LINGERING_POTION) || s.is(Items.FLINT_AND_STEEL) || s.is(Items.BUCKET);
	}

	private Item chooseFood(float health) {
		boolean gapple = health <= gappleHealth.get();
		if (gapple) {
			if (find(s -> s.is(Items.ENCHANTED_GOLDEN_APPLE)) >= 0 || mc.player.getOffhandItem().is(Items.ENCHANTED_GOLDEN_APPLE)) return Items.ENCHANTED_GOLDEN_APPLE;
			if (find(s -> s.is(Items.GOLDEN_APPLE)) >= 0 || mc.player.getOffhandItem().is(Items.GOLDEN_APPLE)) return Items.GOLDEN_APPLE;
		}
		if (foods.contains(mc.player.getOffhandItem().getItem())) return mc.player.getOffhandItem().getItem();
		for (Item food : foods.get()) if (find(s -> s.is(food)) >= 0) return food;
		if (find(s -> s.is(Items.GOLDEN_APPLE)) >= 0) return Items.GOLDEN_APPLE;
		return null;
	}

	private void tickMainHand(boolean lethal, float health) {
		int hotbar = mainhandSlot.get() - 1;
		if (!mc.player.getInventory().getItem(hotbar).is(Items.TOTEM_OF_UNDYING)) {
			int src = findMain(TOTEM);
			if (src >= 0) {
				Myriad.inventory().moveToHotbar(src, hotbar);
				wait = delay.get();
			}
		}
		boolean force = health <= mainhandHealth.get() || (lethal && lethalMainhand.get());
		if (force && mc.player.getInventory().getItem(hotbar).is(Items.TOTEM_OF_UNDYING) && mc.player.getInventory().getSelectedSlot() != hotbar) {
			Myriad.inventory().select(hotbar);
		}
	}

	private boolean lethalIncoming() {
		float health = mc.player.getHealth() + mc.player.getAbsorptionAmount();
		float max = 0, sum = 0;
		for (Entity entity : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(12), en -> en instanceof EndCrystal)) {
			float d = Damage.crystal(mc.player, entity.position());
			sum += d;
			max = Math.max(max, d);
		}
		if (sumDamage.get()) max = Math.max(max, sum);
		if (mc.level.dimension() == Level.NETHER || mc.level.dimension() == Level.END) {
			for (BlockPos bp : BlockPos.betweenClosed(mc.player.blockPosition().offset(-8, -8, -8), mc.player.blockPosition().offset(8, 8, 8))) {
				if (mc.level.getBlockState(bp).getBlock() instanceof BedBlock) max = Math.max(max, Damage.explosion(mc.player, Vec3.atCenterOf(bp), 5));
			}
		}
		for (Player p : mc.level.getEntitiesOfClass(Player.class, mc.player.getBoundingBox().inflate(5), pl -> pl != mc.player && !Myriad.friends().isFriend(pl))) {
			max = Math.max(max, Damage.melee(p, mc.player));
		}
		if (includeFall.get() && mc.player.fallDistance > 3) max = Math.max(max, Damage.fall(mc.player, (float) mc.player.fallDistance));
		return max > 0 && max + buffer.get() >= health;
	}

	/** Main inventory first (keeps the hotbar intact), then the hotbar; returns an inventory index or -1. */
	private int find(Predicate<ItemStack> predicate) {
		int slot = findMain(predicate);
		return slot >= 0 ? slot : Myriad.inventory().findInHotbar(predicate);
	}

	private int findMain(Predicate<ItemStack> predicate) {
		return Myriad.inventory().findInInventory(predicate);
	}
}
