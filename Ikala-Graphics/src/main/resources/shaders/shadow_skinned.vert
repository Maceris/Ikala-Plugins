#version 460
#extension GL_GOOGLE_include_directive : require
#extension GL_EXT_buffer_reference : require

// The shadow vertex shader for skinned meshes: each vertex moved by its bones, then into the
// light's view. Depth only, with no alpha test.

#include "skinning.glsl"

layout(location = 0) in vec3 position;

// The cascade's matrix changes for every map; the skinned buffers go where level of detail's
// tables do, see ShaderBindings.Shadow
layout(push_constant) uniform ShadowConstants {
    mat4 projViewMatrix;
    layout(offset = 72) SkinnedInstances data;
    SkinnedDraws draws;
};

void main()
{
    uvec4 draw = draws.draws[gl_InstanceIndex];
    SkinnedInstance instance = data.instances[draw.x];
    if (regionHidden(instance)) {
        gl_Position = HIDDEN_POSITION;
        return;
    }
    gl_Position = projViewMatrix * instance.model * skinMatrix(data, instance) * vec4(position, 1.0);
}
