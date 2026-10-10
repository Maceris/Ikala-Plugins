#version 460
#extension GL_EXT_buffer_reference : require

// The shadow vertex shader for baked sections: shadow.vert, reading the position from the compact
// vertex format of BakedVertex.

layout(location = 0) in ivec4 position; // xyz: steps from the section's origin, w: user data

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
}
