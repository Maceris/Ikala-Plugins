/*
 * NOTICE: This file is a modified version of contents from
 * https://github.com/lwjglgamedev/lwjglbook, which was licensed under Apache
 * v2.0. Changes have been made related to formatting, functionality, and
 * naming.
 */
package com.ikalagaming.graphics.graph;

import com.ikalagaming.graphics.scene.Scene;

import lombok.Getter;
import lombok.NonNull;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;

/**
 * Used for cascaded shadow mapping to balance quality and performance.
 */
@Getter
public class CascadeShadow {
	/**
	 * The number of partitions that we split the camera frustum into.
	 */
	public static final int SHADOW_MAP_CASCADE_COUNT = 3;

	/**
	 * <p>
	 * Update the cascade shadows for the scene.
	 * </p>
	 * <p>
	 * Function are derived from Vulkan examples from Sascha Willems, and
	 * licensed under the MIT License:
	 * https://github.com/SaschaWillems/Vulkan/tree/master/examples/shadowmappingcascade,
	 * which are based on
	 * https://johanmedestrom.wordpress.com/2016/03/18/opengl-cascaded-shadow-maps/
	 * </p>
	 *
	 * @param cascadeShadows The cascade shadows.
	 * @param scene The scene.
	 */
	public static void updateCascadeShadows(
		@NonNull List<CascadeShadow> cascadeShadows, @NonNull Scene scene) {
		Matrix4f viewMatrix = scene.getCamera().getViewMatrix();
		Matrix4f projMatrix = scene.getProjection().getProjMatrix();
		Vector4f lightPos = new Vector4f(
			scene.getSceneLights().getDirLight().getDirection(), 0);

		final float cascadeSplitLambda = 0.95f;

		float[] cascadeSplits =
			new float[CascadeShadow.SHADOW_MAP_CASCADE_COUNT];

		final float nearClip = projMatrix.perspectiveNear();
		final float farClip = projMatrix.perspectiveFar();
		final float clipRange = farClip - nearClip;

		final float minZ = nearClip;
		final float maxZ = nearClip + clipRange;

		final float range = maxZ - minZ;
		final float ratio = maxZ / minZ;

		/*
		 * Calculate split depths based on view camera frustum Based on method
		 * presented in
		 * https://developer.nvidia.com/gpugems/GPUGems3/gpugems3_ch10.html
		 */
		for (int i = 0; i < CascadeShadow.SHADOW_MAP_CASCADE_COUNT; ++i) {
			float power =
				(i + 1) / (float) (CascadeShadow.SHADOW_MAP_CASCADE_COUNT);
			float log = (float) (minZ * Math.pow(ratio, power));
			float uniform = minZ + range * power;
			float d = cascadeSplitLambda * (log - uniform) + uniform;
			cascadeSplits[i] = (d - nearClip) / clipRange;
		}

		// Calculate orthographic projection matrix for each cascade
		float lastSplitDistance = 0.0f;
		for (int i = 0; i < CascadeShadow.SHADOW_MAP_CASCADE_COUNT; ++i) {
			float splitDistance = cascadeSplits[i];

			Vector3f[] frustumCorners = new Vector3f[] {
				new Vector3f(-1.0f, 1.0f, -1.0f),
				new Vector3f(1.0f, 1.0f, -1.0f),
				new Vector3f(1.0f, -1.0f, -1.0f),
				new Vector3f(-1.0f, -1.0f, -1.0f),
				new Vector3f(-1.0f, 1.0f, 1.0f), new Vector3f(1.0f, 1.0f, 1.0f),
				new Vector3f(1.0f, -1.0f, 1.0f),
				new Vector3f(-1.0f, -1.0f, 1.0f)};

			// Project frustum corners into world space
			Matrix4f invertedCamera =
				(new Matrix4f(projMatrix).mul(viewMatrix)).invert();
			for (int j = 0; j < frustumCorners.length; ++j) {
				Vector4f invCorner =
					new Vector4f(frustumCorners[j], 1.0f).mul(invertedCamera);
				frustumCorners[j] = new Vector3f(invCorner.x / invCorner.w,
					invCorner.y / invCorner.w, invCorner.z / invCorner.w);
			}

			for (int j = 0; j < frustumCorners.length / 2; ++j) {
				Vector3f dist =
					new Vector3f(frustumCorners[j + 4]).sub(frustumCorners[j]);
				frustumCorners[j + 4] = new Vector3f(frustumCorners[j])
					.add(new Vector3f(dist).mul(splitDistance));
				frustumCorners[j] = new Vector3f(frustumCorners[j])
					.add(new Vector3f(dist).mul(lastSplitDistance));
			}

			// Get frustum center
			Vector3f frustumCenter = new Vector3f(0.0f);
			for (int j = 0; j < frustumCorners.length; j++) {
				frustumCenter.add(frustumCorners[j]);
			}
			frustumCenter.div(8.0f);

			float radius = 0.0f;
			for (int j = 0; j < frustumCorners.length; j++) {
				float distance =
					(new Vector3f(frustumCorners[j]).sub(frustumCenter))
						.length();
				radius = Math.max(radius, distance);
			}
			radius = (float) Math.ceil(radius * 16.0f) / 16.0f;

			Vector3f maxExtents = new Vector3f(radius);
			Vector3f minExtents = new Vector3f(maxExtents).mul(-1);

			Vector3f lightDirection =
				(new Vector3f(lightPos.x, lightPos.y, lightPos.z).mul(-1))
					.normalize();
			Vector3f eye = new Vector3f(frustumCenter)
				.sub(new Vector3f(lightDirection).mul(-minExtents.z));
			Vector3f up = new Vector3f(0.0f, 1.0f, 0.0f);
			Matrix4f lightViewMatrix =
				new Matrix4f().lookAt(eye, frustumCenter, up);
			Matrix4f lightOrthoMatrix =
				new Matrix4f().ortho(minExtents.x, maxExtents.x, minExtents.y,
					maxExtents.y, 0.0f, maxExtents.z - minExtents.z, true);

			// Store split distance and matrix in cascade
			CascadeShadow cascadeShadow = cascadeShadows.get(i);
			cascadeShadow.splitDistance =
				(nearClip + splitDistance * clipRange) * -1.0f;
			cascadeShadow.projViewMatrix =
				lightOrthoMatrix.mul(lightViewMatrix);

			lastSplitDistance = cascadeSplits[i];
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

	/**
	 * Create a new cascade shadow.
	 */
	public CascadeShadow() {
		this.projViewMatrix = new Matrix4f();
	}

}
