#ifndef ROUGHENING_GLSL
#define ROUGHENING_GLSL

// Roughness that stands for normals blended away, see Roughening: a von Mises-Fisher lobe fit to
// the mean length r of the unit normals, kappa = (3r - r^3) / (1 - r^2), whose variance 2 / kappa
// adds to the microfacet lobe's alpha^2, alpha being the roughness squared. Match Roughening.

// The longest mean length trusted below 1, so kappa stays finite
const float ROUGHENING_MOST_ALIGNED = 0.99999;

float roughenBySpread(float roughness, float meanLength)
{
    float r = clamp(meanLength, 0.0, ROUGHENING_MOST_ALIGNED);
    if (r <= 0.0) {
        return 1.0;
    }
    float kappa = (3.0 * r - r * r * r) / (1.0 - r * r);
    float alpha = roughness * roughness;
    return min(1.0, sqrt(sqrt(alpha * alpha + 2.0 / kappa)));
}

#endif
