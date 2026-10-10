package com.ikalagaming.graphics.vulkan;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.graph.CascadeShadowSplit;
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.gui.util.Color;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.scene.debug.DebugArrow;
import com.ikalagaming.graphics.scene.debug.DebugBox;
import com.ikalagaming.graphics.scene.debug.DebugCone;
import com.ikalagaming.graphics.scene.debug.DebugFrustum;
import com.ikalagaming.graphics.scene.debug.DebugLine;
import com.ikalagaming.graphics.scene.debug.DebugShape;
import com.ikalagaming.graphics.scene.debug.DebugSphere;
import com.ikalagaming.graphics.scene.debug.DebugVisualizers;
import com.ikalagaming.graphics.scene.lights.PointLight;
import com.ikalagaming.graphics.scene.lights.SpotLight;

import lombok.NonNull;
import org.joml.Matrix4d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.function.Consumer;

/** Makes the shapes for the built-in debug visualizers that are turned on. */
public final class DebugVisualizerShapes {

    /**
     * How far point and spot lights reach. Must match MAX_LIGHT_DISTANCE in lights.frag, where the
     * falloff reaches zero.
     */
    public static final double LIGHT_RANGE = 10;

    /** Half the length of each arm of the cross marking a light's position. */
    private static final double LIGHT_MARKER_SIZE = 0.25;

    /** How far in front of the camera the directional light arrow is drawn. */
    private static final double DIRECTIONAL_ARROW_DISTANCE = 5;

    /** The length of the directional light arrow. */
    private static final double DIRECTIONAL_ARROW_LENGTH = 2;

    /** Color for entity bounds. */
    private static final int BOUNDS_COLOR = Color.rgba(0, 255, 128, 255);

    /** Color for the directional light arrow. */
    private static final int DIRECTIONAL_COLOR = Color.rgba(255, 255, 0, 255);

    /** Color for the frozen observer. */
    private static final int OBSERVER_COLOR = Color.rgba(255, 0, 255, 255);

    /** Colors for each shadow cascade, nearest first. */
    private static final int[] CASCADE_COLORS = {
        Color.rgba(255, 64, 64, 255), Color.rgba(64, 255, 64, 255), Color.rgba(64, 64, 255, 255),
    };

    /**
     * Make the shapes for every visualizer that is on.
     *
     * @param scene The scene.
     * @param frameData The current frame's data, for the shadow cascades.
     * @param consumer Receives each shape.
     */
    public static void collect(
            @NonNull Scene scene,
            @NonNull PerFrameData frameData,
            @NonNull Consumer<DebugShape> consumer) {
        final DebugVisualizers settings = scene.getDebugVisualizers();
        if (settings.isPointLights()) {
            for (PointLight light : scene.getSceneLights().getPointLights()) {
                pointLight(light, consumer);
            }
        }
        if (settings.isSpotLights()) {
            for (SpotLight light : scene.getSceneLights().getSpotLights()) {
                spotLight(light, consumer);
            }
        }
        if (settings.isDirectionalLight()) {
            directionalLight(scene, consumer);
        }
        if (settings.isEntityBounds()) {
            entityBounds(scene, consumer);
        }
        if (settings.isShadowCascades() && frameData.cascadeShadowSplits != null) {
            shadowCascades(scene, frameData.cascadeShadowSplits, consumer);
        }
        if (settings.isObserverFrustum() && scene.isObserverFrozen()) {
            consumer.accept(scene.getObserver().frustum(OBSERVER_COLOR, true));
        }
    }

    private static int colorOf(@NonNull Vector3fc color) {
        return Color.rgba(color.x(), color.y(), color.z(), 1.0f);
    }

    private static void pointLight(@NonNull PointLight light, @NonNull Consumer<DebugShape> out) {
        final int color = colorOf(light.getColor());
        final Vector3dc position = light.getPosition();
        marker(position, color, out);
        out.accept(new DebugSphere(position, LIGHT_RANGE, color));
    }

    private static void spotLight(@NonNull SpotLight light, @NonNull Consumer<DebugShape> out) {
        final PointLight pointLight = light.getPointLight();
        final int color = colorOf(pointLight.getColor());
        final Vector3dc position = pointLight.getPosition();
        final Vector3fc direction = light.getConeDirection();
        marker(position, color, out);
        out.accept(
                new DebugCone(
                        position,
                        new Vector3d(direction.x(), direction.y(), direction.z()),
                        LIGHT_RANGE,
                        Math.acos(light.getCutOff()),
                        color));
    }

