#version 460

in vec2 outTextCoords;
in vec4 outColor;

uniform sampler2D txtSampler;

out vec4 outColor;

void main()
{
    outColor = outColor  * texture(txtSampler, outTextCoords);
}