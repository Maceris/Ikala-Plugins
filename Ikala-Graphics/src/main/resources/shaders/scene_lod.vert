#version 460

// The scene vertex shader for level of detail clusters, in the vertex format of LodVertex. Every
// position is on one grid of 1 / STEPS_PER_UNIT unit from the LOD world's origin, so clusters meet
// exactly; subtracting the camera's grid position as integers keeps positions exact relative to
// the camera however far from the origin. Everything after the inputs matches scene.vert, and
// scene.frag follows with TRIPLANAR on: texture coordinates are projected along the triangle's main
// axis, one repeat per block, unwrapped so they interpolate (see LodTexturing).

layout(location = 0) in ivec3 position;      // grid steps from the world's origin
layout(location = 1) in vec2 normalOct;      // octahedral
layout(location = 2) in uvec2 materialFlags; // x: material index, y: flags

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

// Where the camera is, for each LOD world drawn
layout(push_constant) uniform LodConstants {
    // xyz: the camera's grid step from the world's origin, rounded down
    ivec4 cameraGrid;
    // xyz: the rest of the camera's position past that step, in units
    vec4 cameraFraction;
};

// Grid steps per unit, and its power of two, see LodVertex
const float STEPS_PER_UNIT = 1024.0;
const int STEP_BITS = 10;

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
    vec3 normal = fromOctahedral(normalOct);
    outMaterialIdx = materialFlags.x;

    vec3 render = vec3(position - cameraGrid.xyz) / STEPS_PER_UNIT - cameraFraction.xyz;
    outWorldPosition = vec4(render, 1.0);
    outViewPosition = viewMatrix * outWorldPosition;
    gl_Position = projectionMatrix * outViewPosition;

    // Texture coordinates from the camera's block, so they stay small and their fractions are the
    // world's exactly. The main axis prefers up and down on ties, as LodTexturing.axis does.
    ivec3 block = (cameraGrid.xyz >> STEP_BITS) << STEP_BITS;
    vec3 local = vec3(position - block) / STEPS_PER_UNIT;
    vec3 size = abs(normal);
    vec3 tangent;
    vec3 bitangent;
    if (size.y >= size.x && size.y >= size.z) {
        outTextCoord = local.xz;
        tangent = vec3(1.0, 0.0, 0.0);
        bitangent = vec3(0.0, 0.0, 1.0);
    } else if (size.x >= size.z) {
        // Seen from +x, right is -z; from -x, +z
        float side = normal.x > 0.0 ? -1.0 : 1.0;
        outTextCoord = vec2(side * local.z, -local.y);
        tangent = vec3(0.0, 0.0, side);
        bitangent = vec3(0.0, -1.0, 0.0);
    } else {
        // Seen from +z, right is +x; from -z, -x
        float side = normal.z > 0.0 ? 1.0 : -1.0;
        outTextCoord = vec2(side * local.x, -local.y);
        tangent = vec3(side, 0.0, 0.0);
        bitangent = vec3(0.0, -1.0, 0.0);
    }
    outNormal = normalize(viewMatrix * vec4(normal, 0.0)).xyz;
    outTangent = normalize(viewMatrix * vec4(tangent, 0.0)).xyz;
    outBitangent = normalize(viewMatrix * vec4(bitangent, 0.0)).xyz;
}
