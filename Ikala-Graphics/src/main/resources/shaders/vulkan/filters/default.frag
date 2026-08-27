#version 460

layout(location = 0) in vec2 outTextCoord;
layout(location = 0) out vec4 color;

layout(set = 0, binding = 0) uniform sampler2D screenTexture;

void main()
{
	color = texture(screenTexture, outTextCoord);
}