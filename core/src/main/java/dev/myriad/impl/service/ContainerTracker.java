package dev.myriad.impl.service;

import dev.myriad.api.Myriad;
import dev.myriad.api.event.Subscribe;
import dev.myriad.api.event.events.ContainerEvent;
import dev.myriad.api.event.events.PacketEvent;
import dev.myriad.api.event.events.TickEvent;
import dev.myriad.api.event.events.WorldEvent;
import dev.myriad.api.service.Containers;
import dev.myriad.api.util.Interactions;
import dev.myriad.api.util.Reach;
import dev.myriad.api.util.Slots;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Follows the open container from the network handler's open/contents packets (see ClientPlayNetworkHandlerMixin),
 * and remembers the last block clicked so a container knows where it came from.
 */
public final class ContainerTracker implements Containers {
	/** A click more than this long before the screen opened isn't what opened it. */
	private static final long CLICK_WINDOW_MS = 2000;

	private final Minecraft mc = Minecraft.getInstance();
	private ViewImpl current;
	private BlockPos lastClicked;
	private long lastClickedAt;
	private CompletableFuture<View> pending;
	private BlockPos pendingPos;
	private int pendingTicks;

	// ---- called from ClientPlayNetworkHandlerMixin (render thread, after vanilla handled the packet) ---------------

	public void onOpened(int syncId) {
		closeTracked();
		if (mc.player == null || mc.player.containerMenu.containerId != syncId || syncId == 0) return;
		BlockPos pos = lastClicked != null && System.currentTimeMillis() - lastClickedAt < CLICK_WINDOW_MS ? lastClicked : null;
		current = new ViewImpl(mc.player.containerMenu, pos);
		Myriad.events().post(new ContainerEvent.Opened(current));
	}

	public void onContents(int syncId) {
		if (current == null || current.syncId() != syncId || current.loaded) return;
		current.loaded = true;
		Myriad.events().post(new ContainerEvent.Loaded(current));
		if (pending != null && (pendingPos == null || pendingPos.equals(current.pos))) {
			CompletableFuture<View> f = pending;
			pending = null;
			f.complete(current);
		}
	}

	public void onSlot(int syncId, int slot) {
		if (current == null || current.syncId() != syncId || slot < 0) return;
		Myriad.events().post(new ContainerEvent.SlotUpdated(current, slot));
	}

	// ---- tracking ---------------------------------------------------------------------------------------------------

	@Subscribe
	private void onSend(PacketEvent.Send e) {
		if (e.packet() instanceof ServerboundUseItemOnPacket p) {
			lastClicked = p.getHitResult().getBlockPos().immutable();
			lastClickedAt = System.currentTimeMillis();
		}
	}

	@Subscribe
	private void onTick(TickEvent.Post e) {
		if (current != null && (mc.player == null || mc.player.containerMenu != current.handler)) closeTracked();
		if (pending != null && --pendingTicks <= 0) fail(new TimeoutException("Container at " + pendingPos + " didn't open"));
	}

	@Subscribe
	private void onLeave(WorldEvent.Leave e) {
		closeTracked();
		if (pending != null) fail(new IllegalStateException("Left the world"));
	}

	private void closeTracked() {
		if (current == null) return;
		ViewImpl closed = current;
		current = null;
		Myriad.events().post(new ContainerEvent.Closed(closed));
	}

	private void fail(Throwable t) {
		CompletableFuture<View> f = pending;
		pending = null;
		if (f != null) f.completeExceptionally(t);
	}

	// ---- API --------------------------------------------------------------------------------------------------------

	@Override
	public Optional<View> current() {
		return Optional.ofNullable(current);
	}

	@Override
	public CompletableFuture<View> open(BlockPos pos, int timeoutTicks) {
		if (pending != null) fail(new IllegalStateException("Replaced by another open()"));
		CompletableFuture<View> f = new CompletableFuture<>();
		BlockHitResult hit = Reach.hitFor(pos, false);
		if (mc.player == null || hit == null) {
			f.completeExceptionally(new IllegalStateException("Can't reach " + pos));
			return f;
		}
		if (current != null && pos.equals(current.pos) && current.loaded) {
			f.complete(current);
			return f;
		}
		pending = f;
		pendingPos = pos.immutable();
		pendingTicks = Math.max(1, timeoutTicks);
		// Sneaking with an item in hand would place it instead of opening the container.
		boolean sneaking = mc.player.isShiftKeyDown();
		if (sneaking) mc.player.setShiftKeyDown(false);
		Interactions.interactBlock(hit, InteractionHand.MAIN_HAND, true);
		if (sneaking) mc.player.setShiftKeyDown(true);
		return f;
	}

	@Override
	public void close() {
		if (mc.player != null && current != null) mc.player.closeContainer();
	}

	private final class ViewImpl implements View {
		private final AbstractContainerMenu handler;
		private final BlockPos pos;
		private boolean loaded;

		ViewImpl(AbstractContainerMenu handler, BlockPos pos) {
			this.handler = handler;
			this.pos = pos;
		}

		@Override
		public AbstractContainerMenu handler() {
			return handler;
		}

		@Override
		public int syncId() {
			return handler.containerId;
		}

		@Override
		public @Nullable BlockPos pos() {
			return pos;
		}

		@Override
		public boolean isLoaded() {
			return loaded;
		}

		@Override
		public boolean isOpen() {
			return current == this;
		}

		@Override
		public int size() {
			return Math.max(0, handler.slots.size() - 36);
		}

		@Override
		public ItemStack stack(int slot) {
			return slot >= 0 && slot < size() ? handler.getSlot(slot).getItem() : ItemStack.EMPTY;
		}

		@Override
		public List<ItemStack> stacks() {
			List<ItemStack> out = new ArrayList<>(size());
			for (int i = 0; i < size(); i++) out.add(stack(i));
			return out;
		}

		@Override
		public int find(Predicate<ItemStack> predicate) {
			for (int i = 0; i < size(); i++) if (!stack(i).isEmpty() && predicate.test(stack(i))) return i;
			return -1;
		}

		@Override
		public int emptySlot() {
			for (int i = 0; i < size(); i++) if (stack(i).isEmpty()) return i;
			return -1;
		}

		@Override
		public int count(Predicate<ItemStack> predicate) {
			int n = 0;
			for (int i = 0; i < size(); i++) if (!stack(i).isEmpty() && predicate.test(stack(i))) n += stack(i).getCount();
			return n;
		}

		@Override
		public int playerSlot(int inventoryIndex) {
			return Slots.containerScreen(size(), inventoryIndex);
		}

		@Override
		public void quickMove(int slot) {
			click(slot, 0, ContainerInput.QUICK_MOVE);
		}

		@Override
		public void quickMoveFromPlayer(int inventoryIndex) {
			click(playerSlot(inventoryIndex), 0, ContainerInput.QUICK_MOVE);
		}

		@Override
		public void swapWithHotbar(int slot, int hotbarSlot) {
			click(slot, hotbarSlot, ContainerInput.SWAP);
		}

		@Override
		public void drop(int slot, boolean wholeStack) {
			click(slot, wholeStack ? 1 : 0, ContainerInput.THROW);
		}

		@Override
		public void click(int screenSlot, int button, ContainerInput action) {
			if (!isOpen() || mc.player == null || mc.gameMode == null) return;
			mc.gameMode.handleContainerInput(handler.containerId, screenSlot, button, action, mc.player);
		}

		@Override
		public void close() {
			if (isOpen()) ContainerTracker.this.close();
		}
	}
}
