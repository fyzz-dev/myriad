#version 330

#moj_import <myriad:highlight.glsl>

// Second half of the distance search, then the highlight itself. The nearest mask pixel over this column's row results
// gives the exact distance to the nearest silhouette (searched outwards, stopping once rows further away can't be
// closer); inside a silhouette the fill is drawn instead.
uniform sampler2D SpreadSampler;

out vec4 fragColor;

const int FILL_NONE = 0;
const int FILL_DOTS = 2;

// The highlight's colour at this height: top colour to bottom colour across its gradient rectangle.
vec4 colorAt(int id) {
    vec4 rect = highlightData(id, 2);
    float t = rect.w > rect.y ? clamp((rect.w - gl_FragCoord.y) / (rect.w - rect.y), 0.0, 1.0) : 0.0;
    return mix(highlightData(id, 0), highlightData(id, 1), t);
}

void main() {
    ivec2 p = ivec2(gl_FragCoord.xy);
    vec4 here = texelFetch(SpreadSampler, p, 0);
    if (here.r == 0.0) {
        int id = highlightId(here.gba);
        vec4 fill = highlightData(id, 4);
        int mode = int(fill.x + 0.5);
        if (mode == FILL_NONE) discard;
        float alpha = highlightData(id, 3).w;
        if (mode == FILL_DOTS) {
            // Anchored to the highlight's rectangle, so the grid moves with it rather than sliding across it.
            vec2 cell = mod(gl_FragCoord.xy - highlightData(id, 2).xy, fill.y) - fill.y * 0.5;
            alpha *= clamp(fill.z + 0.5 - length(cell), 0.0, 1.0);
        }
        vec4 color = colorAt(id);
        alpha *= color.a;
        if (alpha <= 0.0) discard;
        fragColor = vec4(color.rgb, alpha);
        return;
    }

    float best = 1e9;
    vec3 nearest = vec3(0.0);
    if (here.r < 1.0) {
        float dx = here.r * 255.0;
        best = dx * dx;
        nearest = here.gba;
    }
    int radius = highlightRadius();
    int height = textureSize(SpreadSampler, 0).y;
    for (int d = 1; d <= radius; d++) {
        float dy2 = float(d * d);
        if (dy2 >= best) break;
        if (p.y - d >= 0) {
            vec4 below = texelFetch(SpreadSampler, ivec2(p.x, p.y - d), 0);
            if (below.r < 1.0) {
                float dx = below.r * 255.0;
                float d2 = dx * dx + dy2;
                if (d2 < best) {
                    best = d2;
                    nearest = below.gba;
                }
            }
        }
        if (p.y + d < height) {
            vec4 above = texelFetch(SpreadSampler, ivec2(p.x, p.y + d), 0);
            if (above.r < 1.0) {
                float dx = above.r * 255.0;
                float d2 = dx * dx + dy2;
                if (d2 < best) {
                    best = d2;
                    nearest = above.gba;
                }
            }
        }
    }
    if (best >= 1e9) discard;

    int id = highlightId(nearest);
    vec4 shape = highlightData(id, 3);
    // Distance from this pixel's centre to the silhouette's edge, half a pixel short of the nearest mask pixel's centre.
    float edge = sqrt(best) - 0.5;
    float alpha = clamp(shape.x + 0.5 - edge, 0.0, 1.0);
    if (shape.y > 0.0) {
        float g = clamp((edge - shape.x) / shape.y, 0.0, 1.0);
        alpha = max(alpha, shape.z * (1.0 - g) * (1.0 - g));
    }
    vec4 color = colorAt(id);
    alpha *= color.a;
    if (alpha <= 0.0) discard;
    fragColor = vec4(color.rgb, alpha);
}
