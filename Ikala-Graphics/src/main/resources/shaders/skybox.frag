#version 460
#extension GL_EXT_nonuniform_qualifier : enable
#extension GL_GOOGLE_include_directive : require

#include "color.glsl"

layout(location = 0) in vec2 outTextCoord;

layout(location = 0) out vec4 fragColor;

layout(set = 0, binding = 0) uniform Uniforms {
    mat4 projectionMatrix;
    mat4 viewMatrix;
    vec4 diffuse;
    int hasTexture;
    int textureIndex;
};

layout(set = 1, binding = 0) uniform sampler2D bindlessTextures[];

// The sky is authored in sRGB; the scene color is linear light, tone mapped later
void main()
{
    vec4 color = hasTexture == 1
        ? texture(bindlessTextures[nonuniformEXT(textureIndex)], outTextCoord)
        : diffuse;
    fragColor = vec4(sRGBToLinear(color.rgb), 1.0);
}