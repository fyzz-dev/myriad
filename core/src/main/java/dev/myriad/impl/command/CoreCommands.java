package dev.myriad.impl.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.myriad.api.Myriad;
import dev.myriad.api.addon.Addon;
import dev.myriad.api.addon.AddonContext;
import dev.myriad.api.addon.AddonState;
import dev.myriad.api.command.Command;
import dev.myriad.api.module.Module;
import dev.myriad.api.setting.KeybindSetting;
import dev.myriad.api.setting.Setting;
import dev.myriad.impl.ui.ThemeManager;
import dev.myriad.impl.ui.WindowManager;
import net.minecraft.command.CommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import static dev.myriad.api.command.arguments.ModuleArgumentType.module;
import static dev.myriad.api.command.arguments.PlayerArgumentType.player;
import static dev.myriad.api.command.arguments.ProfileArgumentType.profile;
import static dev.myriad.api.command.arguments.SettingArgumentType.setting;
import static dev.myriad.api.command.arguments.SettingValueArgumentType.value;

/** System commands that operate on Myriad itself (not features), so they live in core. */
public final class CoreCommands {
	private CoreCommands() {
	}

	public static void register(AddonContext ctx) {
		ctx.registerCommand(new Command("toggle", "Toggles a module.", "t") {
			@Override
			public void build(LiteralArgumentBuilder<CommandSource> b) {
				b.then(argument("module", module()).executes(c -> {
					Module m = c.getArgument("module", Module.class);
					m.toggle();
					if (!m.chatFeedback.get()) info(m.name() + (m.isEnabled() ? " enabled" : " disabled"));
					return SINGLE_SUCCESS;
				}));
			}
		});

		ctx.registerCommand(new Command("bind", "Binds a module to a key (e.g. .bind sprint r, .bind sprint none).") {
			@Override
			public void build(LiteralArgumentBuilder<CommandSource> b) {
				b.then(argument("module", module()).then(argument("key", StringArgumentType.word()).executes(c -> {
					Module m = c.getArgument("module", Module.class);
					KeybindSetting k = m.keybind;
					if (!k.parse(StringArgumentType.getString(c, "key"))) {
						error("Unknown key. Use names like r, f6, left.shift or none.");
						return 0;
					}
					info(m.name() + " bound to " + k.valueString());
					return SINGLE_SUCCESS;
				})));
			}
		});

		ctx.registerCommand(new Command("set", "Shows or changes a module setting.", "s") {
			@Override
			public void build(LiteralArgumentBuilder<CommandSource> b) {
				b.then(argument("module", module())
					.executes(c -> {
						Module m = c.getArgument("module", Module.class);
						info(Text.literal(m.name()).formatted(Formatting.BOLD));
						for (Setting<?> s : m.settings.all()) {
							if (!s.isSerializable()) continue;
							info(Text.literal("  " + m.settings.keyOf(s) + " = ").formatted(Formatting.GRAY).append(Text.literal(s.valueString()).formatted(Formatting.WHITE)));
						}
						return SINGLE_SUCCESS;
					})
					.then(argument("setting", setting())
						.executes(c -> {
							Setting<?> s = dev.myriad.api.command.arguments.SettingArgumentType.get(c, "module", "setting");
							info(s.name() + " = " + s.valueString() + (s.description().isEmpty() ? "" : "  (" + s.description() + ")"));
							return SINGLE_SUCCESS;
						})
						.then(argument("value", value()).executes(c -> {
							Setting<?> s = dev.myriad.api.command.arguments.SettingArgumentType.get(c, "module", "setting");
							String v = c.getArgument("value", String.class);
							if (!s.parse(v)) {
								error("Invalid value '" + v + "' for " + s.name());
								return 0;
							}
							info(s.name() + " set to " + s.valueString());
							return SINGLE_SUCCESS;
						}))));
			}
		});

		ctx.registerCommand(new Command("reset", "Resets a module's settings to defaults.") {
			@Override
			public void build(LiteralArgumentBuilder<CommandSource> b) {
				b.then(argument("module", module()).executes(c -> {
					Module m = c.getArgument("module", Module.class);
					m.settings.resetAll();
					info("Reset " + m.name());
					return SINGLE_SUCCESS;
				}));
			}
		});

		ctx.registerCommand(new Command("profile", "Switches, creates or deletes config profiles.", "config") {
			@Override
			public void build(LiteralArgumentBuilder<CommandSource> b) {
				b.executes(c -> {
					info("Profile: " + Myriad.config().activeProfile() + "  (all: " + String.join(", ", Myriad.config().profiles()) + ")");
					return SINGLE_SUCCESS;
				});
				b.then(literal("load").then(argument("name", profile()).executes(c -> {
					Myriad.config().switchProfile(c.getArgument("name", String.class));
					info("Switched to " + Myriad.config().activeProfile());
					return SINGLE_SUCCESS;
				})));
				b.then(literal("delete").then(argument("name", profile()).executes(c -> {
					String n = c.getArgument("name", String.class);
					if (Myriad.config().deleteProfile(n)) info("Deleted " + n);
					else error("Can't delete " + n + " (missing or active)");
					return SINGLE_SUCCESS;
				})));
				b.then(literal("save").executes(c -> {
					Myriad.config().saveNow();
					info("Saved");
					return SINGLE_SUCCESS;
				}));
			}
		});

		ctx.registerCommand(new Command("friend", "Manages friends.", "f") {
			@Override
			public void build(LiteralArgumentBuilder<CommandSource> b) {
				b.then(literal("add").then(argument("name", player()).executes(c -> {
					String n = c.getArgument("name", String.class);
					info(Myriad.friends().add(n) ? "Added " + n : n + " is already a friend");
					return SINGLE_SUCCESS;
				})));
				b.then(literal("remove").then(argument("name", player()).executes(c -> {
					String n = c.getArgument("name", String.class);
					info(Myriad.friends().remove(n) ? "Removed " + n : n + " isn't a friend");
					return SINGLE_SUCCESS;
				})));
				b.then(literal("list").executes(c -> {
					info("Friends: " + (Myriad.friends().all().isEmpty() ? "none" : String.join(", ", Myriad.friends().all())));
					return SINGLE_SUCCESS;
				}));
			}
		});

		ctx.registerCommand(new Command("addons", "Lists loaded addons.") {
			@Override
			public void build(LiteralArgumentBuilder<CommandSource> b) {
				b.executes(c -> {
					for (Addon a : Myriad.addons()) {
						Formatting f = a.state() == AddonState.FAILED ? Formatting.RED : Formatting.WHITE;
						info(Text.literal(a.name() + " " + a.version()).formatted(f)
							.append(Text.literal("  " + Myriad.modules().ownedBy(a.id()).size() + " modules").formatted(Formatting.GRAY)));
					}
					return SINGLE_SUCCESS;
				});
			}
		});

		ctx.registerCommand(new Command("help", "Lists commands.", "commands") {
			@Override
			public void build(LiteralArgumentBuilder<CommandSource> b) {
				b.executes(c -> {
					String p = Myriad.config().commandPrefix();
					for (Command cmd : Myriad.commands()) {
						info(Text.literal(p + cmd.name()).formatted(Formatting.AQUA).append(Text.literal("  " + cmd.description()).formatted(Formatting.GRAY)));
					}
					return SINGLE_SUCCESS;
				});
			}
		});

		ctx.registerCommand(new Command("panic", "Disables every module.") {
			@Override
			public void build(LiteralArgumentBuilder<CommandSource> b) {
				b.executes(c -> {
					int n = 0;
					for (Module m : Myriad.modules().enabled()) {
						m.disable();
						n++;
					}
					info("Disabled " + n + " modules");
					return SINGLE_SUCCESS;
				});
			}
		});

		ctx.registerCommand(new Command("prefix", "Changes the command prefix.") {
			@Override
			public void build(LiteralArgumentBuilder<CommandSource> b) {
				b.then(argument("prefix", StringArgumentType.word()).executes(c -> {
					Myriad.config().setCommandPrefix(StringArgumentType.getString(c, "prefix"));
					info("Prefix is now " + Myriad.config().commandPrefix());
					return SINGLE_SUCCESS;
				}));
			}
		});

		ctx.registerCommand(new Command("theme", "Lists themes, or applies one by name.") {
			@Override
			public void build(LiteralArgumentBuilder<CommandSource> b) {
				ThemeManager themes = ((WindowManager) Myriad.ui()).themes();
				b.executes(c -> {
					StringBuilder sb = new StringBuilder("Themes: ");
					for (ThemeManager.Entry t : themes.all()) sb.append(t.id().path()).append(' ');
					info(sb.toString());
					return SINGLE_SUCCESS;
				});
				b.then(argument("name", StringArgumentType.greedyString())
					.suggests((c, s) -> CommandSource.suggestMatching(themes.all().stream().map(t -> t.id().path()), s))
					.executes(c -> {
						String n = StringArgumentType.getString(c, "name");
						var found = themes.find(n);
						if (found.isEmpty()) {
							error("No theme " + n);
							return 0;
						}
						Myriad.ui().applyTheme(found.get().id());
						return SINGLE_SUCCESS;
					}));
			}
		});

		ctx.registerCommand(new Command("menu", "Opens the Myriad menu.", "gui", "desktop") {
			@Override
			public void build(LiteralArgumentBuilder<CommandSource> b) {
				// Opening a screen from chat must wait until the chat screen has closed.
				b.executes(c -> {
					mc.send(() -> Myriad.ui().open());
					return SINGLE_SUCCESS;
				});
			}
		});
	}
}
