package dev.myriad.essentials.modules.player;

import dev.myriad.api.module.Categories;
import dev.myriad.api.module.Module;
import dev.myriad.api.module.Modules;
import dev.myriad.api.setting.DoubleSetting;

/**
 * Sets how far you can place and break blocks and hit entities. Servers check reach themselves: vanilla ones allow a
 * little past the normal range, and further only works where they don't check. Applied by this addon's
 * PlayerEntityMixin.
 */
public class Reach extends Module {
	private final DoubleSetting blockRange = sgGeneral.doubleSetting("Block Range").description("Blocks you can place, break and use (vanilla 4.5).")
		.defaultValue(5).range(1, 10).decimals(1).build();
	private final DoubleSetting entityRange = sgGeneral.doubleSetting("Entity Range").description("Entities you can hit and use (vanilla 3).")
		.defaultValue(5).range(1, 10).decimals(1).build();

	public Reach() {
		super(Categories.PLAYER, "Reach", "Place blocks and hit entities from further away.");
	}

	@Override
	public String hudInfo() {
		return String.format("%.1f", entityRange.get());
	}

	/** The block range to use instead of {@code original}. */
	public static double blockRange(double original) {
		Reach m = Modules.active(Reach.class);
		return m == null ? original : m.blockRange.get();
	}

	/** The entity range to use instead of {@code original}. */
	public static double entityRange(double original) {
		Reach m = Modules.active(Reach.class);
		return m == null ? original : m.entityRange.get();
	}
}
