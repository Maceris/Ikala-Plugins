#version 460

// The shadow vertex shader for level of detail clusters: shadow.vert, reading the position from the
// vertex format of LodVertex the way scene_lod.vert does.

layout(location = 0) in ivec3 position; // grid steps from the world's origin

// The cascade's matrix changes for every cascade, the camera for every LOD world. The visible list
// address shadow.vert reads sits between them, unused here.
layout(push_constant) uniform ShadowConstants {
    mat4 projViewMatrix;
    // xyz: the camera's grid step from the world's origin, rounded down
    layout(offset = 80) ivec4 cameraGrid;
    // xyz: the rest of the camera's position past that step, in units
    layout(offset = 96) vec4 cameraFraction;
};

// Grid steps per unit, see LodVertex
const float STEPS_PER_UNIT = 1024.0;

void main()
{
    vec3 render = vec3(position - cameraGrid.xyz) / STEPS_PER_UNIT - cameraFraction.xyz;
    gl_Position = projViewMatrix * vec4(render, 1.0);
}
