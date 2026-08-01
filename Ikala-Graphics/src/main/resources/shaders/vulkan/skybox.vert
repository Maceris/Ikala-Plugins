#version 460

layout(location = 0) in vec3 position;
layout(location = 1) in vec2 texCoord;

layout(location = 0) out vec2 outTextCoord;

//TODO(ches) now that these need to be in blocks, figure out the buffer layout again
layout(set = 0, binding = 0) uniform Uniforms {
    mat4 projectionMatrix;
    mat4 viewMatrix;
};

void main()
{
    gl_Position = projectionMatrix * viewMatrix * vec4(position, 1.0);
    outTextCoord = texCoord;
}