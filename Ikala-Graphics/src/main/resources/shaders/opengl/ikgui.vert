#version 460

layout (location=0) in vec2 inPos;
layout (location=1) in vec2 inTextCoords;
layout (location=2) in vec4 inColor;

out vec2 fragTextCoords;
out vec4 fragColor;

uniform vec2 scale;

void main()
{
    fragTextCoords = inTextCoords;
    fragColor = inColor;
    gl_Position = vec4(inPos * scale + vec2(-1.0, 1.0), 0.0, 1.0);
}