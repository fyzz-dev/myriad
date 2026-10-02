package dev.myriad.api.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;

/**
 * The player's actions, done the way vanilla does them (cooldowns reset, sequence numbers kept, events posted), plus
 * the timers vanilla keeps for them. Use these instead of building the packets yourself.
 */
public final class Interactions {
	private Interactions() {
	}

	private static MinecraftClient mc() {
		return MinecraftClient.getInstance();
	}

	private static boolean ready() {
		return mc().player != null && mc().interactionManager != null;
	}

	/** Attacks {@code target} as a left click would, posting {@code AttackEvent}. */
	public static void attack(Entity target, boolean swing) {
		if (!ready()) return;
		mc().interactionManager.attackEntity(mc().player, target);
		if (swing) mc().player.swingHand(Hand.MAIN_HAND);
	}

	/** Right-clicks a block face (placing, opening, pressing). Returns what happened. */
	public static ActionResult interactBlock(BlockHitResult hit, Hand hand, boolean swing) {
		if (!ready()) return ActionResult.FAIL;
		ActionResult result = mc().interactionManager.interactBlock(mc().player, hand, hit);
		if (swing && result.isAccepted()) mc().player.swingHand(hand);
		return result;
	}

	/** Uses the item in {@code hand} (eating, throwing, shooting) as a right click in the air would. */
	public static ActionResult useItem(Hand hand) {
		if (!ready()) return ActionResult.FAIL;
		return mc().interactionManager.interactItem(mc().player, hand);
	}

	/** Swings the hand: the server sees it, and the client animates it. */
	public static void swing(Hand hand) {
		if (mc().player != null) mc().player.swingHand(hand);
	}

	/** Attack charge, 0..1 (1 = a full-strength hit). */
	public static float attackCharge() {
		return mc().player == null ? 0 : mc().player.getAttackCooldownProgress(0.5f);
	}

	/** Ticks since you last swung to attack (the counter attack charge comes from). */
	public static int ticksSinceAttack() {
		return mc().player == null ? 0 : mc().player.lastAttackedTicks;
	}

	/** Ticks until right-click can be used again (vanilla waits 4 between uses). */
	public static int itemUseCooldown() {
		return mc().itemUseCooldown;
	}

	/** Sets the right-click cooldown, e.g. 0 to use or place every tick. */
	public static void setItemUseCooldown(int ticks) {
		mc().itemUseCooldown = Math.max(0, ticks);
	}

	/** Sets the delay before the player can jump again (vanilla waits 10 ticks between held jumps). */
	public static void setJumpCooldown(int ticks) {
		if (mc().player != null) mc().player.jumpingCooldown = Math.max(0, ticks);
	}
}
