#version 460

in vec2 fragTextCoords;
in vec4 fragColor;

uniform sampler2D txtSampler;

out vec4 outColor;

void main()
{
    outColor = fragColor * texture(txtSampler, fragTextCoords);
}