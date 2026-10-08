#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <myriad:highlight.glsl>

// A retained mesh drawn into the shapes mask. The colour modulator holds the first id of the mesh's palette (as an
// encoded outline colour's red and green bytes) and each vertex's red channel its palette index; the merge group is
// read from the highlight's data, as the mask stores it next to the id.
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    int base = int(ColorModulator.r * 255.0 + 0.5) | (int(ColorModulator.g * 255.0 + 0.5) << 8);
    int id = base + int(vertexColor.r * 255.0 + 0.5);
    int group = int(highlightData(id, 4).w + 0.5) >> 1;
    fragColor = vec4(float(id & 255), float((id >> 8) & 255), float(group), 255.0) / 255.0;
}
