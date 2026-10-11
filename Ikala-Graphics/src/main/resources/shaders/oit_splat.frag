#version 460
#extension GL_EXT_nonuniform_qualifier : enable
#extension GL_EXT_buffer_reference : require
#extension GL_GOOGLE_include_directive : require

// Adds a translucent surface's extinction, -log(1 - opacity), into the voxel-based transparency
// volume, drawn at one pixel per tile. The extinction is split between the two slice centers either
// side of the surface's depth, in fixed point. VoxelOitMath.splat has the same math in Java.

#include "light_inputs.glsl"
#include "oit_volume.glsl"

layout(location = 3) in vec2 outTextCoord;
layout(location = 4) in vec4 outViewPosition;
layout(location = 6) flat in uint outMaterialIdx;

// Each tile's slices, x fastest, then y, then slice
layout(buffer_reference, std430, buffer_reference_align = 4) buffer Extinction {
    uint values[];
};

layout(push_constant) uniform Constants {
    Extinction extinction;
    // How many tiles across and down this frame's volume holds
    uvec2 tiles;
    // SLICES / ln(viewDistance / FIRST_DEPTH)
    float logScale;
};

// Opacity is kept below 1, so the extinction stays finite. Matches OitMath.MAX_OPACITY.
const float MAX_OPACITY = 0.999;

void main() {
    Material material = materials[outMaterialIdx];
    float alpha = material.baseColor.a;
    if (material.textureIndex > 0) {
        alpha = texture(bindlessTextures[nonuniformEXT(material.textureIndex)], outTextCoord).a;
    }
    alpha = min(alpha, MAX_OPACITY);
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
    uint column = tile.x + tiles.x * tile.y;
    uint sliceStride = tiles.x * tiles.y;
    uint lower = uint(clamp(int(lowerSlice), 0, int(SLICES) - 1));
    uint upper = uint(clamp(int(lowerSlice) + 1, 0, int(SLICES) - 1));
    atomicAdd(extinction.values[column + sliceStride * lower], total - upperPart);
    atomicAdd(extinction.values[column + sliceStride * upper], upperPart);
}
