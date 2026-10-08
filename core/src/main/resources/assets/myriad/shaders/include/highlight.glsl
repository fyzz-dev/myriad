// Shared by the highlight passes (see HighlightRenderer). Highlights is a buffer of vec4s: texel 0 is
// (radius, GUI scale, count, boundary radius), then HIGHLIGHT_TEXELS per highlight:
//   0 top colour, 1 bottom colour (RGBA, alpha already faded)
//   2 gradient rectangle in framebuffer pixels (x0, y0, x1, y1; y1 is the top)
//   3 (outline width, glow reach, glow strength, fill opacity), lengths in framebuffer pixels
//   4 (fill: 0 none, 1 solid, 2 dots; dot spacing; dot radius; 1 if hidden parts are dropped)
uniform samplerBuffer Highlights;

const int HIGHLIGHT_TEXELS = 5;

// A mask texel's RGB: highlight id (low byte in red, high byte in green) and merge group (blue). Highlights in the
// same group connect; where groups meet, the later highlight (higher id) outlines itself across the boundary.
int highlightId(vec3 rgb) {
    ivec2 b = ivec2(rgb.rg * 255.0 + 0.5);
    return b.x | (b.y << 8);
}

int highlightGroup(vec3 rgb) {
    return int(rgb.b * 255.0 + 0.5);
}

// The spread texture is 16 bits a channel: (distance + 256 * group, id) for the nearest mask pixel in the row, then
// the same for the nearest pixel of another group (mask pixels only). Distance 255 means none.
const int NONE = 255;

float pack16(int v) {
    return float(v) / 65535.0;
}

int unpack16(float f) {
    return int(f * 65535.0 + 0.5);
}

vec4 highlightData(int id, int texel) {
    return texelFetch(Highlights, 1 + id * HIGHLIGHT_TEXELS + texel);
}

int highlightRadius() {
    return int(texelFetch(Highlights, 0).x + 0.5);
}

int highlightBoundaryRadius() {
    return int(texelFetch(Highlights, 0).w + 0.5);
}
