#version 150

// Dual-Kawase downsample.
in vec2 vUv;
uniform sampler2D uTex;
uniform vec2 uHalfTexel;
uniform float uOffset;
out vec4 fragColor;

void main() {
    vec2 o = uHalfTexel * uOffset;
    vec4 sum = texture(uTex, vUv) * 4.0;
    sum += texture(uTex, vUv - o);
    sum += texture(uTex, vUv + o);
    sum += texture(uTex, vUv + vec2(o.x, -o.y));
    sum += texture(uTex, vUv - vec2(o.x, -o.y));
    fragColor = vec4((sum / 8.0).rgb, 1.0);
}
