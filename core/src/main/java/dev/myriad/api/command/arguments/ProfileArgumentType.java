package dev.myriad.api.command.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.myriad.api.Myriad;
import net.minecraft.command.CommandSource;

import java.util.concurrent.CompletableFuture;

/** A config profile name; suggests existing profiles. */
public class ProfileArgumentType implements ArgumentType<String> {
	public static ProfileArgumentType profile() {
		return new ProfileArgumentType();
	}

	@Override
	public String parse(StringReader reader) {
		return ArgUtil.readWord(reader);
	}

	@Override
	public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
		return CommandSource.suggestMatching(Myriad.config().profiles(), builder);
	}
}
