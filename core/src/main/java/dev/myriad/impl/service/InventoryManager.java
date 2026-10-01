package dev.myriad.impl.service;

import dev.myriad.api.event.Subscribe;
import dev.myriad.api.event.events.PacketEvent;
import dev.myriad.api.event.events.TickEvent;
import dev.myriad.api.service.Inventory;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;

import java.util.function.Predicate;

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

	private int find(Predicate<ItemStack> predicate, int from, int to) {
		if (mc.player == null) return -1;
		for (int i = from; i < to; i++) if (predicate.test(mc.player.getInventory().getStack(i))) return i;
		return -1;
	}
}
