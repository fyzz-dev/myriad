package dev.myriad.essentials.mixin;

import dev.myriad.api.module.Modules;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.myriad.essentials.modules.render.ExtraTab;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(PlayerListHud.class)
public abstract class PlayerListHudMixin {
	@ModifyConstant(method = "collectPlayerEntries", constant = @Constant(longValue = 80L))
	private long essentials$tabSize(long original) {
		ExtraTab tab = Modules.active(ExtraTab.class);
		return tab != null ? tab.tabSize() : original;
	}

	@ModifyReturnValue(method = "collectPlayerEntries", at = @At("RETURN"))
	private List<PlayerListEntry> essentials$sort(List<PlayerListEntry> original) {
		ExtraTab tab = Modules.active(ExtraTab.class);
		return tab != null ? tab.sort(original) : original;
	}

	@ModifyReturnValue(method = "getPlayerName", at = @At("RETURN"))
	private Text essentials$name(Text original, PlayerListEntry entry) {
		ExtraTab tab = Modules.active(ExtraTab.class);
		return tab != null ? tab.name(entry, original) : original;
	}

	@Inject(method = "renderLatencyIcon", at = @At("HEAD"), cancellable = true)
	private void essentials$latency(DrawContext context, int width, int x, int y, PlayerListEntry entry, CallbackInfo ci) {
		ExtraTab tab = Modules.active(ExtraTab.class);
		if (tab != null && tab.drawLatency(context, width, x, y, entry)) ci.cancel();
	}
}
