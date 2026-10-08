#version 330

// The main highlight composite: everything but the insides of addons' own fills (see highlight_fill.glsl).
#moj_import <myriad:highlight_fill.glsl>

vec4 customFill(HighlightFill f) {
    return vec4(0.0);
}
