#version 460
#extension GL_GOOGLE_include_directive : require
#extension GL_EXT_buffer_reference : require

// A water tile's surface, see WaterRender: each vertex is a cell corner relative to its tile's
// corner, which the tile list places relative to the camera.

#include "water.glsl"

// The scene stage's matrices, as the transparent stages bind its set
layout(set = 2, binding = 0) uniform Uniforms {
    mat4 projectionMatrix;
    mat4 viewMatrix;
};

layout(push_constant) uniform Constants {
    WaterTileList tileList;
};

layout(location = 0) in vec3 inPosition;
layout(location = 1) in uint inTile;

layout(location = 0) out vec3 outRenderPosition;
layout(location = 1) out vec4 outViewPosition;
layout(location = 2) flat out uint outTile;

void main() {
    vec3 render = tileList.waterTiles[inTile].offset.xyz + inPosition;
    vec4 view = viewMatrix * vec4(render, 1.0);
    outRenderPosition = render;
    outViewPosition = view;
    outTile = inTile;
    gl_Position = projectionMatrix * view;
}
