package dev.myriad.api.command.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.block.Block;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.concurrent.CompletableFuture;

/** An entry of a game registry by id ({@code diamond_pickaxe} or {@code minecraft:diamond_pickaxe}), with suggestions. */
public class RegistryArgumentType<T> implements ArgumentType<T> {
	private final Registry<T> registry;
	private final DynamicCommandExceptionType unknown;

	RegistryArgumentType(Registry<T> registry, String what) {
		this.registry = registry;
		this.unknown = new DynamicCommandExceptionType(id -> Text.literal("Unknown " + what + " '" + id + "'"));
	}

	public static RegistryArgumentType<Item> item() {
		return new RegistryArgumentType<>(Registries.ITEM, "item");
	}

	public static RegistryArgumentType<Block> block() {
		return new RegistryArgumentType<>(Registries.BLOCK, "block");
	}

	public static RegistryArgumentType<EntityType<?>> entityType() {
		return new RegistryArgumentType<>(Registries.ENTITY_TYPE, "entity type");
	}

	/** Any registry; {@code what} names it in error messages ("enchantment"). */
	public static <T> RegistryArgumentType<T> of(Registry<T> registry, String what) {
		return new RegistryArgumentType<>(registry, what);
	}

	public static <T> T get(CommandContext<?> ctx, String name, Class<T> type) {
		return ctx.getArgument(name, type);
	}

	public static Item getItem(CommandContext<?> ctx, String name) {
		return ctx.getArgument(name, Item.class);
	}

	public static Block getBlock(CommandContext<?> ctx, String name) {
		return ctx.getArgument(name, Block.class);
	}

	public static EntityType<?> getEntityType(CommandContext<?> ctx, String name) {
		return ctx.getArgument(name, EntityType.class);
	}

	@Override
	public T parse(StringReader reader) throws CommandSyntaxException {
		int start = reader.getCursor();
		String raw = ArgUtil.readWord(reader);
		Identifier id = Identifier.tryParse(raw);
		if (id == null || !registry.containsId(id)) {
			reader.setCursor(start);
			throw unknown.createWithContext(reader, raw);
		}
		return registry.get(id);
	}

	@Override
	public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
		// Vanilla entries are suggested without "minecraft:", the way players type them.
		return CommandSource.suggestMatching(registry.getIds().stream().map(id -> id.getNamespace().equals("minecraft") ? id.getPath() : id.toString()), builder);
	}
}
