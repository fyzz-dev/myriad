package dev.myriad.impl.render;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.render.BufferRenderer;

import static org.lwjgl.opengl.GL11.glGetInteger;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL20.GL_CURRENT_PROGRAM;
import static org.lwjgl.opengl.GL30.GL_VERTEX_ARRAY_BINDING;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER_BINDING;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;

/**
 * Saves/restores the GL state Minecraft does not track itself (program, VAO, array buffer). Texture bindings, blend,
 * depth and scissor go through {@link GlStateManager}/RenderSystem so its caches stay correct.
 */
final class GlStateSnapshot {
	private int program, vao, arrayBuffer, activeTexture;

	void save() {
		program = glGetInteger(GL_CURRENT_PROGRAM);
		vao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
		arrayBuffer = glGetInteger(GL_ARRAY_BUFFER_BINDING);
		activeTexture = GlStateManager._getActiveTexture();
	}

	void restore() {
		glBindVertexArray(vao);
		GlStateManager._glBindBuffer(GL_ARRAY_BUFFER, arrayBuffer);
		GlStateManager._glUseProgram(program);
		GlStateManager._activeTexture(activeTexture);
		// Vanilla caches the last bound VertexBuffer; we bound our own VAO, so make it rebind next time.
		BufferRenderer.resetCurrentVertexBuffer();
	}

	static int unit(int i) {
		return GL_TEXTURE0 + i;
	}
}
