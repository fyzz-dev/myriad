package dev.myriad.essentials.modules.misc;

import dev.myriad.api.Myriad;
import dev.myriad.api.event.Subscribe;
import dev.myriad.api.event.events.MouseButtonEvent;
import dev.myriad.api.module.Categories;
import dev.myriad.api.module.Module;
import dev.myriad.api.setting.BoolSetting;
import dev.myriad.api.util.Interactions;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

/**
 * Context actions on the middle mouse button: launch a firework while flying with an elytra, add or remove the
 * player you're looking at as a friend, or throw an ender pearl. Each action can be turned off.
 */
public class MiddleClick extends Module {
	private final BoolSetting rocket = sgGeneral.bool("Rocket").description("Launch a firework while flying with an elytra.").defaultValue(true).build();
	private final BoolSetting friend = sgGeneral.bool("Friend").description("Add or remove the player you're looking at as a friend.").defaultValue(true).build();
	private final BoolSetting pearl = sgGeneral.bool("Pearl").description("Throw an ender pearl when not flying and not looking at a player.").build();
	private final BoolSetting whileUsing = sgGeneral.bool("While Using").description("Allow actions while eating or blocking.").build();

	public MiddleClick() {
		super(Categories.MISC, "Middle Click", "Context actions on the middle mouse button.");
	}

	@Subscribe
	private void onMouse(MouseButtonEvent e) {
		if (!inGame() || e.inScreen() || !e.isPress() || e.button() != GLFW.GLFW_MOUSE_BUTTON_MIDDLE) return;
		if (mc.player.isUsingItem() && !whileUsing.get()) return;
		if (mc.player.isFallFlying()) {
			if (rocket.get() && use(Items.FIREWORK_ROCKET)) e.cancel();
			return;
		}
		if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.ENTITY
			&& ((EntityHitResult) mc.hitResult).getEntity() instanceof Player p) {
			if (friend.get()) {
				toggleFriend(p);
				e.cancel();
			}
			return;
		}
		if (pearl.get() && use(Items.ENDER_PEARL)) e.cancel();
	}

	private void toggleFriend(Player p) {
		String name = p.getGameProfile().name();
		if (Myriad.friends().isFriend(name)) {
			Myriad.friends().remove(name);
			info("Removed " + name + " from friends");
		} else {
			Myriad.friends().add(name);
			info("Added " + name + " to friends");
		}
	}

	/** Uses {@code item} from the hotbar without changing the visible slot. */
	private boolean use(Item item) {
		if (mc.player.getOffhandItem().is(item)) {
			Interactions.useItem(InteractionHand.OFF_HAND);
			Interactions.swing(InteractionHand.OFF_HAND);
			return true;
		}
		int slot = Myriad.inventory().findInHotbar(s -> s.is(item));
		if (slot < 0) return false;
		Myriad.inventory().silentSwap(slot, () -> {
			Interactions.useItem(InteractionHand.MAIN_HAND);
			Interactions.swing(InteractionHand.MAIN_HAND);
		});
		return true;
	}
}
