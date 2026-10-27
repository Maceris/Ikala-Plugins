#version 460
#extension GL_GOOGLE_include_directive : require
#extension GL_EXT_buffer_reference : require

// A water tile's surface, see WaterRender: each vertex is a cell corner relative to its tile's
// corner, which the tile list places relative to the camera, raised and lowered by its wave
// climate's longer waves, which the grid of cells samples well enough, as much as its calm factor
// lets them: shallow water and the last cells before an edge are calm, and sides don't move, see
// WaterMesh.

#include "water.glsl"
#include "water_constants.glsl"

// The scene stage's matrices, as the transparent stages bind its set
layout(set = 2, binding = 0) uniform Uniforms {
    mat4 projectionMatrix;
    mat4 viewMatrix;
};


layout(location = 0) in vec3 inPosition;
layout(location = 1) in uint inTile;
layout(location = 2) in float inCalm;
// A side's outward normal along x and z, zero on the surface
layout(location = 3) in vec2 inSideNormal;
// How fast the water flows here, x and z, and how deep it is
layout(location = 4) in vec2 inFlow;
layout(location = 5) in float inDepth;

layout(location = 0) out vec3 outRenderPosition;
layout(location = 1) out vec4 outViewPosition;
layout(location = 2) flat out uint outTile;
layout(location = 3) out float outCalm;
layout(location = 4) out vec2 outSideNormal;
// Each direction's amplitudes here, the wind's waves' and the ripples', two directions a vector
layout(location = 5) out vec4 outAmplitudes[WAVE_AMPLITUDE_VECTORS];
layout(location = 13) out vec2 outFlow;
layout(location = 14) out float outDepth;

void main() {
    WaterTileData tile = tileList.waterTiles[inTile];
    vec3 render = tile.offset.xyz + inPosition;
    int climate = int(tile.offset.w);
    float waves = inCalm;
    vec2 amplitudes[WAVE_DIRECTIONS];
    for (int b = 0; b < WAVE_DIRECTIONS; ++b) {
        amplitudes[b] = vec2(0.0);
    }
    if ((climate >= 0 || gridOn != 0) && waves > 0.0) {
        WaveField field;
        field.climates = climateList;
        field.profiles = profileList;
        field.grid = gridList;
        field.climate = climate;
        field.ripples = rippleClimate;
        field.gridOn = gridOn != 0;
        field.at = waveGridAt(render.xz, gridCorner, gridCornerCell);
        waveAmplitudesAll(field, amplitudes);
        if (climate >= 0) {
            // Between the two nearest depths' profiles, and the flow's two phases
            float level = waveDepthLevel(inDepth);
            int lower = int(floor(level));
            int upper = min(lower + 1, WAVE_DEPTHS - 1);
            float depthBlend = level - float(lower);
            float phaseBlend = dot(inFlow, inFlow) > 0.0 ? waveFlowWeight(waveTime) : 0.0;
            float height = 0.0;
            for (int phase = 0; phase < 2; ++phase) {
                float phaseWeight = phase == 0 ? 1.0 - phaseBlend : phaseBlend;
                if (phaseWeight <= 0.0) {
                    continue;
                }
                field.shift = waveFlowShift(inFlow, waveTime, phase);
                field.level = lower;
                float atLevel = waveHeight(field, amplitudes, WAVE_VERTEX_LAYER, render.xz);
                if (depthBlend > 0.0) {
                    field.level = upper;
                    atLevel = mix(atLevel,
                        waveHeight(field, amplitudes, WAVE_VERTEX_LAYER, render.xz), depthBlend);
                }
                height += phaseWeight * atLevel;
            }
            render.y += waves * height;
        }
    }
    for (int i = 0; i < WAVE_AMPLITUDE_VECTORS; ++i) {
        outAmplitudes[i] = vec4(amplitudes[2 * i], amplitudes[2 * i + 1]);
    }
    vec4 view = viewMatrix * vec4(render, 1.0);
    outRenderPosition = render;
    outViewPosition = view;
    outTile = inTile;
    outCalm = waves;
    outSideNormal = inSideNormal;
    outFlow = inFlow;
    outDepth = inDepth;
    gl_Position = projectionMatrix * view;
}
