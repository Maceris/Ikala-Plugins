#ifndef OIT_VOLUME_GLSL
#define OIT_VOLUME_GLSL

// The voxel-based transparency volume: TILE_SIZE pixel tiles across the screen, each split into
// SLICES slices by depth, spaced exponentially from FIRST_DEPTH out to the view distance.
// VoxelOitMath has it in Java with tests; keep them in step.
//
// Define OIT_VOLUME_SET before including this to read the integrated transmittance.

const uint TILE_SIZE = 8;
const uint SLICES = 128;
const float FIRST_DEPTH = 0.5;
// How many units of the extinction buffer's integers make one unit of extinction
const float FIXED_POINT_SCALE = 65536.0;
// How many slices nearer than a surface its weight is read, so it doesn't dim itself
const float SLICE_BIAS = 2.0;

// Where a depth (meters in front of the camera) falls among the slices, from 0 to SLICES.
// logScale is SLICES / ln(viewDistance / FIRST_DEPTH).
float sliceCoordinate(float depth, float logScale) {
    return min(log(max(depth, FIRST_DEPTH) / FIRST_DEPTH) * logScale, float(SLICES));
}

#ifdef OIT_VOLUME_SET
// How much light gets through up to the far end of each slice of each tile
layout(set = OIT_VOLUME_SET, binding = 0) uniform sampler3D transmittanceVolume;

// How much light gets through to just in front of a surface, blended between the nearest tiles and
// slices, and towards nothing in the way before the first slice. tiles is how many tiles across
// and down hold this frame's volume, which can be fewer than the image has.
float transmittanceInFront(vec2 fragCoord, float depth, float logScale, vec2 tiles) {
    vec3 size = vec3(textureSize(transmittanceVolume, 0));
    // In texels, whose centers are at + 0.5, kept off the tiles past this frame's edge
    vec2 tile = clamp(fragCoord / float(TILE_SIZE), vec2(0.5), tiles - 0.5);
    float slice = sliceCoordinate(depth, logScale) - SLICE_BIAS;
    if (slice <= -0.5) {
        return 1.0;
    }
    float first = max(slice, 0.5);
    float transmittance = texture(transmittanceVolume, vec3(tile, first) / size).r;
    // Before the first texel center, blend from nothing in the way (1) at -0.5
    return slice < 0.5 ? mix(1.0, transmittance, slice + 0.5) : transmittance;
}
#endif

#endif
