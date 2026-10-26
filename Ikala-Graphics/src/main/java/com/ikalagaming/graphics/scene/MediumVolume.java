package com.ikalagaming.graphics.scene;

import lombok.NonNull;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * A local volume of participating medium, like a column of smoke, a fog bank in a hollow, or steam
 * over water: a shape filled with a medium that stops, scatters and gives off light, drawn in the
 * fog volume with the scene's fog (see {@link Fog}). Its density can be broken up by drifting noise
 * and fades out over a soft edge.
 *
 * <p>Immutable: the {@code with} methods make changed copies.
 *
 * @param shape Its shape.
 * @param center Where its middle is, in the world.
 * @param rotation How it is turned, from its own frame to the world.
 * @param halfSize Its half size along its own x, y and z: a sphere's radius is x, a cylinder's
 *     radius x and half height y, along its own y.
 * @param density How much light it stops per meter inside its edge, its extinction.
 * @param albedo How much of each channel of the light it stops it scatters, 0 to 1; the rest is
 *     absorbed, which makes it dark like soot.
 * @param emission The light it gives off per unit of density, in linear light, like a glowing cloud
 *     over a fire.
 * @param edge How wide its soft edge is, in meters, inside its shape.
 * @param noiseScale How large the noise's features are, in meters.
 * @param noiseStrength How much the noise takes from its density, 0 for none to 1 for patches of
 *     nothing.
 * @param wind How fast the noise drifts, in meters a second, in the world.
 * @param anisotropy Which way it scatters light, its Henyey-Greenstein g: 0 the same every way,
 *     toward 1 mostly onward, as haze glows around the sun, toward -1 mostly back.
 */
