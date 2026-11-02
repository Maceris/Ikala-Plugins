#ifndef SKINNING_GLSL
#define SKINNING_GLSL

// Skinned meshes, see SkinnedRenderer: each vertex follows up to four bones of its instance's
// palette, and is hidden when its instance hides its mask region. SkinnedPacker writes what these
// read. Shaders including this need GL_EXT_buffer_reference.

// How many tint colors an instance has, see Skins.TINT_SLOTS
const uint TINT_SLOTS = 8u;

// One skinned instance: its model matrix relative to the camera, where its palette starts,
// counted in matrices from the start of the same buffer, which of its 64 regions are hidden, its
// tint colors, packed sRGB with red in the low byte, and its decal (see SkinnedDecal): the matrix
// from rest space into its box, and its art's bindless index (0 for none), its two tint slots, a
// byte each, 255 for none, and their fallback colors
struct SkinnedInstance {
    mat4 model;
    uint paletteFirst;
    uint hiddenLow;
    uint hiddenHigh;
    uint padding;
    uint tint[TINT_SLOTS];
    mat4 decalBox;
    uvec4 decal;
};

layout(buffer_reference, std430, buffer_reference_align = 16) readonly buffer SkinnedInstances {
    SkinnedInstance instances[];
};

// The same buffer read as matrices, for the palettes after the instances
layout(buffer_reference, std430, buffer_reference_align = 16) readonly buffer SkinnedMatrices {
    mat4 matrices[];
};

// Two uvec4s per drawn part, see SkinnedPacker: its instance, its material, which tint slot each
// tint mask channel takes, a byte each from red up, 255 for none, and its tint mask's bindless
// index, 0 for none; then the color each mask channel takes where its slot is unset. A draw's
// first instance is its record's index.
layout(buffer_reference, std430, buffer_reference_align = 16) readonly buffer SkinnedDraws {
    uvec4 draws[];
};

// Where a draw's record starts in SkinnedDraws; its fallback colors follow
uint drawRecord(uint index) {
    return 2u * index;
}

// The bit of a draw record's instance word marking parts that take decals, see SkinnedPacker
const uint DECAL_BIT = 0x80000000u;

// A draw record's instance, without its decal bit
uint drawInstance(uvec4 draw) {
    return draw.x & ~DECAL_BIT;
}

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

// Whether a slot's tint is unset (no alpha, see SkinnedRegistry.UNSET), so parts take their own
bool tintUnset(uint tint) {
    return (tint >> 24u) == 0u;
}

// The tint color each mask channel takes, from the draw's packed slots, or the part's fallback
// where the slot is unset
uvec4 tintColors(SkinnedInstance instance, uint slots, uvec4 fallbacks) {
    uvec4 colors;
    for (int c = 0; c < 4; ++c) {
        uint slot = (slots >> (8u * uint(c))) & 0xFFu;
        if (slot == NO_TINT_SLOT) {
            colors[c] = WHITE;
        } else {
            uint tint = instance.tint[slot];
            colors[c] = tintUnset(tint) ? fallbacks[c] : tint;
        }
    }
    return colors;
}

// A tint slot's color, or the fallback where the slot is none or unset
uint slotColor(SkinnedInstance instance, uint slot, uint fallback) {
    if (slot == NO_TINT_SLOT) {
        return fallback;
    }
    uint tint = instance.tint[slot];
    return tintUnset(tint) ? fallback : tint;
}

// The decal's art index and two colors for a part, art 0 for none
uvec4 decalOf(SkinnedInstance instance, uvec4 draw) {
    if ((draw.x & DECAL_BIT) == 0u || instance.decal.x == 0u) {
        return uvec4(0u);
    }
    uint slots = instance.decal.y;
    return uvec4(instance.decal.x,
        slotColor(instance, slots & 0xFFu, instance.decal.z),
        slotColor(instance, (slots >> 8u) & 0xFFu, instance.decal.w), 0u);
}

// Where a rest position lies in the decal's box, and how much its rest normal faces out of it
vec4 decalCoord(SkinnedInstance instance, vec3 restPosition, vec3 restNormal) {
    vec3 box = (instance.decalBox * vec4(restPosition, 1.0)).xyz;
    // The box's z grows along this direction in rest space
    vec3 forward = normalize(vec3(instance.decalBox[0][2], instance.decalBox[1][2],
        instance.decalBox[2][2]));
    return vec4(box, dot(normalize(restNormal), forward));
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
