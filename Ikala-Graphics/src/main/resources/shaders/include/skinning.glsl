#ifndef SKINNING_GLSL
#define SKINNING_GLSL

// Skinned meshes, see SkinnedRenderer: each vertex follows up to four bones of its instance's
// palette, and is hidden when its instance hides its mask region. SkinnedPacker writes what these
// read. Shaders including this need GL_EXT_buffer_reference.

// How many tint colors an instance has, see Skins.TINT_SLOTS
const uint TINT_SLOTS = 8u;

// One skinned instance: its model matrix relative to the camera, where its palette starts,
// counted in matrices from the start of the same buffer, which of its 64 regions are hidden, and
// its tint colors, packed sRGB with red in the low byte
struct SkinnedInstance {
    mat4 model;
    uint paletteFirst;
    uint hiddenLow;
    uint hiddenHigh;
    uint padding;
    uint tint[TINT_SLOTS];
};

layout(buffer_reference, std430, buffer_reference_align = 16) readonly buffer SkinnedInstances {
    SkinnedInstance instances[];
};

// The same buffer read as matrices, for the palettes after the instances
layout(buffer_reference, std430, buffer_reference_align = 16) readonly buffer SkinnedMatrices {
    mat4 matrices[];
};

// One record per drawn part: its instance, its material, which tint slot each tint mask channel
// takes, a byte each from red up, 255 for none, and its tint mask's bindless index, 0 for none. A
// draw's first instance is its record's index.
layout(buffer_reference, std430, buffer_reference_align = 16) readonly buffer SkinnedDraws {
    uvec4 draws[];
};

// The vertex's bones, their weights, adding up to 1, and its mask region, see SkinnedVertex
layout(location = 5) in uvec4 joints;
layout(location = 6) in vec4 weights;
layout(location = 7) in uint region;

// Where a hidden vertex goes: outside the clip volume, so the triangles of a hidden region, whose
// vertices all land here, are dropped
const vec4 HIDDEN_POSITION = vec4(2.0, 2.0, 2.0, 1.0);

// The region of vertices that are never hidden
const uint NO_REGION = 255u;

// Whether the instance hides this vertex's region
bool regionHidden(SkinnedInstance instance) {
    if (region == NO_REGION) {
        return false;
    }
    uint bits = region < 32u ? instance.hiddenLow : instance.hiddenHigh;
    return ((bits >> (region & 31u)) & 1u) != 0u;
}

// A packed tint color that changes nothing
const uint WHITE = 0xFFFFFFFFu;

// The slot number meaning a mask channel takes no tint
const uint NO_TINT_SLOT = 255u;

// The tint color each mask channel takes, from the draw's packed slots
uvec4 tintColors(SkinnedInstance instance, uint slots) {
    uvec4 colors;
    for (int c = 0; c < 4; ++c) {
        uint slot = (slots >> (8u * uint(c))) & 0xFFu;
        colors[c] = slot == NO_TINT_SLOT ? WHITE : instance.tint[slot];
    }
    return colors;
}

// The vertex's blended skinning matrix, from model space at rest to model space posed
mat4 skinMatrix(SkinnedInstances data, SkinnedInstance instance) {
    SkinnedMatrices palette = SkinnedMatrices(data);
    uint first = instance.paletteFirst;
    return palette.matrices[first + joints.x] * weights.x
        + palette.matrices[first + joints.y] * weights.y
        + palette.matrices[first + joints.z] * weights.z
        + palette.matrices[first + joints.w] * weights.w;
}

#endif
