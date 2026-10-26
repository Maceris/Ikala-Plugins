package com.ikalagaming.graphics.vulkan;

import com.ikalagaming.graphics.scene.WaterBody;

import lombok.NonNull;

/**
 * The optics of water, see {@code water.glsl}: what a body of water's contents do to light at red,
 * green and blue, after the bio-optical model of Ma et al. 2016 ("A Real-Time Photo-Realistic
 * Rendering Algorithm of Ocean Color Based on Bio-Optical Model"), and the light a uniform column
 * of water sends back up, in closed form. Keep in step with the shader.
 *
 * <p>Red, green and blue stand for 650, 550 and 450 nm. Sources: pure water's absorption from Pope
 * and Fry 1997 and its scattering from Morel 1974; phytoplankton's absorption, 0.06 a*(l) C^0.65,
 * with a*(l) normalized to 1 at 440 nm from Prieur and Sathyendranath 1981, and particles'
 * scattering, 0.3 C^0.62 (550 / l), as Mobley's classic case 1 model has them; dissolved organic
 * matter's absorption falling as exp(-0.014 (l - 440)). Sediment's mass-specific absorption, 0.041
 * m^2/g at 443 nm falling as exp(-0.0123 (l - 443)) (Babin et al. 2003), and scattering, 0.5 m^2/g
 * (550 / l) (the low end of the 0.5 to 1.5 m^2/g measured for mineral particles), are typical
 * values; real sediments vary by a few times.
 */
public final class WaterMath {

    /** The wavelengths red, green and blue stand for, in nanometers. */
    public static final double[] WAVELENGTHS = {650, 550, 450};

    /** Pure water's absorption at red, green and blue, per meter (Pope and Fry 1997). */
    public static final double[] PURE_WATER_ABSORPTION = {0.340, 0.0565, 0.009_22};

    /** Pure seawater's scattering at 500 nm, per meter (Morel 1974). */
    public static final double PURE_WATER_SCATTERING = 0.002_88;

    /** How pure water's scattering falls with wavelength: (l / 500)^-4.32. */
    public static final double PURE_WATER_EXPONENT = -4.32;

    /** The wavelength pure water's scattering is given at, in nanometers. */
    public static final double PURE_WATER_REFERENCE = 500;

    /**
     * Phytoplankton's absorption spectrum at red, green and blue, normalized to 1 at 440 nm (Prieur
     * and Sathyendranath 1981, at 650, 550 and 450 nm).
     */
    public static final double[] CHLOROPHYLL_SPECIFIC = {0.356, 0.357, 0.944};

    /** The normalized phytoplankton absorption at 440 nm, where CDOM is given. */
    public static final double SPECIFIC_AT_440 = 1;

    /** Phytoplankton's absorption: this times the spectrum times the chlorophyll to a power. */
    public static final double PHYTOPLANKTON_SCALE = 0.06;

    /** The power of the chlorophyll in phytoplankton's absorption. */
    public static final double PHYTOPLANKTON_EXPONENT = 0.65;

    /** Particles' scattering at 550 nm: this times the chlorophyll to a power, per meter. */
    public static final double PARTICLE_SCATTERING = 0.3;

    /** The power of the chlorophyll in particles' scattering. */
    public static final double PARTICLE_EXPONENT = 0.62;

    /** The wavelength particles' and sediment's scattering are given at, in nanometers. */
    public static final double PARTICLE_REFERENCE = 550;

    /** The wavelength CDOM's absorption is given at, in nanometers. */
    public static final double CDOM_WAVELENGTH = 440;

    /** How fast CDOM's absorption falls with wavelength, per nanometer. */
    public static final double CDOM_SLOPE = 0.014;

    /** Case 1 water's CDOM absorption at 440 nm, over the phytoplankton's there. */
    public static final double CDOM_SHARE = 0.2;

    /** Sediment's absorption at 443 nm per gram a cubic meter, in square meters a gram. */
    public static final double SEDIMENT_ABSORPTION = 0.041;

    /** The wavelength sediment's absorption is given at, in nanometers. */
    public static final double SEDIMENT_WAVELENGTH = 443;

    /** How fast sediment's absorption falls with wavelength, per nanometer. */
    public static final double SEDIMENT_SLOPE = 0.0123;

    /** Sediment's scattering at 550 nm per gram a cubic meter, in square meters a gram. */
    public static final double SEDIMENT_SCATTERING = 0.5;

    /**
     * The share of their scattering particles send backward: Petzold's measured 0.0183, which a
     * Henyey-Greenstein phase function of g 0.92 also has.
     */
    public static final double PARTICLE_BACKSCATTER = 0.0183;

    /**
     * The share of its scattering pure water sends backward: half, as it scatters symmetrically.
     */
    public static final double WATER_BACKSCATTER = 0.5;

    /** The factor in the deep water reflectance, f in f b_b / (a + b_b). */
    public static final double REFLECTANCE_FACTOR = 0.33;

    /** Water's refractive index. */
    public static final double REFRACTIVE_INDEX = 1.333;

    /** Light reflected at normal incidence from water, ((n - 1) / (n + 1))^2. */
    public static final double NORMAL_REFLECTANCE =
            ((REFRACTIVE_INDEX - 1) / (REFRACTIVE_INDEX + 1))
                    * ((REFRACTIVE_INDEX - 1) / (REFRACTIVE_INDEX + 1));

    /** The channels: red, green and blue. */
    public static final int CHANNELS = 3;

