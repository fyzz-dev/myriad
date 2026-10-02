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
import net.minecraft.block.BedBlock;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.function.Predicate;

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
		.filter(i -> i.getComponents().contains(DataComponentTypes.FOOD)).visible(eating::get).build();
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
		for (int i = 0; i < mc.player.getInventory().size(); i++) {
			ItemStack s = mc.player.getInventory().getStack(i);
			if (s.isOf(Items.TOTEM_OF_UNDYING)) n += s.getCount();
		}
		return String.valueOf(n);
	}

	@Override
	protected void onDisable() {
		restoreEatSlot();
	}

	@Subscribe
	private void onReceive(PacketEvent.Receive e) {
		if (e.packet() instanceof EntityStatusS2CPacket p && p.getStatus() == 35 && mc.world != null && mc.player != null && p.getEntity(mc.world) == mc.player) popped = true;
	}

	@Subscribe
	private void onTick(TickEvent.Pre e) {
		if (!inGame()) return;
		if (inventoryOnly.get() && mc.currentScreen != null && !(mc.currentScreen instanceof InventoryScreen) && mc.player.currentScreenHandler != mc.player.playerScreenHandler) return;
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
		if (want.test(mc.player.getOffHandStack())) return;
		int slot = find(want);
		if (slot < 0 && want != TOTEM) slot = find(TOTEM);
		if (slot < 0) return;
		Myriad.inventory().swapWithOffhand(slot);
		wait = delay.get();
	}

	private static final Predicate<ItemStack> TOTEM = s -> s.isOf(Items.TOTEM_OF_UNDYING);
	private static final Predicate<ItemStack> CRYSTAL = s -> s.isOf(Items.END_CRYSTAL);

	private Predicate<ItemStack> desiredOffhand(boolean lethal, float health) {
		if (lethal) {
			restoreEatSlot();
			return TOTEM;
		}
		if (eating.get() && mc.options.useKey.isPressed() && !usableInMainHand()) {
			Item food = chooseFood(health);
			if (food != null) {
				if (foodHand.get() == FoodHand.OFF_HAND) return s -> s.isOf(food);
				int slot = Myriad.inventory().findInHotbar(s -> s.isOf(food));
				if (slot >= 0 && mc.player.getInventory().selectedSlot != slot) {
					if (eatPrevSlot < 0) eatPrevSlot = mc.player.getInventory().selectedSlot;
					Myriad.inventory().select(slot);
				}
			}
		} else {
			restoreEatSlot();
		}
		if (crystalSwap.get() && health > crystalHealth.get() && (mc.player.getOffHandStack().isOf(Items.END_CRYSTAL) || find(CRYSTAL) >= 0)) return CRYSTAL;
		return TOTEM;
	}

	private void restoreEatSlot() {
		if (eatPrevSlot >= 0 && mc.player != null && !mc.options.useKey.isPressed()) {
			Myriad.inventory().select(eatPrevSlot);
			eatPrevSlot = -1;
		}
	}

	/** Whether right click already does something with the main hand item (so don't hijack it for food). */
	private boolean usableInMainHand() {
		ItemStack s = mc.player.getMainHandStack();
		if (s.isEmpty()) return false;
		if (s.contains(DataComponentTypes.FOOD) || s.getItem() instanceof net.minecraft.item.BlockItem) return true;
		return s.isOf(Items.BOW) || s.isOf(Items.CROSSBOW) || s.isOf(Items.TRIDENT) || s.isOf(Items.SHIELD) || s.isOf(Items.ENDER_PEARL)
			|| s.isOf(Items.EXPERIENCE_BOTTLE) || s.isOf(Items.FIREWORK_ROCKET) || s.isOf(Items.END_CRYSTAL) || s.isOf(Items.POTION)
			|| s.isOf(Items.SPLASH_POTION) || s.isOf(Items.LINGERING_POTION) || s.isOf(Items.FLINT_AND_STEEL) || s.isOf(Items.BUCKET);
	}

	private Item chooseFood(float health) {
		boolean gapple = health <= gappleHealth.get();
		if (gapple) {
			if (find(s -> s.isOf(Items.ENCHANTED_GOLDEN_APPLE)) >= 0 || mc.player.getOffHandStack().isOf(Items.ENCHANTED_GOLDEN_APPLE)) return Items.ENCHANTED_GOLDEN_APPLE;
			if (find(s -> s.isOf(Items.GOLDEN_APPLE)) >= 0 || mc.player.getOffHandStack().isOf(Items.GOLDEN_APPLE)) return Items.GOLDEN_APPLE;
		}
		if (foods.contains(mc.player.getOffHandStack().getItem())) return mc.player.getOffHandStack().getItem();
		for (Item food : foods.get()) if (find(s -> s.isOf(food)) >= 0) return food;
		if (find(s -> s.isOf(Items.GOLDEN_APPLE)) >= 0) return Items.GOLDEN_APPLE;
		return null;
	}

	private void tickMainHand(boolean lethal, float health) {
		int hotbar = mainhandSlot.get() - 1;
		if (!mc.player.getInventory().getStack(hotbar).isOf(Items.TOTEM_OF_UNDYING)) {
			int src = findMain(TOTEM);
			if (src >= 0) {
				Myriad.inventory().moveToHotbar(src, hotbar);
				wait = delay.get();
			}
		}
		boolean force = health <= mainhandHealth.get() || (lethal && lethalMainhand.get());
		if (force && mc.player.getInventory().getStack(hotbar).isOf(Items.TOTEM_OF_UNDYING) && mc.player.getInventory().selectedSlot != hotbar) {
			Myriad.inventory().select(hotbar);
		}
	}

	private boolean lethalIncoming() {
		float health = mc.player.getHealth() + mc.player.getAbsorptionAmount();
		float max = 0, sum = 0;
		for (Entity entity : mc.world.getOtherEntities(mc.player, mc.player.getBoundingBox().expand(12), en -> en instanceof EndCrystalEntity)) {
			float d = Damage.crystal(mc.player, entity.getPos());
			sum += d;
			max = Math.max(max, d);
		}
		if (sumDamage.get()) max = Math.max(max, sum);
		if (mc.world.getRegistryKey() == World.NETHER || mc.world.getRegistryKey() == World.END) {
			for (BlockPos bp : BlockPos.iterate(mc.player.getBlockPos().add(-8, -8, -8), mc.player.getBlockPos().add(8, 8, 8))) {
				if (mc.world.getBlockState(bp).getBlock() instanceof BedBlock) max = Math.max(max, Damage.explosion(mc.player, Vec3d.ofCenter(bp), 5));
			}
		}
		for (PlayerEntity p : mc.world.getEntitiesByClass(PlayerEntity.class, mc.player.getBoundingBox().expand(5), pl -> pl != mc.player && !Myriad.friends().isFriend(pl))) {
			max = Math.max(max, Damage.melee(p, mc.player));
		}
		if (includeFall.get() && mc.player.fallDistance > 3) max = Math.max(max, Damage.fall(mc.player, mc.player.fallDistance));
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
