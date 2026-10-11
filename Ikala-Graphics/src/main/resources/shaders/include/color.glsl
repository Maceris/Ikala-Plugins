#ifndef COLOR_GLSL
#define COLOR_GLSL

// Colors are authored in sRGB (base colors, textures, fog); lighting is done in linear light, and
// the tone map stage encodes back to sRGB once at the end. Matches ToneMapMath.decode.
vec3 sRGBToLinear(vec3 color) {
    vec3 c = clamp(color, 0.0, 1.0);
    return mix(c / 12.92, pow((c + 0.055) / 1.055, vec3(2.4)), greaterThan(c, vec3(0.04045)));
}

#endif
