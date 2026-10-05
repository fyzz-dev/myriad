package dev.myriad.impl.service;

import dev.myriad.api.Myriad;
import dev.myriad.api.build.Target;
import dev.myriad.api.event.Priority;
import dev.myriad.api.event.Subscribe;
import dev.myriad.api.event.events.InputEvent;
import dev.myriad.api.event.events.PacketEvent;
import dev.myriad.api.event.events.TickEvent;
import dev.myriad.api.event.events.WorldEvent;
import dev.myriad.api.service.Inventory;
import dev.myriad.api.service.PacketLimits;
import dev.myriad.api.util.Slots;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

public final class InventoryManager implements Inventory {
	private final Minecraft mc = Minecraft.getInstance();
	private volatile int serverSlot;
	private Object holder;
	private int holdTicks;

	/**
	 * What the server last heard of your movement: the keys of the last input packet (vanilla only sends one when they
	 * change) and whether you're sprinting. Grim judges a click by these, not by what you press now.
	 */
	private volatile Input sentInput = Input.EMPTY;
	private volatile boolean sentSprinting;

	/** Lowest priority: what actually goes out, after other handlers have changed it. */
	@Subscribe(priority = Priority.LOWEST)
	private void onSend(PacketEvent.Send e) {
		if (e.isCancelled()) return;
		switch (e.packet()) {
			case ServerboundSetCarriedItemPacket p -> serverSlot = p.getSlot();
			case ServerboundPlayerInputPacket p -> sentInput = p.input();
			case ServerboundPlayerCommandPacket p when p.getAction() == ServerboundPlayerCommandPacket.Action.START_SPRINTING -> sentSprinting = true;
			case ServerboundPlayerCommandPacket p when p.getAction() == ServerboundPlayerCommandPacket.Action.STOP_SPRINTING -> sentSprinting = false;
			default -> {
			}
		}
	}

	/** The server can set the slot itself (on join, or by a plugin); a respawn starts with no keys and no sprint. */
	@Subscribe
	private void onReceive(PacketEvent.Receive e) {
		if (e.packet() instanceof ClientboundSetHeldSlotPacket p) serverSlot = p.slot();
		if (e.packet() instanceof ClientboundRespawnPacket || e.packet() instanceof ClientboundLoginPacket) {
			sentInput = Input.EMPTY;
			sentSprinting = false;
		}
	}

