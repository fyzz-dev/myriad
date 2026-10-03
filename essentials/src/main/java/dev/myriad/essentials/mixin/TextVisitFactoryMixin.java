package dev.myriad.essentials.mixin;

import dev.myriad.essentials.modules.render.NameProtect;
import net.minecraft.util.StringDecomposer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(StringDecomposer.class)
public abstract class TextVisitFactoryMixin {
	/** Name Protect: every formatted string drawn by the text renderer passes through here. */
	@ModifyVariable(method = "iterateFormatted(Ljava/lang/String;ILnet/minecraft/network/chat/Style;Lnet/minecraft/network/chat/Style;Lnet/minecraft/util/FormattedCharSink;)Z",
		at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private static String essentials$nameProtect(String text) {
		return NameProtect.replace(text);
	}
}
