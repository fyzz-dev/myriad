package dev.myriad.api.command.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.command.CommandSource;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** A player name; suggests names from the tab list. Any name is accepted. */
public class PlayerArgumentType implements ArgumentType<String> {
	public static PlayerArgumentType player() {
		return new PlayerArgumentType();
	}

	@Override
	public String parse(StringReader reader) {
		return ArgUtil.readWord(reader);
	}

	@Override
	public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
		var handler = MinecraftClient.getInstance().getNetworkHandler();
		List<String> names = handler == null ? List.of()
			: handler.getPlayerList().stream().map(PlayerListEntry::getProfile).map(p -> p.getName()).toList();
		return CommandSource.suggestMatching(names, builder);
	}
}
