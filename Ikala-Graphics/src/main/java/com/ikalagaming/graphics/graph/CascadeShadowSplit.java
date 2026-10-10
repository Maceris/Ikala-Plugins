package com.ikalagaming.graphics.graph;

import com.ikalagaming.graphics.Sections;
import com.ikalagaming.graphics.scene.Projection;
import com.ikalagaming.graphics.scene.Scene;

import lombok.Getter;
import lombok.NonNull;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;

/**
 * One of the sun's shadow cascades. The camera's view out to the shadow distance, which is the
 * scene's view distance, is split into {@link #SHADOW_MAP_CASCADE_COUNT} slices, nearest the
 * smallest, and each gets a shadow map fitted around it.
 *
 * <p>Each cascade is fitted around a sphere that holds its slice of the view, so its size depends
 * only on the slice, not on which way the camera faces, and turning doesn't change the texel size.
 * The sphere's center is snapped to the cascade's texel grid in world space, so as the camera moves
 * every texel stays put on the ground and the shadow edges don't shimmer. The shadow map reaches
 * {@link #CASTER_REACH} further toward the sun than the sphere, so what stands outside the slice
 * still casts into it.
 */
@Getter
public class CascadeShadowSplit {
    /** The number of partitions that we split the camera frustum into. */
    public static final int SHADOW_MAP_CASCADE_COUNT = 3;

    /** The width and height of each cascade's shadow map, in texels. */
    public static final int SHADOW_MAP_SIZE = 2048;

    /** The width of the shadow map in pixels. */
    public static final int SHADOW_MAP_WIDTH = SHADOW_MAP_SIZE;

    /** The height of the shadow map in pixels. */
    public static final int SHADOW_MAP_HEIGHT = SHADOW_MAP_SIZE;

    /**
     * How much the splits follow a logarithmic spacing rather than an even one, from 0 to 1.
     * Logarithmic keeps the texel size in step with the view's, so the near cascades get most of
     * the detail. From GPU Gems 3, chapter 10, Parallel-Split Shadow Maps.
     */
    public static final float SPLIT_LAMBDA = 0.95f;

    /**
     * How much further than a cascade's sphere its shadow map reaches toward the sun, in meters:
     * four sections, so terrain and trees standing outside the slice still cast into it.
     */
    public static final float CASTER_REACH = 4.0f * Sections.SECTION_SIZE;

    /**
     * What each cascade's radius is rounded up to, in meters, so rounding error in the slice's
     * corners never changes it from frame to frame.
     */
    private static final float RADIUS_STEP = 1 / 16f;

    /**
     * Texels left around each cascade's sphere: one for the snapping, two for the light stage's
     * filter.
     */
    private static final int MARGIN_TEXELS = 3;

    /**
     * The light projection and view matrix, from render space to the shadow map.
     *
     * @return The combined projection view matrix.
     */
    private final Matrix4f projViewMatrix;

    /**
     * The view space z of the far end of the cascade's slice, so negative.
     *
     * @return The split distance.
     */
    private float splitDistance;

    /**
     * How wide one texel of the shadow map is, in meters.
     *
     * @return The texel size.
     */
    private float texelSize;

    /** Create a new cascade shadow. */
    public CascadeShadowSplit() {
        projViewMatrix = new Matrix4f();
    }

    /**
     * How far the shadows reach.
     *
     * @param scene The scene.
     * @return The view distance, kept within the far plane, in meters.
     */
    public static double shadowDistance(@NonNull Scene scene) {
        return Math.min(scene.getViewDistance(), Projection.Z_FAR);
    }

    /**
     * Where each cascade's slice of the view ends, mixing logarithmic and even spacing by {@link
     * #SPLIT_LAMBDA}.
     *
     * @param shadowDistance How far the shadows reach, in meters.
     * @return The depth of each slice's far end, nearest first, the last at the shadow distance.
     */
    public static float[] splitDepths(double shadowDistance) {
        final double near = Projection.Z_NEAR;
        final double far = Math.max(shadowDistance, 2 * near);
        final float[] depths = new float[SHADOW_MAP_CASCADE_COUNT];
        for (int i = 0; i < SHADOW_MAP_CASCADE_COUNT; ++i) {
            final double power = (i + 1) / (double) SHADOW_MAP_CASCADE_COUNT;
            final double logarithmic = near * Math.pow(far / near, power);
            final double even = near + (far - near) * power;
            depths[i] = (float) (SPLIT_LAMBDA * (logarithmic - even) + even);
        }
        return depths;
    }

    /**
     * Fit the cascades for this frame.
     *
     * @param cascadeShadowSplits The cascades.
     * @param scene The scene.
     */
    public static void updateCascadeShadows(
            @NonNull CascadeShadowSplit @NonNull [] cascadeShadowSplits, @NonNull Scene scene) {
        update(
                cascadeShadowSplits,
                scene.getProjection().getInverseProjectionMatrix(),
                scene.getCamera().getInvViewMatrix(),
                scene.getCamera().getPosition(),
                scene.getSceneLights().getDirLight().getDirection(),
                shadowDistance(scene));
    }

