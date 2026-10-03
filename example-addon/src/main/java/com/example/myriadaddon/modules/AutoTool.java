package com.example.myriadaddon.modules;

import dev.myriad.api.Myriad;
import dev.myriad.api.event.Subscribe;
import dev.myriad.api.event.events.BlockBreakEvent;
import dev.myriad.api.event.events.TickEvent;
import dev.myriad.api.module.Categories;
import dev.myriad.api.module.Module;
import dev.myriad.api.setting.BoolSetting;
import dev.myriad.api.setting.IntSetting;
import dev.myriad.api.util.Mining;
import dev.myriad.api.util.MyriadId;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Switches to the best hotbar tool when you start mining, and back afterwards.
 *
 * <p>Shows:
 * <ul>
 *   <li>using a shared service ({@code Myriad.inventory()}) instead of poking the inventory directly, so slot changes
 *       stay in sync with other features;</li>
 *   <li>using core helpers ({@link Mining}) instead of re-deriving vanilla maths;</li>
 *   <li>soft integration with another addon by id, without compiling against it: if Myriad Essentials' Speed Mine is
 *       on, it manages tools itself and this module stays out of its way.</li>
 * </ul>
 */
public final class AutoTool extends Module {
	/** Another addon's module, referenced by id so this addon still works when that one isn't installed. */
	private static final MyriadId SPEED_MINE = MyriadId.of("myriad-essentials", "speed_mine");

	private final BoolSetting switchBack = sgGeneral.bool("Switch Back").description("Return to the previous slot when you stop mining.").defaultValue(true).build();
	private final IntSetting keepDurability = sgGeneral.intSetting("Keep Durability").description("Skip tools with this much durability or less, so they don't break.")
		.defaultValue(5).range(0, 100).build();

	private int previousSlot = -1;

	public AutoTool() {
		super(Categories.PLAYER, "Auto Tool", "Switches to the best tool for the block you're mining.");
	}

	@Override
	protected void onDisable() {
		restore();
	}

	@Subscribe
	private void onStartBreaking(BlockBreakEvent.Start e) {
		if (!inGame() || speedMineActive()) return;
		BlockState state = mc.level.getBlockState(e.pos());
		int best = bestSlot(state);
		int current = mc.player.getInventory().getSelectedSlot();
		if (best == -1 || best == current) return;
		if (previousSlot == -1) previousSlot = current;
		Myriad.inventory().select(best);
	}

	@Subscribe
	private void onTick(TickEvent.Post e) {
		if (previousSlot != -1 && inGame() && !mc.gameMode.isDestroying()) restore();
	}

	private void restore() {
		if (previousSlot != -1 && switchBack.get() && inGame()) Myriad.inventory().select(previousSlot);
		previousSlot = -1;
	}

	/** The hotbar slot that mines {@code state} fastest, or -1 if nothing beats what you're holding. */
	private int bestSlot(BlockState state) {
		int best = -1;
		float bestSpeed = Mining.speed(state, mc.player.getInventory().getSelectedSlot());
		for (int i = 0; i < 9; i++) {
			ItemStack stack = mc.player.getInventory().getItem(i);
			if (stack.isDamageableItem() && stack.getMaxDamage() - stack.getDamageValue() <= keepDurability.get()) continue;
			float speed = Mining.speed(state, i);
			if (speed > bestSpeed) {
				bestSpeed = speed;
				best = i;
			}
		}
		return best;
	}

	private static boolean speedMineActive() {
		return Myriad.modules().get(SPEED_MINE).map(Module::isEnabled).orElse(false);
	}
}
