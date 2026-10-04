package dev.myriad.api.service;

import dev.myriad.api.build.Blueprint;
import dev.myriad.api.build.Build;

import java.util.List;

/**
 * Builds a {@link Blueprint}: describe the shape you want, and the planner works out each tick, for what's in reach,
 * what to break and what to place, in an order that works, and does it through {@link Breaking} and
 * {@link Placement}. Nuker, tunnelers, highway builders, surround, scaffold and printers become mostly a blueprint.
 *
 * <ul>
 *   <li>Wrong blocks are broken first, top down so falling blocks drop into space that's already been cleared,
 *   never the one you stand on, and (by default) not if it lets a fluid in.</li>
 *   <li>Placements go bottom up and nearest first, so each one has something to be placed against; a position
 *   with nothing to click waits until a neighbour is placed.</li>
 *   <li>Materials come from the hotbar, moved there from the inventory when needed.</li>
 *   <li>Oriented targets ({@code Target.state}) are placed facing the right way: the planner tries the clicks it
 *   could make and the rotation each needs, as the game would place them, and uses one that comes out right.</li>
 * </ul>
 *
 * <pre>{@code
 * Build build = Myriad.building().start(this, Blueprint.box(a, b, Target.air()), Building.Options.DEFAULT);
 * build.finished().thenAccept(done -> info(done ? "Tunnel finished" : "Stopped"));
 * }</pre>
 *
 * A module's builds stop when it's disabled.
 */
public interface Building {
	/**
	 * @param placing        how blocks are placed ({@code Placement.Options.STRICT} on servers that check)
	 * @param breaking       how blocks are broken
	 * @param breakWrong     break blocks that are in the way of a placement; off for builds that should only fill
	 *                       empty space (surround shouldn't dig up your base)
	 * @param avoidFluids    don't break blocks that hold back water or lava
	 * @param placesPerTick  most placements started per tick
	 * @param priority       the priority of this build's breaks against other modules' breaks
	 * @param keepUp         keep running when everything is done, putting back what changes (surround); otherwise
	 *                       the build finishes once the blueprint is done
	 */
	record Options(Placement.Options placing, Breaking.Options breaking, boolean breakWrong, boolean avoidFluids, int placesPerTick,
	               int priority, boolean keepUp) {
		public static final Options DEFAULT = new Options(Placement.Options.DEFAULT, Breaking.Options.DEFAULT, true, true, 4, 0, false);
		public static final Options STRICT = new Options(Placement.Options.STRICT, Breaking.Options.STRICT, true, true, 1, 0, false);

		public Options withKeepUp(boolean keepUp) {
			return new Options(placing, breaking, breakWrong, avoidFluids, placesPerTick, priority, keepUp);
		}

		public Options withBreakWrong(boolean breakWrong) {
			return new Options(placing, breaking, breakWrong, avoidFluids, placesPerTick, priority, keepUp);
		}
	}

	/** Starts building {@code blueprint} for {@code owner}. */
	Build start(Object owner, Blueprint blueprint, Options options);

	/** Stops every build {@code owner} started. */
	void stop(Object owner);

	/** The builds running now. */
	List<Build> running();
}
