package dev.myriad.api.util;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.ItemTags;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Questions about item stacks that features keep asking: enchantments, food, contents, durability, gear kind. */
public final class ItemInfo {
	private ItemInfo() {
	}

	/** The level of {@code enchantment} on the stack, 0 if it doesn't have it. */
	public static int enchantmentLevel(ItemStack stack, RegistryKey<Enchantment> enchantment) {
		for (Object2IntMap.Entry<RegistryEntry<Enchantment>> e : stack.getEnchantments().getEnchantmentEntries()) {
			if (e.getKey().matchesKey(enchantment)) return e.getIntValue();
		}
		return 0;
	}

	public static boolean hasEnchantment(ItemStack stack, RegistryKey<Enchantment> enchantment) {
		return enchantmentLevel(stack, enchantment) > 0;
	}

	public static boolean isFood(ItemStack stack) {
		return stack.contains(DataComponentTypes.FOOD);
	}

	/** Hunger and saturation, or null if it isn't food. */
	public static @Nullable FoodComponent food(ItemStack stack) {
		return stack.get(DataComponentTypes.FOOD);
	}

	public static boolean isShulkerBox(ItemStack stack) {
		return stack.getItem() instanceof BlockItem b && b.getBlock() instanceof ShulkerBoxBlock;
	}

	/**
	 * What's stored in a container item (a shulker box, or any item carrying contents), slot by slot; empty if it has
	 * none.
	 */
	public static List<ItemStack> contents(ItemStack stack) {
		ContainerComponent c = stack.get(DataComponentTypes.CONTAINER);
		List<ItemStack> out = new ArrayList<>();
		if (c != null) c.stream().forEach(out::add);
		return out;
	}

	/** Uses left before it breaks; {@link Integer#MAX_VALUE} for items that don't wear out. */
	public static int durability(ItemStack stack) {
		return stack.isDamageable() ? stack.getMaxDamage() - stack.getDamage() : Integer.MAX_VALUE;
	}

	/** Durability left as 0..1 (1 for items that don't wear out). */
	public static float durabilityFraction(ItemStack stack) {
		return stack.isDamageable() ? 1f - stack.getDamage() / (float) stack.getMaxDamage() : 1f;
	}

	public static boolean isWeapon(ItemStack stack) {
		return stack.isIn(ItemTags.SWORDS) || stack.isIn(ItemTags.AXES) || stack.isOf(Items.TRIDENT) || stack.isOf(Items.MACE);
	}

	public static boolean isTool(ItemStack stack) {
		return stack.isIn(ItemTags.PICKAXES) || stack.isIn(ItemTags.AXES) || stack.isIn(ItemTags.SHOVELS) || stack.isIn(ItemTags.HOES)
			|| stack.isOf(Items.SHEARS);
	}

	/** The armour slot it's worn in (head, chest, legs, feet; elytra is chest), or null if it isn't armour. */
	public static @Nullable EquipmentSlot armorSlot(ItemStack stack) {
		EquippableComponent e = stack.get(DataComponentTypes.EQUIPPABLE);
		return e != null && e.slot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR ? e.slot() : null;
	}

	/** The inventory index ({@link Slots#HEAD} etc.) for an armour slot. */
	public static int inventoryIndex(EquipmentSlot armorSlot) {
		return switch (armorSlot) {
			case HEAD -> Slots.HEAD;
			case CHEST -> Slots.CHEST;
			case LEGS -> Slots.LEGS;
			case FEET -> Slots.FEET;
			case OFFHAND -> Slots.OFF_HAND;
			default -> throw new IllegalArgumentException("Not an armour slot: " + armorSlot);
		};
	}
}
