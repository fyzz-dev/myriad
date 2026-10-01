package dev.myriad.impl.render;

import com.mojang.blaze3d.platform.GlStateManager;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;

import static org.lwjgl.opengl.GL20.*;

/** Raw GL program wrapper: attribute binding, cached uniform locations. */
public final class GlProgram {
	private final int id;
	private final Object2IntOpenHashMap<String> uniforms = new Object2IntOpenHashMap<>();

	public GlProgram(String vertex, String fragment, String... attributes) {
		int v = compile(GL_VERTEX_SHADER, vertex);
		int f = compile(GL_FRAGMENT_SHADER, fragment);
		id = glCreateProgram();
		glAttachShader(id, v);
		glAttachShader(id, f);
		for (int i = 0; i < attributes.length; i++) glBindAttribLocation(id, i, attributes[i]);
		glLinkProgram(id);
		if (glGetProgrami(id, GL_LINK_STATUS) == 0) {
			throw new IllegalStateException("Link failed for " + vertex + "/" + fragment + ":\n" + glGetProgramInfoLog(id));
		}
		glDeleteShader(v);
		glDeleteShader(f);
		uniforms.defaultReturnValue(-2);
	}

	private static int compile(int type, String file) {
		int s = glCreateShader(type);
		glShaderSource(s, read(file));
		glCompileShader(s);
		if (glGetShaderi(s, GL_COMPILE_STATUS) == 0) throw new IllegalStateException("Compile failed for " + file + ":\n" + glGetShaderInfoLog(s));
		return s;
	}

	private static String read(String file) {
		String path = "/assets/myriad/shaders/" + file;
		try (InputStream in = GlProgram.class.getResourceAsStream(path)) {
			if (in == null) throw new IllegalStateException("Missing shader " + path);
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new IllegalStateException("Cannot read shader " + path, e);
		}
	}

	public void use() {
		GlStateManager._glUseProgram(id);
	}

	private int location(String name) {
		int loc = uniforms.getInt(name);
		if (loc == -2) {
			loc = glGetUniformLocation(id, name);
			uniforms.put(name, loc);
		}
		return loc;
	}

	public void set(String name, int v) {
		int l = location(name);
		if (l >= 0) glUniform1i(l, v);
	}

	public void set(String name, float v) {
		int l = location(name);
		if (l >= 0) glUniform1f(l, v);
	}

	public void set(String name, float x, float y) {
		int l = location(name);
		if (l >= 0) glUniform2f(l, x, y);
	}

	public void set(String name, Matrix4f m) {
		int l = location(name);
		if (l < 0) return;
		try (MemoryStack stack = MemoryStack.stackPush()) {
			FloatBuffer fb = stack.mallocFloat(16);
			m.get(fb);
			glUniformMatrix4fv(l, false, fb);
		}
	}
}
