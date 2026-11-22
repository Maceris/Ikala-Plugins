#ifndef DEFORM_GLSL
#define DEFORM_GLSL

// The deformation field, see DeformMath and DeformationField: prints pressed into soft surfaces
// around the camera, a uint a texel, the window's texels addressed by world texel modulo the size.
// Keep in step with DeformMath. Includers enable GL_EXT_buffer_reference.

const int DEFORM_SIZE = 2048;
const float DEFORM_TEXELS_PER_METER = 32.0;
// A texel packs how deep it is pressed in its high 16 bits, in 0.1 mm biased so a rim is below the
// bias, and the top of the surface pressed in its low 16 bits, at 1/16 m; zero is untouched
const float DEFORM_DEPTH_UNIT = 1e-4;
const int DEFORM_DEPTH_BIAS = 8192;
const float DEFORM_HEIGHT_STEPS = 16.0;
// The column table: columns a side, by world column modulo this, and surfaces a column
const int DEFORM_TABLE_SIZE = 128;
const int DEFORM_SLOTS = 4;
// A surface's flags in the table
const uint DEFORM_PRESENT = 1u;
const uint DEFORM_PRESSABLE = 2u;
// How far above or below a surface a stamp may be and still press it, in meters
const float DEFORM_STAMP_REACH = 0.25;

layout(buffer_reference, std430, buffer_reference_align = 4) buffer DeformField {
    uint texels[];
};

// One surface in a column: the column, so stale slots from another column aren't read; its top and
// bottom in meters, how fast it fills back in, and its flags
struct DeformSurface {
    ivec2 column;
    float top;
    float bottom;
    float refill;
    uint flags;
    uint pad0;
    uint pad1;
};

layout(buffer_reference, std430, buffer_reference_align = 16) readonly buffer DeformTable {
    DeformSurface surfaces[];
};

// Where a world texel is in the field
int deformIndex(ivec2 texel) {
    ivec2 wrapped = texel & (DEFORM_SIZE - 1);
    return wrapped.y * DEFORM_SIZE + wrapped.x;
}

// Where a world column's slot is in the table
int deformSlot(ivec2 column, int slot) {
    ivec2 wrapped = column & (DEFORM_TABLE_SIZE - 1);
    return (wrapped.y * DEFORM_TABLE_SIZE + wrapped.x) * DEFORM_SLOTS + slot;
}

// A surface top's height as the field keys it, 16 bits of 1/16 m steps
uint deformHeightKey(float top) {
    return uint(int(round(top * DEFORM_HEIGHT_STEPS))) & 0xFFFFu;
}

// How deep a texel is pressed, in meters: zero untouched, negative on a rim
float deformDepth(uint texel) {
    return texel == 0u ? 0.0 : float(int(texel >> 16) - DEFORM_DEPTH_BIAS) * DEFORM_DEPTH_UNIT;
}

// Pack a press, never zero
uint deformPack(float depth, float top) {
    int units = clamp(int(round(depth / DEFORM_DEPTH_UNIT)) + DEFORM_DEPTH_BIAS, 1, 0xFFFF);
    return (uint(units) << 16) | deformHeightKey(top);
}

#endif
