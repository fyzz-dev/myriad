package dev.myriad.essentials.modules.render;

import dev.myriad.api.event.Subscribe;
import dev.myriad.api.event.events.TickEvent;
import dev.myriad.api.module.Categories;
import dev.myriad.api.module.Module;
import dev.myriad.api.module.Modules;
import dev.myriad.api.setting.RegistryListSetting;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;

/**
 * Hides every block except the ones you choose (ores, spawners, chests by default), so they stand out through the
 * terrain. While on, chunk occlusion culling and smooth lighting are turned off and the world is fully lit; your
 * settings come back when you turn it off. Applied by this addon's block and fluid render mixins.
 */
public class Xray extends Module {

	private final RegistryListSetting<Block> blocks = sgGeneral.blocks("Blocks").description("Blocks that stay visible.").defaultValue(
		Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE, Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE,
		Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE, Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE,
		Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE, Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE,
		Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE, Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE,
		Blocks.NETHER_GOLD_ORE, Blocks.NETHER_QUARTZ_ORE, Blocks.ANCIENT_DEBRIS, Blocks.SPAWNER,
		Blocks.CHEST, Blocks.ENDER_CHEST, Blocks.TRAPPED_CHEST
	).onChanged(v -> reloadSoon()).build();

	private Boolean previousAo;
	private boolean previousChunkCulling = true;
	private int reloadTicks;

	public Xray() {
		super(Categories.RENDER, "Xray", "Shows only the blocks you choose.");
	}

	/** Whether {@code state} is drawn (always true while Xray is off). */
	public static boolean visible(BlockState state) {
		Xray m = Modules.active(Xray.class);
		return m == null || state.isAir() || m.blocks.get().isEmpty() || m.blocks.contains(state.getBlock());
	}

	@Override
	public String hudInfo() {
		return String.valueOf(blocks.get().size());
	}

	@Override
	protected void onEnable() {
		previousChunkCulling = mc.chunkCullingEnabled;
		mc.chunkCullingEnabled = false;
		previousAo = mc.options.getAo().getValue();
		mc.options.getAo().setValue(false);
		reload();
	}

	@Override
	protected void onDisable() {
		mc.chunkCullingEnabled = previousChunkCulling;
		if (previousAo != null) mc.options.getAo().setValue(previousAo);
		previousAo = null;
		reload();
	}

	@Subscribe
	private void onTick(TickEvent.Post e) {
		// Something else may turn culling back on (e.g. F3 debug toggles).
		mc.chunkCullingEnabled = false;
		if (reloadTicks > 0 && --reloadTicks == 0) reload();
	}

	private void reloadSoon() {
		if (isEnabled()) reloadTicks = 2;
	}

	private void reload() {
		reloadTicks = 0;
		if (mc.worldRenderer != null) mc.worldRenderer.reload();
	}
}
