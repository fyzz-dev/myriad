package dev.myriad.essentials.modules.player;

import dev.myriad.api.event.Subscribe;
import dev.myriad.api.event.events.MovementPacketsEvent;
import dev.myriad.api.module.Categories;
import dev.myriad.api.module.Module;
import dev.myriad.api.module.Modules;
import dev.myriad.api.setting.EnumSetting;
import dev.myriad.essentials.modules.movement.Flight;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

/**
 * Prevents fall damage. Modes trade compatibility for safety:
 * <ul>
 * <li>Anti: nudges the position you send up by 0.1 so the server never sees you land from the fall.</li>
 * <li>Packet: claims you are on the ground the whole way down.</li>
 * <li>Limit: sends one packet far away (or at y=0 in the Nether) that resets your fall distance.</li>
 * <li>Strict: as you near the ground, snaps the sent position onto it and stops your descent.</li>
 * <li>Grim: sends a ground packet a hair above you and lands client-side each tick.</li>
 * </ul>
 */
public class NoFall extends Module {
	public enum Mode {
		ANTI, LIMIT, PACKET, STRICT, GRIM
	}

	private final EnumSetting<Mode> mode = sgGeneral.enumSetting("Mode", Mode.ANTI).build();

	public NoFall() {
		super(Categories.PLAYER, "No Fall", "Prevents fall damage.");
	}

	@Override
	public String hudInfo() {
		return mode.get().name().charAt(0) + mode.get().name().substring(1).toLowerCase();
	}

	private boolean falling() {
		var p = mc.player;
		return p.fallDistance > p.getSafeFallDistance() && !p.isOnGround() && !p.isGliding() && !p.getAbilities().creativeMode
			&& !Modules.isActive(Flight.class);
	}

	@Subscribe
	private void onMovement(MovementPacketsEvent e) {
		if (!inGame() || !falling()) return;
		var p = mc.player;
		switch (mode.get()) {
			case PACKET -> e.onGround = true;
			case ANTI -> e.y += 0.10000000149011612;
			case STRICT -> {
				double ground = groundY() - 0.1;
				if (p.getY() - ground < 3.0 && p.fallDistance >= 3f) {
					p.setVelocity(p.getVelocity().x, 0, p.getVelocity().z);
					e.y = ground;
					p.fallDistance = 0;
				}
			}
			case LIMIT -> {
				if (mc.world.getRegistryKey() == World.NETHER) {
					send(new PlayerMoveC2SPacket.PositionAndOnGround(p.getX(), 0, p.getZ(), true, p.horizontalCollision));
				} else {
					send(new PlayerMoveC2SPacket.PositionAndOnGround(0, 64, 0, true, p.horizontalCollision));
				}
				p.fallDistance = 0;
			}
			case GRIM -> {
				send(new PlayerMoveC2SPacket.Full(p.getX(), p.getY() + 1.0e-9, p.getZ(), p.getYaw(), p.getPitch(), true, p.horizontalCollision));
				p.onLanding();
			}
		}
	}

	private void send(PlayerMoveC2SPacket packet) {
		mc.getNetworkHandler().sendPacket(packet);
	}

	/** The top of the first solid layer below the player. */
	private double groundY() {
		Box bb = mc.player.getBoundingBox();
		for (int y = (int) Math.round(mc.player.getY()); y > mc.world.getBottomY(); y--) {
			Box below = new Box(bb.minX, y - 1.0, bb.minZ, bb.maxX, y, bb.maxZ);
			if (!mc.world.isSpaceEmpty(below) || below.minY > mc.player.getY()) continue;
			return y;
		}
		return mc.world.getBottomY();
	}
}
