package com.ikalagaming.graphics.render;

import lombok.Getter;
import lombok.Setter;
import org.lwjgl.opengl.GL15;

/**
 * References to buffers for rendering entitites, so we can cache the buffers.
 *
 * @author Ches Burks
 *
 */
@Getter
@Setter
class CommandBuffer {
	/**
	 * The static model render command buffer ID.
	 *
	 * @param staticCommandBuffer The new ID
	 * @return The draw command buffer.
	 */
	private int staticCommandBuffer;

	/**
	 * How many static model draw commands we have set up.
	 *
	 * @param staticDrawCount The number of draw commands.
	 * @return The number of draw commands.
	 */
	private int staticDrawCount;

	/**
	 * The animated model render command buffer ID.
	 *
	 * @param animatedCommandBuffer The new ID
	 * @return The draw command buffer.
	 */
	private int animatedCommandBuffer;

	/**
	 * How many animated model draw commands we have set up.
	 *
	 * @param staticDrawCount The number of draw commands.
	 * @return The number of draw commands.
	 */
	private int animatedDrawCount;

	/**
	 * Delete the buffers. This should only be done once and only after buffers
	 * have been generated.
	 */
	public void cleanup() {
		GL15.glDeleteBuffers(this.staticCommandBuffer);
		GL15.glDeleteBuffers(this.animatedCommandBuffer);
	}
}
