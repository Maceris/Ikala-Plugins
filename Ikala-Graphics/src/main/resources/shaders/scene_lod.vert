#version 460
#extension GL_EXT_buffer_reference : require

// The scene vertex shader for level of detail clusters, in the vertex format of LodVertex. Every
// position is on one grid of 1 / STEPS_PER_UNIT unit from the LOD world's origin, so clusters meet
// exactly; subtracting the camera's grid position as integers keeps positions exact relative to
// the camera however far from the origin.
//
// There is no normal: scene.frag, with TRIPLANAR on, works out each flat triangle's normal from how
// its position changes across the screen, and projects the material's texture along the
// triangle's main axis, one repeat per block (see LodTexturing). So the outputs it would otherwise
// get carry what it needs for that, the same at every vertex of a draw:
// - outNormal, outTangent, outBitangent: the world's x, y and z axes in view space
// - outViewPosition.xyz: where the camera is within its block, which added to the render space
//   position gives coordinates whose fractions are the world's exactly
// - outWorldPosition: the position in render space, as scene.vert gives it

layout(location = 0) in ivec3 position;      // grid steps from the world's origin
layout(location = 1) in uvec2 materialFlags; // x: material index, y: flags

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

void main()
{
    Cluster cluster = clusterTable.clusters[gl_InstanceIndex];
    World world = worldTable.worlds[cluster.place.x - 1];
    ivec4 cameraGrid = world.cameraGrid;
    vec4 cameraFraction = world.cameraFraction;
    outMaterialIdx = materialFlags.x;

    vec3 render = vec3(position - cameraGrid.xyz) / STEPS_PER_UNIT - cameraFraction.xyz;
    outWorldPosition = vec4(render, 1.0);
    gl_Position = projectionMatrix * viewMatrix * outWorldPosition;

    // The camera within its block: the steps past the block's corner, and the rest
    ivec3 block = (cameraGrid.xyz >> STEP_BITS) << STEP_BITS;
    outViewPosition = vec4(vec3(cameraGrid.xyz - block) / STEPS_PER_UNIT + cameraFraction.xyz, 1.0);
    outNormal = (viewMatrix * vec4(1.0, 0.0, 0.0, 0.0)).xyz;
    outTangent = (viewMatrix * vec4(0.0, 1.0, 0.0, 0.0)).xyz;
    outBitangent = (viewMatrix * vec4(0.0, 0.0, 1.0, 0.0)).xyz;
    // y: the debug coloring's hue, or -1 for none
    float hue = -1.0;
    uint colors = world.info.x;
    if (colors == COLORS_CLUSTERS) {
        hue = hueOf(uint(gl_InstanceIndex));
    } else if (colors == COLORS_GROUPS) {
        uvec4 bound = floatBitsToUint(cluster.parentBound);
        hue = hueOf(bound.x ^ (bound.y * 3u) ^ (bound.z * 7u) ^ (bound.w * 13u));
    } else if (colors == COLORS_LEVELS) {
        hue = fract(float(cluster.unit.z) * LEVEL_HUE_STEP);
    } else if (colors == COLORS_CELLS) {
        hue = hueOf(cluster.place.y);
    }
    outTextCoord = vec2(0.0, hue);
}
