#version 150

// Dual-Kawase upsample.
in vec2 vUv;
uniform sampler2D uTex;
uniform vec2 uHalfTexel;
uniform float uOffset;
out vec4 fragColor;

void main() {
    vec2 o = uHalfTexel * uOffset;
    vec4 sum = texture(uTex, vUv + vec2(-o.x * 2.0, 0.0));
    sum += texture(uTex, vUv + vec2(-o.x, o.y)) * 2.0;
    sum += texture(uTex, vUv + vec2(0.0, o.y * 2.0));
    sum += texture(uTex, vUv + vec2(o.x, o.y)) * 2.0;
    sum += texture(uTex, vUv + vec2(o.x * 2.0, 0.0));
    sum += texture(uTex, vUv + vec2(o.x, -o.y)) * 2.0;
    sum += texture(uTex, vUv + vec2(0.0, -o.y * 2.0));
    sum += texture(uTex, vUv + vec2(-o.x, -o.y)) * 2.0;
    fragColor = vec4((sum / 12.0).rgb, 1.0);
}
