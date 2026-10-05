package com.example.myriadaddon.waypoints;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * A saved position. Immutable: change one by replacing it in the {@link WaypointStore}.
 *
 * @param dimension the dimension id, e.g. {@code minecraft:the_nether}
 * @param color     ARGB, or 0 to use the Waypoints module's default colour (which follows the theme)
 */
public record Waypoint(String name, BlockPos pos, String dimension, int color, boolean visible) {
	public Vec3 center() {
		return Vec3.atCenterOf(pos);
	}

	public Waypoint withVisible(boolean visible) {
		return new Waypoint(name, pos, dimension, color, visible);
	}

	public String coords() {
		return pos.getX() + " " + pos.getY() + " " + pos.getZ();
	}

	JsonObject toJson() {
		JsonObject o = new JsonObject();
		o.addProperty("name", name);
		JsonArray at = new JsonArray();
		at.add(pos.getX());
		at.add(pos.getY());
		at.add(pos.getZ());
		o.add("pos", at);
		o.addProperty("dimension", dimension);
		if (color != 0) o.addProperty("color", String.format("#%08X", color));
		if (!visible) o.addProperty("visible", false);
		return o;
	}

	static Waypoint fromJson(JsonObject o) {
		int color = o.has("color") ? (int) Long.parseLong(o.get("color").getAsString().substring(1), 16) : 0;
		JsonArray at = o.getAsJsonArray("pos");
		return new Waypoint(o.get("name").getAsString(), new BlockPos(at.get(0).getAsInt(), at.get(1).getAsInt(), at.get(2).getAsInt()),
			o.get("dimension").getAsString(), color, !o.has("visible") || o.get("visible").getAsBoolean());
	}
}
