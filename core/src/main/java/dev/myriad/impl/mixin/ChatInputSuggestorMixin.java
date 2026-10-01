package dev.myriad.impl.mixin;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.suggestion.Suggestions;
import dev.myriad.api.Myriad;
import dev.myriad.impl.MyriadImpl;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.command.CommandSource;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.concurrent.CompletableFuture;

/** Feeds Myriad's dispatcher into vanilla chat autocompletion when the input starts with the command prefix. */
@Mixin(ChatInputSuggestor.class)
public abstract class ChatInputSuggestorMixin {
	@Shadow
	private ParseResults<CommandSource> parse;
	@Shadow
	@Final
	TextFieldWidget textField;
	@Shadow
	boolean completingSuggestions;
	@Shadow
	@Nullable
	private ChatInputSuggestor.SuggestionWindow window;
	@Shadow
	@Nullable
	private CompletableFuture<Suggestions> pendingSuggestions;

	@Shadow
	protected abstract void showCommandSuggestions();

	@Inject(method = "refresh", at = @At(value = "INVOKE", target = "Lcom/mojang/brigadier/StringReader;canRead()Z", remap = false),
		cancellable = true, locals = LocalCapture.CAPTURE_FAILHARD)
	private void myriad$refresh(CallbackInfo ci, String input, StringReader reader) {
		if (!Myriad.isReady()) return;
		String prefix = Myriad.config().commandPrefix();
		if (!reader.getString().startsWith(prefix, reader.getCursor())) return;
		reader.setCursor(reader.getCursor() + prefix.length());
		var commands = MyriadImpl.get().commandManager();
		if (parse == null) parse = commands.dispatcher().parse(reader, commands.source());
		int cursor = textField.getCursor();
		if (cursor >= prefix.length() && (window == null || !completingSuggestions)) {
			pendingSuggestions = commands.dispatcher().getCompletionSuggestions(parse, cursor);
			pendingSuggestions.thenRun(() -> {
				if (pendingSuggestions.isDone()) showCommandSuggestions();
			});
		}
		ci.cancel();
	}
}
