package com.ikalagaming.graphics.render;

import static org.lwjgl.opengl.GL30.glBindBufferBase;
import static org.lwjgl.opengl.GL42.glMemoryBarrier;
import static org.lwjgl.opengl.GL43.GL_COMPUTE_SHADER;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BARRIER_BIT;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER;
import static org.lwjgl.opengl.GL43.glDispatchCompute;

import com.ikalagaming.graphics.ShaderUniforms;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.graph.RenderBuffers;
import com.ikalagaming.graphics.graph.ShaderProgram;
import com.ikalagaming.graphics.graph.UniformsMap;
import com.ikalagaming.graphics.scene.Entity;
import com.ikalagaming.graphics.scene.Scene;

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
			"shaders/anim.comp", GL_COMPUTE_SHADER));
		shaderProgram = new ShaderProgram(shaderModuleDataList);
		createUniforms();
	}

	/**
	 * Clean up the shader program.
	 */
	public void cleanup() {
		shaderProgram.cleanup();
	}

	/**
	 * Create the uniforms for the compute shader.
	 */
	private void createUniforms() {
		uniformsMap = new UniformsMap(shaderProgram.getProgramId());
		uniformsMap.createUniform(ShaderUniforms.Animation.DRAW_PARAMETERS + "."
			+ ShaderUniforms.Animation.DrawParameters.SOURCE_OFFSET);
		uniformsMap.createUniform(ShaderUniforms.Animation.DRAW_PARAMETERS + "."
			+ ShaderUniforms.Animation.DrawParameters.SOURCE_SIZE);
		uniformsMap.createUniform(ShaderUniforms.Animation.DRAW_PARAMETERS + "."
			+ ShaderUniforms.Animation.DrawParameters.WEIGHTS_OFFSET);
		uniformsMap.createUniform(ShaderUniforms.Animation.DRAW_PARAMETERS + "."
			+ ShaderUniforms.Animation.DrawParameters.BONES_MATRICES_OFFSET);
		uniformsMap.createUniform(ShaderUniforms.Animation.DRAW_PARAMETERS + "."
			+ ShaderUniforms.Animation.DrawParameters.DESTINATION_OFFSET);
	}

	/**
	 * Compute animation transformations for all animated models in the scene.
	 * 
	 * @param scene The scene we are rendering.
	 * @param globalBuffer The buffers for indirect drawing of models.
	 */
	public void render(Scene scene, RenderBuffers globalBuffer) {
		shaderProgram.bind();
		glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0,
			globalBuffer.getBindingPosesBuffer());
		glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1,
			globalBuffer.getBonesIndicesWeightsBuffer());
		glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2,
			globalBuffer.getBonesMatricesBuffer());
		glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 3,
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
					uniformsMap.setUniform(
						ShaderUniforms.Animation.DRAW_PARAMETERS + "."
							+ ShaderUniforms.Animation.DrawParameters.SOURCE_OFFSET,
						animMeshDrawData.getBindingPoseOffset());
					uniformsMap.setUniform(
						ShaderUniforms.Animation.DRAW_PARAMETERS + "."
							+ ShaderUniforms.Animation.DrawParameters.SOURCE_SIZE,
						meshDrawData.getSizeInBytes() / 4);
					uniformsMap.setUniform(
						ShaderUniforms.Animation.DRAW_PARAMETERS + "."
							+ ShaderUniforms.Animation.DrawParameters.WEIGHTS_OFFSET,
						animMeshDrawData.getWeightsOffset());
					uniformsMap.setUniform(
						ShaderUniforms.Animation.DRAW_PARAMETERS + "."
							+ ShaderUniforms.Animation.DrawParameters.BONES_MATRICES_OFFSET,
						frame.getOffset());
					uniformsMap.setUniform(
						ShaderUniforms.Animation.DRAW_PARAMETERS + "."
							+ ShaderUniforms.Animation.DrawParameters.DESTINATION_OFFSET,
						dstOffset);
					glDispatchCompute(groupSize, 1, 1);
					dstOffset += meshDrawData.getSizeInBytes() / 4;
				}
			}
		}

		glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
		shaderProgram.unbind();
	}
}
