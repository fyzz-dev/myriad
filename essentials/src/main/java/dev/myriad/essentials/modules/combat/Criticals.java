package dev.myriad.essentials.modules.combat;

import dev.myriad.api.Myriad;
import dev.myriad.api.event.Priority;
import dev.myriad.api.event.Subscribe;
import dev.myriad.api.event.events.AttackEvent;
import dev.myriad.api.module.Categories;
import dev.myriad.api.module.Module;
import dev.myriad.api.setting.BoolSetting;
import dev.myriad.api.setting.EnumSetting;
import dev.myriad.api.util.Packets;
import dev.myriad.api.util.Timer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.registry.tag.ItemTags;

/**
 * Turns hits into critical hits by telling the server you hopped a fraction of a block just before attacking.
 * Packet works on most servers; Strict sends a three-step hop that strict anticheats accept, but only while you're
 * standing still inside a block. Sprinting is paused around the hop (a sprint hit can't crit).
 */
public class Criticals extends Module {
	public enum Mode {
		PACKET, STRICT
	}

	private static final double OFFSET = 0.0626, OFFSET_2 = 0.0455;

	/** When the last crit hop was sent; Velocity holds off briefly so the hop isn't undone. */
	private final Timer sinceHop = new Timer();

	private final EnumSetting<Mode> mode = sgGeneral.enumSetting("Mode", Mode.PACKET).build();
	private final BoolSetting swordOnly = sgGeneral.bool("Sword Only").description("Only crit while holding a sword or axe.").build();

	public Criticals() {
		super(Categories.COMBAT, "Criticals", "Makes your hits critical hits.");
	}

	@Override
	public String hudInfo() {
		return mode.get() == Mode.PACKET ? "Packet" : "Strict";
	}

	/** Whether a crit hop went out in the last {@code ms} milliseconds. */
	public boolean hoppedWithin(long ms) {
		return !sinceHop.passed(ms);
	}

	/** Runs just before the attack packet goes out, so the hop packets reach the server first. */
	@Subscribe(priority = Priority.LOWEST)
	private void onAttack(AttackEvent e) {
		if (!inGame()) return;
		Entity target = e.target();
		if (mode.get() == Mode.STRICT) {
			crit2b2t(target);
			return;
		}
		if (swordOnly.get()) {
			ItemStack held = mc.player.getMainHandStack();
			if (!held.isIn(ItemTags.SWORDS) && !held.isIn(ItemTags.AXES)) return;
		}
		if (!canCrit(target)) return;
		var pl = mc.player;
		boolean sprinting = pl.isSprinting();
		if (sprinting) Packets.sendSilently(new ClientCommandC2SPacket(pl, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
		float yaw = Myriad.rotations().serverYaw(), pitch = Myriad.rotations().serverPitch();
		Packets.sendSilently(new PlayerMoveC2SPacket.Full(pl.getX(), pl.getY() + OFFSET, pl.getZ(), yaw, pitch, false, pl.horizontalCollision));
		Packets.sendSilently(new PlayerMoveC2SPacket.Full(pl.getX(), pl.getY(), pl.getZ(), yaw, pitch, false, pl.horizontalCollision));
		pl.addCritParticles(target);
		sinceHop.reset();
		if (sprinting) Packets.sendSilently(new ClientCommandC2SPacket(pl, ClientCommandC2SPacket.Mode.START_SPRINTING));
	}

	private boolean canCrit(Entity target) {
		if (!(target instanceof LivingEntity) || !target.isAlive()) return false;
		var p = mc.player;
		return !p.hasVehicle() && !p.isGliding() && !p.isTouchingWater() && !p.isInLava() && !p.isHoldingOntoLadder() && !p.hasStatusEffect(StatusEffects.BLINDNESS);
	}

	private void crit2b2t(Entity target) {
		var p = mc.player;
		if (!(target instanceof LivingEntity) || target instanceof EndCrystalEntity || !p.isOnGround()) return;
		var in = p.input.playerInput;
		if (!p.isInsideWall() || in.forward() || in.backward() || in.left() || in.right()) return;
		boolean sprinting = p.isSprinting(), collision = p.horizontalCollision;
		if (sprinting) Packets.sendSilently(new ClientCommandC2SPacket(p, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
		Packets.sendSilently(new PlayerMoveC2SPacket.PositionAndOnGround(p.getX(), p.getY(), p.getZ(), false, collision));
		Packets.sendSilently(new PlayerMoveC2SPacket.PositionAndOnGround(p.getX(), p.getY() + OFFSET, p.getZ(), false, collision));
		Packets.sendSilently(new PlayerMoveC2SPacket.PositionAndOnGround(p.getX(), p.getY() + OFFSET_2, p.getZ(), false, collision));
		p.addCritParticles(target);
		if (sprinting) Packets.sendSilently(new ClientCommandC2SPacket(p, ClientCommandC2SPacket.Mode.START_SPRINTING));
		sinceHop.reset();
	}
}
