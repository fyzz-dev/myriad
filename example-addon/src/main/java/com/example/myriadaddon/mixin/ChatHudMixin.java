package com.example.myriadaddon.mixin;

import com.example.myriadaddon.modules.ChatTimestamps;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Addons ship mixins like any Fabric mod (see myriad-example.mixins.json). Keep them thin: hand off to the module and
 * let it decide. Prefix handler names with your mod id so they can't clash with other mods' mixins.
 */
@Mixin(ChatHud.class)
public abstract class ChatHudMixin {
	@ModifyVariable(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
		at = @At("HEAD"), argsOnly = true)
	private Text example$timestamp(Text message) {
		return ChatTimestamps.decorate(message);
	}
}
