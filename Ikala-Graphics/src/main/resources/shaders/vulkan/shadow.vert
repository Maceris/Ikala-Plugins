#version 460

layout(location = 0) in vec3 position;
layout(location = 1) in vec3 normal;
layout(location = 2) in vec3 tangent;
layout(location = 3) in vec3 bitangent;
layout(location = 4) in vec2 texCoord;

//TODO(ches) now that these need to be in blocks, figure out the buffer layout again
layout(set = 0, binding = 0) uniform Uniforms {
    mat4 projViewMatrix;
};

layout(std430, set = 0, binding = 1) buffer Matrices {
	mat4 modelMatrices[];
};

void main()
{
    vec4 initPos = vec4(position, 1.0);
    mat4 modelMatrix = modelMatrices[gl_BaseInstance + gl_InstanceIndex];
    gl_Position = projViewMatrix * modelMatrix * initPos;
}