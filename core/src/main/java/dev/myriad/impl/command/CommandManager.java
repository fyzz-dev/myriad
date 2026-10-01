package dev.myriad.impl.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.myriad.api.Myriad;
import dev.myriad.api.command.Command;
import dev.myriad.api.event.Priority;
import dev.myriad.api.event.Subscribe;
import dev.myriad.api.event.events.ChatSendEvent;
import dev.myriad.api.registry.Registry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.CommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Builds a Brigadier dispatcher from the command registry and runs prefixed chat messages through it. */
public final class CommandManager {
	private static final Logger LOG = LoggerFactory.getLogger("Myriad/Commands");
	private final MinecraftClient mc = MinecraftClient.getInstance();
	private final Registry<Command> commands;
	private CommandDispatcher<CommandSource> dispatcher;

	public CommandManager(Registry<Command> commands) {
		this.commands = commands;
		commands.onAdd(c -> dispatcher = null);
		commands.onRemove(c -> dispatcher = null);
	}

	public CommandDispatcher<CommandSource> dispatcher() {
		if (dispatcher == null) {
			CommandDispatcher<CommandSource> d = new CommandDispatcher<>();
			for (Command c : commands) {
				try {
					register(d, c, c.name());
					for (String alias : c.aliases()) register(d, c, alias);
				} catch (RuntimeException e) {
					LOG.error("Command {} failed to build", c.id(), e);
				}
			}
			dispatcher = d;
		}
		return dispatcher;
	}

	private static void register(CommandDispatcher<CommandSource> d, Command c, String name) {
		LiteralArgumentBuilder<CommandSource> b = LiteralArgumentBuilder.literal(name);
		c.build(b);
		d.register(b);
	}

	public CommandSource source() {
		return mc.getNetworkHandler() != null ? mc.getNetworkHandler().getCommandSource() : null;
	}

	public ParseResults<CommandSource> parse(String input) {
		return dispatcher().parse(input, source());
	}

	/** Runs a command line (without prefix), reporting errors in chat. */
	public void execute(String input) {
		try {
			dispatcher().execute(input, source());
		} catch (CommandSyntaxException e) {
			Myriad.chat(Text.literal(e.getMessage()).formatted(Formatting.RED));
		} catch (Exception e) {
			LOG.error("Command '{}' failed", input, e);
			Myriad.chat(Text.literal("Command failed: " + e).formatted(Formatting.RED));
		}
	}

	@Subscribe(priority = Priority.HIGHEST)
	private void onChat(ChatSendEvent e) {
		String prefix = Myriad.config().commandPrefix();
		if (!e.message().startsWith(prefix)) return;
		e.cancel();
		mc.inGameHud.getChatHud().addToMessageHistory(e.message());
		execute(e.message().substring(prefix.length()));
	}
}
