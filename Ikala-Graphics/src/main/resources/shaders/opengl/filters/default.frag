#version 460

in vec2 outTextCoord;
out vec4 color;

uniform sampler2D screenTexture;

void main()
{
	color = texture(screenTexture, outTextCoord);
} 