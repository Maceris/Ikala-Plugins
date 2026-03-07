#version 460

const int ELEMENT_TYPE_CIRCLE = 0;
const int ELEMENT_TYPE_LINE_ARC = 1;
const int ELEMENT_TYPE_LINE_BEZIER = 2;
const int ELEMENT_TYPE_LINE_STRAIGHT = 3;
const int ELEMENT_TYPE_RECTANGLE = 4;
const int ELEMENT_TYPE_POLYGON = 5;
const int ELEMENT_TYPE_TEXT = 6;

const int ELEMENT_STYLE_FILL = 0;
const int ELEMENT_STYLE_BORDER = 1;
const int ELEMENT_STYLE_TEXTURE = 2;

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
    const bool textured = command.style == ELEMENT_STYLE_TEXTURE;
    const bool onlyBorder = command.style == ELEMENT_STYLE_BORDER;
    const bool onlyFill = command.style == ELEMENT_STYLE_FILL;

    if (textured) {
        //TODO(ches) textures
        return;
    }
    // We know there's one
    if (command.pointCount != 1 || command.detailCount != 4) {
        outColor = ERROR_COLOR;
        return;
    }
    Point point = points[command.pointIndex];
    const vec2 pos = point.pos;
    const vec2 size = point.misc;
    const float borderStroke = command.stroke;

    // Fragment position relative to the sdf (center) point.
    const vec2 posRelative = fragPos - pos;
    const vec2 halfSize = vec2(size.x / 2, size.y / 2);
    PointDetail detail0 = pointDetails[command.detailIndex];    //top left
    PointDetail detail1 = pointDetails[command.detailIndex + 1];//top right
    PointDetail detail2 = pointDetails[command.detailIndex + 2];//bottom right
    PointDetail detail3 = pointDetails[command.detailIndex + 3];//bottom left

    detail0 = posRelative.y < 0 ? detail0 : detail3;
    detail1 = posRelative.y < 0 ? detail1 : detail2;
    detail0 = posRelative.x > 0 ? detail1 : detail0;

    const float relevantRadius = detail0.radius;

    const vec2 d = abs(posRelative) - halfSize + relevantRadius;
    const float sdf = length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - relevantRadius;

    vec4 tint = intToColor(uint(detail0.tint));
    if (onlyBorder) {
        if (sdf >= -(borderStroke / 2) && sdf <= (borderStroke / 2)) {
            outColor = intToColor(uint(detail0.colorOrTextureID)) + tint;
        }
        else {
            outColor = vec4(0, 0, 0, 0) ;
        }
    }
    else if (onlyFill) {
        if (sdf < 0) {
            outColor = intToColor(uint(detail0.colorOrTextureID)) + tint;
        }
        else {
            outColor = vec4(0, 0, 0, 0) ;
        }
    }
    else if (textured) {
        //TODO(ches) textures
    }
    else {
        outColor = ERROR_COLOR;
    }

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

    // Fragment position in pixels but...
    // with y flipped to be 0 at the top left like our points expect
    vec2 fragPos = vec2(gl_FragCoord.x, (-2 / scale.y) - gl_FragCoord.y);

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