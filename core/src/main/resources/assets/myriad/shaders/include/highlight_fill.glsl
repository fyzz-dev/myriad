// The highlight composite: outline, glow and fill from the spread pass. A fill shader (an addon's own fill, see
// HighlightStyle.Fill.custom) is a fragment shader that imports this and defines customFill:
//
//     #version 330
//     #moj_import <myriad:highlight_fill.glsl>
//
//     vec4 customFill(HighlightFill f) {
//         return vec4(f.color.rgb, f.color.a * f.opacity * pattern(f.local));
//     }
//
// It runs once more per frame for its own highlights' insides (MYRIAD_FILL_ID is its number); the main pass (number 0)
// draws everything else.

#moj_import <myriad:highlight.glsl>

#ifndef MYRIAD_FILL_ID
#define MYRIAD_FILL_ID 0
#endif

// What a fill shader gets for one pixel inside a highlight.
struct HighlightFill {
    vec2 pixel;    // framebuffer pixel (gl_FragCoord.xy)
    vec2 local;    // pixel relative to the highlight's screen rectangle, from its bottom-left corner
    vec2 size;     // that rectangle's size in pixels
    vec4 color;    // the highlight's colour here (gradient applied; alpha includes any fade)
    float opacity; // the style's fill opacity
    float spacing; // the style's dot spacing, in pixels: a pattern's scale
    float dotSize; // the style's dot size (diameter), in pixels
    float scale;   // pixels per GUI pixel
    float time;    // seconds, for animation (wraps every hour)
};

vec4 customFill(HighlightFill f);

// Second half of the distance search, then the highlight itself. The nearest candidate over this column's row results
// gives the exact distance (searched outwards, stopping once rows further away can't be closer). Outside every
// silhouette: the nearest highlight's outline and glow. Inside one: its fill, under the outline of a later highlight of
// another group that touches it.
uniform sampler2D SpreadSampler;

out vec4 fragColor;

const int FILL_NONE = 0;
const int FILL_DOTS = 2;
const int FILL_CUSTOM = 16;

// The highlight's colour at this height: top colour to bottom colour across its gradient rectangle.
vec4 colorAt(int id) {
    vec4 rect = highlightData(id, 2);
    float t = rect.w > rect.y ? clamp((rect.w - gl_FragCoord.y) / (rect.w - rect.y), 0.0, 1.0) : 0.0;
    return mix(highlightData(id, 0), highlightData(id, 1), t);
}

// Outline (and, outside silhouettes, glow) coverage of highlight id at squared distance d2 from its nearest pixel.
float edgeAlpha(int id, float d2, bool glow) {
    vec4 shape = highlightData(id, 3);
    // From this pixel's centre to the silhouette's edge, half a pixel short of the nearest mask pixel's centre.
    float edge = sqrt(d2) - 0.5;
    float alpha = clamp(shape.x + 0.5 - edge, 0.0, 1.0);
    if (glow && shape.y > 0.0) {
        float g = clamp((edge - shape.x) / shape.y, 0.0, 1.0);
        alpha = max(alpha, shape.z * (1.0 - g) * (1.0 - g));
    }
    return alpha;
}

