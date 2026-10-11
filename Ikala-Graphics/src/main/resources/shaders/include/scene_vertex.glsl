#ifndef SCENE_VERTEX_GLSL
#define SCENE_VERTEX_GLSL

// The scene vertex shader for meshes in the full vertex format. The scene stage includes it
// with SCENE_SET 0, the transparent stage with its own set.
// SCENE_SET: which descriptor set holds the scene stage's bindings
#ifndef SCENE_SET
#define SCENE_SET 0
#endif

#include "material.glsl"


layout(location = 0) in vec3 position;
layout(location = 1) in vec3 normal;
layout(location = 2) in vec3 tangent;
layout(location = 3) in vec3 bitangent;
layout(location = 4) in vec2 texCoord;

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

// The model matrix of every instance slot, relative to the camera
layout(std430, set = SCENE_SET, binding = 1) readonly buffer Matrices {
	mat4 modelMatrices[];
};

layout(std430, set = SCENE_SET, binding = 2) readonly buffer Materials {
    Material materials[];
};

// What this pass draws, written by the culling pass: (instance slot, material) per instance,
// grouped by mesh. Each draw command's first instance points at its mesh's group.
layout(std430, set = SCENE_SET, binding = 3) readonly buffer Visible {
    uvec2 visible[];
};

void main()
{
    vec4 initPos = vec4(position, 1.0);
    vec4 initNormal = vec4(normal, 0.0);
    vec4 initTangent = vec4(tangent, 0.0);
    vec4 initBitangent = vec4(bitangent, 0.0);

    // gl_InstanceIndex already includes the base instance, unlike gl_InstanceID in OpenGL
    uvec2 instance = visible[gl_InstanceIndex];
    outMaterialIdx = instance.y;

    mat4 modelMatrix = modelMatrices[instance.x];
    mat4 modelViewMatrix = viewMatrix * modelMatrix;
    outWorldPosition = modelMatrix * initPos;
    outViewPosition  = viewMatrix * outWorldPosition;
    gl_Position   = projectionMatrix * outViewPosition;
    outNormal     = normalize(modelViewMatrix * initNormal).xyz;
    outTangent    = normalize(modelViewMatrix * initTangent).xyz;
    outBitangent  = normalize(modelViewMatrix * initBitangent).xyz;
    outTextCoord  = texCoord;
}

#endif
