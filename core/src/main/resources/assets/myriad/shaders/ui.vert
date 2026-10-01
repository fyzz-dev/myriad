#version 150

// One quad of the batched UI renderer. Everything is in framebuffer pixels.
in vec2 aPos;
in vec2 aLocal;   // pixel position inside the shape's box (0..size)
in vec2 aSize;    // shape box size in pixels
in vec4 aRadii;   // corner radii: top-left, top-right, bottom-right, bottom-left
in vec4 aColor1;
in vec4 aColor2;
in vec4 aParams;  // x = shape type, y = thickness/softness, z = gradient angle (radians), w = unused
in vec2 aUv;

uniform mat4 uProj;

out vec2 vLocal;
out vec2 vUv;
flat out vec2 vSize;
flat out vec4 vRadii;
flat out vec4 vColor1;
flat out vec4 vColor2;
flat out vec4 vParams;

void main() {
    gl_Position = uProj * vec4(aPos, 0.0, 1.0);
    vLocal = aLocal;
    vUv = aUv;
    vSize = aSize;
    vRadii = aRadii;
    vColor1 = aColor1;
    vColor2 = aColor2;
    vParams = aParams;
}
