package dev.myriad.api.command;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import dev.myriad.api.Myriad;
import dev.myriad.api.registry.Identified;
import dev.myriad.api.util.MyriadId;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.CommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;

/**
 * A chat command (run with the prefix, {@code .} by default) built on Brigadier.
 *
 * <pre>{@code
 * public class Hello extends Command {
 *     public Hello() { super("hello", "Says hello."); }
 *
 *     @Override
 *     public void build(LiteralArgumentBuilder<CommandSource> b) {
 *         b.executes(ctx -> { info("Hello!"); return SINGLE_SUCCESS; });
 *     }
 * }
 * }</pre>
 */
public abstract class Command implements Identified {
	public static final int SINGLE_SUCCESS = com.mojang.brigadier.Command.SINGLE_SUCCESS;
	/** The game client. Bound again when Myriad starts, in case this class loaded before the client existed. */
	protected static MinecraftClient mc = MinecraftClient.getInstance();

	private final String name;
	private final String description;
	private final List<String> aliases;
	private MyriadId id;

	protected Command(String name, String description, String... aliases) {
		this.name = name;
		this.description = description;
		this.aliases = List.of(aliases);
	}

	/** Add arguments and executors to the literal for {@link #name()} (and every alias). */
	public abstract void build(LiteralArgumentBuilder<CommandSource> builder);

	public String name() {
		return name;
	}

	public String description() {
		return description;
	}

	public List<String> aliases() {
		return aliases;
	}

	@Override
	public MyriadId id() {
		if (id == null) throw new IllegalStateException("Command '" + name + "' is not registered yet");
		return id;
	}

	@ApiStatus.Internal
	public void assignNamespace(String namespace) {
		id = MyriadId.of(namespace, name);
	}

	protected static <T> RequiredArgumentBuilder<CommandSource, T> argument(String name, ArgumentType<T> type) {
		return RequiredArgumentBuilder.argument(name, type);
	}

	protected static LiteralArgumentBuilder<CommandSource> literal(String name) {
		return LiteralArgumentBuilder.literal(name);
	}

	protected void info(String message) {
		Myriad.chat(Text.literal(message));
	}

	protected void info(Text message) {
		Myriad.chat(message);
	}

	protected void error(String message) {
		Myriad.chat(Text.literal(message).formatted(Formatting.RED));
	}

	@org.jetbrains.annotations.ApiStatus.Internal
	public static void bindClient(MinecraftClient client) {
		mc = client;
	}
}
