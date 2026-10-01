package dev.myriad.impl.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.myriad.api.render.Canvas;
import dev.myriad.api.render.FontFamily;
import dev.myriad.impl.render.font.FontManager;
import dev.myriad.impl.render.font.Glyph;
import dev.myriad.impl.render.font.SizedFont;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayDeque;
import java.util.Deque;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.GL_TEXTURE1;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;

/**
 * The batched GL implementation of {@link Canvas}. All shapes go through one shader (ui.vert/ui.frag) with a fat
 * vertex format, so a whole desktop is usually a handful of draw calls; batches only break on texture or clip changes.
 */
public final class UiRenderer implements Canvas {
	private static final int FILL = 0, OUTLINE = 1, SHADOW = 2, GLYPH = 3, IMAGE = 4, BACKDROP = 5;
	private static final int FLOATS_PER_VERTEX = 24;
	private static final int STRIDE = FLOATS_PER_VERTEX * 4;
	private static final int MAX_QUADS = 8192;

	private static UiRenderer instance;

	private final MinecraftClient mc = MinecraftClient.getInstance();
	private final GlProgram program = new GlProgram("ui.vert", "ui.frag",
		"aPos", "aLocal", "aSize", "aRadii", "aColor1", "aColor2", "aParams", "aUv");
	private final FontManager fonts = new FontManager();
	private final FrameBlur blur = new FrameBlur();
	private final GlStateSnapshot snapshot = new GlStateSnapshot();
	private final int vao, vbo, ebo;
	private final FloatBuffer data = MemoryUtil.memAllocFloat(MAX_QUADS * 4 * FLOATS_PER_VERTEX);
	private final Matrix4f projection = new Matrix4f();
	private final Deque<State> stack = new ArrayDeque<>();

	private int quads;
	private int texture;
	private boolean active;

	// Frame state
	private DrawContext context;
	private int fbW, fbH;
	private long frame;
	private int blurTexture;
	private boolean blurEnabled;
	private int blurPasses = 3;
	private float blurOffset = 2.5f;
	private long lastBegin;
	private float frameDelta;

	// Transform/clip/alpha state
	private State state = new State();

	// Theme defaults
	private FontFamily defaultFont = FontFamily.SANS;
	private float defaultSize = 7.5f;

	/** The renderer if it has been created (it needs GL, so it is created on first draw). */
	public static UiRenderer peek() {
		return instance;
	}

	public static UiRenderer get() {
		if (instance == null) instance = new UiRenderer();
		return instance;
	}

	private UiRenderer() {
		snapshot.save();
		vao = glGenVertexArrays();
		vbo = glGenBuffers();
		ebo = glGenBuffers();
		glBindVertexArray(vao);
		GlStateManager._glBindBuffer(GL_ARRAY_BUFFER, vbo);
		glBufferData(GL_ARRAY_BUFFER, (long) data.capacity() * 4, GL_STREAM_DRAW);
		int[] sizes = {2, 2, 2, 4, 4, 4, 4, 2};
		int offset = 0;
		for (int i = 0; i < sizes.length; i++) {
			glEnableVertexAttribArray(i);
			glVertexAttribPointer(i, sizes[i], GL_FLOAT, false, STRIDE, offset * 4L);
			offset += sizes[i];
		}
		IntBuffer idx = MemoryUtil.memAllocInt(MAX_QUADS * 6);
		for (int q = 0; q < MAX_QUADS; q++) {
			int b = q * 4;
			idx.put(b).put(b + 1).put(b + 2).put(b + 2).put(b + 3).put(b);
		}
		idx.flip();
		glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
		glBufferData(GL_ELEMENT_ARRAY_BUFFER, idx, GL_STATIC_DRAW);
		MemoryUtil.memFree(idx);
		snapshot.restore();
	}

	public FontManager fonts() {
		return fonts;
	}

	public void setDefaults(FontFamily font, float size) {
		this.defaultFont = font;
		this.defaultSize = size;
	}

