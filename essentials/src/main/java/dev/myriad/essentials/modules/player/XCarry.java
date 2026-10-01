package dev.myriad.essentials.modules.player;

import dev.myriad.api.event.Subscribe;
import dev.myriad.api.event.events.PacketEvent;
import dev.myriad.api.module.Categories;
import dev.myriad.api.module.Module;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;

/**
 * Keeps items in your 2x2 crafting grid after closing your inventory, by never telling the server it was closed.
 * Containers still close normally. Turning the module off closes the inventory for real, which drops the items
 * back into your inventory.
 */
public class XCarry extends Module {
	public XCarry() {
		super(Categories.PLAYER, "X Carry", "Use the crafting grid as four extra inventory slots.");
	}

	@Subscribe
	private void onSend(PacketEvent.Send e) {
		if (mc.player != null && e.packet() instanceof CloseHandledScreenC2SPacket p && p.getSyncId() == mc.player.playerScreenHandler.syncId) e.cancel();
	}

	@Override
	protected void onDisable() {
		if (inGame()) mc.getNetworkHandler().sendPacket(new CloseHandledScreenC2SPacket(mc.player.playerScreenHandler.syncId));
	}
}
