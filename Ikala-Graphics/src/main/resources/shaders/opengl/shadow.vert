#version 460

layout(location = 0) in vec3 position;
layout(location = 1) in vec3 normal;
layout(location = 2) in vec3 tangent;
layout(location = 3) in vec3 bitangent;
layout(location = 4) in vec2 texCoord;

struct DrawElement
{
    int modelMatrixIndex;
    int materialIndex;
};

uniform mat4 projViewMatrix;

layout(std430, binding = 1) buffer DrawElements {
    DrawElement drawElements[];
};

layout(std430, binding = 2) buffer Matrices {
	mat4 modelMatrices[];
};

void main()
{
    vec4 initPos = vec4(position, 1.0);
    uint idx = gl_BaseInstance + gl_InstanceID;
	DrawElement drawElement = drawElements[idx];
    mat4 modelMatrix = modelMatrices[drawElement.modelMatrixIndex];
    gl_Position = projViewMatrix * modelMatrix * initPos;
}