	/** Expires holds at the start of the tick, so the slot change back goes out before movement, like vanilla's. */
	@Subscribe(priority = 950)
	private void onTick(TickEvent.Pre e) {
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
	public ItemStack serverItem() {
		if (mc.player == null) return ItemStack.EMPTY;
		var inv = mc.player.getInventory();
		return holder != null ? inv.getItem(serverSlot) : inv.getSelectedItem();
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
		int server = serverSlot, visible = mc.player.getInventory().getSelectedSlot();
		// Already held at the server (perhaps by a hold): nothing to send.
		if (server == hotbarSlot) {
			action.run();
			return;
		}
		if (hotbarSlot == visible) {
			// A hold has the server on another slot; switch it back for the action, then return to the hold.
			mc.getConnection().send(new ServerboundSetCarriedItemPacket(hotbarSlot));
			try {
				action.run();
			} finally {
				mc.getConnection().send(new ServerboundSetCarriedItemPacket(server));
			}
			return;
		}
		select(hotbarSlot);
		try {
			action.run();
		} finally {
			select(visible);
			if (server != visible) mc.getConnection().send(new ServerboundSetCarriedItemPacket(server));
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
		if (!canClick(1)) return false;
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

	/**
	 * Grim (MultiActionsC) cancels a click while the last input packet had a movement key or jump down, or sneak, or
	 * while you're sprinting. A click at the start of a tick goes out before that tick's input packet, so the one
	 * that counts is the last one sent.
	 */
	@Override
	public boolean safeToClick() {
		if (mc.player == null) return false;
		Input sent = sentInput;
		return !sent.forward() && !sent.backward() && !sent.left() && !sent.right() && !sent.jump() && !sent.shift() && !sentSprinting;
	}

	/** A still tick is wanted: the next input releases movement, jump, sneak and sprint. */
	private boolean stillRequested;
	/** Sprinting was stopped for a still tick; it's pressed again once you move forward. */
	private boolean resumeSprint;

	@Override
	public boolean prepareClick() {
		if (safeToClick()) return true;
		// Letting go of sneak on an edge, with what's left of your speed, would walk you off it: wait for it to settle.
		if (mc.player != null && mc.player.isShiftKeyDown() && mc.player.onGround() && wouldStepOffEdge()) return false;
		stillRequested = true;
		return false;
	}

	/** Whether this tick's motion, without sneak's edge guard, would take you off the block you stand on. */
	private boolean wouldStepOffEdge() {
		var p = mc.player;
		var v = p.getDeltaMovement();
		var box = p.getBoundingBox();
		return p.level().noCollision(p, new AABB(box.minX + 1e-7 + v.x, box.minY - p.maxUpStep() - 1e-7, box.minZ + 1e-7 + v.z,
			box.maxX - 1e-7 + v.x, box.minY, box.maxZ - 1e-7 + v.z));
	}

	/** Last, so it has the final say over what's sent: Grim takes these keys as what you were doing at the click. */
	@Subscribe(priority = Priority.LOWEST)
	private void onInput(InputEvent e) {
		if (mc.player == null) return;
		if (stillRequested) {
			stillRequested = false;
			e.forward = e.backward = e.left = e.right = e.jump = e.sneak = e.sprint = false;
			if (mc.player.isSprinting()) {
				mc.player.setSprinting(false);
				resumeSprint = true;
			}
		} else if (resumeSprint) {
			resumeSprint = false;
			if (e.forward) e.sprint = true;
		}
	}

	@Override
	public int pullToHotbar(int inventoryIndex, Predicate<ItemStack> replaceable) {
		if (mc.player == null || inventoryIndex < 9 || inventoryIndex >= 36 || !prepareClick()) return -1;
		int to = roomFor(replaceable);
		return to >= 0 && moveToHotbar(inventoryIndex, to) ? to : -1;
	}

	/**
	 * The hotbar slot to bring an item into: an empty one, else one {@code replaceable} accepts, else building blocks.
	 * Never the selected slot, the one the server holds, or one with something borrowed in it. -1 if none.
	 */
	private int roomFor(Predicate<ItemStack> replaceable) {
		var inv = mc.player.getInventory();
		int selected = inv.getSelectedSlot(), to = -1, blocks = -1;
		for (int i = 0; i < 9; i++) {
			if (i == selected || i == serverSlot || borrowAt(i) != null) continue;
			ItemStack s = inv.getItem(i);
			if (s.isEmpty()) return i;
			if (to < 0 && replaceable.test(s)) to = i;
			if (blocks < 0 && Target.solid().preference(s) >= 0) blocks = i;
		}
		return to >= 0 ? to : blocks;
	}

	// ---- borrowing ----------------------------------------------------------------------------------------------

	/** An item borrowed from {@code home} (9-35) into hotbar slot {@code slot}, to go back once it's no longer wanted. */
	private static final class Borrow {
		final Object owner;
		final int home, slot, selectedBefore;
		/** What was borrowed, and what it made room for (now at home; null if the hotbar slot was empty). */
		final Item item, displaced;
		int lastWanted, waited, outOfPlace;
		boolean giveBack;

		Borrow(Object owner, int home, int slot, int selectedBefore, Item item, Item displaced, int now) {
			this.owner = owner;
			this.home = home;
			this.slot = slot;
			this.selectedBefore = selectedBefore;
			this.item = item;
			this.displaced = displaced;
			this.lastWanted = now;
		}
	}

	/** A borrowed item goes back once nothing has wanted it for this long. */
	private static final int KEEP_TICKS = 10;
	/** Ticks a return waits for a moment you aren't moving before releasing your keys for one. */
	private static final int WAIT_FOR_STILL = 40;
	/**
	 * Ticks the items may look out of place before a borrow is forgotten: an inventory update the server sent before it
	 * had the borrowing click can arrive after it, showing the old places until the next one.
	 */
	private static final int OUT_OF_PLACE_TICKS = 20;

	private final List<Borrow> borrows = new ArrayList<>();
	private int ticks;

	@Override
	public int borrow(Object owner, int inventoryIndex, Predicate<ItemStack> replaceable) {
		if (mc.player == null || inventoryIndex < 0 || inventoryIndex >= 36) return -1;
		if (inventoryIndex < 9) {
			Borrow b = borrowAt(inventoryIndex);
			if (b != null) {
				b.lastWanted = ticks;
				b.giveBack = false;
			}
			return inventoryIndex;
		}
		if (!prepareClick()) return -1;
		int to = roomFor(replaceable == null ? s -> false : replaceable);
		if (to < 0) return -1;
		var inv = mc.player.getInventory();
		Item item = inv.getItem(inventoryIndex).getItem();
		ItemStack displaced = inv.getItem(to);
		Item displacedItem = displaced.isEmpty() ? null : displaced.getItem();
		if (!moveToHotbar(inventoryIndex, to)) return -1;
		borrows.add(new Borrow(owner, inventoryIndex, to, inv.getSelectedSlot(), item, displacedItem, ticks));
		return to;
	}

	@Override
	public void giveBack(Object owner) {
		for (Borrow b : borrows) if (b.owner == owner) b.giveBack = true;
	}

	@Override
	public ItemStack shownInHotbar(int hotbarSlot) {
		if (mc.player == null) return ItemStack.EMPTY;
		var inv = mc.player.getInventory();
		Borrow b = borrowAt(hotbarSlot);
		if (b == null || inv.getSelectedSlot() == hotbarSlot || !intact(b)) return inv.getItem(hotbarSlot);
		return inv.getItem(b.home);
	}

	private Borrow borrowAt(int hotbarSlot) {
		for (Borrow b : borrows) if (b.slot == hotbarSlot) return b;
		return null;
	}

	/** The borrowed item (or nothing, if it was used up) is still in its slot, and what it displaced at home. */
	private boolean intact(Borrow b) {
		var inv = mc.player.getInventory();
		ItemStack here = inv.getItem(b.slot), home = inv.getItem(b.home);
		return (here.isEmpty() || here.is(b.item)) && (b.displaced == null ? home.isEmpty() : home.is(b.displaced));
	}

	/**
	 * Puts back borrowed items nothing wants any more, one click a tick. A borrow stays while the server holds its slot
	 * (a hold, or a silent swap) or you're using it; one whose items have moved is forgotten where it is.
	 */
	@Subscribe(priority = 940)
	private void tickBorrows(TickEvent.Pre e) {
		ticks++;
		if (borrows.isEmpty() || mc.player == null) return;
		var inv = mc.player.getInventory();
		int selected = inv.getSelectedSlot();
		for (Borrow b : new ArrayList<>(borrows)) {
			if (!intact(b)) {
				if (++b.outOfPlace > OUT_OF_PLACE_TICKS) borrows.remove(b);
				continue;
			}
			b.outOfPlace = 0;
			boolean held = serverSlot == b.slot && (holder != null || serverSlot != selected);
			if (held || selected == b.slot && mc.player.isUsingItem()) b.lastWanted = ticks;
			if (held || !b.giveBack && ticks - b.lastWanted < KEEP_TICKS) continue;
			// A moment you aren't moving if one comes soon; otherwise your keys are released for a tick.
			if (!safeToClick() && !(++b.waited > WAIT_FOR_STILL && prepareClick())) continue;
			if (!canClick(1)) continue;
			if (selected == b.slot && b.selectedBefore != b.slot) select(b.selectedBefore);
			clickSwap(b.home, b.slot);
			borrows.remove(b);
			return;
		}
	}

	@Subscribe
	private void onLeave(WorldEvent.Leave e) {
		borrows.clear();
	}

	/** True if the player's own screen is the one open to clicks, and the packet budget has room for {@code clicks}. */
	private boolean canClick(int clicks) {
		return mc.player != null && mc.gameMode != null && mc.player.containerMenu == mc.player.inventoryMenu
			&& Myriad.limits().canSend(PacketLimits.Kind.INVENTORY, clicks);
	}

	private static boolean valid(int index) {
		return index >= 0 && index <= Slots.OFF_HAND;
	}

	private void click(int index, int button, ContainerInput action) {
		mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, Slots.playerScreen(index), button, action, mc.player);
	}

	@Override
	public boolean move(int from, int to) {
		if (!canClick(3) || !valid(from) || !valid(to)) return false;
		if (from == to) return true;
		if (Slots.isHotbar(to)) return clickSwap(from, to);
		if (Slots.isHotbar(from)) return clickSwap(to, from);
		// Pick up, put down (swapping with what's there), then put back whatever ended up on the cursor.
		click(from, 0, ContainerInput.PICKUP);
		click(to, 0, ContainerInput.PICKUP);
		if (!mc.player.containerMenu.getCarried().isEmpty()) click(from, 0, ContainerInput.PICKUP);
		return true;
	}

	@Override
	public boolean merge(int from, int to) {
		if (!valid(from) || !valid(to) || from == to) return false;
		var inv = mc.player == null ? null : mc.player.getInventory();
		if (inv == null || !ItemStack.isSameItemSameComponents(inv.getItem(from), inv.getItem(to)) || inv.getItem(from).isEmpty()) return false;
		if (!canClick(3)) return false;
		// Pick the source up, drop it on the target (they combine), and put any remainder back.
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
		if (!canClick(1) || !valid(inventoryIndex) || inventoryIndex == Slots.OFF_HAND) return false;
		// Button 40 is the off hand swap key.
		click(inventoryIndex, 40, ContainerInput.SWAP);
		return true;
	}

	@Override
	public boolean quickMove(int inventoryIndex) {
		if (!canClick(1) || !valid(inventoryIndex)) return false;
		click(inventoryIndex, 0, ContainerInput.QUICK_MOVE);
		return true;
	}

	@Override
	public boolean drop(int inventoryIndex, boolean wholeStack) {
		if (!canClick(1) || !valid(inventoryIndex)) return false;
		click(inventoryIndex, wholeStack ? 1 : 0, ContainerInput.THROW);
		return true;
	}

	private int find(Predicate<ItemStack> predicate, int from, int to) {
		if (mc.player == null) return -1;
		for (int i = from; i < to; i++) if (predicate.test(mc.player.getInventory().getItem(i))) return i;
		return -1;
	}
}
