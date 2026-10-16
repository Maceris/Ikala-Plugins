#version 460
#extension GL_EXT_buffer_reference : require

// The shadow vertex shader for baked sections' cut out meshes, like leaves: shadow_baked.vert, also
// passing the texture coordinates and material on to shadow_cutout.frag. The material comes from
// each vertex, since a section mixes many.

layout(location = 0) in ivec4 position;      // xyz: steps from the section's origin, w: user data
layout(location = 1) in vec2 texCoord;
layout(location = 2) in uvec2 materialFlags; // x: material index

layout(location = 0) out vec2 outTextCoord;
layout(location = 1) flat out uint outMaterialIdx;

// What this frame draws, written by the culling pass: (instance slot, material) per instance,
// grouped by mesh. Each draw command's first instance points at its mesh's group.
layout(buffer_reference, std430, buffer_reference_align = 8) readonly buffer Visible {
    uvec2 visible[];
};

// These change for every cascade
layout(push_constant) uniform ShadowConstants {
    mat4 projViewMatrix;
    Visible visible;
};

// The model matrix of every instance slot, relative to the camera
layout(std430, set = 0, binding = 0) readonly buffer Matrices {
	mat4 modelMatrices[];
};

void main()
{
    mat4 modelMatrix = modelMatrices[visible.visible[gl_InstanceIndex].x];
    gl_Position = projViewMatrix * modelMatrix * vec4(vec3(position.xyz), 1.0);
    outTextCoord = texCoord;
    outMaterialIdx = materialFlags.x;
}
