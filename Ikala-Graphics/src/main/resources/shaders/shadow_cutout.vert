#version 460
#extension GL_EXT_buffer_reference : require

// The shadow vertex shader for standard meshes whose texture can cut holes, like leaves: shadow.vert,
// also passing the texture coordinates and material on to shadow_cutout.frag.

layout(location = 0) in vec3 position;
layout(location = 1) in vec2 texCoord;

layout(location = 0) out vec2 outTextCoord;
layout(location = 1) flat out uint outMaterialIdx;
// Never a coverage volume shell's, see shadow_cutout.frag
layout(location = 2) out float outShellAlpha;
// A seed per triangle for a shell's threshold, the index of its first vertex, which flat outputs
// take
layout(location = 3) flat out uint outShellSeed;

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
    uvec2 instance = visible.visible[gl_InstanceIndex];
    gl_Position = projViewMatrix * modelMatrices[instance.x] * vec4(position, 1.0);
    outTextCoord = texCoord;
    outMaterialIdx = instance.y;
    outShellAlpha = -1.0;
    outShellSeed = uint(gl_VertexIndex);
}
