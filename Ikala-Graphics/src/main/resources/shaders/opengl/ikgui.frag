#version 460

const uint ELEMENT_TYPE_CIRCLE = 0;
const uint ELEMENT_TYPE_LINE_ARC = 1;
const uint ELEMENT_TYPE_LINE_BEZIER = 2;
const uint ELEMENT_TYPE_LINE_STRAIGHT = 3;
const uint ELEMENT_TYPE_RECTANGLE = 4;
const uint ELEMENT_TYPE_POLYGON = 5;
const uint ELEMENT_TYPE_TEXT = 6;

const uint ELEMENT_STYLE_ONLY_FILL = 0;
const uint ELEMENT_STYLE_ONLY_BORDER = 1;
const uint ELEMENT_STYLE_FILL_AND_BORDER = 2;
const uint ELEMENT_STYLE_TEXTURE = 3;

struct Point
{
    vec2 pos;
    vec2 misc;
};

struct PointDetail
{
    float radius;
    uint colorOrTextureID;
    uint tint;
};

struct Command
{
    uint pointIndex;
    uint detailIndex;
    // 2 shorts
    uint pointCountAndDetailCount;
    // 2 shorts
    uint typeAndStyle;
    float stroke;
};

in vec2 fragTextCoords;
in vec4 fragColor;

uniform sampler2D txtSampler;

out vec4 outColor;

layout(std430, binding = 0) buffer Commands {
	Command commands[];
};

layout(std430, binding = 1) buffer Points {
	Point points[];
};

layout(std430, binding = 2) buffer PointDetails {
	PointDetail pointDetails[];
};

void main()
{
    outColor = fragColor * texture(txtSampler, fragTextCoords);
}