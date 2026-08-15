#version 460
#extension GL_EXT_nonuniform_qualifier : enable

layout(location = 0) in vec2 fragTextCoords;
layout(location = 1) in vec4 fragColor;

layout(location = 0) out vec4 outColor;

layout(set = 0, binding = 0) uniform Uniforms {
    vec2 scale;
    int fontTexture;
};

layout(set = 0, binding = 1) uniform sampler2D bindlessTextures[];

void main()
{
    outColor = fragColor * texture(bindlessTextures[nonuniformEXT(fontTexture)], fragTextCoords);
}