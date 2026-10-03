package dev.myriad.impl.render;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * Myriad's render pipelines and the vertex format of the UI renderer. Registered with vanilla's static pipelines so
 * they compile with the shaders on every resource reload.
 */
public final class MyriadPipelines {
	/** One UI quad corner: see {@code shaders/core/ui.vsh}. Only Position is a vanilla attribute; the rest Myriad writes. */
	public static final VertexFormat UI_FORMAT = VertexFormat.builder(0)
		.addAttribute("Position", GpuFormat.RGB32_FLOAT)
		.addAttribute("Local", GpuFormat.RG32_FLOAT)
		.addAttribute("Size", GpuFormat.RG32_FLOAT)
		.addAttribute("Radii", GpuFormat.RGBA32_FLOAT)
		.addAttribute("Color1", GpuFormat.RGBA32_FLOAT)
		.addAttribute("Color2", GpuFormat.RGBA32_FLOAT)
		.addAttribute("Params", GpuFormat.RGBA32_FLOAT)
		.addAttribute("TexCoord", GpuFormat.RG32_FLOAT)
		.addAttribute("Clip", GpuFormat.RGBA32_FLOAT)
		.build();

	public static final RenderPipeline UI = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
		.withLocation(id("pipeline/ui"))
		.withVertexShader(id("core/ui"))
		.withFragmentShader(id("core/ui"))
		.withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER1)
		.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
		.withVertexBinding(0, UI_FORMAT)
		.withPrimitiveTopology(PrimitiveTopology.QUADS)
		.withCull(false)
		.build());

	private static final BindGroupLayout BLUR_LAYOUT = BindGroupLayout.builder()
		.withSampler("InSampler")
		.withUniform("BlurInfo", UniformType.UNIFORM_BUFFER)
		.build();

	public static final RenderPipeline BLUR_DOWN = RenderPipelines.register(blur("blur_down"));
	public static final RenderPipeline BLUR_UP = RenderPipelines.register(blur("blur_up"));

	// World shapes. Depth is reversed in 26.x (1 is near), so "in front" is GREATER_THAN_OR_EQUAL and a positive bias
	// pulls shapes towards the camera, keeping fills that lie on a block face from z-fighting with it.
	private static final DepthStencilState WORLD_DEPTH = new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false, 1f, 1f);
	private static final DepthStencilState THROUGH_WALLS = new DepthStencilState(CompareOp.ALWAYS_PASS, false);

	public static final RenderPipeline WORLD_QUADS = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
		.withLocation(id("pipeline/world_quads")).withDepthStencilState(WORLD_DEPTH).build());
	public static final RenderPipeline WORLD_QUADS_XRAY = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
		.withLocation(id("pipeline/world_quads_xray")).withDepthStencilState(THROUGH_WALLS).build());
	public static final RenderPipeline WORLD_LINES = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
		.withLocation(id("pipeline/world_lines")).withDepthStencilState(WORLD_DEPTH).build());
	public static final RenderPipeline WORLD_LINES_XRAY = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
		.withLocation(id("pipeline/world_lines_xray")).withDepthStencilState(THROUGH_WALLS).build());

	// Retained meshes (WorldMesh, ChunkCache): the same shapes, but the vertex shaders add ModelOffset (the mesh origin
	// relative to the camera), so one GPU buffer is drawn frame after frame without being rebuilt.
	public static final RenderPipeline MESH_QUADS = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
		.withLocation(id("pipeline/mesh_quads")).withVertexShader(id("core/mesh_color")).withDepthStencilState(WORLD_DEPTH).build());
	public static final RenderPipeline MESH_QUADS_XRAY = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
		.withLocation(id("pipeline/mesh_quads_xray")).withVertexShader(id("core/mesh_color")).withDepthStencilState(THROUGH_WALLS).build());
	public static final RenderPipeline MESH_LINES = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
		.withLocation(id("pipeline/mesh_lines")).withVertexShader(id("core/mesh_lines")).withDepthStencilState(WORLD_DEPTH).build());
	public static final RenderPipeline MESH_LINES_XRAY = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
		.withLocation(id("pipeline/mesh_lines_xray")).withVertexShader(id("core/mesh_lines")).withDepthStencilState(THROUGH_WALLS).build());

	// Entity models in a flat colour (Chams, ESP): submitted in the entity vertex format, the shader only reads position
	// and colour.
	public static final RenderPipeline MODEL_FILL = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
		.withLocation(id("pipeline/model_fill")).withVertexBinding(0, DefaultVertexFormat.ENTITY)
		.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false)).build());
	public static final RenderPipeline MODEL_FILL_XRAY = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
		.withLocation(id("pipeline/model_fill_xray")).withVertexBinding(0, DefaultVertexFormat.ENTITY).withDepthStencilState(THROUGH_WALLS).build());

	public static final RenderType QUADS = RenderType.create("myriad_quads", RenderSetup.builder(WORLD_QUADS).sortOnUpload().createRenderSetup());
	public static final RenderType QUADS_XRAY = RenderType.create("myriad_quads_xray", RenderSetup.builder(WORLD_QUADS_XRAY).sortOnUpload().createRenderSetup());
	public static final RenderType LINES = RenderType.create("myriad_lines", RenderSetup.builder(WORLD_LINES).createRenderSetup());
	public static final RenderType LINES_XRAY = RenderType.create("myriad_lines_xray", RenderSetup.builder(WORLD_LINES_XRAY).createRenderSetup());
	public static final RenderType MODEL = RenderType.create("myriad_model_fill", RenderSetup.builder(MODEL_FILL).createRenderSetup());
	public static final RenderType MODEL_XRAY = RenderType.create("myriad_model_fill_xray", RenderSetup.builder(MODEL_FILL_XRAY).createRenderSetup());

	private MyriadPipelines() {
	}

	/** Loads the class, registering the pipelines; call before the first resource reload. */
	public static void init() {
	}

	private static RenderPipeline blur(String name) {
		return RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
			.withLocation(id("pipeline/" + name))
			.withVertexShader(id("core/blur"))
			.withFragmentShader(id("core/" + name))
			.withBindGroupLayout(BLUR_LAYOUT)
			.withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_ALL))
			.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
			.build();
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("myriad", path);
	}
}
