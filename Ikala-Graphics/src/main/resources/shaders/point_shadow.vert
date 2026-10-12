#version 460

// Draws a standard mesh that moves into a point light shadow map's face, placed by its instance's
// model matrix. See PointShadows.

layout(location = 0) in vec3 position;

layout(push_constant) uniform PointShadowConstants {
    mat4 projViewMatrix;
    // x: the instance slot
    uvec4 instance;
};

// The model matrix of every instance slot, relative to the camera
layout(std430, set = 0, binding = 0) readonly buffer Matrices {
    mat4 modelMatrices[];
};

void main()
{
    gl_Position = projViewMatrix * modelMatrices[instance.x] * vec4(position, 1.0);
}
