#ifndef ENVIRONMENT_GLSL
#define ENVIRONMENT_GLSL

// The light the sky gives, see Environment: its GGX prefiltered cube by roughness and its spherical
// harmonics, in the sky's own radiance. Include with ENVIRONMENT_SET defined.

// Match Environment
const int ENVIRONMENT_LEVELS = 6;
const int ENVIRONMENT_HARMONICS = 9;
// Rec. 709 luminance, to scale the sky by its brightness without changing its color
const vec3 ENVIRONMENT_LUMINANCE = vec3(0.2126, 0.7152, 0.0722);

layout(set = ENVIRONMENT_SET, binding = 3) uniform samplerCube environmentCube;
layout(std430, set = ENVIRONMENT_SET, binding = 4) readonly buffer EnvironmentHarmonics {
    vec4 environmentHarmonics[ENVIRONMENT_HARMONICS];
};

// The sky's average radiance, from the first coefficient: the integral of a function times Y00
// over the sphere, divided by 4 pi and by Y00 again
vec3 environmentAverage() {
    return environmentHarmonics[0].rgb * 0.282095;
}

// The irradiance on a surface facing a world direction, over pi: the radiance that, the same from
// every direction, would light it as much. Ramamoorthi and Hanrahan's convolution of the
// coefficients with the cosine lobe.
vec3 environmentDiffuse(vec3 n) {
    const float A0 = 1.0;
    const float A1 = 2.0 / 3.0;
    const float A2 = 0.25;
    vec3 e = A0 * 0.282095 * environmentHarmonics[0].rgb
        + A1 * 0.488603 * (environmentHarmonics[1].rgb * n.y
            + environmentHarmonics[2].rgb * n.z
            + environmentHarmonics[3].rgb * n.x)
        + A2 * (1.092548 * (environmentHarmonics[4].rgb * n.x * n.y
                + environmentHarmonics[5].rgb * n.y * n.z
                + environmentHarmonics[7].rgb * n.x * n.z)
            + 0.315392 * environmentHarmonics[6].rgb * (3.0 * n.z * n.z - 1.0)
            + 0.546274 * environmentHarmonics[8].rgb * (n.x * n.x - n.y * n.y));
    // A strong second band can ring below zero opposite a bright sky
    return max(e, vec3(0));
}

// The sky reflected toward a world direction by a surface of a roughness
vec3 environmentSpecular(vec3 r, float roughness) {
    return textureLod(environmentCube, r, roughness * float(ENVIRONMENT_LEVELS - 1)).rgb;
}

#endif