public record MediumVolume(
        @NonNull Shape shape,
        @NonNull Vector3dc center,
        @NonNull Quaternionfc rotation,
        @NonNull Vector3fc halfSize,
        float density,
        @NonNull Vector3fc albedo,
        @NonNull Vector3fc emission,
        float edge,
        float noiseScale,
        float noiseStrength,
        @NonNull Vector3fc wind,
        float anisotropy) {

    /** A medium volume's shape, in its own frame around its middle. */
    public enum Shape {
        /** A box of its half size. */
        BOX,
        /** A sphere of radius half size x. */
        SPHERE,
        /** An upright cylinder along its y, of radius half size x and half height half size y. */
        CYLINDER
    }

    /** How dense a new volume is, per meter: thick smoke, a few meters to see through. */
    public static final float DEFAULT_DENSITY = 0.5f;

    /** How wide a new volume's soft edge is, in meters. */
    public static final float DEFAULT_EDGE = 1;

    /** How large a new volume's noise is, in meters. */
    public static final float DEFAULT_NOISE_SCALE = 4;

    /** How much a new volume's noise takes from its density. */
    public static final float DEFAULT_NOISE_STRENGTH = 0.5f;

    /** Which way a new volume scatters light: a little onward, as smoke does. */
    public static final float DEFAULT_ANISOTROPY = 0.2f;

    /** The most a volume's anisotropy can be either way, short of all one way. */
    public static final float MAX_ANISOTROPY = 0.95f;

    /**
     * Make defensive copies of the vectors and rotation.
     *
     * @param shape Its shape.
     * @param center Its middle.
     * @param rotation Its rotation.
     * @param halfSize Its half size.
     * @param density Its extinction.
     * @param albedo Its albedo.
     * @param emission Its emission.
     * @param edge Its soft edge's width.
     * @param noiseScale Its noise's size.
     * @param noiseStrength Its noise's strength.
     * @param wind Its noise's drift.
     * @param anisotropy Which way it scatters light.
     */
    public MediumVolume {
        center = new Vector3d(center);
        rotation = new Quaternionf(rotation);
        halfSize = new Vector3f(halfSize);
        albedo = new Vector3f(albedo);
        emission = new Vector3f(emission);
        wind = new Vector3f(wind);
        density = Math.max(0, density);
        edge = Math.max(0, edge);
        noiseScale = Math.max(Float.MIN_NORMAL, noiseScale);
        noiseStrength = Math.clamp(noiseStrength, 0, 1);
        anisotropy = Math.clamp(anisotropy, -MAX_ANISOTROPY, MAX_ANISOTROPY);
    }

    /**
     * A volume of gray smoke filling a shape, unturned, with the default density, edge, noise and
     * anisotropy, and no wind.
     *
     * @param shape The shape.
     * @param center Where its middle is, in the world.
     * @param halfSize Its half size, see {@link #halfSize()}.
     * @return The volume.
     */
    public static MediumVolume of(
            @NonNull Shape shape, @NonNull Vector3dc center, @NonNull Vector3fc halfSize) {
        return new MediumVolume(
                shape,
                center,
                new Quaternionf(),
                halfSize,
                DEFAULT_DENSITY,
                new Vector3f(0.7f),
                new Vector3f(),
                DEFAULT_EDGE,
                DEFAULT_NOISE_SCALE,
                DEFAULT_NOISE_STRENGTH,
                new Vector3f(),
                DEFAULT_ANISOTROPY);
    }

    /**
     * A copy moved somewhere else.
     *
     * @param newCenter Where its middle is, in the world.
     * @return The copy.
     */
    public MediumVolume withCenter(@NonNull Vector3dc newCenter) {
        return new MediumVolume(
                shape,
                newCenter,
                rotation,
                halfSize,
                density,
                albedo,
                emission,
                edge,
                noiseScale,
                noiseStrength,
                wind,
                anisotropy);
    }

    /**
     * A copy turned another way.
     *
     * @param newRotation From its own frame to the world.
     * @return The copy.
     */
    public MediumVolume withRotation(@NonNull Quaternionfc newRotation) {
        return new MediumVolume(
                shape,
                center,
                newRotation,
                halfSize,
                density,
                albedo,
                emission,
                edge,
                noiseScale,
                noiseStrength,
                wind,
                anisotropy);
    }

    /**
     * A copy of another size.
     *
     * @param newHalfSize Its half size, see {@link #halfSize()}.
     * @return The copy.
     */
    public MediumVolume withHalfSize(@NonNull Vector3fc newHalfSize) {
        return new MediumVolume(
                shape,
                center,
                rotation,
                newHalfSize,
                density,
                albedo,
                emission,
                edge,
                noiseScale,
                noiseStrength,
                wind,
                anisotropy);
    }

    /**
     * A copy with another medium.
     *
     * @param newDensity Its extinction, per meter.
     * @param newAlbedo Its albedo.
     * @param newEmission Its emission per unit of density.
     * @return The copy.
     */
    public MediumVolume withMedium(
            float newDensity, @NonNull Vector3fc newAlbedo, @NonNull Vector3fc newEmission) {
        return new MediumVolume(
                shape,
                center,
                rotation,
                halfSize,
                newDensity,
                newAlbedo,
                newEmission,
                edge,
                noiseScale,
                noiseStrength,
                wind,
                anisotropy);
    }

    /**
     * A copy with other noise and edge.
     *
     * @param newEdge Its soft edge's width, in meters.
     * @param newNoiseScale Its noise's feature size, in meters.
     * @param newNoiseStrength How much the noise takes from its density, 0 to 1.
     * @param newWind How fast the noise drifts, in meters a second.
     * @return The copy.
     */
    public MediumVolume withNoise(
            float newEdge,
            float newNoiseScale,
            float newNoiseStrength,
            @NonNull Vector3fc newWind) {
        return new MediumVolume(
                shape,
                center,
                rotation,
                halfSize,
                density,
                albedo,
                emission,
                newEdge,
                newNoiseScale,
                newNoiseStrength,
                newWind,
                anisotropy);
    }

    /**
     * A copy that scatters light another way.
     *
     * @param newAnisotropy Its Henyey-Greenstein g, see {@link #anisotropy()}.
     * @return The copy.
     */
    public MediumVolume withAnisotropy(float newAnisotropy) {
        return new MediumVolume(
                shape,
                center,
                rotation,
                halfSize,
                density,
                albedo,
                emission,
                edge,
                noiseScale,
                noiseStrength,
                wind,
                newAnisotropy);
    }

    /**
     * How far from its middle any of it reaches, for culling.
     *
     * @return The radius of a sphere around its middle holding all of it, in meters.
     */
    public double boundingRadius() {
        return switch (shape) {
            case BOX -> halfSize.length();
            case SPHERE -> halfSize.x();
            case CYLINDER -> Math.hypot(halfSize.x(), halfSize.y());
        };
    }
}
