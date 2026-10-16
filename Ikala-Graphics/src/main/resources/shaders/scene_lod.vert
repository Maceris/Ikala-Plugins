#version 460
#extension GL_EXT_buffer_reference : require

// The scene vertex shader for level of detail clusters, in the vertex format of LodVertex. Every
// position is on one grid of 1 / STEPS_PER_UNIT unit from the LOD world's origin, so clusters meet
// exactly; subtracting the camera's grid position as integers keeps positions exact relative to
// the camera however far from the origin.
//
// A textured triangle's vertices carry the mesh's normal, tangent and texture coordinates, and are
// passed on as scene_baked_vertex.glsl passes them, with outViewPosition.w 0 to say so.
//
// An averaged triangle's (FLAG_AVERAGED) carry a normal and, in place of a tangent and texture
// coordinates, their own look blended from the surface they stand for (see Attributes.averaged).
// outViewPosition.w is 2 to say so, outNormal is the normal, outTangent the color in sRGB as a
// texture's would be, outTextCoord the roughness and metallic, and outBitangent.x the specular.
//
// A projected triangle's have none: scene.frag, with TRIPLANAR on, works out each flat triangle's
// normal from how its position changes across the screen, and projects the material's texture
// along the triangle's main axis, one repeat per block (see LodTexturing). So the outputs it would
// otherwise get carry what it needs for that, the same at every vertex of a draw:
// - outNormal, outTangent, outBitangent: the world's x, y and z axes in view space
// - outViewPosition.xyz: where the camera is within its block, which added to the render space
//   position gives coordinates whose fractions are the world's exactly; w is 1
//
// Either way outWorldPosition.xyz is the position in render space, as scene.vert gives it, and its
// w the debug coloring's hue, or -1 for none.
//
// An instance of a model (see LodCullMath) draws the model's clusters turned, scaled and placed by
// the instance, its positions grid steps from the model's origin, and draws some of the model's
// materials with others of its own.

layout(location = 0) in ivec3 position;      // grid steps from the world's origin
layout(location = 1) in uvec2 materialFlags; // x: material index, y: flags
layout(location = 2) in vec2 normalOct;      // octahedral, for textured triangles
layout(location = 3) in vec2 tangentOct;     // octahedral, for textured triangles
layout(location = 4) in vec2 texCoord;       // for textured triangles
layout(location = 5) in uint tangentBits;    // the tangent's bytes whole, for averaged triangles
layout(location = 6) in uint texCoordBits;   // the texture coordinates' bytes whole, likewise

layout(location = 0) out vec3 outNormal;
layout(location = 1) out vec3 outTangent;
layout(location = 2) out vec3 outBitangent;
layout(location = 3) out vec2 outTextCoord;
layout(location = 4) out vec4 outViewPosition;
layout(location = 5) out vec4 outWorldPosition;
layout(location = 6) flat out uint outMaterialIdx;

layout(set = 0, binding = 0) uniform Uniforms {
    mat4 projectionMatrix;
    mat4 viewMatrix;
};

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

layout(push_constant) uniform LodConstants {
    Worlds worldTable;
    Clusters clusterTable;
};

// What a world's clusters are colored by, its info.x, see DebugVisualizers.LodColors
const uint COLORS_CLUSTERS = 1;
const uint COLORS_GROUPS = 2;
const uint COLORS_LEVELS = 3;
const uint COLORS_CELLS = 4;
// Spreads hues apart: the golden ratio's fraction
const float HUE_STEP = 0.61803399;
// Levels step around the hue circle in sevenths, so neighbors differ and the first few don't repeat
const float LEVEL_HUE_STEP = 1.0 / 7.0;

// A hue in [0, 1) for an id, well apart from the next id's
float hueOf(uint id)
{
    // A quick integer hash, so neighboring ids don't get neighboring hues
    id ^= id >> 16;
    id *= 0x7feb352du;
    id ^= id >> 15;
    return fract(float(id & 0xFFFFu) * HUE_STEP);
}

// Grid steps per unit, and its power of two, see LodVertex
const float STEPS_PER_UNIT = 1024.0;
const int STEP_BITS = 10;

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


// LodVertex's flags
const uint FLAG_FLIPPED_BITANGENT = 1;
const uint FLAG_TEXTURED = 2;
const uint FLAG_AVERAGED = 4;

// Undo the octahedral folding BakedVertex.octahedral does
vec3 fromOctahedral(vec2 e)
{
    vec3 v = vec3(e, 1.0 - abs(e.x) - abs(e.y));
    if (v.z < 0.0) {
        v.xy = (1.0 - abs(v.yx)) * vec2(v.x >= 0.0 ? 1.0 : -1.0, v.y >= 0.0 ? 1.0 : -1.0);
    }
    return normalize(v);
}

