#version 460

layout(location = 0) in vec2 outTextCoord;

layout(location = 0) out vec4 fragColor;

//TODO(ches) now that these need to be in blocks, figure out the buffer layout again
layout(set = 0, binding = 0) uniform Uniforms {
    vec4 diffuse;
    int hasTexture;
};

layout(set = 0, binding = 1) uniform sampler2D textureSampler;

void main()
{
    if (hasTexture == 1) {
        fragColor = texture(textureSampler, outTextCoord);
    } else {
        fragColor = diffuse;
    }
}