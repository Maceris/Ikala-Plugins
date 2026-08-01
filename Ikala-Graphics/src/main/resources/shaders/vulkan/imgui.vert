#version 460

layout (location=0) in vec2 inPos;
layout (location=1) in vec2 inTextCoords;
layout (location=2) in vec4 inColor;

layout(location = 0) out vec2 fragTextCoords;
layout(location = 1) out vec4 fragColor;

//TODO(ches) now that these need to be in blocks, figure out the buffer layout again
layout(set = 0, binding = 0) uniform Uniforms {
    vec2 scale;
};

void main()
{
    fragTextCoords = inTextCoords;
    fragColor = inColor;
    gl_Position = vec4(inPos * scale + vec2(-1.0, 1.0), 0.0, 1.0);
}