void main()
{
    uint id = uint(gl_InstanceIndex);
    bool instanced = (id & INSTANCED) != 0u;
    Cluster cluster;
    World world;
    vec3 render;
    if (instanced) {
        world = worldTable.worlds[(id & ~INSTANCED) >> INSTANCE_SHIFT];
        cluster = clusterTable.clusters[world.model.x - 1 + (id & CLUSTER_MASK)];
        // The instance's scale is in renderOffset.w
        render = rotate(world.rotation, vec3(position) * (world.renderOffset.w / STEPS_PER_UNIT))
            + world.renderOffset.xyz;
    } else {
        cluster = clusterTable.clusters[id];
        world = worldTable.worlds[cluster.place.x - 1];
        render = vec3(position - world.cameraGrid.xyz) / STEPS_PER_UNIT
            - world.cameraFraction.xyz;
    }
    ivec4 cameraGrid = world.cameraGrid;
    vec4 cameraFraction = world.cameraFraction;
    outMaterialIdx = materialFlags.x;
    for (uint i = 0u; instanced && i < world.model.w; ++i) {
        if (world.remapFrom[i] == materialFlags.x) {
            outMaterialIdx = world.remapTo[i];
        }
    }
    // An instance's model frame turned into the world's; a world's is the world's
    vec4 turn = instanced ? world.rotation : vec4(0.0, 0.0, 0.0, 1.0);

    gl_Position = projectionMatrix * viewMatrix * vec4(render, 1.0);

    if ((materialFlags.y & FLAG_TEXTURED) != 0) {
        vec3 normal = rotate(turn, fromOctahedral(normalOct));
        vec3 tangent = rotate(turn, fromOctahedral(tangentOct));
        float handedness = (materialFlags.y & FLAG_FLIPPED_BITANGENT) != 0 ? -1.0 : 1.0;
        outNormal = normalize((viewMatrix * vec4(normal, 0.0)).xyz);
        outTangent = normalize((viewMatrix * vec4(tangent, 0.0)).xyz);
        outBitangent = normalize((viewMatrix * vec4(handedness * cross(normal, tangent), 0.0)).xyz);
        if ((materialFlags.y & FLAG_AVERAGED) != 0) {
            // Red, green and blue in sRGB and the roughness, a byte each; then past the texture
            // coordinates' NaN, the metallic and specular
            vec4 look = unpackUnorm4x8(tangentBits);
            vec4 more = unpackUnorm4x8(texCoordBits);
            outTangent = look.rgb;
            outTextCoord = vec2(look.a, more.z);
            outBitangent = vec3(more.w, 0.0, 0.0);
            outViewPosition = vec4(0.0, 0.0, 0.0, 2.0);
        } else {
            outTextCoord = texCoord;
            outViewPosition = vec4(0.0);
        }
    } else {
        // The camera within its block: the steps past the block's corner, and the rest; an
        // instance's blocks don't line up with the world's, so its projection starts at the camera
        ivec3 block = (cameraGrid.xyz >> STEP_BITS) << STEP_BITS;
        outViewPosition = instanced
            ? vec4(0.0, 0.0, 0.0, 1.0)
            : vec4(vec3(cameraGrid.xyz - block) / STEPS_PER_UNIT + cameraFraction.xyz, 1.0);
        outNormal = (viewMatrix * vec4(rotate(turn, vec3(1.0, 0.0, 0.0)), 0.0)).xyz;
        outTangent = (viewMatrix * vec4(rotate(turn, vec3(0.0, 1.0, 0.0)), 0.0)).xyz;
        outBitangent = (viewMatrix * vec4(rotate(turn, vec3(0.0, 0.0, 1.0)), 0.0)).xyz;
        outTextCoord = vec2(0.0);
    }
    // The debug coloring's hue, or -1 for none
    float hue = -1.0;
    uint colors = world.info.x;
    if (colors == COLORS_CLUSTERS) {
        hue = hueOf(id);
    } else if (colors == COLORS_GROUPS) {
        uvec4 bound = floatBitsToUint(cluster.parentBound);
        hue = hueOf(bound.x ^ (bound.y * 3u) ^ (bound.z * 7u) ^ (bound.w * 13u));
    } else if (colors == COLORS_LEVELS) {
        hue = fract(float(cluster.unit.z) * LEVEL_HUE_STEP);
    } else if (colors == COLORS_CELLS) {
        hue = hueOf(cluster.place.y);
    }
    outWorldPosition = vec4(render, hue);
}
