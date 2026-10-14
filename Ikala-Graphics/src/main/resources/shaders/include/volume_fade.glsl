#ifndef VOLUME_FADE_GLSL
#define VOLUME_FADE_GLSL

// The hand over from sections' own cut out and translucent blocks to their volume bricks, see
// VolumeMath: over a stretch of distance, a growing share of each pixel's places go to the brick,
// picked by a dither that changes each frame, so TAA blends the two. A section's blocks drop a
// place where the dither is under the fade there, and the brick marches only those places, so
// every place is drawn by exactly one of them.

// Interleaved gradient noise (Jimenez 2014): a different value for each pixel and frame
float volumeNoise(vec2 pixel, uint frame) {
    vec2 p = pixel + 5.588238 * float(frame % 64u);
    return fract(52.9829189 * fract(dot(p, vec2(0.06711056, 0.00583715))));
}

// The dither the hand over compares against, apart from the march's own noise
float volumeDither(vec2 pixel, uint frame) {
    return volumeNoise(pixel.yx + vec2(37.0, 17.0), frame);
}

// How much of a place at a distance goes to the brick, from 0 at the fade's start to 1 at its end
float volumeFade(float distance, float fadeStart, float fadeLength) {
    return clamp((distance - fadeStart) / fadeLength, 0.0, 1.0);
}

// Whether the brick draws a place, rather than the section's blocks
bool volumeTakes(vec2 pixel, uint frame, float distance, float fadeStart, float fadeLength) {
    return volumeDither(pixel, frame) < volumeFade(distance, fadeStart, fadeLength);
}

#endif
