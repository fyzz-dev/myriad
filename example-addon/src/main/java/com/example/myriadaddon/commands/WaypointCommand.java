package com.example.myriadaddon.commands;

import com.example.myriadaddon.modules.Waypoints;
import com.example.myriadaddon.waypoints.Waypoint;
import com.example.myriadaddon.waypoints.WaypointStore;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.myriad.api.command.Command;
import net.minecraft.command.CommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * {@code .waypoint} (or {@code .wp}): list, add, remove, hide and show waypoints. Commands are plain Brigadier, so
 * sub-commands, typed arguments and suggestions work as they do in vanilla.
 *
 * <pre>
 * .wp                       list waypoints in this world
 * .wp add Home              at your feet
 * .wp add "Iron Farm" 120 64 -300
 * .wp remove Home
 * .wp hide Home / .wp show Home
 * .wp clear
 * </pre>
 */
public final class WaypointCommand extends Command {
	private final WaypointStore store;

	public WaypointCommand(WaypointStore store) {
		super("waypoint", "Lists, adds and removes waypoints.", "wp");
		this.store = store;
	}

	@Override
	public void build(LiteralArgumentBuilder<CommandSource> b) {
		b.executes(c -> list());
		b.then(literal("list").executes(c -> list()));

		b.then(literal("add").then(argument("name", StringArgumentType.string())
			.executes(c -> {
				if (mc.player == null) return fail("Join a world first.");
				return add(StringArgumentType.getString(c, "name"), mc.player.getBlockPos());
			})
			.then(argument("x", IntegerArgumentType.integer()).then(argument("y", IntegerArgumentType.integer()).then(argument("z", IntegerArgumentType.integer())
				.executes(c -> add(StringArgumentType.getString(c, "name"),
					new BlockPos(IntegerArgumentType.getInteger(c, "x"), IntegerArgumentType.getInteger(c, "y"), IntegerArgumentType.getInteger(c, "z")))))))));

		b.then(literal("remove").then(argument("waypoint", new WaypointArgumentType(store)).executes(c -> {
			Waypoint w = WaypointArgumentType.get(c, "waypoint");
			store.remove(w.name());
			info("Removed " + w.name() + ".");
			return SINGLE_SUCCESS;
		})));

		b.then(literal("hide").then(argument("waypoint", new WaypointArgumentType(store)).executes(c -> setVisible(WaypointArgumentType.get(c, "waypoint"), false))));
		b.then(literal("show").then(argument("waypoint", new WaypointArgumentType(store)).executes(c -> setVisible(WaypointArgumentType.get(c, "waypoint"), true))));

		b.then(literal("clear").executes(c -> {
			int n = store.clear();
			info(n == 0 ? "No waypoints to clear." : "Removed " + n + " waypoint" + (n == 1 ? "" : "s") + ".");
			return SINGLE_SUCCESS;
		}));
	}

	private int list() {
		if (WaypointStore.worldKey() == null) return fail("Join a world first.");
		List<Waypoint> all = store.here();
		if (all.isEmpty()) {
			info("No waypoints here yet. Add one with .waypoint add <name>.");
			return SINGLE_SUCCESS;
		}
		info(all.size() + " waypoint" + (all.size() == 1 ? "" : "s") + ":");
		String dim = WaypointStore.currentDimension();
		for (Waypoint w : all) {
			Text line = Text.literal(" " + w.name()).formatted(w.visible() ? Formatting.WHITE : Formatting.GRAY)
				.append(Text.literal("  " + w.coords()).formatted(Formatting.GRAY));
			if (!w.dimension().equals(dim)) line = line.copy().append(Text.literal("  " + w.dimension().replace("minecraft:", "")).formatted(Formatting.DARK_GRAY));
			else if (mc.player != null) line = line.copy().append(Text.literal("  " + Waypoints.formatDistance(w.center().distanceTo(mc.player.getPos()))).formatted(Formatting.AQUA));
			info(line);
		}
		return SINGLE_SUCCESS;
	}

	private int add(String name, BlockPos pos) {
		if (!store.put(name, pos)) return fail("Join a world first.");
		info("Added " + name + " at " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + ".");
		return SINGLE_SUCCESS;
	}

	private int setVisible(Waypoint w, boolean visible) {
		store.setVisible(w.name(), visible);
		info((visible ? "Showing " : "Hiding ") + w.name() + ".");
		return SINGLE_SUCCESS;
	}

	private int fail(String message) {
		error(message);
		return 0;
	}
}
