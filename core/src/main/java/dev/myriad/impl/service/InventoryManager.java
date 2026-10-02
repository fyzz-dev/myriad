package dev.myriad.impl.service;

import dev.myriad.api.event.Subscribe;
import dev.myriad.api.event.events.PacketEvent;
import dev.myriad.api.event.events.TickEvent;
import dev.myriad.api.service.Inventory;
import dev.myriad.api.util.Slots;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;

import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

public final class InventoryManager implements Inventory {
	private final MinecraftClient mc = MinecraftClient.getInstance();
	private volatile int serverSlot;
	private Object holder;
	private int holdTicks;

	@Subscribe
	private void onSend(PacketEvent.Send e) {
		if (e.packet() instanceof UpdateSelectedSlotC2SPacket p) serverSlot = p.getSelectedSlot();
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
		if (serverSlot != hotbarSlot) mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(hotbarSlot));
		return true;
	}

	@Override
	public void release(Object owner) {
		if (holder == null || holder != owner) return;
		holder = null;
		holdTicks = 0;
		if (mc.player != null && mc.getNetworkHandler() != null) {
			int visible = mc.player.getInventory().selectedSlot;
			if (serverSlot != visible) mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(visible));
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
		mc.player.getInventory().selectedSlot = hotbarSlot;
		mc.interactionManager.syncSelectedSlot();
	}

	@Override
	public void silentSwap(int hotbarSlot, Runnable action) {
		if (mc.player == null || hotbarSlot < 0 || hotbarSlot > 8) return;
		int previous = mc.player.getInventory().selectedSlot;
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
			double s = score.applyAsDouble(mc.player.getInventory().getStack(i));
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
		for (int i = 0; i < 36; i++) if (predicate.test(inv.getStack(i))) n += inv.getStack(i).getCount();
		if (predicate.test(inv.getStack(Slots.OFF_HAND))) n += inv.getStack(Slots.OFF_HAND).getCount();
		return n;
	}

	@Override
	public boolean moveToHotbar(int inventoryIndex, int hotbarSlot) {
		if (mc.player == null || hotbarSlot < 0 || hotbarSlot > 8 || inventoryIndex < 0 || inventoryIndex >= 36) return false;
		if (inventoryIndex == hotbarSlot) return true;
		if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) return false;
		mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, Slots.playerScreen(inventoryIndex), hotbarSlot, SlotActionType.SWAP, mc.player);
		return true;
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
			if (to == -1) to = mc.player.getInventory().selectedSlot;
		}
		return moveToHotbar(from, to) ? to : -1;
	}

	private int find(Predicate<ItemStack> predicate, int from, int to) {
		if (mc.player == null) return -1;
		for (int i = from; i < to; i++) if (predicate.test(mc.player.getInventory().getStack(i))) return i;
		return -1;
	}
}
