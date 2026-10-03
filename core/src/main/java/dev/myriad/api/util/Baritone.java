package dev.myriad.api.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/**
 * Drives Baritone when it's installed, without depending on it: every call is a no-op (or false) when it's missing,
 * so a module can offer "use Baritone" options to everyone and they simply do nothing without it.
 *
 * <pre>{@code
 * if (Baritone.isAvailable()) Baritone.pathTo(x, y, z);
 * Baritone.pause(this);   // e.g. while eating
 * Baritone.resume(this);
 * }</pre>
 *
 * Pauses are counted by owner: Baritone resumes once every module that paused it has resumed, and never resumes a
 * pause it didn't make.
 */
public final class Baritone {
	private static final Logger LOG = LoggerFactory.getLogger("Myriad/Baritone");
	private static final Set<Object> pausedBy = new HashSet<>();
	private static Boolean installed;
	private static boolean warned;

	private static Method getProvider, getPrimaryBaritone, getCommandManager, execute, getPathingBehavior, isPathing, cancelEverything,
		getCustomGoalProcess, setGoalAndPath, isActive;
	private static Class<?> goalBlock, goalXZ, goal;

	private Baritone() {
	}

	/** Whether Baritone is installed and ready (it has a primary instance once a world is loaded). */
	public static boolean isAvailable() {
		return primary() != null;
	}

	/** Whether Baritone is walking a path right now. */
	public static boolean isPathing() {
		Object b = primary();
		if (b == null) return false;
		try {
			return (boolean) isPathing.invoke(getPathingBehavior.invoke(b)) || (boolean) isActive.invoke(getCustomGoalProcess.invoke(b));
		} catch (ReflectiveOperationException | RuntimeException e) {
			fail(e);
			return false;
		}
	}

	/** Walks to a block, mining and placing as Baritone's settings allow. */
	public static boolean pathTo(int x, int y, int z) {
		return path(goalBlock, new Class<?>[]{int.class, int.class, int.class}, x, y, z);
	}

	/** Walks to a column, at whatever height. */
	public static boolean pathTo(int x, int z) {
		return path(goalXZ, new Class<?>[]{int.class, int.class}, x, z);
	}

	private static boolean path(Class<?> goalType, Class<?>[] params, Object... args) {
		Object b = primary();
		if (b == null) return false;
		try {
			Object g = goalType.getConstructor(params).newInstance(args);
			setGoalAndPath.invoke(getCustomGoalProcess.invoke(b), g);
			return true;
		} catch (ReflectiveOperationException | RuntimeException e) {
			fail(e);
			return false;
		}
	}

	/** Stops whatever Baritone is doing. */
	public static void stop() {
		Object b = primary();
		if (b == null) return;
		try {
			cancelEverything.invoke(getPathingBehavior.invoke(b));
		} catch (ReflectiveOperationException | RuntimeException e) {
			fail(e);
		}
	}

	/** Runs a Baritone command, without its prefix ("goto 0 64 0", "mine diamond_ore"). */
	public static boolean command(String command) {
		Object b = primary();
		if (b == null) return false;
		try {
			return (boolean) execute.invoke(getCommandManager.invoke(b), command);
		} catch (ReflectiveOperationException | RuntimeException e) {
			fail(e);
			return false;
		}
	}

	/** Pauses Baritone on behalf of {@code owner}, until it calls {@link #resume}. */
	public static void pause(Object owner) {
		if (!isAvailable() || !pausedBy.add(owner) || pausedBy.size() > 1) return;
		command("pause");
	}

	/** Ends {@code owner}'s pause; Baritone carries on once nobody else is pausing it. */
	public static void resume(Object owner) {
		if (!pausedBy.remove(owner) || !pausedBy.isEmpty()) return;
		command("resume");
	}

	public static boolean isPausedBy(Object owner) {
		return pausedBy.contains(owner);
	}

	private static Object primary() {
		if (!installed()) return null;
		try {
			return getPrimaryBaritone.invoke(getProvider.invoke(null));
		} catch (ReflectiveOperationException | RuntimeException e) {
			fail(e);
			return null;
		}
	}

	private static boolean installed() {
		if (installed != null) return installed;
		try {
			// The API interfaces, not the implementation classes, so this works with any Baritone build.
			Class<?> api = Class.forName("baritone.api.BaritoneAPI");
			Class<?> provider = Class.forName("baritone.api.IBaritoneProvider");
			Class<?> baritone = Class.forName("baritone.api.IBaritone");
			Class<?> commands = Class.forName("baritone.api.command.manager.ICommandManager");
			Class<?> pathing = Class.forName("baritone.api.behavior.IPathingBehavior");
			Class<?> custom = Class.forName("baritone.api.process.ICustomGoalProcess");
			Class<?> process = Class.forName("baritone.api.process.IBaritoneProcess");
			goal = Class.forName("baritone.api.pathing.goals.Goal");
			goalBlock = Class.forName("baritone.api.pathing.goals.GoalBlock");
			goalXZ = Class.forName("baritone.api.pathing.goals.GoalXZ");
			getProvider = api.getMethod("getProvider");
			getPrimaryBaritone = provider.getMethod("getPrimaryBaritone");
			getCommandManager = baritone.getMethod("getCommandManager");
			execute = commands.getMethod("execute", String.class);
			getPathingBehavior = baritone.getMethod("getPathingBehavior");
			isPathing = pathing.getMethod("isPathing");
			cancelEverything = pathing.getMethod("cancelEverything");
			getCustomGoalProcess = baritone.getMethod("getCustomGoalProcess");
			setGoalAndPath = custom.getMethod("setGoalAndPath", goal);
			isActive = process.getMethod("isActive");
			installed = true;
		} catch (ClassNotFoundException e) {
			installed = false;
		} catch (ReflectiveOperationException | RuntimeException e) {
			LOG.warn("Baritone is installed but its API doesn't look as expected; Baritone options are off", e);
			installed = false;
		}
		return installed;
	}

	private static void fail(Throwable t) {
		if (warned) return;
		warned = true;
		LOG.warn("A Baritone call failed (further failures are not logged)", t);
	}
}
