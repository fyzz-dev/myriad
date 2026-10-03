package dev.myriad.essentials.modules.combat;

import dev.myriad.api.Myriad;
import dev.myriad.api.event.Subscribe;
import dev.myriad.api.event.events.KeyEvent;
import dev.myriad.api.event.events.MouseButtonEvent;
import dev.myriad.api.event.events.TickEvent;
import dev.myriad.api.module.Categories;
import dev.myriad.api.module.Module;
import dev.myriad.api.setting.BoolSetting;
import dev.myriad.api.setting.DoubleSetting;
import dev.myriad.api.setting.EnumSetting;
import dev.myriad.api.setting.IntSetting;
import dev.myriad.api.setting.KeybindSetting;
import dev.myriad.api.setting.SettingGroup;
import dev.myriad.api.util.ItemInfo;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * Wears the best armour you're carrying, one piece at a time. Best means most armour and toughness, then your
 * preferred protection enchantment; pieces about to break and curse of binding are skipped.
 * <p>
 * For the chest slot you choose between a chestplate and an elytra, and the Chest Swap bind flips between them (and
 * swaps straight away). Until you land, an elytra you're flying with is never taken off.
 */
public class AutoArmor extends Module {
	public enum Protection {
		PROTECTION, BLAST, FIRE, PROJECTILE
	}

	public enum Chest {
		CHESTPLATE, ELYTRA
	}

	private static final EquipmentSlot[] SLOTS = {EquipmentSlot.CHEST, EquipmentSlot.HEAD, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private final IntSetting delay = sgGeneral.intSetting("Delay").description("Ticks between equipping pieces.").defaultValue(2).range(0, 20).build();
	private final EnumSetting<Protection> prefer = sgGeneral.enumSetting("Prefer", Protection.PROTECTION).description("Protection enchantment to favour between otherwise equal pieces.").build();
	private final BoolSetting avoidBinding = sgGeneral.bool("Avoid Binding").description("Never put on armour with curse of binding.").defaultValue(true).build();
	private final DoubleSetting preserve = sgGeneral.doubleSetting("Preserve").description("Skip pieces with less durability than this (%).").defaultValue(5).range(0, 50).decimals(0).build();
	private final BoolSetting pauseInContainers = sgGeneral.bool("Pause In Containers").description("Don't swap while a chest or other container is open.").defaultValue(true).build();

	private final SettingGroup sgChest = settings.group("Chest Swap");
	private final EnumSetting<Chest> chest = sgChest.enumSetting("Chest", Chest.CHESTPLATE).description("What to wear in the chest slot.").build();
	private final KeybindSetting swapKey = sgChest.keybind("Swap Bind").description("Flip between chestplate and elytra.").build();
	private final BoolSetting awaitLanding = sgChest.bool("Await Landing").description("Keep the elytra on until you land.").defaultValue(true).build();
	private final BoolSetting notify = sgChest.bool("Notify").description("Say which one you swapped to.").defaultValue(true).build();

	private int wait;

	public AutoArmor() {
		super(Categories.COMBAT, "Auto Armor", "Wears your best armour, and swaps between chestplate and elytra on a bind.");
	}

	@Override
	public String hudInfo() {
		return chest.get() == Chest.ELYTRA ? "Elytra" : null;
	}

	@Subscribe
	private void onKey(KeyEvent e) {
		if (swapKey.wasPressed(e)) flipChest();
	}

	@Subscribe
	private void onMouse(MouseButtonEvent e) {
		if (swapKey.wasPressed(e)) flipChest();
	}

	private void flipChest() {
		chest.set(chest.get() == Chest.ELYTRA ? Chest.CHESTPLATE : Chest.ELYTRA);
		wait = 0;
		if (notify.get()) info("Chest: " + (chest.get() == Chest.ELYTRA ? "elytra" : "chestplate"));
	}

	@Subscribe
	private void onTick(TickEvent.Pre e) {
		if (!inGame() || mc.player.isCreative()) return;
		if (pauseInContainers.get() && mc.gui.screen() != null && !(mc.gui.screen() instanceof InventoryScreen) && mc.player.containerMenu != mc.player.inventoryMenu) return;
		if (wait > 0) {
			wait--;
			return;
		}
		for (EquipmentSlot slot : SLOTS) {
			if (equipBest(slot)) {
				wait = delay.get();
				return;
			}
		}
	}

	/** Puts on a better piece for {@code slot} if you have one; true if it swapped. */
	private boolean equipBest(EquipmentSlot slot) {
		ItemStack worn = mc.player.getItemBySlot(slot);
		if (slot == EquipmentSlot.CHEST && awaitLanding.get() && mc.player.isFallFlying() && isElytra(worn)) return false;
		if (avoidBinding.get() && ItemInfo.hasEnchantment(worn, Enchantments.BINDING_CURSE)) return false;
		double current = score(worn, slot);
		int best = -1;
		double bestScore = current;
		for (int i = 0; i < 36; i++) {
			ItemStack s = mc.player.getInventory().getItem(i);
			if (s.isEmpty() || ItemInfo.armorSlot(s) != slot) continue;
			double sc = score(s, slot);
			if (sc > bestScore) {
				bestScore = sc;
				best = i;
			}
		}
		return best >= 0 && Myriad.inventory().move(best, ItemInfo.inventoryIndex(slot));
	}

	/** Higher is better; 0 for nothing worth wearing. */
	private double score(ItemStack s, EquipmentSlot slot) {
		if (s.isEmpty()) return 0;
		if (avoidBinding.get() && ItemInfo.hasEnchantment(s, Enchantments.BINDING_CURSE)) return 0;
		if (s.isDamageableItem() && ItemInfo.durabilityFraction(s) * 100 < preserve.get()) return 0.01;
		if (slot == EquipmentSlot.CHEST) {
			boolean elytra = isElytra(s);
			// The wanted kind always beats the other; the other still beats an empty slot.
			boolean wanted = elytra == (chest.get() == Chest.ELYTRA);
			if (elytra) return (wanted ? 1000 : 0.5) + ItemInfo.durabilityFraction(s);
			if (!wanted) return 0.5 + armorValue(s, slot) / 1000;
		}
		// Pumpkins and heads go in the head slot but aren't armour.
		ItemAttributeModifiers mods = s.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		if (mods.compute(Attributes.ARMOR, 0, slot) <= 0) return 0;
		return 1 + armorValue(s, slot);
	}

	private double armorValue(ItemStack s, EquipmentSlot slot) {
		ItemAttributeModifiers mods = s.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		double armor = mods.compute(Attributes.ARMOR, 0, slot), toughness = mods.compute(Attributes.ARMOR_TOUGHNESS, 0, slot);
		double protection = ItemInfo.enchantmentLevel(s, Enchantments.PROTECTION);
		double preferred = ItemInfo.enchantmentLevel(s, preferredKey());
		return armor * 10 + toughness * 4 + protection * 2 + preferred * 3 + ItemInfo.durabilityFraction(s) * 0.1;
	}

	private ResourceKey<Enchantment> preferredKey() {
		return switch (prefer.get()) {
			case PROTECTION -> Enchantments.PROTECTION;
			case BLAST -> Enchantments.BLAST_PROTECTION;
			case FIRE -> Enchantments.FIRE_PROTECTION;
			case PROJECTILE -> Enchantments.PROJECTILE_PROTECTION;
		};
	}

	private static boolean isElytra(ItemStack s) {
		return !s.isEmpty() && LivingEntity.canGlideUsing(s, EquipmentSlot.CHEST);
	}
}
