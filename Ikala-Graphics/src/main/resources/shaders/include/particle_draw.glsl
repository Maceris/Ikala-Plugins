#ifndef PARTICLE_DRAW_GLSL
#define PARTICLE_DRAW_GLSL

// What the particle billboards' shaders share, see ParticleDraw: their push constants, which the
// transparency splat and the transparent color pass both fill, and what passes from vertex to
// fragment. Includers enable GL_EXT_buffer_reference.

#include "particles.glsl"

// Each tile's slices of the transparency volume, as oit_splat.frag adds into them
layout(buffer_reference, std430, buffer_reference_align = 4) buffer SplatExtinction {
    uint splatValues[];
};

layout(push_constant) uniform Constants {
    ParticleBuffer pool;
    // The alive list drawn, one billboard per entry
    IndexList drawn;
    // The pool's anchor in render space: the anchor minus the camera
    vec3 anchor;
    // How far in front of the opaque scene a particle starts fading, per meter of its size
    float softScale;
    // The size of the area drawn, in pixels
    vec2 screenSize;
    // How many tiles across and down this frame's transparency volume holds
    vec2 tiles;
    // SLICES / ln(viewDistance / FIRST_DEPTH)
    float logScale;
    // Nonzero to weight by the transparency volume, zero for the depth weights
    int voxelWeights;
    // Pixels per fragment along each side: 1 for the color pass, a tile's for the splat
    float pixelScale;
    uint padding;
    // The transparency volume's extinction, for the splat
    SplatExtinction splat;
    // Each particle's light, see particle_light.comp
    RadianceList radianceList;
};

// Where the corner is across the billboard, -1 to 1 each way
#define PARTICLE_VARYING_CORNER 0
// Its color and opacity, linear
#define PARTICLE_VARYING_COLOR 1
// The billboard's point in view space
#define PARTICLE_VARYING_VIEW 2
// Its size, then how much of its streak is motion, 0 for an unstretched billboard: history can't
// follow it there. One varying, so the transparency splat, which reads only the size, still takes
// in everything the vertex shader gives.
#define PARTICLE_VARYING_SIZE 3

#endif
