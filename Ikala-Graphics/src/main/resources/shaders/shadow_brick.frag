#version 460
#extension GL_EXT_buffer_reference : require
#extension GL_GOOGLE_include_directive : require

// Volume bricks in a shadow map: from where the map's ray through this fragment enters the brick's
// box, the brick is walked a voxel at a time, as volume_depth.frag walks it from the camera, to the
// first solid voxel, whose depth is written a little past its face so lit faces don't shadow
// themselves. A solid voxel's faces are cut out where its blocks' texture is, as leaves are, so a
// face the walk crosses stops the light with the chance it is covered, picked per texel: the light
// stage's filter averages neighboring texels into partial shadow, about as much as the leaves'
// holes let through. Blocks drop the faces they share, so only where the walk enters and leaves a
// run of solid voxels is a face crossed.

#define VOLUME_SET 2
#include "volume.glsl"

layout(location = 0) in vec3 outRender;
layout(location = 1) flat in vec3 outCorner;
layout(location = 2) flat in uint outSlot;
layout(location = 3) flat in vec3 outDirection;
layout(location = 4) flat in float outDepthStep;

// How far past a solid voxel's face its shadow starts, in meters: half a voxel
const float SHADOW_OFFSET = 0.5;

// A well spread hash (PCG)
uint pcg(uint v)
{
    uint state = v * 747796405u + 2891336453u;
    uint word = ((state >> ((state >> 28u) + 4u)) ^ state) * 277803737u;
    return (word >> 22u) ^ word;
}

void main()
{
    // No component exactly 0, which would make 0 times infinity where the ray lies in a face
    vec3 direction = mix(outDirection, vec3(1e-20), equal(outDirection, vec3(0.0)));
    // The walk's rays start at the origin, so the origin moves to this fragment's point
    vec3 origin = outRender;
    vec3 corner = outCorner - origin;
    vec2 span = volumeRayBox(direction, corner, corner + float(VOLUME_BRICK));
    float start = max(span.x, 0.0);
    float end = span.y;
    if (!(start < end)) {
        // Missed, or not a number
        discard;
    }
    VolumeWalk walk = volumeWalkStart(direction, corner, start);
    // The cells holding something solid
    uvec2 solidCells = volumeCellMasks[outSlot].xy;
    uint texel = pcg(uint(gl_FragCoord.x) ^ (uint(gl_FragCoord.y) << 16));
    float t = start;
    bool found = false;
    // Whether the walk is in a run of solid voxels, and the last one's coverage
    bool inside = false;
    float insideCoverage = 0.0;
    int axis;
    for (int i = 0; i < VOLUME_MAX_VOXELS; ++i) {
        bool cellSolid = volumeCellSet(solidCells, walk.voxel / VOLUME_CELL);
        float stored = cellSolid ? volumeStored(outSlot, walk.voxel).a : 0.0;
        bool solid = cellSolid && volumeIsSolid(stored);
        // Blocks drop the faces they share, so only a run's first and last faces are crossed
        if (solid != inside) {
            float coverage = solid ? volumeCoverage(stored) : insideCoverage;
            uint voxel = uint(walk.voxel.x + VOLUME_BRICK * (walk.voxel.y + VOLUME_BRICK * walk.voxel.z));
            uint face = outSlot * uint(2 * VOLUME_BRICK * VOLUME_BRICK * VOLUME_BRICK) + 2u * voxel
                + (solid ? 0u : 1u);
            if (float(pcg(texel ^ pcg(face)) >> 8) / 16777216.0 < coverage) {
                found = true;
                break;
            }
            inside = solid;
        }
        if (solid) {
            insideCoverage = volumeCoverage(stored);
        }
        if (!cellSolid) {
            t = volumeWalkSkipCell(walk, direction, corner, axis);
            if (t >= end || volumeWalkOutside(walk)) {
                break;
            }
            continue;
        }
        float leave = min(volumeWalkNext(walk), end);
        if (leave >= end) {
            break;
        }
        t = leave;
        volumeWalkStep(walk);
        if (volumeWalkOutside(walk)) {
            break;
        }
    }
    if (!found) {
        discard;
    }
    // The walk's origin is this fragment's point
    float depth = gl_FragCoord.z + outDepthStep * (t + SHADOW_OFFSET);
    if (!(depth >= 0.0 && depth <= 1.0)) {
        discard;
    }
    gl_FragDepth = depth;
}
