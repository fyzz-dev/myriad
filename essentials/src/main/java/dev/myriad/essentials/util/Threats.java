package dev.myriad.essentials.util;

import dev.myriad.api.Myriad;
import dev.myriad.api.combat.Damage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * How much damage nearby dangers could do to you right now: end crystals, beds (which explode outside the Overworld),
 * charged respawn anchors (which explode outside the Nether), players about to hit you, and the fall you're in. Offhand
 * and Auto Disconnect use it to decide when a hit could kill you.
 */
public final class Threats {
	private Threats() {
	}

	private static LocalPlayer player() {
		return Minecraft.getInstance().player;
	}

	/** Your health including absorption. */
	public static float health() {
		return player().getHealth() + player().getAbsorptionAmount();
	}

	/** The worst crystal within {@code range}, or all of them added up when {@code sum}. */
	public static float crystals(double range, boolean sum) {
		float max = 0, total = 0;
		LocalPlayer p = player();
		for (var e : p.level().getEntities(p, p.getBoundingBox().inflate(range), en -> en instanceof EndCrystal)) {
			float d = Damage.crystal(p, e.position());
			total += d;
			max = Math.max(max, d);
		}
		return sum ? total : max;
	}

	/** The worst bed within {@code range} blocks; 0 in the Overworld, where beds don't explode. */
	public static float beds(int range) {
		LocalPlayer p = player();
		if (p.level().dimension() == Level.OVERWORLD) return 0;
		float max = 0;
		for (BlockPos pos : BlockPos.betweenClosed(p.blockPosition().offset(-range, -range, -range), p.blockPosition().offset(range, range, range))) {
			if (p.level().getBlockState(pos).getBlock() instanceof BedBlock) max = Math.max(max, Damage.bed(p, pos));
		}
		return max;
	}

	/** The worst charged respawn anchor within {@code range} blocks; 0 in the Nether, where anchors work. */
	public static float anchors(int range) {
		LocalPlayer p = player();
		if (p.level().dimension() == Level.NETHER) return 0;
		float max = 0;
		for (BlockPos pos : BlockPos.betweenClosed(p.blockPosition().offset(-range, -range, -range), p.blockPosition().offset(range, range, range))) {
			BlockState s = p.level().getBlockState(pos);
			if (s.getBlock() instanceof RespawnAnchorBlock && s.getValue(RespawnAnchorBlock.CHARGE) > 0) max = Math.max(max, Damage.anchor(p, pos));
		}
		return max;
	}

	/** The hardest hit a non-friend within {@code range} could land. */
	public static float players(double range) {
		LocalPlayer p = player();
		float max = 0;
		for (Player other : p.level().getEntitiesOfClass(Player.class, p.getBoundingBox().inflate(range), o -> o != p && !Myriad.friends().isFriend(o))) {
			max = Math.max(max, Damage.melee(other, p));
		}
		return max;
	}

	/** Damage from the fall you're in, if you landed now. */
	public static float fall() {
		LocalPlayer p = player();
		return p.fallDistance > 3 ? Damage.fall(p, (float) p.fallDistance) : 0;
	}
}
