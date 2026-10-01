package dev.myriad.essentials.util;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;

/** Flat, translucent, untextured layers for entity chams; one depth-tested, one drawn through walls. */
public final class ChamsLayers {
	public static final RenderLayer FILL = layer("myriad_chams_fill", RenderPhase.LEQUAL_DEPTH_TEST);
	public static final RenderLayer FILL_THROUGH_WALLS = layer("myriad_chams_fill_xray", RenderPhase.ALWAYS_DEPTH_TEST);

	private ChamsLayers() {
	}

	private static RenderLayer layer(String name, RenderPhase.DepthTest depth) {
		return RenderLayer.of(name, VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS, 1536,
			RenderLayer.MultiPhaseParameters.builder()
				.program(RenderPhase.POSITION_COLOR_PROGRAM)
				.transparency(RenderPhase.TRANSLUCENT_TRANSPARENCY)
				.depthTest(depth)
				.cull(RenderPhase.DISABLE_CULLING)
				.writeMaskState(RenderPhase.COLOR_MASK)
				.build(false));
	}
}
