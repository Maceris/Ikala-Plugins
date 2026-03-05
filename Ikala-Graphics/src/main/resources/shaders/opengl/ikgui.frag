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

flat in int quadID;

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

float hash11(uint n) {
    n = (n << 13u) ^ n;
    n = n * (n * n * 15731u + 789221u) + 1376312589u;
    return float(n & uvec3(0x7fffffffu)) / float(0x7fffffff);
}

vec3 hashIntToColor(uint id) {
    float r = hash11(id);
    float g = hash11(id + 1u);
    float b = hash11(id + 2u);
    return vec3(r, g, b);
}

void main()
{
    vec3 color = hashIntToColor(uint(quadID));
    outColor = vec4(color, 1.0);
}