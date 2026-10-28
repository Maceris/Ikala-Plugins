#ifndef TRANSLUCENT_SHADOW_GLSL
#define TRANSLUCENT_SHADOW_GLSL

// Shadows through see-through surfaces, like glass, see TranslucentShadowMath for the math in Java,
// with tests; keep them in step. Each sun cascade has a translucent map of moments: per texel, the
// absorbance of everything see-through along its line toward the sun, per color and as one number,
// and the power moments of the depths it was met at, weighted by it. Depths are the cascade's, 0 at
// the sun's end to 1.

// How far the moments are pulled toward an even spread's, TranslucentShadowMath.MOMENT_BIAS
const float TRANSLUCENT_MOMENT_BIAS = 3e-5;

// How little absorbance counts as nothing in the way, TranslucentShadowMath.MIN_ABSORBANCE
const float TRANSLUCENT_MIN_ABSORBANCE = 1e-4;

// How much of a texel's absorbance lies in front of a depth, from 0 to 1, from the moments of its
// depths over its total: Peters and Klein's 4 moment Hamburger bound, see
// TranslucentShadowMath.shareInFront
float translucentShareInFront(vec4 moments, float depth) {
    vec4 b = mix(moments, vec4(0.5), TRANSLUCENT_MOMENT_BIAS);
    float l32d22 = -b.x * b.y + b.z;
    float d22 = -b.x * b.x + b.y;
    float squaredVariance = -b.y * b.y + b.w;
    float d33d22 = squaredVariance * d22 - l32d22 * l32d22;
    float inverseD22 = 1.0 / d22;
    float l32 = l32d22 * inverseD22;
    vec3 c = vec3(1.0, depth, depth * depth);
    c.y -= b.x;
    c.z -= b.y + l32 * c.y;
    c.y *= inverseD22;
    c.z *= d22 / d33d22;
    c.y -= l32 * c.z;
    c.x -= dot(c.yz, b.xy);
    float p = c.y / c.z;
    float q = c.x / c.z;
    float root = sqrt(max(0.0, p * p * 0.25 - q));
    float z1 = -p * 0.5 - root;
    float z2 = -p * 0.5 + root;
    vec4 pick = z2 < depth ? vec4(z1, depth, 1.0, 1.0)
        : (z1 < depth ? vec4(depth, z1, 0.0, 1.0) : vec4(0.0));
    float quotient = (pick.x * z2 - b.x * (pick.x + z2) + b.y) / ((z2 - pick.y) * (depth - z1));
    return clamp(pick.z + pick.w * quotient, 0.0, 1.0);
}

#endif