	public void setBlur(boolean enabled, int passes, float offset) {
		this.blurEnabled = enabled;
		this.blurPasses = passes;
		this.blurOffset = offset;
	}

	/** Starts drawing. {@code pixelScale} is framebuffer pixels per UI unit. */
	public UiRenderer begin(DrawContext ctx, float pixelScale) {
		if (active) throw new IllegalStateException("UiRenderer.begin() called twice");
		active = true;
		context = ctx;
		ctx.draw();
		fbW = mc.getWindow().getFramebufferWidth();
		fbH = mc.getWindow().getFramebufferHeight();
		projection.setOrtho(0, fbW, fbH, 0, -1, 1);
		long now = System.nanoTime();
		frameDelta = lastBegin == 0 ? 16f : Math.min(100f, (now - lastBegin) / 1_000_000f);
		lastBegin = now;
		state = new State();
		state.scale = pixelScale;
		state.clipX1 = fbW;
		state.clipY1 = fbH;
		stack.clear();
		quads = 0;
		texture = 0;
		blurTexture = 0;
		return this;
	}

	public void end() {
		flush();
		active = false;
		context = null;
	}

	public boolean isActive() {
		return active;
	}

	/** Advance once per rendered frame so the blur is computed at most once per frame. */
	public void nextFrame() {
		frame++;
	}

	// ------------------------------------------------------------------------------------------------------------
	// Transform helpers

	private float px(float x) {
		return x * state.scale + state.tx;
	}

	private float py(float y) {
		return y * state.scale + state.ty;
	}

	private int color(int argb) {
		if (state.alpha >= 1f) return argb;
		return (Math.round((argb >>> 24) * state.alpha) << 24) | (argb & 0xFFFFFF);
	}

	private boolean culled(float x, float y, float w, float h) {
		return x > state.clipX1 || y > state.clipY1 || x + w < state.clipX0 || y + h < state.clipY0;
	}

	// ------------------------------------------------------------------------------------------------------------
	// Shapes

	@Override
	public void rect(float x, float y, float w, float h, int color) {
		roundRect(x, y, w, h, 0, 0, 0, 0, color);
	}

	@Override
	public void roundRect(float x, float y, float w, float h, float radius, int color) {
		roundRect(x, y, w, h, radius, radius, radius, radius, color);
	}

	@Override
	public void roundRect(float x, float y, float w, float h, float tl, float tr, float br, float bl, int color) {
		float s = state.scale;
		box(px(x), py(y), w * s, h * s, tl * s, tr * s, br * s, bl * s, color, color, FILL, 0, 0, 0, 0, 0, 0, 0);
	}

	@Override
	public void gradientRect(float x, float y, float w, float h, float radius, int from, int to, float angle) {
		float s = state.scale, r = radius * s;
		box(px(x), py(y), w * s, h * s, r, r, r, r, from, to, FILL, 0, (float) Math.toRadians(angle), 0, 0, 0, 0, 0);
	}

	@Override
	public void outline(float x, float y, float w, float h, float radius, float thickness, int color) {
		gradientOutline(x, y, w, h, radius, thickness, color, color, 0);
	}

	@Override
	public void gradientOutline(float x, float y, float w, float h, float radius, float thickness, int from, int to, float angle) {
		float s = state.scale, r = radius * s;
		box(px(x), py(y), w * s, h * s, r, r, r, r, from, to, OUTLINE, Math.max(1f, thickness * s), (float) Math.toRadians(angle), 0, 0, 0, 0, 0);
	}

	@Override
	public void shadow(float x, float y, float w, float h, float radius, float size, int color) {
		float s = state.scale, r = radius * s, sz = size * s;
		box(px(x) - sz, py(y) - sz, w * s + sz * 2, h * s + sz * 2, r, r, r, r, color, color, SHADOW, sz, 0, 0, 0, 0, 0, 0);
	}

