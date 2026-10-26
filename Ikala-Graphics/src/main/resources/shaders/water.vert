#version 460
#extension GL_GOOGLE_include_directive : require
#extension GL_EXT_buffer_reference : require

// A water tile's surface, see WaterRender: each vertex is a cell corner relative to its tile's
// corner, which the tile list places relative to the camera, raised and lowered by its wave
// climate's longer waves, which the grid of cells samples well enough. Shallow water calms them.

#include "water.glsl"

// The scene stage's matrices, as the transparent stages bind its set
layout(set = 2, binding = 0) uniform Uniforms {
    mat4 projectionMatrix;
    mat4 viewMatrix;
};

layout(push_constant) uniform Constants {
    WaterTileList tileList;
    WaveClimateList climateList;
    WaveProfileList profileList;
};

layout(location = 0) in vec3 inPosition;
layout(location = 1) in uint inTile;
layout(location = 2) in float inDepth;

layout(location = 0) out vec3 outRenderPosition;
layout(location = 1) out vec4 outViewPosition;
layout(location = 2) flat out uint outTile;
layout(location = 3) out float outCalm;

void main() {
    WaterTileData tile = tileList.waterTiles[inTile];
    vec3 render = tile.offset.xyz + inPosition;
    int climate = int(tile.offset.w);
    // How much of the waves the depth here allows, 0 to 1
    float waves = clamp(inDepth / WAVE_CALM_DEPTH, 0.0, 1.0);
    if (climate >= 0 && waves > 0.0) {
        render.y += waves
            * waveHeight(climateList, profileList, climate, WAVE_VERTEX_LAYER, render.xz);
    }
    vec4 view = viewMatrix * vec4(render, 1.0);
    outRenderPosition = render;
    outViewPosition = view;
    outTile = inTile;
    outCalm = waves;
    gl_Position = projectionMatrix * view;
}
