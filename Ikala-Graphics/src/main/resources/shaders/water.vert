#version 460
#extension GL_GOOGLE_include_directive : require
#extension GL_EXT_buffer_reference : require

// A water tile's surface, see WaterRender: each vertex is a cell corner relative to its tile's
// corner, which the tile list places relative to the camera, raised and lowered by its wave
// climate's longer waves, which the grid of cells samples well enough, as much as its calm factor
// lets them: shallow water and the last cells before an edge are calm, and sides don't move, see
// WaterMesh.

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
layout(location = 2) in float inCalm;
// A side's outward normal along x and z, zero on the surface
layout(location = 3) in vec2 inSideNormal;

layout(location = 0) out vec3 outRenderPosition;
layout(location = 1) out vec4 outViewPosition;
layout(location = 2) flat out uint outTile;
layout(location = 3) out float outCalm;
layout(location = 4) out vec2 outSideNormal;

void main() {
    WaterTileData tile = tileList.waterTiles[inTile];
    vec3 render = tile.offset.xyz + inPosition;
    int climate = int(tile.offset.w);
    float waves = inCalm;
    if (climate >= 0 && waves > 0.0) {
        render.y += waves
            * waveHeight(climateList, profileList, climate, WAVE_VERTEX_LAYER, render.xz);
    }
    vec4 view = viewMatrix * vec4(render, 1.0);
    outRenderPosition = render;
    outViewPosition = view;
    outTile = inTile;
    outCalm = waves;
    outSideNormal = inSideNormal;
    gl_Position = projectionMatrix * view;
}
