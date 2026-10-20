#version 460
#extension GL_EXT_buffer_reference : require

// The shadow vertex shader for level of detail clusters with cut out materials, like leaves:
// shadow_lod.vert, also passing the texture coordinates and material, remapped for instances, on to
// shadow_cutout.frag. A coverage volume shell's corner passes its coverage, blended from looking
// straight in to grazing by how the light sees it, as scene_lod.vert blends it by the camera.

layout(location = 0) in ivec3 position;      // grid steps from the world's origin
layout(location = 1) in vec2 texCoord;       // for textured triangles
layout(location = 2) in uvec2 materialFlags; // x: material index, y: flags
layout(location = 3) in uint texCoordBits;   // the texture coordinates' bits, for shells
layout(location = 4) in uint normalBits;     // the normal's bits, for shells

layout(location = 0) out vec2 outTextCoord;
layout(location = 1) flat out uint outMaterialIdx;
// A coverage volume shell's coverage, or -1 for a cut out material
layout(location = 2) out float outShellAlpha;
// A seed per triangle for a shell's threshold, the index of its first vertex, which flat outputs
// take
layout(location = 3) flat out uint outShellSeed;

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

// Match LodVertex
const uint FLAG_SHELL = 16u;
// Grid steps per unit, see LodVertex
const float STEPS_PER_UNIT = 1024.0;
// Where a shell corner's two coverages hold, by the cosine of the angle the light sees it at, see
// scene_lod.vert
const float SHELL_STRAIGHT_COS = 0.966;
const float SHELL_GRAZING_COS = 0.259;
// The low bits of each half of a shell corner's normal, which hold half its grazing coverage, see
// Attributes.shellNormal
const uint GRAZING_MASK = 15u;

// Undo the octahedral folding BakedVertex.octahedral does
vec3 fromOctahedral(vec2 e)
{
    vec3 v = vec3(e, 1.0 - abs(e.x) - abs(e.y));
    if (v.z < 0.0) {
        v.xy = (1.0 - abs(v.yx)) * vec2(v.x >= 0.0 ? 1.0 : -1.0, v.y >= 0.0 ? 1.0 : -1.0);
    }
    return normalize(v);
}

// The direction light travels through a point the matrix maps to clip, which keeps its projected
// x and y: the light's forward axis for an orthographic map, the line to it for a perspective one
vec3 lightAlong(vec4 clip)
{
    vec3 row0 = vec3(projViewMatrix[0][0], projViewMatrix[1][0], projViewMatrix[2][0]);
    vec3 row1 = vec3(projViewMatrix[0][1], projViewMatrix[1][1], projViewMatrix[2][1]);
    vec3 row3 = vec3(projViewMatrix[0][3], projViewMatrix[1][3], projViewMatrix[2][3]);
    return normalize(cross(row0 - clip.x / clip.w * row3, row1 - clip.y / clip.w * row3));
}

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
    // An instance's model frame turned into the world's; a world's is the world's
    vec4 turn = vec4(0.0, 0.0, 0.0, 1.0);
    if ((id & INSTANCED) != 0u) {
        World world = worldTable.worlds[(id & ~INSTANCED) >> INSTANCE_SHIFT];
        turn = world.rotation;
        render = rotate(world.rotation, vec3(position) * (world.renderOffset.w / STEPS_PER_UNIT))
            + world.renderOffset.xyz;
        // An instance may draw some of its model's materials with others
        outMaterialIdx = materialFlags.x;
        for (uint i = 0u; i < world.model.w; ++i) {
            if (world.remapFrom[i] == materialFlags.x) {
                outMaterialIdx = world.remapTo[i];
            }
        }
    } else {
        World world = worldTable.worlds[clusterTable.clusters[id].place.x - 1];
        render = vec3(position - world.cameraGrid.xyz) / STEPS_PER_UNIT
            - world.cameraFraction.xyz;
        outMaterialIdx = materialFlags.x;
    }
    gl_Position = projViewMatrix * vec4(render, 1.0);
    outTextCoord = texCoord;
    // A shell's corner keeps its straight in coverage in the high byte, see Attributes.shell, and
    // its grazing coverage in its normal's low bits
    outShellAlpha = -1.0;
    outShellSeed = uint(gl_VertexIndex);
    if ((materialFlags.y & FLAG_SHELL) != 0u) {
        float straight = float(texCoordBits >> 24u) / 255.0;
        float grazing = float((normalBits & GRAZING_MASK)
            | (((normalBits >> 16u) & GRAZING_MASK) << 4u)) / 255.0;
        vec3 normal = rotate(turn, fromOctahedral(unpackSnorm2x16(normalBits)));
        float facing = abs(dot(normal, lightAlong(gl_Position)));
        outShellAlpha = mix(grazing, straight, clamp((facing - SHELL_GRAZING_COS)
            / (SHELL_STRAIGHT_COS - SHELL_GRAZING_COS), 0.0, 1.0));
    }
}
