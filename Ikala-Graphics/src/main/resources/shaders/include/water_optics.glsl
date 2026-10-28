#ifndef WATER_OPTICS_GLSL
#define WATER_OPTICS_GLSL

// Water's optics, see WaterMath for the same math in Java, with tests; keep them in step. Shared by
// the water's surface and the fog volume's water.

// Water's refractive index, and the light it reflects at normal incidence, WaterMath
const float WATER_INDEX = 1.333;
const float WATER_NORMAL_REFLECTANCE = 0.0203;

// The average cosine of the light coming down through water, which sets how fast the diffuse light
// fades with depth, (a + b_b) / this per meter
const float WATER_DOWNWELLING_COSINE = 0.8;

// The light a surface reflects at an angle, Schlick's approximation, see WaterMath.fresnel
float waterFresnel(float cosine) {
    float m = 1.0 - clamp(cosine, 0.0, 1.0);
    float m2 = m * m;
    return WATER_NORMAL_REFLECTANCE + (1.0 - WATER_NORMAL_REFLECTANCE) * m2 * m2 * m;
}

// A ray's cosine to the normal once it crosses into the water, see WaterMath.refractedCosine
float waterRefractedCosine(float cosine) {
    float c = clamp(cosine, 0.0, 1.0);
    float sine = sqrt(1.0 - c * c) / WATER_INDEX;
    return sqrt(1.0 - sine * sine);
}

// The factor in deep water's reflectance, WaterMath.REFLECTANCE_FACTOR
const float WATER_REFLECTANCE_FACTOR = 0.33;

// How much of the light coming down a column sends back up, not counting its bottom, see
// WaterMath.columnReflectance
vec3 waterColumnReflectance(vec3 absorption, vec3 backscattering, float down, float up) {
    vec3 attenuation = absorption + backscattering;
    vec3 deep = WATER_REFLECTANCE_FACTOR * backscattering / max(attenuation, vec3(1e-6));
    return deep * (1.0 - exp(-attenuation * max(down + up, 0.0)));
}

#endif
