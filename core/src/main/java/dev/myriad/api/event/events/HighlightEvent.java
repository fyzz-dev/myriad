package dev.myriad.api.event.events;

import dev.myriad.api.render.HighlightStyle;
import dev.myriad.impl.render.HighlightRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Highlights: outlines, glows and fills traced around the exact silhouette of entities and shapes, drawn by a
 * screen-space shader after the world. Listen for these events while your module wants to highlight something; while
 * nothing listens, highlighting costs nothing. Both are posted on the render thread, every frame.
 * <p>
 * Colours are ARGB; the alpha fades the whole highlight (handy for fade-ins). The look comes from a
 * {@link HighlightStyle}; {@link dev.myriad.api.render.HighlightSettings} gives modules the standard options for one.
 *
 * <pre>{@code
 * @Subscribe
 * private void onHighlight(HighlightEvent.Entity e) {
 *     if (e.entity() instanceof Player) e.highlight(settings.style(), 0xFFFF5555);
 * }
 * }</pre>
 */
public abstract class HighlightEvent {
	/**
	 * An entity is being prepared for drawing this frame: call {@link #highlight} to highlight it. The highlight covers
	 * everything drawn with the entity (armour, held items, capes); riders are asked about separately. The first
	 * listener to highlight an entity wins, so higher-priority listeners override lower ones.
	 * <p>
	 * Vanilla's glowing effect uses the same silhouettes: while anything listens for highlight events, glowing
	 * entities are drawn by Myriad's shader too (as a thin outline with a short glow in their team colour).
	 * <p>
	 * One instance is reused for every entity: don't keep it past the handler.
	 */
	public static final class Entity extends HighlightEvent {
		private net.minecraft.world.entity.Entity entity;
		private float tickDelta;
		private @Nullable HighlightStyle style;
		private int color;

		@ApiStatus.Internal
		public Entity() {
		}

		@ApiStatus.Internal
		public Entity reset(net.minecraft.world.entity.Entity entity, float tickDelta) {
			this.entity = entity;
			this.tickDelta = tickDelta;
			this.style = null;
			this.color = 0;
			return this;
		}

		public net.minecraft.world.entity.Entity entity() {
			return entity;
		}

		/** The partial tick the entity is drawn at. */
		public float tickDelta() {
			return tickDelta;
		}

		/** Highlights the entity in {@code color} (ARGB). Ignored if already highlighted or {@code color}'s alpha is 0. */
		public void highlight(HighlightStyle style, int color) {
			if (this.style != null || style == null || (color >>> 24) == 0) return;
			this.style = style;
			this.color = color;
		}

		public boolean isHighlighted() {
			return style != null;
		}

		@ApiStatus.Internal
		public @Nullable HighlightStyle style() {
			return style;
		}

		@ApiStatus.Internal
		public int color() {
			return color;
		}
	}

	/**
	 * Once per frame, after {@link Render3DEvent}: highlight shapes in the world (containers, blocks, areas). Shapes
	 * added by one call form one silhouette, and touching silhouettes in the same frame share an outline, so a double
	 * chest or a cluster of ores is outlined as one shape.
	 * <p>
	 * One instance is reused every frame: don't keep it past the handler.
	 */
	public static final class Shapes extends HighlightEvent {
		private float tickDelta;

		@ApiStatus.Internal
		public Shapes() {
		}

		@ApiStatus.Internal
		public Shapes reset(float tickDelta) {
			this.tickDelta = tickDelta;
			return this;
		}

		public float tickDelta() {
			return tickDelta;
		}

		/** Highlights a box (world coordinates) in {@code color} (ARGB). Boxes off screen are skipped cheaply. */
		public void box(AABB box, HighlightStyle style, int color) {
			HighlightRenderer.INSTANCE.box(box, style, color);
		}

		/**
		 * Highlights the outline shape of the block at {@code pos} (a chest's smaller box, a slab, a stair's two boxes),
		 * or the full block if it has no shape.
		 */
		public void block(BlockPos pos, HighlightStyle style, int color) {
			HighlightRenderer.INSTANCE.block(pos, style, color);
		}
	}
}