    /**
     * What a body of water does to light, per channel.
     *
     * @param absorption How much light it absorbs per meter, red, green and blue.
     * @param scattering How much it scatters per meter.
     * @param backscattering How much it scatters backward per meter: half of pure water's, a sliver
     *     of particles'.
     */
    public record Optics(double[] absorption, double[] scattering, double[] backscattering) {

        /**
         * Its extinction, absorption plus scattering, in one channel.
         *
         * @param channel 0 for red, 1 for green, 2 for blue.
         * @return The extinction, per meter.
         */
        public double extinction(int channel) {
            return absorption[channel] + scattering[channel];
        }
    }

    /** Static helpers only. */
    private WaterMath() {}

    /**
     * Phytoplankton's absorption, 0.06 a*(l) C^0.65.
     *
     * @param chlorophyll The chlorophyll, in milligrams a cubic meter.
     * @param specific The normalized spectrum at the wavelength, a*(l).
     * @return The absorption, per meter.
     */
    public static double phytoplanktonAbsorption(double chlorophyll, double specific) {
        return PHYTOPLANKTON_SCALE
                * specific
                * Math.pow(Math.max(0, chlorophyll), PHYTOPLANKTON_EXPONENT);
    }

    /**
     * What a body of water does to light at red, green and blue: pure water, phytoplankton,
     * dissolved organic matter and sediment, added up.
     *
     * @param body The water.
     * @return Its optics.
     */
    public static Optics optics(@NonNull WaterBody body) {
        final double[] absorption = new double[CHANNELS];
        final double[] scattering = new double[CHANNELS];
        final double[] backscattering = new double[CHANNELS];
        for (int channel = 0; channel < CHANNELS; ++channel) {
            final double wavelength = WAVELENGTHS[channel];
            final double water =
                    PURE_WATER_SCATTERING
                            * Math.pow(wavelength / PURE_WATER_REFERENCE, PURE_WATER_EXPONENT);
            final double particles =
                    PARTICLE_SCATTERING
                                    * Math.pow(body.chlorophyll(), PARTICLE_EXPONENT)
                                    * PARTICLE_REFERENCE
                                    / wavelength
                            + SEDIMENT_SCATTERING
                                    * body.sediment()
                                    * PARTICLE_REFERENCE
                                    / wavelength;
            absorption[channel] =
                    PURE_WATER_ABSORPTION[channel]
                            + phytoplanktonAbsorption(
                                    body.chlorophyll(), CHLOROPHYLL_SPECIFIC[channel])
                            + body.cdom() * Math.exp(-CDOM_SLOPE * (wavelength - CDOM_WAVELENGTH))
                            + SEDIMENT_ABSORPTION
                                    * body.sediment()
                                    * Math.exp(
                                            -SEDIMENT_SLOPE * (wavelength - SEDIMENT_WAVELENGTH));
            scattering[channel] = water + particles;
            backscattering[channel] = water * WATER_BACKSCATTER + particles * PARTICLE_BACKSCATTER;
        }
        return new Optics(absorption, scattering, backscattering);
    }

    /**
     * How much light a water surface reflects at an angle, Schlick's approximation from {@link
     * #NORMAL_REFLECTANCE}.
     *
     * @param cosine The cosine between the light and the surface's normal, on the air side.
     * @return The share reflected, 0 to 1.
     */
    public static double fresnel(double cosine) {
        final double m = 1 - Math.clamp(cosine, 0, 1);
        final double m2 = m * m;
        return NORMAL_REFLECTANCE + (1 - NORMAL_REFLECTANCE) * m2 * m2 * m;
    }

    /**
     * The cosine of a ray's angle to the normal once it crosses from air into water, by Snell's
     * law.
     *
     * @param cosine The cosine in the air.
     * @return The cosine in the water, from about 0.66 for grazing light to 1.
     */
    public static double refractedCosine(double cosine) {
        final double c = Math.clamp(cosine, 0, 1);
        final double sine = Math.sqrt(1 - c * c) / REFRACTIVE_INDEX;
        return Math.sqrt(1 - sine * sine);
    }

    /**
     * How much of the light coming down into deep water it sends back up, its irradiance
     * reflectance just under the surface: 0.33 b_b / (a + b_b), from radiative transfer runs over a
     * wide range of waters (Gordon et al. 1975; Morel and Prieur 1977).
     *
     * @param absorption The water's absorption, per meter.
     * @param backscattering Its backscattering, per meter.
     * @return The reflectance, 0 to about 0.33.
     */
    public static double deepReflectance(double absorption, double backscattering) {
        final double attenuation = absorption + backscattering;
        return attenuation <= 0 ? 0 : REFLECTANCE_FACTOR * backscattering / attenuation;
    }

    /**
     * How much of the light coming down a column of water of some depth sends back up, not counting
     * its bottom, as the shallow water model of Maritorena, Morel and Gentili 1994 has it: the deep
     * reflectance, reached as the light's way down to the bottom and back up the view grows,
     * falling off by the diffuse attenuation a + b_b.
     *
     * @param absorption The water's absorption, per meter.
     * @param backscattering Its backscattering, per meter.
     * @param down How far the light goes down to the bottom, in meters: its depth over the light's
     *     cosine in the water.
     * @param up How far the view goes through the water to the bottom, in meters.
     * @return The reflectance of the water above the bottom.
     */
    public static double columnReflectance(
            double absorption, double backscattering, double down, double up) {
        final double attenuation = absorption + backscattering;
        return deepReflectance(absorption, backscattering)
                * (1 - Math.exp(-attenuation * Math.max(0, down + up)));
    }
}
