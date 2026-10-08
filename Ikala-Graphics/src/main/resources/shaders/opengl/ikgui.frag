#version 460
#extension GL_ARB_bindless_texture : require

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

const vec4 ERROR_COLOR =  vec4(1.0f, 0.0f, 0.0f, 1.0f);

struct Point
{
    vec2 pos;
    vec2 misc;
};

struct PointDetail
{
    float radius;
    float alphaRadius;
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
// The top left of the viewport being rendered, since points are in absolute coordinates
uniform vec2 displayPosition;
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

// Bindless handles for the textures used by draw commands, indexed by texture ID.
layout(std430, binding = 3) buffer TextureHandles {
	uvec2 textureHandles[];
};

vec4 sampleTexture(int textureIndex, vec2 uv) {
    return texture(sampler2D(textureHandles[textureIndex]), uv);
}

float hash11(uint n) {
    n = (n << 13u) ^ n;
    n = n * (n * n * 15731u + 789221u) + 1376312589u;
    return float(n & uvec3(0x7fffffffu)) / float(0x7fffffff);
}

vec4 hashIntToColor(uint id) {
    float r = hash11(id);
    float g = hash11(id + 1u);
    float b = hash11(id + 2u);
    return vec4(r, g, b, 0.3f);
}

vec4 intToColor(uint color) {
    uint r = (color >> 24) & 0xFF;
    uint g = (color >> 16) & 0xFF;
    uint b = (color >> 8) & 0xFF;
    uint a = color & 0xFF;

    return vec4(r/255.0f, g/255.0f, b/255.0f, a/255.0f);
}

vec4 colorAndTint(int color, int tint) {
    vec4 result = intToColor(uint(color)) + intToColor(uint(tint));
    return clamp(result, 0.0f, 1.0f);
}

const float PI = 3.14159265358979f;
const float TWO_PI = 6.28318530717959f;

// How many line segments we use to approximate cubic bezier curves
const int CUBIC_BEZIER_SAMPLES = 32;

// Fraction of the pixel covered by a shape, given the signed distance to its edge. This gives us
// roughly one pixel of anti-aliasing on the edges.
float coverage(float sdf) {
    return clamp(0.5f - sdf, 0.0f, 1.0f);
}

// Alpha multiplier for blending to transparent. Positive radius fades from an opaque center to a
// transparent edge, negative fades from an opaque edge to a transparent center, 0 does nothing.
float alphaRadiusMultiplier(float sdf, float alphaRadius) {
    if (alphaRadius > 0) {
        return clamp(-sdf / alphaRadius, 0.0f, 1.0f);
    }
    if (alphaRadius < 0) {
        return 1.0f - clamp(sdf / alphaRadius, 0.0f, 1.0f);
    }
    return 1.0f;
}

vec4 detailColor(PointDetail detail) {
    return colorAndTint(detail.colorOrTextureID, detail.tint);
}

// Shade a closed shape given its signed distance, according to the command style.
void shadeShape(Command command, float sdf, vec4 color, float alphaRadius) {
    switch (command.style) {
        case ELEMENT_STYLE_FILL:
            outColor = color;
            outColor.a *= coverage(sdf) * alphaRadiusMultiplier(sdf, alphaRadius);
            break;
        case ELEMENT_STYLE_BORDER:
            outColor = color;
            outColor.a *= coverage(abs(sdf) - command.stroke / 2);
            break;
        case ELEMENT_STYLE_TEXTURE:
            //TODO(ches) textures
            outColor = vec4(1, 0, 1, 1.0);
            break;
        default:
            outColor = ERROR_COLOR;
            break;
    }
}

// Distance from a point to a line segment, with t set to how far along the segment the closest
// point is (0 to 1).
float sdSegment(vec2 pos, vec2 a, vec2 b, out float t) {
    const vec2 pa = pos - a;
    const vec2 ba = b - a;
    const float lengthSquared = dot(ba, ba);
    t = lengthSquared > 0 ? clamp(dot(pa, ba) / lengthSquared, 0.0f, 1.0f) : 0.0f;
    return length(pa - ba * t);
}

// Signed distance to an arbitrary (possibly concave) polygon, negative inside.
float sdPolygon(vec2 pos, int pointIndex, int pointCount) {
    vec2 first = points[pointIndex].pos;
    float distanceSquared = dot(pos - first, pos - first);
    float windingSign = 1.0f;
    for (int i = 0, j = pointCount - 1; i < pointCount; j = i, i++) {
        const vec2 vi = points[pointIndex + i].pos;
        const vec2 vj = points[pointIndex + j].pos;
        const vec2 e = vj - vi;
        const vec2 w = pos - vi;
        const float edgeLengthSquared = dot(e, e);
        const vec2 b = edgeLengthSquared > 0
            ? w - e * clamp(dot(w, e) / edgeLengthSquared, 0.0f, 1.0f)
            : w;
        distanceSquared = min(distanceSquared, dot(b, b));
        // Winding test, flipping the sign every time we cross an edge
        const bvec3 c = bvec3(pos.y >= vi.y, pos.y < vj.y, e.x * w.y > e.y * w.x);
        if (all(c) || all(not(c))) {
            windingSign *= -1.0f;
        }
    }
    return windingSign * sqrt(distanceSquared);
}

// Distance to a quadratic bezier curve, analytically.
float sdBezierQuadratic(vec2 pos, vec2 p0, vec2 p1, vec2 p2) {
    const vec2 a = p1 - p0;
    const vec2 b = p0 - 2.0f * p1 + p2;
    if (dot(b, b) < 0.0001f) {
        // Degenerate (straight) curve
        float t;
        return sdSegment(pos, p0, p2, t);
    }
    const vec2 c = a * 2.0f;
    const vec2 d = p0 - pos;
    const float kk = 1.0f / dot(b, b);
    const float kx = kk * dot(a, b);
    const float ky = kk * (2.0f * dot(a, a) + dot(d, b)) / 3.0f;
    const float kz = kk * dot(d, a);

    float result;
    const float p = ky - kx * kx;
    const float p3 = p * p * p;
    const float q = kx * (2.0f * kx * kx - 3.0f * ky) + kz;
    float h = q * q + 4.0f * p3;
    if (h >= 0.0f) {
        h = sqrt(h);
        const vec2 x = (vec2(h, -h) - q) / 2.0f;
        const vec2 uv = sign(x) * pow(abs(x), vec2(1.0f / 3.0f));
        const float t = clamp(uv.x + uv.y - kx, 0.0f, 1.0f);
        const vec2 offset = d + (c + b * t) * t;
        result = dot(offset, offset);
    } else {
        const float z = sqrt(-p);
        const float v = acos(q / (p * z * 2.0f)) / 3.0f;
        const float m = cos(v);
        const float n = sin(v) * 1.732050808f;
        const vec3 t = clamp(vec3(m + m, -n - m, n - m) * z - kx, 0.0f, 1.0f);
        const vec2 offset1 = d + (c + b * t.x) * t.x;
        const vec2 offset2 = d + (c + b * t.y) * t.y;
        result = min(dot(offset1, offset1), dot(offset2, offset2));
    }
    return sqrt(result);
}

vec2 bezierCubic(vec2 p0, vec2 p1, vec2 p2, vec2 p3, float t) {
    const float u = 1.0f - t;
    return u * u * u * p0 + 3.0f * u * u * t * p1 + 3.0f * u * t * t * p2 + t * t * t * p3;
}

// Distance to a cubic bezier curve, approximated with line segments.
float sdBezierCubic(vec2 pos, vec2 p0, vec2 p1, vec2 p2, vec2 p3) {
    float result = 1e20f;
    vec2 previous = p0;
    float t;
    for (int i = 1; i <= CUBIC_BEZIER_SAMPLES; ++i) {
        const vec2 current = bezierCubic(p0, p1, p2, p3, float(i) / CUBIC_BEZIER_SAMPLES);
        result = min(result, sdSegment(pos, previous, current, t));
        previous = current;
    }
    return result;
}

// Circles and ellipses.
// Point 0: pos = center, misc = radius (x, y).
// Detail 0: color and alpha radius.
void draw_circle(Command command, vec2 fragPos) {
    if (command.pointCount != 1 || command.detailCount < 1) {
        outColor = ERROR_COLOR;
        return;
    }
    const Point point = points[command.pointIndex];
    const vec2 radii = max(point.misc, vec2(0.0001f));
    const vec2 posRelative = fragPos - point.pos;

    float sdf;
    if (radii.x == radii.y) {
        sdf = length(posRelative) - radii.x;
    } else {
        // Approximate ellipse distance, which is accurate near the edge
        const float k1 = length(posRelative / radii);
        const float k2 = length(posRelative / (radii * radii));
        sdf = k2 > 0 ? k1 * (k1 - 1.0f) / k2 : -min(radii.x, radii.y);
    }

    const PointDetail detail = pointDetails[command.detailIndex];
    shadeShape(command, sdf, detailColor(detail), detail.alphaRadius);
}

// Arcs of a circle, drawn as a line with round caps.
// Point 0: pos = center, misc.x = radius.
// Point 1: pos.x = start angle, pos.y = end angle (radians, clockwise on screen from +x).
// Details: 1 for a solid color, or 2 for a gradient from the start angle to the end angle.
// Stroke: line thickness.
void draw_line_arc(Command command, vec2 fragPos) {
    if (command.pointCount != 2 || command.detailCount < 1) {
        outColor = ERROR_COLOR;
        return;
    }
    const Point centerPoint = points[command.pointIndex];
    const Point anglePoint = points[command.pointIndex + 1];
    const vec2 center = centerPoint.pos;
    const float radius = centerPoint.misc.x;
    const float angleMin = min(anglePoint.pos.x, anglePoint.pos.y);
    const float angleMax = max(anglePoint.pos.x, anglePoint.pos.y);
    const float span = angleMax - angleMin;

    const vec2 posRelative = fragPos - center;
    float distance;
    const float angle = mod(atan(posRelative.y, posRelative.x) - angleMin, TWO_PI);
    // How far along the arc we are, from 0 (start) to 1 (end)
    float t;
    if (span >= TWO_PI || angle <= span) {
        // Within the arc, so the distance is to the circle
        distance = abs(length(posRelative) - radius);
        t = span > 0 ? angle / span : 0.0f;
    } else {
        // Outside the arc, so the distance is to the closest end point
        const vec2 start = radius * vec2(cos(angleMin), sin(angleMin));
        const vec2 end = radius * vec2(cos(angleMax), sin(angleMax));
        const float startDistance = length(posRelative - start);
        const float endDistance = length(posRelative - end);
        distance = min(startDistance, endDistance);
        t = startDistance < endDistance ? 0.0f : 1.0f;
    }

    vec4 color = detailColor(pointDetails[command.detailIndex]);
    if (command.detailCount >= 2) {
        color = mix(color, detailColor(pointDetails[command.detailIndex + 1]), t);
    }
    outColor = color;
    outColor.a *= coverage(distance - command.stroke / 2);
}

// Bezier curves, drawn as a line with round caps.
// Points: 3 for a quadratic curve, 4 for a cubic curve. Only pos is used.
// Detail 0: color.
// Stroke: line thickness.
void draw_line_bezier(Command command, vec2 fragPos) {
    if ((command.pointCount != 3 && command.pointCount != 4) || command.detailCount < 1) {
        outColor = ERROR_COLOR;
        return;
    }
    const vec2 p0 = points[command.pointIndex].pos;
    const vec2 p1 = points[command.pointIndex + 1].pos;
    const vec2 p2 = points[command.pointIndex + 2].pos;

    float distance;
    if (command.pointCount == 3) {
        distance = sdBezierQuadratic(fragPos, p0, p1, p2);
    } else {
        const vec2 p3 = points[command.pointIndex + 3].pos;
        distance = sdBezierCubic(fragPos, p0, p1, p2, p3);
    }

    outColor = detailColor(pointDetails[command.detailIndex]);
    outColor.a *= coverage(distance - command.stroke / 2);
}

// Straight line segments with round caps.
// Points 0 and 1: pos = end points.
// Details: 1 for a solid color, or 2 for a gradient from the first to second point.
// Stroke: line thickness.
void draw_line_straight(Command command, vec2 fragPos) {
    if (command.pointCount != 2 || command.detailCount < 1) {
        outColor = ERROR_COLOR;
        return;
    }
    const vec2 p0 = points[command.pointIndex].pos;
    const vec2 p1 = points[command.pointIndex + 1].pos;
    float t;
    const float distance = sdSegment(fragPos, p0, p1, t);

    vec4 color = detailColor(pointDetails[command.detailIndex]);
    if (command.detailCount >= 2) {
        color = mix(color, detailColor(pointDetails[command.detailIndex + 1]), t);
    }
    outColor = color;
    outColor.a *= coverage(distance - command.stroke / 2);
}

// How far outside a rectangle's rounded shape a pixel may be and still be filled, when it is inside
// the half-open bounds. This only absorbs floating point error along the flat edges, the half-open
// bounds test is what actually decides pixels on the edges.
const float FILL_EDGE_TOLERANCE = 0.01f;

// Rectangle fills have hard edges, so they use a half-open fill rule: a pixel exactly on the left
// or top edge is inside, and one exactly on the right or bottom edge is outside. When two
// rectangles share an edge, each pixel along it is filled by exactly one of them, so there are no
// gaps or double coverage. The edges are snapped to 1/256 of a pixel (like the GPU's own subpixel
// precision) so that both rectangles agree exactly on where a shared edge is, despite floating
// point error from reconstructing the edges from the center and size.
bool insideHalfOpen(vec2 fragPos, vec2 center, vec2 halfSize) {
    const vec2 minEdge = round((center - halfSize) * 256.0f) / 256.0f;
    const vec2 maxEdge = round((center + halfSize) * 256.0f) / 256.0f;
    return all(greaterThanEqual(fragPos, minEdge)) && all(lessThan(fragPos, maxEdge));
}

// The color of a rectangle at a position relative to its center, blended bilinearly between the
// colors of the four corners.
vec4 rectangleColor(Command command, vec2 posRelative, vec2 halfSize) {
    const vec4 topLeft = detailColor(pointDetails[command.detailIndex]);
    const vec4 topRight = detailColor(pointDetails[command.detailIndex + 1]);
    const vec4 bottomRight = detailColor(pointDetails[command.detailIndex + 2]);
    const vec4 bottomLeft = detailColor(pointDetails[command.detailIndex + 3]);
    const vec2 uv = clamp((posRelative + halfSize) / max(2 * halfSize, vec2(0.0001f)), 0.0f, 1.0f);
    return mix(mix(topLeft, topRight, uv.x), mix(bottomLeft, bottomRight, uv.x), uv.y);
}

// Rectangles with rounded corners.
// Point 0: pos = center, misc = size.
// Point 1 (optional, textures only): pos = (min u, min v), misc = (max u, max v).
// Details 0-3: rounding, alpha radius, color (or texture index for the texture style), and tint,
// for the top left, top right, bottom right, and bottom left corners. For the fill and border
// styles the colors are blended bilinearly between the corners.
// Stroke: border thickness for the border style.
void draw_rectangle(Command command, vec2 fragPos) {
    if (command.pointCount < 1 || command.pointCount > 2 || command.detailCount != 4) {
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
    PointDetail relevantDetail = posRelative.x < 0 ? detail0 : detail1;
    const float relevantRadius = relevantDetail.radius;

    const vec2 d = abs(posRelative) - halfSize + relevantRadius;
    const float sdf = length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - relevantRadius;

    vec2 alphaD = abs(posRelative) - halfSize;
    // Now find which corner is second closest, unless we are actually strictly diagonal
    const bool moreVerticalThanHorizontal = alphaD.y > alphaD.x;
    PointDetail adjacentDetail = relevantDetail;
    if (alphaD.x != alphaD.y) {
        // Reset the detail ordering
        detail0 = pointDetails[command.detailIndex];    //top left
        detail1 = pointDetails[command.detailIndex + 1];//top right
        detail2 = pointDetails[command.detailIndex + 2];//bottom right
        detail3 = pointDetails[command.detailIndex + 3];//bottom left

         if (posRelative.y < 0 && posRelative.x < 0) {
            // Top left
            adjacentDetail = moreVerticalThanHorizontal ? detail1 : detail3;
         }
         else if (posRelative.y < 0 && posRelative.x >= 0) {
            // Top right
            adjacentDetail = moreVerticalThanHorizontal ? detail0 : detail2;
         }
         else if (posRelative.y >= 0 && posRelative.x >= 0) {
            // Bottom right
            adjacentDetail = moreVerticalThanHorizontal ? detail3 : detail1;
         }
         else {
            // Bottom left
            adjacentDetail = moreVerticalThanHorizontal ? detail2 : detail0;
         }
    }
    const float roundedRadius = max(abs(relevantDetail.alphaRadius), abs(adjacentDetail.alphaRadius));
    alphaD = alphaD + roundedRadius;
    const float alphaSdf = length(max(alphaD, 0.0)) + min(max(alphaD.x, alphaD.y), 0.0) - roundedRadius;

    //TODO(ches) Fix the sharp line at non-rounded half-quadrants
    float relevantAlphaRadius = relevantDetail.alphaRadius;
    if (relevantAlphaRadius == 0 && adjacentDetail.alphaRadius != 0) {
        relevantAlphaRadius = adjacentDetail.alphaRadius;
    }
    float alphaMul = 1.0f;
    if (relevantAlphaRadius > 0) {
        alphaMul = clamp(-(alphaSdf / roundedRadius), 0, 1);
    }
    else if (relevantAlphaRadius < 0) {
        alphaMul = 1 - clamp(-(alphaSdf / roundedRadius), 0, 1);
    }
    // else 0, leave at 1.0f

    // Hard-edged fills (solid and textured) use the half-open fill rule, and the rounded shape for
    // the corners.
    const bool filled = sdf < FILL_EDGE_TOLERANCE && insideHalfOpen(fragPos, pos, halfSize);

    switch (command.style) {
        case ELEMENT_STYLE_FILL:
            if (filled) {
                outColor = rectangleColor(command, posRelative, halfSize);
                outColor.a *= alphaMul;
            }
            else {
                outColor = vec4(0, 0, 0, 0);
            }
            break;
        case ELEMENT_STYLE_BORDER:
            if (sdf >= -(borderStroke / 2) && sdf <= (borderStroke / 2)) {
                outColor = rectangleColor(command, posRelative, halfSize);
            }
            else {
                outColor = vec4(0, 0, 0, 0);
            }
            break;
        case ELEMENT_STYLE_TEXTURE:
            if (filled) {
                // Position within the rectangle, from 0 to 1
                vec2 uv = (posRelative + halfSize) / max(size, vec2(0.0001f));
                if (command.pointCount >= 2) {
                    // The texture coordinate range, (min u, min v) and (max u, max v)
                    const Point uvPoint = points[command.pointIndex + 1];
                    uv = mix(uvPoint.pos, uvPoint.misc, uv);
                }
                outColor = sampleTexture(relevantDetail.colorOrTextureID, uv)
                    * intToColor(uint(relevantDetail.tint));
                outColor.a *= alphaMul;
            }
            else {
                outColor = vec4(0, 0, 0, 0);
            }
            break;
        default:
            outColor = ERROR_COLOR;
            break;
    }
}

// Arbitrary polygons, convex or concave, with points in order.
// Points: at least 3, pos = the vertices.
// Detail 0: color and alpha radius.
// Stroke: border thickness for the border style.
float cross2d(vec2 a, vec2 b) {
    return a.x * b.y - a.y * b.x;
}

// Find the (u, v) parameters of a point within a quad with corners a, b, c, d in order, such that
// a is (0, 0), b is (1, 0), c is (1, 1), and d is (0, 1).
vec2 inverseBilinear(vec2 pos, vec2 a, vec2 b, vec2 c, vec2 d) {
    const vec2 e = b - a;
    const vec2 f = d - a;
    const vec2 g = a - b + c - d;
    const vec2 h = pos - a;

    const float k2 = cross2d(g, f);
    const float k1 = cross2d(e, f) + cross2d(h, g);
    const float k0 = cross2d(h, e);

    float v;
    if (abs(k2) < 0.001f) {
        // Edges are parallel, so this is linear
        v = k1 != 0 ? -k0 / k1 : 0.0f;
    } else {
        float w = k1 * k1 - 4.0f * k0 * k2;
        if (w < 0.0f) {
            return vec2(-1.0f);
        }
        w = sqrt(w);
        const float ik2 = 0.5f / k2;
        v = (-k1 - w) * ik2;
        if (v < 0.0f || v > 1.0f) {
            v = (-k1 + w) * ik2;
        }
    }
    // Solve for u using whichever axis is better conditioned
    const vec2 denominator = e + g * v;
    const float u = abs(denominator.x) > abs(denominator.y)
        ? (h.x - f.x * v) / denominator.x
        : (h.y - f.y * v) / denominator.y;
    return vec2(u, v);
}

// Texture coordinates for a point within a textured polygon, interpolated from the vertex
// texture coordinates. Triangles use barycentric coordinates, quads use bilinear coordinates.
vec2 polygonUV(Command command, vec2 pos) {
    const Point p0 = points[command.pointIndex];
    const Point p1 = points[command.pointIndex + 1];
    const Point p2 = points[command.pointIndex + 2];
    if (command.pointCount == 3) {
        const float area = cross2d(p1.pos - p0.pos, p2.pos - p0.pos);
        if (abs(area) < 0.0001f) {
            return p0.misc;
        }
        const float w1 = cross2d(pos - p0.pos, p2.pos - p0.pos) / area;
        const float w2 = cross2d(p1.pos - p0.pos, pos - p0.pos) / area;
        const float w0 = 1.0f - w1 - w2;
        return w0 * p0.misc + w1 * p1.misc + w2 * p2.misc;
    }
    if (command.pointCount == 4) {
        const Point p3 = points[command.pointIndex + 3];
        // Clamp so anti-aliased edge pixels still sample within the image
        const vec2 st = clamp(inverseBilinear(pos, p0.pos, p1.pos, p2.pos, p3.pos), 0.0f, 1.0f);
        return mix(mix(p0.misc, p1.misc, st.x), mix(p3.misc, p2.misc, st.x), st.y);
    }
    //TODO(ches) texture coordinates for polygons with more than 4 points
    return p0.misc;
}

// The color of a triangle at a position, blended between the colors of the three vertices using
// barycentric coordinates.
vec4 triangleColor(Command command, vec2 pos) {
    const vec2 p0 = points[command.pointIndex].pos;
    const vec2 p1 = points[command.pointIndex + 1].pos;
    const vec2 p2 = points[command.pointIndex + 2].pos;
    const vec4 c0 = detailColor(pointDetails[command.detailIndex]);
    const float area = cross2d(p1 - p0, p2 - p0);
    if (abs(area) < 0.0001f) {
        return c0;
    }
    const vec4 c1 = detailColor(pointDetails[command.detailIndex + 1]);
    const vec4 c2 = detailColor(pointDetails[command.detailIndex + 2]);
    // Clamp so anti-aliased edge pixels just outside the triangle don't extrapolate the colors
    float w1 = clamp(cross2d(pos - p0, p2 - p0) / area, 0.0f, 1.0f);
    float w2 = clamp(cross2d(p1 - p0, pos - p0) / area, 0.0f, 1.0f);
    const float total = w1 + w2;
    if (total > 1.0f) {
        w1 /= total;
        w2 /= total;
    }
    return (1.0f - w1 - w2) * c0 + w1 * c1 + w2 * c2;
}

// Arbitrary polygons, convex or concave, with points in order.
// Points: at least 3, pos = the vertices, misc = texture coordinates for the texture style.
// Detail 0: color (or texture index for the texture style), alpha radius, and tint.
// Details 1-2 (optional, triangles only): colors for the second and third vertices, so the color
// is blended between the vertices. Detail 0 is then the color of the first vertex.
// Stroke: border thickness for the border style.
void draw_polygon(Command command, vec2 fragPos) {
    if (command.pointCount < 3 || command.detailCount < 1) {
        outColor = ERROR_COLOR;
        return;
    }
    const float sdf = sdPolygon(fragPos, command.pointIndex, command.pointCount);
    const PointDetail detail = pointDetails[command.detailIndex];
    if (command.style == ELEMENT_STYLE_TEXTURE) {
        const vec2 uv = polygonUV(command, fragPos);
        outColor = sampleTexture(detail.colorOrTextureID, uv) * intToColor(uint(detail.tint));
        outColor.a *= coverage(sdf) * alphaRadiusMultiplier(sdf, detail.alphaRadius);
        return;
    }
    vec4 color = detailColor(detail);
    if (command.pointCount == 3 && command.detailCount >= 3) {
        color = triangleColor(command, fragPos);
    }
    shadeShape(command, sdf, color, detail.alphaRadius);
}

// Points: glyph top left with misc = (cos, sin) of the rotation, then the glyph location in the
// atlas with misc = size. An optional third point is a clip rect relative to the unrotated glyph
// top left, pos = min and misc = max.
// Detail 0: the tint (text color).
void draw_text(Command command, vec2 fragPos) {
    if ((command.pointCount != 2 && command.pointCount != 3) || command.detailCount != 1) {
        outColor = ERROR_COLOR;
        return;
    }

    Point quadPoint = points[command.pointIndex];
    Point textPoint = points[command.pointIndex + 1];
    const vec2 textPos = textPoint.pos;
    const vec2 textSize = textPoint.misc;

    PointDetail detail = pointDetails[command.detailIndex];

    // Undo the rotation to get the position within the glyph, in pixels. A rotation of (0, 0) is
    // treated as no rotation.
    vec2 rotation = quadPoint.misc;
    if (rotation == vec2(0.0f)) {
        rotation = vec2(1.0f, 0.0f);
    }
    const vec2 offset = fragPos - quadPoint.pos;
    const vec2 local = vec2(
        offset.x * rotation.x + offset.y * rotation.y,
        -offset.x * rotation.y + offset.y * rotation.x
    );

    // Rotated glyphs don't fill their quad, and may be clipped before rotating
    if (local.x < 0.0f || local.y < 0.0f || local.x > textSize.x || local.y > textSize.y) {
        discard;
    }
    if (command.pointCount == 3) {
        Point clipPoint = points[command.pointIndex + 2];
        if (local.x < clipPoint.pos.x || local.y < clipPoint.pos.y
                || local.x > clipPoint.misc.x || local.y > clipPoint.misc.y) {
            discard;
        }
    }

    // Fragment position within the glyph, from 0 to 1.
    const vec2 posInQuad = local / textSize;

    const vec2 textureSizeInt = textureSize(txtSampler, 0);
    const vec2 topLeftPosInTexture = vec2(textPos.x / textureSizeInt.x, textPos.y / textureSizeInt.y);
    const vec2 bottomRightPosInTexture = vec2(
        (textPos.x + textSize.x) / textureSizeInt.x,
        (textPos.y + textSize.y) / textureSizeInt.y
    );

    const vec2 texturePos = mix(topLeftPosInTexture, bottomRightPosInTexture, posInQuad);

    vec4 color = texture(txtSampler, texturePos) * intToColor(uint(detail.tint));
    outColor = clamp(color, 0.0f, 1.0f);
}

void main() {
    Command command = commands[quadID];

    // Fragment position in pixels but...
    // with y flipped to be 0 at the top left like our points expect, and offset by the viewport
    // position since points are in absolute coordinates
    vec2 fragPos = vec2(gl_FragCoord.x, (-2 / scale.y) - gl_FragCoord.y) + displayPosition;

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