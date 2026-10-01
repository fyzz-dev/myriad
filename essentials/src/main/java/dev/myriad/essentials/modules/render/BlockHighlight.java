package dev.myriad.essentials.modules.render;

import dev.myriad.api.event.Subscribe;
import dev.myriad.api.event.events.Render3DEvent;
import dev.myriad.api.module.Categories;
import dev.myriad.api.module.Module;
import dev.myriad.api.render.Renderer3D;
import dev.myriad.api.setting.BoolSetting;
import dev.myriad.api.setting.ColorSetting;
import dev.myriad.api.setting.DoubleSetting;
import dev.myriad.api.setting.EnumSetting;
import dev.myriad.api.setting.IntSetting;
import dev.myriad.api.setting.SettingColor;
import dev.myriad.api.util.ColorUtil;
import net.minecraft.block.BlockState;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.shape.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * Replaces the thin black block outline with a themed box that can glide between blocks or fade out behind you.
 */
public class BlockHighlight extends Module {

	public enum Animate {
		OFF, SLIDE, FADE
	}

	private final EnumSetting<Renderer3D.ShapeMode> shape = sgGeneral.enumSetting("Shape", Renderer3D.ShapeMode.BOTH).build();
	private final ColorSetting fill = sgGeneral.color("Fill").defaultValue(SettingColor.role(SettingColor.Mode.ACCENT, 50)).visible(() -> shape.get() != Renderer3D.ShapeMode.LINES).build();
	private final ColorSetting outline = sgGeneral.color("Outline").defaultValue(SettingColor.role(SettingColor.Mode.ACCENT)).visible(() -> shape.get() != Renderer3D.ShapeMode.FILL).build();
	private final DoubleSetting lineWidth = sgGeneral.doubleSetting("Line Width").defaultValue(1.5).range(0.5, 5).decimals(1).build();
	private final BoolSetting throughWalls = sgGeneral.bool("Through Walls").description("Draw the parts hidden behind other blocks too.").defaultValue(true).build();
	private final EnumSetting<Animate> animate = sgGeneral.enumSetting("Animate", Animate.SLIDE).description("How the highlight moves between blocks.").build();
	private final DoubleSetting slideSpeed = sgGeneral.doubleSetting("Glide Speed").defaultValue(5).range(0.1, 20).decimals(1).visible(() -> animate.get() == Animate.SLIDE).build();
	private final IntSetting fadeTime = sgGeneral.intSetting("Fade Time").description("Milliseconds old highlights take to fade.").defaultValue(250).range(50, 2000)
		.visible(() -> animate.get() == Animate.FADE).build();

	private Box slide, current;
	private final List<Fading> fading = new ArrayList<>();
	private long lastFrame;

	private record Fading(Box box, long start) {
	}

	public BlockHighlight() {
		super(Categories.RENDER, "Block Highlight", "A themed, animated block outline.");
	}

	@Override
	protected void onDisable() {
		slide = current = null;
		fading.clear();
	}

	@Subscribe
	private void onRender(Render3DEvent e) {
		if (!inGame()) return;
		Renderer3D.lineWidth(lineWidth.getFloat());
		Box target = target();
		long now = System.currentTimeMillis();
		float dt = lastFrame == 0 ? 0.016f : Math.min(0.1f, (now - lastFrame) / 1000f);
		lastFrame = now;
		switch (animate.get()) {
			case OFF -> {
				if (target != null) draw(target, 1);
			}
			case SLIDE -> {
				if (target == null) {
					slide = null;
					return;
				}
				if (slide == null) slide = target;
				else {
					double t = MathHelper.clamp(slideSpeed.get() * dt * 2.5, 0, 1);
					slide = new Box(MathHelper.lerp(t, slide.minX, target.minX), MathHelper.lerp(t, slide.minY, target.minY), MathHelper.lerp(t, slide.minZ, target.minZ),
						MathHelper.lerp(t, slide.maxX, target.maxX), MathHelper.lerp(t, slide.maxY, target.maxY), MathHelper.lerp(t, slide.maxZ, target.maxZ));
				}
				draw(slide, 1);
			}
			case FADE -> {
				if (target != null) {
					if (current != null && !current.equals(target)) fading.add(new Fading(current, now));
					current = target;
					draw(target, 1);
				} else if (current != null) {
					fading.add(new Fading(current, now));
					current = null;
				}
				fading.removeIf(f -> now - f.start >= fadeTime.get());
				for (Fading f : fading) draw(f.box, 1 - (now - f.start) / (float) fadeTime.get());
			}
		}
	}

	private Box target() {
		if (!(mc.crosshairTarget instanceof BlockHitResult hit) || hit.getType() == HitResult.Type.MISS) return null;
		BlockPos pos = hit.getBlockPos();
		BlockState state = mc.world.getBlockState(pos);
		if (state.isAir()) return null;
		VoxelShape shape = state.getOutlineShape(mc.world, pos);
		return shape.isEmpty() ? null : shape.getBoundingBox().offset(pos);
	}

	private void draw(Box box, float alpha) {
		if (alpha <= 0) return;
		if (!throughWalls.get()) box = box.expand(0.002);
		int f = fill.argb(), o = outline.argb();
		Renderer3D.box(box, ColorUtil.withAlpha(f, (int) (ColorUtil.alpha(f) * alpha)), ColorUtil.withAlpha(o, (int) (ColorUtil.alpha(o) * alpha)), shape.get(), throughWalls.get());
	}
}
