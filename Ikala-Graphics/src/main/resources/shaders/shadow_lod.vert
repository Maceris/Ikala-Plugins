#version 460
#extension GL_EXT_buffer_reference : require

// The shadow vertex shader for level of detail clusters: shadow.vert, reading the position from the
// vertex format of LodVertex the way scene_lod.vert does, instances of models included.

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
    // An instance's turn, a quaternion
    vec4 rotation;
    // x: 1 + the model's first cluster entry (0 for a world), w: the instance's material remaps
    uvec4 model;
    uvec4 remapFrom;
    uvec4 remapTo;
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

// An instanced draw's instance, see LodCullMath.drawId
const uint INSTANCED = 0x80000000u;
const uint INSTANCE_SHIFT = 16;
const uint CLUSTER_MASK = 0xFFFFu;

// Turn a vector by a quaternion
vec3 rotate(vec4 q, vec3 v)
{
    vec3 t = 2.0 * cross(q.xyz, v);
    return v + q.w * t + cross(q.xyz, t);
}

void main()
{
    uint id = uint(gl_InstanceIndex);
    vec3 render;
    if ((id & INSTANCED) != 0u) {
        World world = worldTable.worlds[(id & ~INSTANCED) >> INSTANCE_SHIFT];
        render = rotate(world.rotation, vec3(position) * (world.renderOffset.w / STEPS_PER_UNIT))
            + world.renderOffset.xyz;
    } else {
        World world = worldTable.worlds[clusterTable.clusters[id].place.x - 1];
        render = vec3(position - world.cameraGrid.xyz) / STEPS_PER_UNIT
            - world.cameraFraction.xyz;
    }
    gl_Position = projViewMatrix * vec4(render, 1.0);
}