    /**
     * A small cross marking a point, drawn on top so it shows through geometry.
     *
     * @param position The point.
     * @param color The packed RGBA color.
     * @param out Receives the lines.
     */
    private static void marker(
            @NonNull Vector3dc position, int color, @NonNull Consumer<DebugShape> out) {
        final double s = LIGHT_MARKER_SIZE;
        final double x = position.x();
        final double y = position.y();
        final double z = position.z();
        out.accept(
                new DebugLine(new Vector3d(x - s, y, z), new Vector3d(x + s, y, z), color, true));
        out.accept(
                new DebugLine(new Vector3d(x, y - s, z), new Vector3d(x, y + s, z), color, true));
        out.accept(
                new DebugLine(new Vector3d(x, y, z - s), new Vector3d(x, y, z + s), color, true));
    }

    private static void directionalLight(@NonNull Scene scene, @NonNull Consumer<DebugShape> out) {
        Vector3f forward = new Vector3f();
        scene.getCamera().getInvViewMatrix().transformDirection(0, 0, -1, forward);
        Vector3f direction = new Vector3f(scene.getSceneLights().getDirLight().getDirection());
        if (direction.lengthSquared() == 0) {
            return;
        }
        direction.normalize();
        Vector3d center =
                new Vector3d(forward.x, forward.y, forward.z)
                        .mul(DIRECTIONAL_ARROW_DISTANCE)
                        .add(scene.getCamera().getPosition());
        Vector3d offset =
                new Vector3d(direction.x, direction.y, direction.z)
                        .mul(DIRECTIONAL_ARROW_LENGTH / 2);
        out.accept(
                new DebugArrow(
                        new Vector3d(center).sub(offset),
                        new Vector3d(center).add(offset),
                        DIRECTIONAL_COLOR,
                        true));
    }

    private static void entityBounds(@NonNull Scene scene, @NonNull Consumer<DebugShape> out) {
        final VulkanInstance renderer = GraphicsManager.getRenderInstance();
        if (renderer == null || renderer.getState().instances == null) {
            return;
        }
        final InstanceRegistry instances = renderer.getState().instances.getRegistry();
        Vector3d center = new Vector3d();
        Vector3d halfExtents = new Vector3d();
        Quaterniond rotation = new Quaterniond();
        for (Model model : scene.getModelMap().values()) {
            instances.visitTransforms(
                    model,
                    (slot, alive, position, instanceRotation, scale) -> {
                        rotation.set(instanceRotation);
                        for (MeshData mesh : model.getMeshDataList()) {
                            Vector3fc min = mesh.getAabbMin();
                            Vector3fc max = mesh.getAabbMax();
                            center.set(
                                    (min.x() + max.x()) * 0.5 * scale,
                                    (min.y() + max.y()) * 0.5 * scale,
                                    (min.z() + max.z()) * 0.5 * scale);
                            rotation.transform(center);
                            center.add(position);
                            halfExtents.set(
                                    (max.x() - min.x()) * 0.5 * scale,
                                    (max.y() - min.y()) * 0.5 * scale,
                                    (max.z() - min.z()) * 0.5 * scale);
                            out.accept(
                                    new DebugBox(
                                            center, halfExtents, rotation, BOUNDS_COLOR, false));
                        }
                    });
        }
    }

    private static void shadowCascades(
            @NonNull Scene scene,
            @NonNull CascadeShadowSplit[] cascades,
            @NonNull Consumer<DebugShape> out) {
        // TEMP cascade freeze
        if (TEMP_FREEZE && TEMP_CACHE != null) {
            TEMP_CACHE.forEach(out);
            return;
        }
        java.util.List<DebugShape> made = new java.util.ArrayList<>();
        final Vector3dc camera = scene.getCamera().getPosition();
        for (int i = 0; i < cascades.length; ++i) {
            if (cascades[i] == null) {
                continue;
            }
            // The cascades are in render space, so move them back to world space
            Matrix4d inverse =
                    new Matrix4d(cascades[i].getProjViewMatrix())
                            .invert()
                            .translateLocal(camera.x(), camera.y(), camera.z());
            DebugShape shape =
                    DebugFrustum.fromInverseProjectionView(
                            inverse, CASCADE_COLORS[i % CASCADE_COLORS.length], true);
            made.add(shape);
            out.accept(shape);
        }
        TEMP_CACHE = made;
    }

    // TEMP cascade freeze
    public static volatile boolean TEMP_FREEZE;
    private static volatile java.util.List<DebugShape> TEMP_CACHE;

    private DebugVisualizerShapes() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
