/*
 * NOTICE: This file is a modified version of contents from
 * https://github.com/lwjglgamedev/lwjglbook, which was licensed under Apache
 * v2.0. Changes have been made related to formatting, functionality, and
 * naming.
 */
package com.ikalagaming.graphics.render;

import com.ikalagaming.graphics.ShaderUniforms;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.graph.RenderBuffers;
import com.ikalagaming.graphics.graph.ShaderProgram;
import com.ikalagaming.graphics.graph.UniformsMap;
import com.ikalagaming.graphics.scene.Entity;
import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles computations for animated models.
 */
public class AnimationRender {
	/**
	 * The compute shader for animations.
	 */
	private ShaderProgram shaderProgram;
	/**
	 * The uniforms for the program.
	 */
	private UniformsMap uniformsMap;

	/**
	 * Set up a new animation renderer.
	 */
	public AnimationRender() {
		List<ShaderProgram.ShaderModuleData> shaderModuleDataList =
			new ArrayList<>();
		shaderModuleDataList.add(new ShaderProgram.ShaderModuleData(
			"shaders/anim.comp", GL43.GL_COMPUTE_SHADER));
		this.shaderProgram = new ShaderProgram(shaderModuleDataList);
		this.createUniforms();
	}

	/**
	 * Clean up the shader program.
	 */
	public void cleanup() {
		this.shaderProgram.cleanup();
	}

	/**
	 * Create the uniforms for the compute shader.
	 */
	private void createUniforms() {
		this.uniformsMap = new UniformsMap(this.shaderProgram.getProgramID());
		this.uniformsMap.createUniform(ShaderUniforms.Animation.DRAW_PARAMETERS
			+ "." + ShaderUniforms.Animation.DrawParameters.SOURCE_OFFSET);
		this.uniformsMap.createUniform(ShaderUniforms.Animation.DRAW_PARAMETERS
			+ "." + ShaderUniforms.Animation.DrawParameters.SOURCE_SIZE);
		this.uniformsMap.createUniform(ShaderUniforms.Animation.DRAW_PARAMETERS
			+ "." + ShaderUniforms.Animation.DrawParameters.WEIGHTS_OFFSET);
		this.uniformsMap.createUniform(ShaderUniforms.Animation.DRAW_PARAMETERS
			+ "."
			+ ShaderUniforms.Animation.DrawParameters.BONES_MATRICES_OFFSET);
		this.uniformsMap.createUniform(ShaderUniforms.Animation.DRAW_PARAMETERS
			+ "." + ShaderUniforms.Animation.DrawParameters.DESTINATION_OFFSET);
	}

	/**
	 * Compute animation transformations for all animated models in the scene.
	 *
	 * @param scene The scene we are rendering.
	 * @param globalBuffer The buffers for indirect drawing of models.
	 */
	public void render(@NonNull Scene scene,
		@NonNull RenderBuffers globalBuffer) {
		this.shaderProgram.bind();
		GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 0,
			globalBuffer.getBindingPosesBuffer());
		GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 1,
			globalBuffer.getBonesIndicesWeightsBuffer());
		GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 2,
			globalBuffer.getBonesMatricesBuffer());
		GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 3,
			globalBuffer.getDestAnimationBuffer());

		int dstOffset = 0;
		for (Model model : scene.getModelMap().values()) {
			if (model.isAnimated()) {
				for (RenderBuffers.MeshDrawData meshDrawData : model
					.getMeshDrawDataList()) {
					RenderBuffers.AnimMeshDrawData animMeshDrawData =
						meshDrawData.getAnimMeshDrawData();
					Entity entity = animMeshDrawData.getEntity();
					Model.AnimatedFrame frame =
						entity.getAnimationData().getCurrentFrame();
					int groupSize = (int) Math
						.ceil((float) meshDrawData.getSizeInBytes() / (14 * 4));
					this.uniformsMap.setUniform(
						ShaderUniforms.Animation.DRAW_PARAMETERS + "."
							+ ShaderUniforms.Animation.DrawParameters.SOURCE_OFFSET,
						animMeshDrawData.getBindingPoseOffset());
					this.uniformsMap.setUniform(
						ShaderUniforms.Animation.DRAW_PARAMETERS + "."
							+ ShaderUniforms.Animation.DrawParameters.SOURCE_SIZE,
						meshDrawData.getSizeInBytes() / 4);
					this.uniformsMap.setUniform(
						ShaderUniforms.Animation.DRAW_PARAMETERS + "."
							+ ShaderUniforms.Animation.DrawParameters.WEIGHTS_OFFSET,
						animMeshDrawData.getWeightsOffset());
					this.uniformsMap.setUniform(
						ShaderUniforms.Animation.DRAW_PARAMETERS + "."
							+ ShaderUniforms.Animation.DrawParameters.BONES_MATRICES_OFFSET,
						frame.getOffset());
					this.uniformsMap.setUniform(
						ShaderUniforms.Animation.DRAW_PARAMETERS + "."
							+ ShaderUniforms.Animation.DrawParameters.DESTINATION_OFFSET,
						dstOffset);
					GL43.glDispatchCompute(groupSize, 1, 1);
					dstOffset += meshDrawData.getSizeInBytes() / 4;
				}
			}
		}

		GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);
		this.shaderProgram.unbind();
	}
}