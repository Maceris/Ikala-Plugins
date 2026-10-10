#version 460
#extension GL_EXT_buffer_reference : require

layout(location = 0) in vec3 position;

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
    vec4 initPos = vec4(position, 1.0);
    // gl_InstanceIndex already includes the base instance, unlike gl_InstanceID in OpenGL
    mat4 modelMatrix = modelMatrices[visible.visible[gl_InstanceIndex].x];
    gl_Position = projViewMatrix * modelMatrix * initPos;
}