void main() {
    ivec2 p = ivec2(gl_FragCoord.xy);
    int height = textureSize(SpreadSampler, 0).y;
    vec4 here = texelFetch(SpreadSampler, p, 0);
    int hereNear = unpack16(here.r);

    if (hereNear % 256 == 0) {
        // Inside a silhouette.
        int id = unpack16(here.g);
        int group = hereNear / 256;
        vec4 fill = highlightData(id, 4);
        int mode = int(fill.x + 0.5);
        // An addon's fill is drawn by its own pass; each pass draws only its own fill's insides.
        if ((mode >= FILL_CUSTOM) != (MYRIAD_FILL_ID != 0) || (MYRIAD_FILL_ID != 0 && mode != MYRIAD_FILL_ID)) discard;
        float fillAlpha = 0.0;
        vec4 fillColor = colorAt(id);
        if (mode >= FILL_CUSTOM) {
            vec4 rect = highlightData(id, 2);
            HighlightFill f;
            f.pixel = gl_FragCoord.xy;
            f.local = gl_FragCoord.xy - rect.xy;
            f.size = rect.zw - rect.xy;
            f.color = fillColor;
            f.opacity = highlightData(id, 3).w;
            f.spacing = fill.y;
            f.dotSize = fill.z * 2.0;
            f.scale = highlightGuiScale();
            f.time = highlightTime();
            vec4 c = customFill(f);
            fillColor = vec4(c.rgb, 1.0);
            fillAlpha = clamp(c.a, 0.0, 1.0);
        } else if (mode != FILL_NONE) {
            fillAlpha = highlightData(id, 3).w;
            if (mode == FILL_DOTS) {
                // Anchored to the highlight's rectangle, so the grid moves with it rather than sliding across it.
                vec2 cell = mod(gl_FragCoord.xy - highlightData(id, 2).xy, fill.y) - fill.y * 0.5;
                fillAlpha *= clamp(fill.z + 0.5 - length(cell), 0.0, 1.0);
            }
            fillAlpha *= fillColor.a;
        }

        // The nearest pixel of another group: in rows of this group, its own search found it; a row whose pixel here
        // belongs to another group is a candidate itself. (Rows outside every silhouette are skipped: there the plain
        // outline already shows the edge.)
        float best = 1e9;
        int other = -1;
        int hereOther = unpack16(here.b);
        if (hereOther % 256 != NONE) {
            float dx = float(hereOther % 256);
            best = dx * dx;
            other = unpack16(here.a);
        }
        int reach = highlightBoundaryRadius();
        for (int d = 1; d <= reach; d++) {
            float dy2 = float(d * d);
            if (dy2 >= best) break;
            for (int s = -1; s <= 1; s += 2) {
                int y = p.y + s * d;
                if (y < 0 || y >= height) continue;
                vec4 row = texelFetch(SpreadSampler, ivec2(p.x, y), 0);
                int near = unpack16(row.r);
                if (near % 256 != 0) continue;
                if (near / 256 == group) {
                    int o = unpack16(row.b);
                    if (o % 256 == NONE) continue;
                    float dx = float(o % 256);
                    if (dx * dx + dy2 < best) {
                        best = dx * dx + dy2;
                        other = unpack16(row.a);
                    }
                } else if (dy2 < best) {
                    best = dy2;
                    other = unpack16(row.g);
                }
            }
        }

        float lineAlpha = 0.0;
        vec4 lineColor = vec4(0.0);
        // The later highlight draws the shared edge, over the earlier one.
        if (other > id) {
            lineColor = colorAt(other);
            lineAlpha = edgeAlpha(other, best, false) * lineColor.a;
        }
        float alpha = lineAlpha + fillAlpha * (1.0 - lineAlpha);
        if (alpha <= 0.0) discard;
        vec3 rgb = (lineColor.rgb * lineAlpha + fillColor.rgb * fillAlpha * (1.0 - lineAlpha)) / alpha;
        fragColor = vec4(rgb, alpha);
        return;
    }

    // Outlines and glow outside silhouettes belong to the main pass.
    if (MYRIAD_FILL_ID != 0) discard;
    float best = 1e9;
    int nearest = -1;
    if (hereNear % 256 != NONE) {
        float dx = float(hereNear % 256);
        best = dx * dx;
        nearest = unpack16(here.g);
    }
    int radius = highlightRadius();
    for (int d = 1; d <= radius; d++) {
        float dy2 = float(d * d);
        if (dy2 >= best) break;
        for (int s = -1; s <= 1; s += 2) {
            int y = p.y + s * d;
            if (y < 0 || y >= height) continue;
            vec4 row = texelFetch(SpreadSampler, ivec2(p.x, y), 0);
            int near = unpack16(row.r);
            if (near % 256 == NONE) continue;
            float dx = float(near % 256);
            float d2 = dx * dx + dy2;
            if (d2 < best) {
                best = d2;
                nearest = unpack16(row.g);
            }
        }
    }
    if (nearest < 0) discard;
    vec4 color = colorAt(nearest);
    float alpha = edgeAlpha(nearest, best, true) * color.a;
    if (alpha <= 0.0) discard;
    fragColor = vec4(color.rgb, alpha);
}
