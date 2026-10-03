#version 330

#moj_import <minecraft:globals.glsl>

// One quad of the batched UI renderer. Everything is in framebuffer pixels, y down.
in vec3 Position;
in vec2 Local;    // pixel position inside the shape's box (0..size)
in vec2 Size;     // shape box size in pixels
in vec4 Radii;    // corner radii: top-left, top-right, bottom-right, bottom-left
in vec4 Color1;
in vec4 Color2;
in vec4 Params;   // x = shape type, y = thickness/softness, z = gradient angle (radians), w = unused
in vec2 TexCoord;
in vec4 Clip;     // visible area: x0, y0, x1, y1

out vec2 vLocal;
out vec2 vUv;
out vec2 vPixel;
out vec2 vScreenUv;
flat out vec2 vSize;
flat out vec4 vRadii;
flat out vec4 vColor1;
flat out vec4 vColor2;
flat out vec4 vParams;
flat out vec4 vClip;

void main() {
    gl_Position = vec4(Position.x / ScreenSize.x * 2.0 - 1.0, 1.0 - Position.y / ScreenSize.y * 2.0, 0.0, 1.0);
    vLocal = Local;
    vUv = TexCoord;
    vPixel = Position.xy;
    vScreenUv = vec2(Position.x / ScreenSize.x, 1.0 - Position.y / ScreenSize.y);
    vSize = Size;
    vRadii = Radii;
    vColor1 = Color1;
    vColor2 = Color2;
    vParams = Params;
    vClip = Clip;
}
