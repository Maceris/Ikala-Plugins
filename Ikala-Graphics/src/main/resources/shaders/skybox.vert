#version 460

layout(location = 0) in vec3 position;
layout(location = 1) in vec2 texCoord;

layout(location = 0) out vec2 outTextCoord;

layout(set = 0, binding = 0) uniform Uniforms {
    mat4 projectionMatrix;
    mat4 viewMatrix;
    vec4 diffuse;
    int hasTexture;
    int textureIndex;
};

void main()
{
    // Put the skybox at the far plane, so it only shows where the scene didn't draw anything
    gl_Position = (projectionMatrix * viewMatrix * vec4(position, 1.0)).xyww;
    outTextCoord = texCoord;
}