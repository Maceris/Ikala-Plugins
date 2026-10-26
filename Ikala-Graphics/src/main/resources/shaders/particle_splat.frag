#version 460
#extension GL_EXT_nonuniform_qualifier : enable
#extension GL_GOOGLE_include_directive : require
#extension GL_EXT_buffer_reference : require

// Adds a particle billboard's extinction into the voxel-based transparency volume, drawn at one
// pixel per tile, the same way oit_splat.frag does for translucent surfaces, so particles weight
// the surfaces behind them and are weighted by those in front.

#include "light_inputs.glsl"
#include "oit_volume.glsl"
#include "particle_fragment.glsl"

void main() {
    float shapeAlpha = particleShapeAlpha();
    if (shapeAlpha <= 0.0) {
        return;
    }
    float alpha = particleAlpha(shapeAlpha);
    if (alpha <= 0.0) {
        return;
    }
    uvec2 tile = uvec2(gl_FragCoord.xy);
    float center = sliceCoordinate(-outViewPosition.z, logScale) - 0.5;
    float lowerSlice = floor(center);
    float fraction = center - lowerSlice;
    uint total = uint(-log(1.0 - alpha) * FIXED_POINT_SCALE + 0.5);
    // Rounded once, so the two parts always add up to the whole
    uint upperPart = uint(float(total) * fraction + 0.5);
    uint column = tile.x + uint(tiles.x) * tile.y;
    uint sliceStride = uint(tiles.x) * uint(tiles.y);
    uint lower = uint(clamp(int(lowerSlice), 0, int(SLICES) - 1));
    uint upper = uint(clamp(int(lowerSlice) + 1, 0, int(SLICES) - 1));
    atomicAdd(splat.splatValues[column + sliceStride * lower], total - upperPart);
    atomicAdd(splat.splatValues[column + sliceStride * upper], upperPart);
}
