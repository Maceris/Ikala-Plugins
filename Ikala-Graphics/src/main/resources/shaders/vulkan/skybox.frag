#version 460
#extension GL_EXT_nonuniform_qualifier : enable

layout(location = 0) in vec2 outTextCoord;

layout(location = 0) out vec4 fragColor;

layout(set = 0, binding = 0) uniform Uniforms {
    mat4 projectionMatrix;
    mat4 viewMatrix;
    vec4 diffuse;
    int hasTexture;
    int textureIndex;
};

layout(set = 0, binding = 1) uniform sampler2D bindlessTextures[];

void main()
{
    if (hasTexture == 1) {
        fragColor = texture(bindlessTextures[nonuniformEXT(textureIndex)], outTextCoord);
    } else {
        fragColor = diffuse;
    }
}