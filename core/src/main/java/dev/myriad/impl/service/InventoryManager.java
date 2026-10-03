package dev.myriad.impl.service;

import dev.myriad.api.event.Subscribe;
import dev.myriad.api.event.events.PacketEvent;
import dev.myriad.api.event.events.TickEvent;
import dev.myriad.api.service.Inventory;
import dev.myriad.api.util.Slots;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

public final class InventoryManager implements Inventory {
	private final Minecraft mc = Minecraft.getInstance();
	private volatile int serverSlot;
	private Object holder;
	private int holdTicks;

	@Subscribe
	private void onSend(PacketEvent.Send e) {
		if (e.packet() instanceof ServerboundSetCarriedItemPacket p) serverSlot = p.getSlot();
	}

	@Subscribe
	private void onTick(TickEvent.Post e) {
		if (holder == null) return;
		if (mc.player == null || --holdTicks <= 0) release(holder);
	}

	@Override
	public boolean hold(Object owner, int hotbarSlot, int maxTicks) {
		if (mc.player == null || hotbarSlot < 0 || hotbarSlot > 8) return false;
		if (holder != null && holder != owner) return false;
		holder = owner;
		holdTicks = Math.max(1, maxTicks);
		if (serverSlot != hotbarSlot) mc.getConnection().send(new ServerboundSetCarriedItemPacket(hotbarSlot));
		return true;
	}

	@Override
	public void release(Object owner) {
		if (holder == null || holder != owner) return;
		holder = null;
		holdTicks = 0;
		if (mc.player != null && mc.getConnection() != null) {
			int visible = mc.player.getInventory().getSelectedSlot();
			if (serverSlot != visible) mc.getConnection().send(new ServerboundSetCarriedItemPacket(visible));
		}
	}

	@Override
	public boolean isHolding() {
		return holder != null;
	}

	@Override
	public int serverSlot() {
		return serverSlot;
	}

	@Override
	public void select(int hotbarSlot) {
		if (mc.player == null || hotbarSlot < 0 || hotbarSlot > 8) return;
		mc.player.getInventory().setSelectedSlot(hotbarSlot);
		mc.gameMode.ensureHasSentCarriedItem();
	}

	@Override
	public void silentSwap(int hotbarSlot, Runnable action) {
		if (mc.player == null || hotbarSlot < 0 || hotbarSlot > 8) return;
		int previous = mc.player.getInventory().getSelectedSlot();
		if (previous == hotbarSlot) {
			action.run();
			return;
		}
		select(hotbarSlot);
		try {
			action.run();
		} finally {
			select(previous);
		}
	}

	@Override
	public int findInHotbar(Predicate<ItemStack> predicate) {
		return find(predicate, 0, 9);
	}

	@Override
	public int findInInventory(Predicate<ItemStack> predicate) {
		return find(predicate, 9, 36);
	}

	@Override
	public int bestInHotbar(ToDoubleFunction<ItemStack> score) {
		return best(score, 0, 9);
	}

	@Override
	public int bestInInventory(ToDoubleFunction<ItemStack> score) {
		return best(score, 0, 36);
	}

	private int best(ToDoubleFunction<ItemStack> score, int from, int to) {
		if (mc.player == null) return -1;
		int best = -1;
		double bestScore = 0;
		for (int i = from; i < to; i++) {
			double s = score.applyAsDouble(mc.player.getInventory().getItem(i));
			if (s > bestScore) {
				bestScore = s;
				best = i;
			}
		}
		return best;
	}

	@Override
	public int count(Predicate<ItemStack> predicate) {
		if (mc.player == null) return 0;
		int n = 0;
		var inv = mc.player.getInventory();
		for (int i = 0; i < 36; i++) if (predicate.test(inv.getItem(i))) n += inv.getItem(i).getCount();
		if (predicate.test(inv.getItem(Slots.OFF_HAND))) n += inv.getItem(Slots.OFF_HAND).getCount();
		return n;
	}

	@Override
	public boolean moveToHotbar(int inventoryIndex, int hotbarSlot) {
		if (mc.player == null || hotbarSlot < 0 || hotbarSlot > 8 || inventoryIndex < 0 || inventoryIndex >= 36) return false;
		if (inventoryIndex == hotbarSlot) return true;
		if (!canClick()) return false;
		return clickSwap(inventoryIndex, hotbarSlot);
	}

	@Override
	public int ensureInHotbar(Predicate<ItemStack> predicate, int preferredSlot) {
		int slot = findInHotbar(predicate);
		if (slot != -1) return slot;
		int from = findInInventory(predicate);
		if (from == -1) return -1;
		int to = preferredSlot;
		if (to < 0 || to > 8) {
			to = findInHotbar(ItemStack::isEmpty);
			if (to == -1) to = mc.player.getInventory().getSelectedSlot();
		}
		return moveToHotbar(from, to) ? to : -1;
	}

	/** True if the player's own screen is the one open to clicks. */
	private boolean canClick() {
		return mc.player != null && mc.gameMode != null && mc.player.containerMenu == mc.player.inventoryMenu;
	}

	private static boolean valid(int index) {
		return index >= 0 && index <= Slots.OFF_HAND;
	}

	private void click(int index, int button, ContainerInput action) {
		mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, Slots.playerScreen(index), button, action, mc.player);
	}

	@Override
	public boolean move(int from, int to) {
		if (!canClick() || !valid(from) || !valid(to)) return false;
		if (from == to) return true;
		if (Slots.isHotbar(to)) return clickSwap(from, to);
		if (Slots.isHotbar(from)) return clickSwap(to, from);
		// Pick up, put down (swapping with what's there), then put back whatever ended up on the cursor.
		click(from, 0, ContainerInput.PICKUP);
		click(to, 0, ContainerInput.PICKUP);
		if (!mc.player.containerMenu.getCarried().isEmpty()) click(from, 0, ContainerInput.PICKUP);
		return true;
	}

	private boolean clickSwap(int index, int hotbarSlot) {
		click(index, hotbarSlot, ContainerInput.SWAP);
		return true;
	}

	@Override
	public boolean swapWithOffhand(int inventoryIndex) {
		if (!canClick() || !valid(inventoryIndex) || inventoryIndex == Slots.OFF_HAND) return false;
		// Button 40 is the off hand swap key.
		click(inventoryIndex, 40, ContainerInput.SWAP);
		return true;
	}

	@Override
	public boolean quickMove(int inventoryIndex) {
		if (!canClick() || !valid(inventoryIndex)) return false;
		click(inventoryIndex, 0, ContainerInput.QUICK_MOVE);
		return true;
	}

	@Override
	public boolean drop(int inventoryIndex, boolean wholeStack) {
		if (!canClick() || !valid(inventoryIndex)) return false;
		click(inventoryIndex, wholeStack ? 1 : 0, ContainerInput.THROW);
		return true;
	}

	private int find(Predicate<ItemStack> predicate, int from, int to) {
		if (mc.player == null) return -1;
		for (int i = from; i < to; i++) if (predicate.test(mc.player.getInventory().getItem(i))) return i;
		return -1;
	}
}
