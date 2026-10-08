#version 460

layout(location = 0) in vec3 position;

// These change for every cascade and model
layout(push_constant) uniform ShadowConstants {
    mat4 projViewMatrix;
    uint firstMatrix;
};

layout(std430, set = 0, binding = 0) readonly buffer Matrices {
	mat4 modelMatrices[];
};

void main()
{
    vec4 initPos = vec4(position, 1.0);
    // gl_InstanceIndex already includes the base instance, unlike gl_InstanceID in OpenGL
    mat4 modelMatrix = modelMatrices[firstMatrix + gl_InstanceIndex];
    gl_Position = projViewMatrix * modelMatrix * initPos;
}
