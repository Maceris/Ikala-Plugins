#version 460
#extension GL_GOOGLE_include_directive : require
#extension GL_EXT_buffer_reference : require

// The scene vertex shader for skinned meshes: scene_vertex.glsl, with each vertex moved by its
// bones first. Drawn with scene.frag, so skinned meshes shade like everything else.

#include "skinning.glsl"

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
// No material remaps or shells here, see scene_lod.vert
layout(location = 7) flat out uvec4 outRemaps;
layout(location = 8) flat out uint outShellSeed;

layout(set = 0, binding = 0) uniform Uniforms {
    mat4 projectionMatrix;
    mat4 viewMatrix;
};

// Where scene_lod.vert's level of detail tables go, since only one of them is bound at a time
layout(push_constant) uniform SkinnedConstants {
    SkinnedInstances data;
    SkinnedDraws draws;
};

void main()
{
    uvec4 draw = draws.draws[gl_InstanceIndex];
    SkinnedInstance instance = data.instances[draw.x];
    outMaterialIdx = draw.y;
    outRemaps = uvec4(0xFFFFFFFFu);
    outShellSeed = 0u;
    outTextCoord = texCoord;

    if (regionHidden(instance)) {
        gl_Position = HIDDEN_POSITION;
        outNormal = vec3(0.0, 1.0, 0.0);
        outTangent = vec3(1.0, 0.0, 0.0);
        outBitangent = vec3(0.0, 0.0, 1.0);
        outViewPosition = vec4(0.0);
        outWorldPosition = vec4(0.0);
        return;
    }

    // Shape bones can scale unevenly, which bends normals a little; they are renormalized
    mat4 modelMatrix = instance.model * skinMatrix(data, instance);
    mat4 modelViewMatrix = viewMatrix * modelMatrix;
    outWorldPosition = modelMatrix * vec4(position, 1.0);
    outViewPosition = viewMatrix * outWorldPosition;
    gl_Position = projectionMatrix * outViewPosition;
    outNormal = normalize(modelViewMatrix * vec4(normal, 0.0)).xyz;
    outTangent = normalize(modelViewMatrix * vec4(tangent, 0.0)).xyz;
    outBitangent = normalize(modelViewMatrix * vec4(bitangent, 0.0)).xyz;
}