	@Override
	public void backdrop(float x, float y, float w, float h, float radius, float opacity) {
		if (!blurEnabled) return;
		if (blurTexture == 0) {
			flush();
			blurTexture = blur.blurredScene(frame, blurPasses, blurOffset);
		}
		float s = state.scale, r = radius * s;
		int c = (Math.round(Math.clamp(opacity, 0f, 1f) * 255) << 24) | 0xFFFFFF;
		box(px(x), py(y), w * s, h * s, r, r, r, r, c, c, BACKDROP, 0, 0, 0, 0, 0, 0, 0);
	}

	@Override
	public void circle(float cx, float cy, float radius, int color) {
		roundRect(cx - radius, cy - radius, radius * 2, radius * 2, radius, color);
	}

	@Override
	public void line(float x1, float y1, float x2, float y2, float thickness, int color) {
		float ax = px(x1), ay = py(y1), bx = px(x2), by = py(y2);
		float dx = bx - ax, dy = by - ay;
		float len = (float) Math.sqrt(dx * dx + dy * dy);
		if (len < 0.01f) return;
		float th = Math.max(1f, thickness * state.scale);
		float nx = -dy / len * th / 2, ny = dx / len * th / 2;
		ensure(0);
		int c = color(color);
		vertex(ax - nx, ay - ny, 0, 0, len, th, 0, 0, 0, 0, c, c, FILL, 0, 0, 0, 0);
		vertex(ax + nx, ay + ny, 0, th, len, th, 0, 0, 0, 0, c, c, FILL, 0, 0, 0, 0);
		vertex(bx + nx, by + ny, len, th, len, th, 0, 0, 0, 0, c, c, FILL, 0, 0, 0, 0);
		vertex(bx - nx, by - ny, len, 0, len, th, 0, 0, 0, 0, c, c, FILL, 0, 0, 0, 0);
		quads++;
	}

	@Override
	public void texture(Identifier id, float x, float y, float w, float h, float u0, float v0, float u1, float v1, int tint) {
		texture(mc.getTextureManager().getTexture(id).getGlId(), x, y, w, h, u0, v0, u1, v1, tint);
	}

	@Override
	public void texture(int glTextureId, float x, float y, float w, float h, float u0, float v0, float u1, float v1, int tint) {
		float s = state.scale;
		box(px(x), py(y), w * s, h * s, 0, 0, 0, 0, tint, tint, IMAGE, 0, 0, u0, v0, u1, v1, glTextureId);
	}

	@Override
	public void item(ItemStack stack, float x, float y, float size, boolean overlay) {
		if (stack.isEmpty()) return;
		flush();
		float gs = (float) mc.getWindow().getScaleFactor();
		MatrixStack m = context.getMatrices();
		m.push();
		m.translate(px(x) / gs, py(y) / gs, 0);
		float k = size * state.scale / gs / 16f;
		m.scale(k, k, 1);
		applyScissor();
		context.drawItem(stack, 0, 0);
		if (overlay) context.drawStackOverlay(mc.textRenderer, stack, 0, 0);
		context.draw();
		RenderSystem.disableScissor();
		m.pop();
	}

	private void box(float x, float y, float w, float h, float tl, float tr, float br, float bl, int c1, int c2,
					 int type, float param, float angle, float u0, float v0, float u1, float v1, int tex) {
		if (w <= 0 || h <= 0 || culled(x, y, w, h)) return;
		c1 = color(c1);
		c2 = color(c2);
		if ((c1 >>> 24) == 0 && (c2 >>> 24) == 0) return;
		ensure(tex);
		vertex(x, y, 0, 0, w, h, tl, tr, br, bl, c1, c2, type, param, angle, u0, v0);
		vertex(x, y + h, 0, h, w, h, tl, tr, br, bl, c1, c2, type, param, angle, u0, v1);
		vertex(x + w, y + h, w, h, w, h, tl, tr, br, bl, c1, c2, type, param, angle, u1, v1);
		vertex(x + w, y, w, 0, w, h, tl, tr, br, bl, c1, c2, type, param, angle, u1, v0);
		quads++;
	}

	private void ensure(int tex) {
		if (!active) throw new IllegalStateException("Canvas used outside of UiRenderer.begin()/end()");
		if (tex != 0 && texture != 0 && tex != texture) flush();
		if (quads >= MAX_QUADS) flush();
		if (tex != 0) texture = tex;
	}

