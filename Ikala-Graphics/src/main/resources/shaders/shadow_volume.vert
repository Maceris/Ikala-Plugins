#version 460
#extension GL_EXT_buffer_reference : require

// Coverage volume bricks' boxes in a shadow map: scene_volume.vert with the cascade's matrix, also
// finding the direction the map looks along, see shadow_volume.frag.

layout(location = 0) in ivec3 position;  // grid steps from the world's origin
layout(location = 1) in float voxelSize; // the brick's voxel size, in the model's units
layout(location = 2) in uint corner;     // which corner of the box: bits x, y, z from the lowest
layout(location = 3) in uint slot;       // the brick's slot in the brick table

// Where the fragment is, in render space: from the camera, in the world's axes
layout(location = 0) out vec3 outRender;
// The box's lowest corner in render space
layout(location = 1) flat out vec3 outCorner;
// The model's turn, a quaternion: the box's axes in the world
layout(location = 2) flat out vec4 outTurn;
// A voxel's width in render space, the brick's slot
layout(location = 3) flat out vec2 outCell;
layout(location = 4) flat out uint outSlot;
// The direction the map looks along, in render space
layout(location = 5) flat out vec3 outDirection;


struct World {
    vec4 renderOffset;
    vec4 eye;
    ivec4 cameraGrid;
    vec4 cameraFraction;
    uvec4 info;
    vec4 rotation;
    uvec4 model;
    uvec4 remapFrom;
    uvec4 remapTo;
};

layout(buffer_reference, std430, buffer_reference_align = 16) readonly buffer Worlds {
    World worlds[];
};

struct Cluster {
    vec4 tight;
    vec4 bound;
    vec4 parentBound;
    uvec4 info;
    uvec4 place;
    uvec4 unit;
};

layout(buffer_reference, std430, buffer_reference_align = 16) readonly buffer Clusters {
    Cluster clusters[];
};

layout(push_constant) uniform ShadowConstants {
    mat4 projViewMatrix;
    layout(offset = 72) Worlds worldTable;
    Clusters clusterTable;
};

// Match LodVertex and LodCullMath
const float STEPS_PER_UNIT = 1024.0;
const uint INSTANCED = 0x80000000u;
const uint INSTANCE_SHIFT = 16;
const uint CLUSTER_MASK = 0xFFFFu;
// Match CoverageBricks
const float BRICK_SIDE = 8.0;

vec3 rotate(vec4 q, vec3 v)
{
    vec3 t = 2.0 * cross(q.xyz, v);
    return v + q.w * t + cross(q.xyz, t);
}

void main()
{
    uint id = uint(gl_InstanceIndex);
    bool instanced = (id & INSTANCED) != 0u;
    vec3 render;
    vec4 turn = vec4(0.0, 0.0, 0.0, 1.0);
    float scale = 1.0;
    if (instanced) {
        World world = worldTable.worlds[(id & ~INSTANCED) >> INSTANCE_SHIFT];
        turn = world.rotation;
        scale = world.renderOffset.w;
        render = rotate(turn, vec3(position) * (scale / STEPS_PER_UNIT)) + world.renderOffset.xyz;
    } else {
        World world = worldTable.worlds[clusterTable.clusters[id].place.x - 1];
        render = vec3(position - world.cameraGrid.xyz) / STEPS_PER_UNIT
            - world.cameraFraction.xyz;
    }
    float cell = voxelSize * scale;
    vec3 bits = vec3(float(corner & 1u), float((corner >> 1) & 1u), float((corner >> 2) & 1u));
    outRender = render;
    outCorner = render - rotate(turn, bits * (BRICK_SIDE * cell));
    outTurn = turn;
    outCell = vec2(cell, scale);
    outSlot = slot;
    gl_Position = projViewMatrix * vec4(render, 1.0);
    // Where this vertex's spot on the map is at the near and far ends of its depth range
    mat4 inverseMatrix = inverse(projViewMatrix);
    vec4 near = inverseMatrix * vec4(gl_Position.xy / gl_Position.w, 0.0, 1.0);
    vec4 far = inverseMatrix * vec4(gl_Position.xy / gl_Position.w, 1.0, 1.0);
    outDirection = normalize(far.xyz / far.w - near.xyz / near.w);
}
