#version 330

#moj_import <myriad:highlight.glsl>

// First half of the distance search: the nearest mask pixel in this row, within the reach, searched outwards so it
// stops at the first hit. Out: R = its distance / 255 (1 when none), GBA = its highlight id.
uniform sampler2D InSampler;

out vec4 fragColor;

void main() {
    ivec2 p = ivec2(gl_FragCoord.xy);
    vec4 here = texelFetch(InSampler, p, 0);
    if (here.a > 0.0) {
        fragColor = vec4(0.0, here.rgb);
        return;
    }
    int radius = highlightRadius();
    int width = textureSize(InSampler, 0).x;
    for (int d = 1; d <= radius; d++) {
        if (p.x - d >= 0) {
            vec4 left = texelFetch(InSampler, ivec2(p.x - d, p.y), 0);
            if (left.a > 0.0) {
                fragColor = vec4(float(d) / 255.0, left.rgb);
                return;
            }
        }
        if (p.x + d < width) {
            vec4 right = texelFetch(InSampler, ivec2(p.x + d, p.y), 0);
            if (right.a > 0.0) {
                fragColor = vec4(float(d) / 255.0, right.rgb);
                return;
            }
        }
    }
    fragColor = vec4(1.0, 0.0, 0.0, 0.0);
}