    /**
     * Fit the cascades around a view.
     *
     * @param cascades The cascades to fill out.
     * @param inverseProjection The inverse of the camera's projection matrix.
     * @param inverseView The inverse of the camera's view matrix, from view space to render space.
     * @param cameraPosition The camera's world position, the render space origin.
     * @param lightDirection The way the sun's light travels.
     * @param shadowDistance How far the shadows reach, in meters.
     */
    public static void update(
            @NonNull CascadeShadowSplit @NonNull [] cascades,
            @NonNull Matrix4fc inverseProjection,
            @NonNull Matrix4fc inverseView,
            @NonNull Vector3dc cameraPosition,
            @NonNull Vector3fc lightDirection,
            double shadowDistance) {
        final float[] depths = splitDepths(shadowDistance);
        final Vector3d direction = new Vector3d(lightDirection);
        if (direction.lengthSquared() == 0) {
            direction.set(0, -1, 0);
        }
        direction.normalize();
        // Any up that isn't along the light will do; it only turns the shadow map
        final Vector3d up =
                Math.abs(direction.y) > 0.99 ? new Vector3d(0, 0, 1) : new Vector3d(0, 1, 0);
        final Matrix4d lightRotation = new Matrix4d().lookAlong(direction, up);
        final Matrix4d inverseLightRotation = lightRotation.invert(new Matrix4d());
        final Vector3f[] rays = cornerRays(inverseProjection);

        for (int i = 0; i < SHADOW_MAP_CASCADE_COUNT; ++i) {
            final float near = i == 0 ? Projection.Z_NEAR : depths[i - 1];
            final float far = depths[i];

            // The sphere around the slice, worked out in view space so turning can't change it
            final Vector3f center = new Vector3f();
            for (Vector3f ray : rays) {
                center.fma(near, ray).fma(far, ray);
            }
            center.div(2f * rays.length);
            float radius = 0;
            for (Vector3f ray : rays) {
                radius = Math.max(radius, new Vector3f(ray).mul(near).distance(center));
                radius = Math.max(radius, new Vector3f(ray).mul(far).distance(center));
            }
            radius = (float) Math.ceil(radius / RADIUS_STEP) * RADIUS_STEP;
            // Texels to spare on each side, since snapping moves the center by up to a texel and
            // the light stage's filter reads two texels around a point
            final float texelSize = 2 * radius / (SHADOW_MAP_SIZE - 2 * MARGIN_TEXELS);
            final float extent = radius + MARGIN_TEXELS * texelSize;

            // Snap the center to the texel grid in world space, in double precision
            inverseView.transformPosition(center);
            final Vector3d world = new Vector3d(center).add(cameraPosition);
            lightRotation.transformPosition(world);
            world.x = Math.floor(world.x / texelSize) * texelSize;
            world.y = Math.floor(world.y / texelSize) * texelSize;
            inverseLightRotation.transformPosition(world);
            final Vector3f snapped =
                    new Vector3f(
                            (float) (world.x - cameraPosition.x()),
                            (float) (world.y - cameraPosition.y()),
                            (float) (world.z - cameraPosition.z()));

            final float back = extent + CASTER_REACH;
            final Vector3f eye =
                    new Vector3f(snapped)
                            .sub(
                                    (float) (direction.x * back),
                                    (float) (direction.y * back),
                                    (float) (direction.z * back));
            /*
             * NOTE(ches) This has to be right handed to match the orthographic projection. With a left handed view,
             * surfaces closer to the light ended up with larger depth values, so the shadow map kept the surfaces
             * furthest from the light and shadows were drawn on top of the geometry casting them.
             */
            final Matrix4f lightView =
                    new Matrix4f()
                            .lookAt(
                                    eye,
                                    snapped,
                                    new Vector3f((float) up.x, (float) up.y, (float) up.z));
            final CascadeShadowSplit cascade = cascades[i];
            cascade.projViewMatrix
                    .setOrtho(-extent, extent, -extent, extent, 0, back + extent, true)
                    .mul(lightView);
            cascade.splitDistance = -far;
            cascade.texelSize = texelSize;
        }
    }

    /**
     * The view space rays through the corners of the screen, each reaching one meter in front of
     * the camera.
     *
     * @param inverseProjection The inverse of the camera's projection matrix.
     * @return The four rays.
     */
    private static Vector3f[] cornerRays(@NonNull Matrix4fc inverseProjection) {
        final Vector3f[] rays = new Vector3f[4];
        int corner = 0;
        for (int y = -1; y <= 1; y += 2) {
            for (int x = -1; x <= 1; x += 2) {
                // Any depth is on the same ray
                final Vector4f point = new Vector4f(x, y, 0.5f, 1).mul(inverseProjection);
                final Vector3f ray = new Vector3f(point.x, point.y, point.z).div(point.w);
                rays[corner++] = ray.div(-ray.z);
            }
        }
        return rays;
    }
}
