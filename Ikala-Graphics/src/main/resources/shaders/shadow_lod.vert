#version 460
#extension GL_EXT_buffer_reference : require

// The shadow vertex shader for level of detail clusters: shadow.vert, reading the position from the
// vertex format of LodVertex the way scene_lod.vert does.

layout(location = 0) in ivec3 position; // grid steps from the world's origin

// Where the camera is for each LOD world, see LodCullMath
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

// The cluster each draw is, see LodCullMath: each draw's instance is its cluster's entry
struct Cluster {
    vec4 tight;
    vec4 bound;
    // The same for every cluster of a group
    vec4 parentBound;
    uvec4 info;
    // x: world slot + 1, y: cell slot
    uvec4 place;
    // z: level
    uvec4 unit;
};

layout(buffer_reference, std430, buffer_reference_align = 16) readonly buffer Clusters {
    Cluster clusters[];
};

// The cascade's matrix, then the world and cluster tables after the visible list address
// shadow.vert reads, unused here
layout(push_constant) uniform ShadowConstants {
    mat4 projViewMatrix;
    layout(offset = 72) Worlds worldTable;
    Clusters clusterTable;
};

// Grid steps per unit, see LodVertex
const float STEPS_PER_UNIT = 1024.0;

void main()
{
    World world = worldTable.worlds[clusterTable.clusters[gl_InstanceIndex].place.x - 1];
    vec3 render =
        vec3(position - world.cameraGrid.xyz) / STEPS_PER_UNIT - world.cameraFraction.xyz;
    gl_Position = projViewMatrix * vec4(render, 1.0);
}
