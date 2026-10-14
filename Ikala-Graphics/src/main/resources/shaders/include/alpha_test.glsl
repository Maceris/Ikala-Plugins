#ifndef ALPHA_TEST_GLSL
#define ALPHA_TEST_GLSL

// Alpha tested cut outs, like leaves and plants. Up close they cut where their texture's alpha is
// under a half. Further away, where a pixel spans many texels, a smaller mip's alpha is the share
// of them that is covered; cutting it at a half wears faces away to slivers, or fills them in, by
// how sparse the texture is. So the cut blends, over the first few mips, into a threshold of its
// own for each texel, between 0 and 1 (hashed alpha testing, Wyman and McGuire 2017): a pixel is
// then covered as often as its texels are, its edges come out soft for TAA to blend, and the
// threshold stays with the texture, so a still picture doesn't flicker. Sections' blocks and their
// volume bricks both cut this way, so they agree.

// Where alpha cuts up close
const float ALPHA_CUT = 0.5;
// By which mip level the cut is each texel's own threshold
const float ALPHA_SOFT_MIPS = 2.0;

// The mip level a texture of a size is read at, from its coordinates' screen space gradients
float alphaTestMip(vec2 dx, vec2 dy, vec2 size) {
    vec2 x = dx * size;
    vec2 y = dy * size;
    return 0.5 * log2(max(max(dot(x, x), dot(y, y)), 1e-8));
}

// A full texel of a texture's own threshold, between 0 and 1 (a PCG hash)
float alphaTestHash(vec2 uv, vec2 size) {
    uvec2 texel = uvec2(ivec2(floor(fract(uv) * size)));
    uint h = texel.x * 747796405u + texel.y * 2891336453u + 277803737u;
    h = ((h >> ((h >> 28u) + 4u)) ^ h) * 277803737u;
    h = (h >> 22u) ^ h;
    return float(h) / 4294967296.0;
}

// Whether a texture's alpha at texture coordinates, read at a mip level, covers the pixel
bool alphaTestPasses(float alpha, float mip, vec2 uv, vec2 size) {
    float soft = clamp(mip / ALPHA_SOFT_MIPS, 0.0, 1.0);
    return alpha >= mix(ALPHA_CUT, alphaTestHash(uv, size), soft);
}

#endif