	private void vertex(float x, float y, float lx, float ly, float w, float h, float tl, float tr, float br, float bl,
						int c1, int c2, int type, float param, float angle, float u, float v) {
		data.put(x).put(y).put(lx).put(ly).put(w).put(h).put(tl).put(tr).put(br).put(bl);
		data.put((c1 >> 16 & 255) / 255f).put((c1 >> 8 & 255) / 255f).put((c1 & 255) / 255f).put((c1 >>> 24) / 255f);
		data.put((c2 >> 16 & 255) / 255f).put((c2 >> 8 & 255) / 255f).put((c2 & 255) / 255f).put((c2 >>> 24) / 255f);
		data.put(type).put(param).put(angle).put(0);
		data.put(u).put(v);
	}

	// ------------------------------------------------------------------------------------------------------------
	// Text

	private SizedFont font(FontFamily family, float size) {
		return fonts.get(family, Math.round(size * state.scale));
	}

	@Override
	public float text(String text, float x, float y, int color) {
		return text(defaultFont, defaultSize, text, x, y, color);
	}

	@Override
	public float text(FontFamily family, float size, String text, float x, float y, int color) {
		if (text == null || text.isEmpty()) return 0;
		SizedFont f = font(family, size);
		float startX = Math.round(px(x));
		float cx = startX;
		float top = Math.round(py(y));
		if (top > state.clipY1 || top + f.lineHeight < state.clipY0) return f.width(text) / state.scale;
		int base = color(color);
		int current = base;
		for (int i = 0; i < text.length(); ) {
			int cp = text.codePointAt(i);
			i += Character.charCount(cp);
			if (cp == '§' && i < text.length()) {
				Formatting fmt = Formatting.byCode(text.charAt(i++));
				if (fmt == null || fmt == Formatting.RESET) current = base;
				else if (fmt.getColorValue() != null) current = (base & 0xFF000000) | fmt.getColorValue();
				continue;
			}
			Glyph g = f.glyph(cp);
			if (g == null) continue;
			if (g.visible()) {
				float gx = cx + g.offsetX(), gy = top + g.offsetY();
				float gw = g.cellWidth(), gh = g.cellHeight();
				if (!culled(gx, gy, gw, gh)) {
					ensure(g.texture());
					vertex(gx, gy, 0, 0, gw, gh, 0, 0, 0, 0, current, current, GLYPH, 0, 0, g.u0(), g.v0());
					vertex(gx, gy + gh, 0, 0, gw, gh, 0, 0, 0, 0, current, current, GLYPH, 0, 0, g.u0(), g.v1());
					vertex(gx + gw, gy + gh, 0, 0, gw, gh, 0, 0, 0, 0, current, current, GLYPH, 0, 0, g.u1(), g.v1());
					vertex(gx + gw, gy, 0, 0, gw, gh, 0, 0, 0, 0, current, current, GLYPH, 0, 0, g.u1(), g.v0());
					quads++;
				}
			}
			cx += g.advance();
		}
		return (cx - startX) / state.scale;
	}

	@Override
	public float textWidth(String text) {
		return textWidth(defaultFont, defaultSize, text);
	}

	@Override
	public float textWidth(FontFamily family, float size, String text) {
		if (text == null || text.isEmpty()) return 0;
		return font(family, size).width(text) / state.scale;
	}

	@Override
	public float textHeight() {
		return textHeight(defaultFont, defaultSize);
	}

	@Override
	public float textHeight(FontFamily family, float size) {
		return font(family, size).lineHeight / state.scale;
	}

	@Override
	public FontFamily defaultFont() {
		return defaultFont;
	}

	@Override
	public float defaultFontSize() {
		return defaultSize;
	}

	// ------------------------------------------------------------------------------------------------------------
	// State

	@Override
	public void push() {
		stack.push(state);
		state = state.copy();
	}

