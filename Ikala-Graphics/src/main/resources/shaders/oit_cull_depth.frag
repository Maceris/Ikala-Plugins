#version 460
#extension GL_EXT_buffer_reference : require
#extension GL_EXT_nonuniform_qualifier : enable
#extension GL_GOOGLE_include_directive : require

// Writes the depth the transparent stage tests against: the opaque scene's, or nearer where the
// voxel-based transparency volume says no light gets back from there, Drobot's zero transmittance
// depth (SIGGRAPH 2025). Past it, every surface would read under MIN_VISIBLE and discard itself,
// so the depth test drops it before it is shaded. Each pixel takes the furthest zero slice of the
// tiles its weight lookup blends between, see transmittanceInFront. VoxelOitMath.cullDepth has the
// same math in Java.

#include "oit_volume.glsl"

layout(location = 0) in vec2 outTextCoord;

// Each tile's zero slice, x fastest, or SLICES where light gets through them all
layout(buffer_reference, std430, buffer_reference_align = 4) readonly buffer ZeroSlices {
    uint zeroSlices[];
};

layout(push_constant) uniform Constants {
    ZeroSlices zero;
    // How many tiles across and down this frame's volume holds
    uvec2 tiles;
    // SLICES / ln(viewDistance / FIRST_DEPTH)
    float logScale;
    // The bindless slot of the opaque scene's depth
    int depthIndex;
    // The projection's m22 and m32, which turn a view space z into depth
    vec2 projectionZ;
};

layout(set = 0, binding = 0) uniform sampler2D bindlessTextures[];

uint zeroSliceAt(uvec2 tile) {
    return zero.zeroSlices[tile.x + tiles.x * tile.y];
}

void main() {
    float sceneDepth = texelFetch(bindlessTextures[nonuniformEXT(depthIndex)],
        ivec2(gl_FragCoord.xy), 0).x;
    // The texel centers either side of where the lookup reads, in tiles
    vec2 read = clamp(gl_FragCoord.xy / float(TILE_SIZE), vec2(0.5), vec2(tiles) - 0.5) - 0.5;
    uvec2 low = uvec2(floor(read));
    uvec2 high = min(low + 1u, tiles - 1u);
    uint zeroSlice = max(max(zeroSliceAt(low), zeroSliceAt(uvec2(high.x, low.y))),
        max(zeroSliceAt(uvec2(low.x, high.y)), zeroSliceAt(high)));
    float coordinate = float(zeroSlice) + 0.5 + SLICE_BIAS + CULL_MARGIN;
    if (zeroSlice >= SLICES || coordinate >= float(SLICES)) {
        gl_FragDepth = sceneDepth;
        return;
    }
    // A distance in front of the camera, so a view space z of minus it
    float distance = FIRST_DEPTH * exp(coordinate / logScale);
    float cullDepth = (projectionZ.y - projectionZ.x * distance) / distance;
    gl_FragDepth = min(sceneDepth, cullDepth);
}
