package com.ikalagaming.graphics.graph;

import com.ikalagaming.graphics.scene.Projection;
import com.ikalagaming.graphics.scene.Scene;

import lombok.Getter;
import lombok.NonNull;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;

/** Used for cascaded shadow mapping to balance quality and performance. */
@Getter
public class CascadeShadow {
    /** The number of partitions that we split the camera frustum into. */
    public static final int SHADOW_MAP_CASCADE_COUNT = 3;

    /**
     * We calculate the splits once, and use the same values every time we calculate the cascade
     * shadows.
     */
    private static final float[] cachedSplits = new float[CascadeShadow.SHADOW_MAP_CASCADE_COUNT];

    static {
        final float nearClip = Projection.Z_NEAR;
        final float farClip = Projection.Z_FAR;
        final float clipRange = farClip - nearClip;

        final float ratio = farClip / nearClip;

        final float cascadeSplitLambda = 0.95f;

        /*
         * Calculate split depths based on view camera frustum Based on method
         * presented in
         * https://developer.nvidia.com/gpugems/GPUGems3/gpugems3_ch10.html
         */
        for (int i = 0; i < CascadeShadow.SHADOW_MAP_CASCADE_COUNT; ++i) {
            float power = (i + 1) / (float) (CascadeShadow.SHADOW_MAP_CASCADE_COUNT);
            float logPart = (float) (nearClip * Math.pow(ratio, power));
            float uniform = nearClip + clipRange * power;
            float d = cascadeSplitLambda * (logPart - uniform) + uniform;
            cachedSplits[i] = (d - nearClip) / clipRange;
        }
    }

    /**
     * Update the cascade shadows for the scene.
     *
     * <p>Function are derived from Vulkan examples from Sascha Willems, and licensed under the MIT
     * License: https://github.com/SaschaWillems/Vulkan/tree/master/examples/shadowmappingcascade,
     * which are based on
     * https://johanmedestrom.wordpress.com/2016/03/18/opengl-cascaded-shadow-maps/
     *
     * @param cascadeShadows The cascade shadows.
     * @param scene The scene.
     */
    public static void updateCascadeShadows(
            @NonNull List<CascadeShadow> cascadeShadows, @NonNull Scene scene) {
        final Matrix4f viewMatrix = scene.getCamera().getViewMatrix();
        final Matrix4f projectionMatrix = scene.getProjection().getProjectionMatrix();
        final Vector3f lightDir = scene.getSceneLights().getDirLight().getDirection();

        final float nearClip = Projection.Z_NEAR;
        final float farClip = Projection.Z_FAR;
        final float clipRange = farClip - nearClip;

        // Calculate orthographic projection matrix for each cascade
        float lastSplitDistance = 0.0f;
        for (int i = 0; i < CascadeShadow.SHADOW_MAP_CASCADE_COUNT; ++i) {
            float splitDistance = cachedSplits[i];

            Vector3f[] frustumCorners = { //
                new Vector3f(-1.0f, 1.0f, -1.0f), //
                new Vector3f(1.0f, 1.0f, -1.0f), //
                new Vector3f(1.0f, -1.0f, -1.0f), //
                new Vector3f(-1.0f, -1.0f, -1.0f), //
                new Vector3f(-1.0f, 1.0f, 1.0f), //
                new Vector3f(1.0f, 1.0f, 1.0f), //
                new Vector3f(1.0f, -1.0f, 1.0f), //
                new Vector3f(-1.0f, -1.0f, 1.0f) //
            };

            // Project frustum corners into world space
            Matrix4f invertedCamera = (new Matrix4f(projectionMatrix).mul(viewMatrix)).invert();
            for (int j = 0; j < frustumCorners.length; ++j) {
                Vector4f invCorner = new Vector4f(frustumCorners[j], 1.0f).mul(invertedCamera);
                frustumCorners[j] =
                        new Vector3f(invCorner.x, invCorner.y, invCorner.z).div(invCorner.w);
            }

            for (int j = 0; j < frustumCorners.length / 2; ++j) {
                Vector3f dist = new Vector3f(frustumCorners[j + 4]).sub(frustumCorners[j]);
                frustumCorners[j + 4] =
                        new Vector3f(frustumCorners[j]).add(new Vector3f(dist).mul(splitDistance));
                frustumCorners[j] =
                        new Vector3f(frustumCorners[j])
                                .add(new Vector3f(dist).mul(lastSplitDistance));
            }

            // Get frustum center
            Vector3f frustumCenter = new Vector3f(0.0f);
            for (Vector3f frustumCorner : frustumCorners) {
                frustumCenter.add(frustumCorner);
            }
            frustumCenter.div(8.0f);

            float radius = 0.0f;
            for (Vector3f frustumCorner : frustumCorners) {
                float distance = (new Vector3f(frustumCorner).sub(frustumCenter)).length();
                radius = Math.max(radius, distance);
            }
            radius = (float) Math.ceil(radius * 16.0f) / 16.0f;

            Vector3f lightDirection = new Vector3f(lightDir).mul(-1).normalize();
            Vector3f eye = new Vector3f(frustumCenter).add(lightDirection.mul(radius));
            Vector3f up = new Vector3f(0.0f, 1.0f, 0.0f);
            Matrix4f lightViewMatrix = new Matrix4f().lookAtLH(eye, frustumCenter, up);
            Matrix4f lightOrthoMatrix =
                    new Matrix4f().ortho(-radius, radius, -radius, radius, 0, 2 * radius, true);

            // Store split distance and matrix in cascade
            CascadeShadow cascadeShadow = cascadeShadows.get(i);
            cascadeShadow.splitDistance = (nearClip + splitDistance * clipRange) * -1.0f;
            cascadeShadow.projViewMatrix = lightOrthoMatrix.mul(lightViewMatrix);

            lastSplitDistance = cachedSplits[i];
        }
    }

    /**
     * The light view and projection matrix.
     *
     * @return The combined projection view matrix.
     */
    private Matrix4f projViewMatrix;

    /**
     * The distance to the split that this slice represents.
     *
     * @return The split distance.
     */
    private float splitDistance;

    /** Create a new cascade shadow. */
    public CascadeShadow() {
        projViewMatrix = new Matrix4f();
    }
}
