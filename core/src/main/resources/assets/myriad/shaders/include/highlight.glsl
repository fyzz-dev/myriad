// Shared by the highlight passes (see HighlightRenderer). Highlights is a buffer of vec4s: texel 0 is
// (radius, GUI scale, count, 0), then HIGHLIGHT_TEXELS per highlight:
//   0 top colour, 1 bottom colour (RGBA, alpha already faded)
//   2 gradient rectangle in framebuffer pixels (x0, y0, x1, y1; y1 is the top)
//   3 (outline width, glow reach, glow strength, fill opacity), lengths in framebuffer pixels
//   4 (fill: 0 none, 1 solid, 2 dots; dot spacing; dot radius; 1 if hidden parts are dropped)
uniform samplerBuffer Highlights;

const int HIGHLIGHT_TEXELS = 5;

// The highlight id a mask texel's RGB holds (low byte in red).
int highlightId(vec3 rgb) {
    ivec3 b = ivec3(rgb * 255.0 + 0.5);
    return b.r | (b.g << 8) | (b.b << 16);
}

vec4 highlightData(int id, int texel) {
    return texelFetch(Highlights, 1 + id * HIGHLIGHT_TEXELS + texel);
}

int highlightRadius() {
    return int(texelFetch(Highlights, 0).x + 0.5);
}
