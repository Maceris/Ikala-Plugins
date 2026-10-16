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

// The scene color is linear light, tone mapped later. The sky's texture samples in linear light
// already; its color is given sRGB encoded, as material colors are.
void main()
{
    vec3 color = hasTexture == 1
        ? texture(bindlessTextures[nonuniformEXT(textureIndex)], outTextCoord).rgb
        : sRGBToLinear(diffuse.rgb);
    fragColor = vec4(color, 1.0);
}