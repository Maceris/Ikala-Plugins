#ifndef SCENE_BAKED_VERTEX_GLSL
#define SCENE_BAKED_VERTEX_GLSL

// SCENE_SET: which descriptor set holds the scene stage's bindings
#ifndef SCENE_SET
#define SCENE_SET 0
#endif

// The scene vertex shader for baked sections, which come in the compact vertex format of
// BakedVertex rather than the full one scene.vert reads. Everything after the inputs matches
// scene.vert, and the same fragment shader follows.

layout(location = 0) in ivec4 position;      // xyz: steps from the section's origin, w: user data
layout(location = 1) in vec2 normalOct;      // octahedral
layout(location = 2) in vec2 tangentOct;     // octahedral
layout(location = 3) in vec2 texCoord;
layout(location = 4) in uvec2 materialFlags; // x: material index, y: FLAG_FLIPPED_BITANGENT

layout(location = 0) out vec3 outNormal;
layout(location = 1) out vec3 outTangent;
layout(location = 2) out vec3 outBitangent;
layout(location = 3) out vec2 outTextCoord;
layout(location = 4) out vec4 outViewPosition;
layout(location = 5) out vec4 outWorldPosition;
layout(location = 6) flat out uint outMaterialIdx;

layout(set = SCENE_SET, binding = 0) uniform Uniforms {
    mat4 projectionMatrix;
    mat4 viewMatrix;
};

// The model matrix of every instance slot, relative to the camera. A section's is scaled from
// steps to units.
layout(std430, set = SCENE_SET, binding = 1) readonly buffer Matrices {
	mat4 modelMatrices[];
};

// What this pass draws, written by the culling pass: (instance slot, material) per instance. The
// material comes from each vertex instead, since a section mixes many.
layout(std430, set = SCENE_SET, binding = 3) readonly buffer Visible {
    uvec2 visible[];
};

// Set in the flags when the bitangent is -(normal x tangent)
const uint FLAG_FLIPPED_BITANGENT = 1;

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
    vec3 tangent = fromOctahedral(tangentOct);
    float handedness = (materialFlags.y & FLAG_FLIPPED_BITANGENT) != 0 ? -1.0 : 1.0;
    vec3 bitangent = handedness * cross(normal, tangent);

    uvec2 instance = visible[gl_InstanceIndex];
    outMaterialIdx = materialFlags.x;

    mat4 modelMatrix = modelMatrices[instance.x];
    mat4 modelViewMatrix = viewMatrix * modelMatrix;
    outWorldPosition = modelMatrix * vec4(vec3(position.xyz), 1.0);
    outViewPosition  = viewMatrix * outWorldPosition;
    gl_Position   = projectionMatrix * outViewPosition;
    // The section's scale is uniform, so normalizing undoes it
    outNormal     = normalize(modelViewMatrix * vec4(normal, 0.0)).xyz;
    outTangent    = normalize(modelViewMatrix * vec4(tangent, 0.0)).xyz;
    outBitangent  = normalize(modelViewMatrix * vec4(bitangent, 0.0)).xyz;
    outTextCoord  = texCoord;
}

#endif
