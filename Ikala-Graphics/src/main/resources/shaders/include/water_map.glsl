#ifndef WATER_MAP_GLSL
#define WATER_MAP_GLSL

// The water around the camera, see WaterVolume. Includers enable GL_EXT_buffer_reference.

// One water body's absorption, scattering and backscattering at red, green and blue, per meter
struct WaterBodyOptics {
    vec4 absorption;
    vec4 scattering;
    vec4 backscattering;
};

// The map: its corner in world cells, its size in cells and how many bodies; its corner relative
// to the camera and the camera's height; the bodies; then each cell's surface and bottom heights,
// its body, and 1 if it holds water
layout(buffer_reference, std430, buffer_reference_align = 16) readonly buffer WaterMap {
    ivec2 corner;
    int size;
    int bodyCount;
    vec4 cornerCamera;
    WaterBodyOptics bodies[16];
    vec4 cells[];
};

#endif
