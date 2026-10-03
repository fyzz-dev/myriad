#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// Retained world meshes (WorldMesh): vertices are relative to the mesh's origin, and ModelOffset is that origin
// relative to the camera, so positions stay small and precise anywhere in the world.

in vec3 Position;
in vec4 Color;

out vec4 vertexColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position + ModelOffset, 1.0);

    vertexColor = Color;
}
