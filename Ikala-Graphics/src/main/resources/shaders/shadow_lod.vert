#version 460
#extension GL_EXT_buffer_reference : require

// The shadow vertex shader for level of detail clusters: shadow.vert, reading the position from the
// vertex format of LodVertex the way scene_lod.vert does.

layout(location = 0) in ivec3 position; // grid steps from the world's origin

// Where the camera is for each LOD world, see LodCullMath; each draw's instance is its world
struct World {
    vec4 renderOffset;
    vec4 eye;
    // xyz: the camera's grid step from the world's origin, rounded down
    ivec4 cameraGrid;
    // xyz: the rest of the camera's position past that step, in units
    vec4 cameraFraction;
    uvec4 info;
};

layout(buffer_reference, std430, buffer_reference_align = 16) readonly buffer Worlds {
    World worlds[];
};

// The cascade's matrix, then the world table after the visible list address shadow.vert reads,
// unused here
layout(push_constant) uniform ShadowConstants {
    mat4 projViewMatrix;
    layout(offset = 72) Worlds worldTable;
};

// Grid steps per unit, see LodVertex
const float STEPS_PER_UNIT = 1024.0;

void main()
{
    World world = worldTable.worlds[gl_InstanceIndex];
    vec3 render =
        vec3(position - world.cameraGrid.xyz) / STEPS_PER_UNIT - world.cameraFraction.xyz;
    gl_Position = projViewMatrix * vec4(render, 1.0);
}
