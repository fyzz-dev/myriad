package dev.myriad.impl.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.*;

/**
 * Blurs the current scene once per frame (dual Kawase: downsample N levels, upsample back to half resolution). Every
 * frosted window then samples the same texture instead of copying and blurring once per window.
 */
public final class FrameBlur {
	private static final int MAX_LEVELS = 6;

	private final GlProgram down = new GlProgram("blur.vert", "blur_down.frag", "aPos");
	private final GlProgram up = new GlProgram("blur.vert", "blur_up.frag", "aPos");
	private final int[] fbo = new int[MAX_LEVELS + 1];
	private final int[] tex = new int[MAX_LEVELS + 1];
	private final int[] w = new int[MAX_LEVELS + 1];
	private final int[] h = new int[MAX_LEVELS + 1];
	private final int vao, vbo;
	private final GlStateSnapshot snapshot = new GlStateSnapshot();
	private int width, height;
	private long lastFrame = -1;
	private int result;

	public FrameBlur() {
		snapshot.save();
		vao = glGenVertexArrays();
		vbo = glGenBuffers();
		glBindVertexArray(vao);
		GlStateManager._glBindBuffer(GL_ARRAY_BUFFER, vbo);
		try (MemoryStack stack = MemoryStack.stackPush()) {
			FloatBuffer tri = stack.floats(-1, -1, 3, -1, -1, 3);
			glBufferData(GL_ARRAY_BUFFER, tri, GL_STATIC_DRAW);
		}
		glEnableVertexAttribArray(0);
		glVertexAttribPointer(0, 2, GL_FLOAT, false, 8, 0);
		snapshot.restore();
	}

	/**
	 * Returns a texture holding the blurred scene for this frame, computing it on first call per frame.
	 *
	 * @param passes 1..6, more = wider blur
	 * @param offset sample spread per pass
	 */
	public int blurredScene(long frame, int passes, float offset) {
		if (frame == lastFrame && result != 0) return result;
		lastFrame = frame;
		passes = Math.clamp(passes, 1, MAX_LEVELS);
		Framebuffer main = MinecraftClient.getInstance().getFramebuffer();
		resize(main.textureWidth, main.textureHeight);

		snapshot.save();
		RenderSystem.disableBlend();
		RenderSystem.disableScissor();
		RenderSystem.disableDepthTest();

		// Level 0: scene at half resolution.
		GlStateManager._glBindFramebuffer(GL_READ_FRAMEBUFFER, main.fbo);
		GlStateManager._glBindFramebuffer(GL_DRAW_FRAMEBUFFER, fbo[0]);
		GlStateManager._glBlitFrameBuffer(0, 0, main.textureWidth, main.textureHeight, 0, 0, w[0], h[0], GL_COLOR_BUFFER_BIT, GL_LINEAR);

		glBindVertexArray(vao);
		down.use();
		down.set("uTex", 0);
		down.set("uOffset", offset);
		for (int i = 0; i < passes; i++) pass(down, i, i + 1);
		up.use();
		up.set("uTex", 0);
		up.set("uOffset", offset);
		for (int i = passes; i > 0; i--) pass(up, i, i - 1);

		snapshot.restore();
		main.beginWrite(true);
		RenderSystem.enableDepthTest();
		result = tex[0];
		return result;
	}

	private void pass(GlProgram program, int from, int to) {
		GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, fbo[to]);
		GlStateManager._viewport(0, 0, w[to], h[to]);
		program.set("uHalfTexel", 0.5f / w[from], 0.5f / h[from]);
		GlStateManager._activeTexture(GL_TEXTURE0);
		GlStateManager._bindTexture(tex[from]);
		glDrawArrays(GL_TRIANGLES, 0, 3);
	}

	private void resize(int fbW, int fbH) {
		if (fbW == width && fbH == height && fbo[0] != 0) return;
		width = fbW;
		height = fbH;
		for (int i = 0; i <= MAX_LEVELS; i++) {
			if (fbo[i] != 0) {
				glDeleteFramebuffers(fbo[i]);
				GlStateManager._deleteTexture(tex[i]);
			}
			w[i] = Math.max(1, fbW >> (i + 1));
			h[i] = Math.max(1, fbH >> (i + 1));
			tex[i] = GlStateManager._genTexture();
			GlStateManager._bindTexture(tex[i]);
			GlStateManager._texParameter(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
			GlStateManager._texParameter(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
			GlStateManager._texParameter(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
			GlStateManager._texParameter(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
			GlStateManager._texImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w[i], h[i], 0, GL_RGBA, GL_UNSIGNED_BYTE, null);
			fbo[i] = glGenFramebuffers();
			GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, fbo[i]);
			glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, tex[i], 0);
		}
		MinecraftClient.getInstance().getFramebuffer().beginWrite(true);
	}
}
