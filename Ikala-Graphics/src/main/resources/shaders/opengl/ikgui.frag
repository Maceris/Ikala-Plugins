#version 460

const int ELEMENT_TYPE_CIRCLE = 0;
const int ELEMENT_TYPE_LINE_ARC = 1;
const int ELEMENT_TYPE_LINE_BEZIER = 2;
const int ELEMENT_TYPE_LINE_STRAIGHT = 3;
const int ELEMENT_TYPE_RECTANGLE = 4;
const int ELEMENT_TYPE_POLYGON = 5;
const int ELEMENT_TYPE_TEXT = 6;

const int ELEMENT_STYLE_ONLY_FILL = 0;
const int ELEMENT_STYLE_ONLY_BORDER = 1;
const int ELEMENT_STYLE_FILL_AND_BORDER = 2;
const int ELEMENT_STYLE_TEXTURE = 3;

const vec4 ERROR_COLOR =  vec4(1, 0, 0, 1);

struct Point
{
    vec2 pos;
    vec2 misc;
};

struct PointDetail
{
    float radius;
    int colorOrTextureID;
    int tint;
};

struct Command
{
    int pointIndex;
    int detailIndex;
    int pointCount;
    int detailCount;
    int type;
    int style;
    float stroke;
};

flat in int quadID;

// Used to convert from pixel coordinates to Normalized Device Coordinates of (-1, 1)
uniform vec2 scale;
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

vec4 intToColor(uint color) {
    uint r = (color >> 24) & 0xFF;
    uint g = (color >> 16) & 0xFF;
    uint b = (color >> 8) & 0xFF;
    uint a = color & 0xFF;

    return vec4(r/255.0f, g/255.0f, b/255.0f, a/255.0f);
}

vec2 normalizePoint(vec2 pos) {
    return pos * scale + vec2(-1.0, 1.0);
}

void draw_circle(Command command, vec2 fragPos) {
    //TODO(Ches) complete this
    vec3 color = hashIntToColor(uint(quadID));
    // blue
    outColor = vec4(0, 0, 1, 1.0);
}

void draw_line_arc(Command command, vec2 fragPos) {
    //TODO(Ches) complete this
    vec3 color = hashIntToColor(uint(quadID));
    // green
    outColor = vec4(0, 1, 0, 1.0);
}

void draw_line_bezier(Command command, vec2 fragPos) {
    //TODO(Ches) complete this
    vec3 color = hashIntToColor(uint(quadID));
    // teal
    outColor = vec4(0, 1, 1, 1.0);
}

void draw_line_straight(Command command, vec2 fragPos) {
    //TODO(Ches) complete this
    vec3 color = hashIntToColor(uint(quadID));
    // purple
    outColor = vec4(1, 0, 1, 1.0);
}

void draw_rectangle(Command command, vec2 fragPos) {
    bool textured = command.style == ELEMENT_STYLE_TEXTURE;
    bool has_border = command.style == ELEMENT_STYLE_ONLY_BORDER || command.style == ELEMENT_STYLE_FILL_AND_BORDER;
    bool has_fill = command.style == ELEMENT_STYLE_ONLY_FILL || command.style == ELEMENT_STYLE_FILL_AND_BORDER;

    if (textured) {
        //TODO(ches) textures
        return;
    }
    // We know there's one
    if (command.pointCount != 1) {
        outColor = ERROR_COLOR;
        return;
    }
    Point point = points[command.pointIndex];
    vec2 pos = normalizePoint(point.pos);
    vec2 size = normalizePoint(point.misc);

    float dist = distance(pos, fragPos);

    float clamped = clamp(dist, 0, 1);
    outColor = vec4(1 - clamped, 1 - clamped, 1 - clamped, 1) ;
}

void draw_polygon(Command command, vec2 fragPos) {
    //TODO(Ches) complete this
    vec3 color = hashIntToColor(uint(quadID));
    // yellow
    outColor = vec4(1, 1, 0, 1.0);
}

void draw_text(Command command, vec2 fragPos) {
    //TODO(Ches) complete this
    vec3 color = hashIntToColor(uint(quadID));
    // white
    outColor = vec4(1, 1, 1, 1.0);
}

void main() {
    Command command = commands[quadID];

    vec2 fragPos = normalizePoint(gl_FragCoord.xy);

    switch (command.type) {
        case ELEMENT_TYPE_CIRCLE:
            draw_circle(command, fragPos);
            break;
        case ELEMENT_TYPE_LINE_ARC:
            draw_line_arc(command, fragPos);
            break;
        case ELEMENT_TYPE_LINE_BEZIER:
            draw_line_bezier(command, fragPos);
            break;
        case ELEMENT_TYPE_LINE_STRAIGHT:
            draw_line_straight(command, fragPos);
            break;
        case ELEMENT_TYPE_RECTANGLE:
            draw_rectangle(command, fragPos);
            break;
        case ELEMENT_TYPE_POLYGON:
            draw_polygon(command, fragPos);
            break;
        case ELEMENT_TYPE_TEXT:
            draw_text(command, fragPos);
            break;
        default:
            // Whoops
            outColor = ERROR_COLOR;
            return;
    }

}