	@Override
	public void pop() {
		State prev = stack.pop();
		if (prev.clipX0 != state.clipX0 || prev.clipY0 != state.clipY0 || prev.clipX1 != state.clipX1 || prev.clipY1 != state.clipY1) {
			flush();
		}
		state = prev;
	}

	/** Stack depth, so callers can recover from a panel that threw mid-render. */
	public int depth() {
		return stack.size();
	}

	public void restoreDepth(int depth) {
		while (stack.size() > depth) pop();
	}

	/** Computes the frame blur now, from what has been drawn so far (call before drawing windows). */
	public void captureBlur() {
		if (!blurEnabled) return;
		flush();
		blurTexture = blur.blurredScene(frame, blurPasses, blurOffset);
	}

	@Override
	public void translate(float x, float y) {
		state.tx += x * state.scale;
		state.ty += y * state.scale;
	}

	@Override
	public void scale(float s) {
		state.scale *= s;
	}

	@Override
	public float pixelScale() {
		return state.scale;
	}

	@Override
	public void clip(float x, float y, float w, float h) {
		flush();
		float x0 = px(x), y0 = py(y), x1 = px(x + w), y1 = py(y + h);
		state.clipX0 = Math.max(state.clipX0, (int) Math.floor(x0));
		state.clipY0 = Math.max(state.clipY0, (int) Math.floor(y0));
		state.clipX1 = Math.min(state.clipX1, (int) Math.ceil(x1));
		state.clipY1 = Math.min(state.clipY1, (int) Math.ceil(y1));
	}

	@Override
	public void alpha(float alpha) {
		state.alpha *= Math.clamp(alpha, 0f, 1f);
	}

	@Override
	public DrawContext drawContext() {
		return context;
	}

	@Override
	public float frameDeltaMs() {
		return frameDelta;
	}

	private boolean clipping() {
		return state.clipX0 > 0 || state.clipY0 > 0 || state.clipX1 < fbW || state.clipY1 < fbH;
	}

	private void applyScissor() {
		if (!clipping()) {
			RenderSystem.disableScissor();
			return;
		}
		int w = Math.max(0, state.clipX1 - state.clipX0), h = Math.max(0, state.clipY1 - state.clipY0);
		RenderSystem.enableScissor(state.clipX0, fbH - state.clipY0 - h, w, h);
	}

	@Override
	public void flush() {
		if (quads == 0) return;
		data.flip();
		snapshot.save();
		RenderSystem.enableBlend();
		RenderSystem.blendFuncSeparate(GL_ONE, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
		RenderSystem.disableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.disableCull();
		applyScissor();

		program.use();
		program.set("uProj", projection);
		program.set("uScreen", fbW, fbH);
		program.set("uTex", 0);
		program.set("uBlur", 1);
		GlStateManager._activeTexture(GL_TEXTURE1);
		GlStateManager._bindTexture(blurTexture);
		GlStateManager._activeTexture(GL_TEXTURE0);
		if (texture != 0) GlStateManager._bindTexture(texture);

		glBindVertexArray(vao);
		GlStateManager._glBindBuffer(GL_ARRAY_BUFFER, vbo);
		glBufferData(GL_ARRAY_BUFFER, (long) data.capacity() * 4, GL_STREAM_DRAW);
		glBufferSubData(GL_ARRAY_BUFFER, 0, data);
		glDrawElements(GL_TRIANGLES, quads * 6, GL_UNSIGNED_INT, 0);

		snapshot.restore();
		RenderSystem.disableScissor();
		RenderSystem.depthMask(true);
		RenderSystem.enableDepthTest();
		RenderSystem.enableCull();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableBlend();

		data.clear();
		quads = 0;
		texture = 0;
	}

	private static final class State {
		float tx, ty, scale = 1f, alpha = 1f;
		int clipX0, clipY0, clipX1, clipY1;

		State copy() {
			State s = new State();
			s.tx = tx;
			s.ty = ty;
			s.scale = scale;
			s.alpha = alpha;
			s.clipX0 = clipX0;
			s.clipY0 = clipY0;
			s.clipX1 = clipX1;
			s.clipY1 = clipY1;
			return s;
		}
	}
}
