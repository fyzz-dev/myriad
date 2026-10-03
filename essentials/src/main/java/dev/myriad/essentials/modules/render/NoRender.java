package dev.myriad.essentials.modules.render;

import dev.myriad.api.module.Categories;
import dev.myriad.api.module.Module;
import dev.myriad.api.module.Modules;
import dev.myriad.api.setting.BoolSetting;
import dev.myriad.api.setting.RegistryListSetting;
import dev.myriad.api.setting.SettingGroup;
import java.util.function.Function;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Hides distracting overlays, HUD parts, entities and blocks. Read by this addon's render mixins. */
public class NoRender extends Module {

	private final SettingGroup sgOverlays = settings.group("Overlays");
	public final BoolSetting hurtCam = sgGeneral.bool("Hurt Camera").description("No camera shake when you take damage.").defaultValue(true).build();
	public final BoolSetting totem = sgGeneral.bool("Totem Animation").description("No totem pop animation.").defaultValue(true).build();
	public final BoolSetting weather = sgGeneral.bool("Weather").description("No rain or snow.").build();
	public final BoolSetting toasts = sgGeneral.bool("Toasts").description("No advancement, recipe or system toasts.").build();
	public final BoolSetting fire = sgOverlays.bool("Fire Overlay").defaultValue(true).build();
	public final BoolSetting blockOverlay = sgOverlays.bool("Block Overlay").description("No texture when your head is inside a block.").defaultValue(true).build();
	public final BoolSetting liquidOverlay = sgOverlays.bool("Liquid Overlay").description("No underwater texture.").build();
	public final BoolSetting vignette = sgOverlays.bool("Vignette").defaultValue(true).build();
	public final BoolSetting portal = sgOverlays.bool("Portal Overlay").description("No purple swirl or nausea overlay.").defaultValue(true).build();
	public final BoolSetting pumpkin = sgOverlays.bool("Pumpkin Overlay").defaultValue(true).build();

	private final SettingGroup sgHud = settings.group("HUD");
	public final BoolSetting bossBar = sgHud.bool("Boss Bar").build();
	public final BoolSetting statusEffects = sgHud.bool("Status Effects").description("No potion icons in the top right.").build();
	public final BoolSetting xpBar = sgHud.bool("XP Bar").build();
	public final BoolSetting itemName = sgHud.bool("Item Name").description("No item name above the hotbar when switching.").build();

	private final SettingGroup sgWorld = settings.group("World");
	public final BoolSetting armor = sgWorld.bool("Armor").description("No armour on players and mobs.").build();
	public final BoolSetting burning = sgWorld.bool("Burning").description("No flames on burning entities.").build();
	public final BoolSetting damageTint = sgWorld.bool("Damage Tint").description("No red tint on hurt entities.").build();
	public final BoolSetting deadEntities = sgWorld.bool("Dead Entities").description("Hide entities as soon as they die.").build();
	private final RegistryListSetting<EntityType<?>> entities = sgWorld.entityTypes("Entities").description("Entity types never drawn.").build();
	private final RegistryListSetting<Block> blocks = sgWorld.blocks("Blocks").description("Blocks never drawn.").onChanged(v -> reload()).build();
	public final BoolSetting vines = sgWorld.bool("Vines").description("Hide vines, kelp and other hanging plants.").onChanged(v -> reload()).build();
	public final BoolSetting textureRotations = sgWorld.bool("Texture Rotations").description("Remove random block offsets (flowers, grass), which can leak coordinates.")
		.onChanged(v -> reload()).build();

	public NoRender() {
		super(Categories.RENDER, "No Render", "Hides distracting effects, overlays and things.");
	}

	/**
	 * Whether No Render is on and hides the thing {@code option} picks, e.g. {@code NoRender.hides(n -> n.fire)}.
	 * Used by this addon's mixins.
	 */
	public static boolean hides(Function<NoRender, BoolSetting> option) {
		NoRender m = Modules.active(NoRender.class);
		return m != null && option.apply(m).get();
	}

	@Override
	protected void onEnable() {
		reload();
	}

	@Override
	protected void onDisable() {
		reload();
	}

	private void reload() {
		if (mc.levelRenderer != null && mc.level != null && (isEnabled() || !blocks.get().isEmpty() || vines.get() || textureRotations.get())) mc.levelRenderer.invalidateCompiledGeometry(mc.level, mc.options, mc.gameRenderer.mainCamera(), mc.getBlockColors());
	}

	/** Whether the entity should not be drawn at all. */
	public static boolean hides(Entity e) {
		NoRender m = Modules.active(NoRender.class);
		if (m == null) return false;
		if (m.entities.contains(e.getType())) return true;
		return m.deadEntities.get() && e instanceof LivingEntity l && l.isDeadOrDying();
	}

	/** Whether the block should not be drawn. */
	public static boolean hides(BlockState state) {
		NoRender m = Modules.active(NoRender.class);
		if (m == null) return false;
		Block b = state.getBlock();
		if (m.blocks.contains(b)) return true;
		return m.vines.get() && (b == Blocks.VINE || b == Blocks.CAVE_VINES || b == Blocks.CAVE_VINES_PLANT || b == Blocks.TWISTING_VINES
			|| b == Blocks.TWISTING_VINES_PLANT || b == Blocks.WEEPING_VINES || b == Blocks.WEEPING_VINES_PLANT || b == Blocks.KELP || b == Blocks.KELP_PLANT
			|| b == Blocks.GLOW_LICHEN);
	}
}
