package com.ikalagaming.graphics.render;

import com.ikalagaming.graphics.GuiInstance;
import com.ikalagaming.graphics.ShaderUniforms;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.graph.GuiMesh;
import com.ikalagaming.graphics.graph.ShaderProgram;
import com.ikalagaming.graphics.graph.Texture;
import com.ikalagaming.graphics.graph.UniformsMap;
import com.ikalagaming.graphics.scene.Scene;

import imgui.ImDrawData;
import imgui.ImFontAtlas;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.type.ImInt;
import org.joml.Vector2f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * A utility for handling the rendering of a Graphical User Interface.
 */
public class GuiRender {
	/**
	 * The mesh to render.
	 */
	private GuiMesh guiMesh;
	/**
	 * The scale of the GUI.
	 */
	private Vector2f scale;
	/**
	 * The shader program for rendering the UI.
	 */
	private ShaderProgram shaderProgram;
	/**
	 * The texture we render the UI to.
	 */
	private Texture texture;
	/**
	 * The uniforms for the shader program.
	 */
	private UniformsMap uniformsMap;

	/**
	 * Set up a new GUI renderer for the window.
	 * 
	 * @param window The window we are rendering in.
	 */
	public GuiRender(Window window) {
		List<ShaderProgram.ShaderModuleData> shaderModuleDataList =
			new ArrayList<>();
		shaderModuleDataList.add(new ShaderProgram.ShaderModuleData(
			"shaders/gui.vert", GL20.GL_VERTEX_SHADER));
		shaderModuleDataList.add(new ShaderProgram.ShaderModuleData(
			"shaders/gui.frag", GL20.GL_FRAGMENT_SHADER));
		this.shaderProgram = new ShaderProgram(shaderModuleDataList);
		this.createUniforms();
		this.createUIResources(window);
	}

	/**
	 * Clean up shaders and textures.
	 */
	public void cleanup() {
		this.shaderProgram.cleanup();
		this.texture.cleanup();
	}

	/**
	 * Set up imgui and create fonts, textures, meshes, etc.
	 * 
	 * @param window The window we are using.
	 */
	private void createUIResources(Window window) {
		ImGui.createContext();

		ImGuiIO imGuiIO = ImGui.getIO();
		imGuiIO.setIniFilename(null);
		imGuiIO.setDisplaySize(window.getWidth(), window.getHeight());

		ImFontAtlas fontAtlas = ImGui.getIO().getFonts();
		ImInt width = new ImInt();
		ImInt height = new ImInt();
		ByteBuffer buf = fontAtlas.getTexDataAsRGBA32(width, height);
		this.texture = new Texture(width.get(), height.get(), buf);

		this.guiMesh = new GuiMesh();
	}

	/**
	 * Set up uniforms for the GUI shader.
	 */
	private void createUniforms() {
		this.uniformsMap = new UniformsMap(this.shaderProgram.getProgramId());
		this.uniformsMap.createUniform(ShaderUniforms.GUI.SCALE);
		this.scale = new Vector2f();
	}

	/**
	 * Render the GUI over the given scene.
	 * 
	 * @param scene The scene we are drawing.
	 */
	public void render(Scene scene) {
		GuiInstance guiInstance = scene.getGuiInstance();
		if (guiInstance == null) {
			return;
		}
		guiInstance.drawGui();

		this.shaderProgram.bind();

		GL11.glEnable(GL11.GL_BLEND);
		GL14.glBlendEquation(GL14.GL_FUNC_ADD);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		GL11.glDisable(GL11.GL_CULL_FACE);

		GL30.glBindVertexArray(this.guiMesh.getVaoId());

		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.guiMesh.getVerticesVBO());
		GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER,
			this.guiMesh.getIndicesVBO());

		ImGuiIO io = ImGui.getIO();
		this.scale.x = 2.0f / io.getDisplaySizeX();
		this.scale.y = -2.0f / io.getDisplaySizeY();
		this.uniformsMap.setUniform(ShaderUniforms.GUI.SCALE, this.scale);

		ImDrawData drawData = ImGui.getDrawData();
		int numLists = drawData.getCmdListsCount();
		for (int i = 0; i < numLists; i++) {
			GL15.glBufferData(GL15.GL_ARRAY_BUFFER,
				drawData.getCmdListVtxBufferData(i), GL15.GL_STREAM_DRAW);
			GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER,
				drawData.getCmdListIdxBufferData(i), GL15.GL_STREAM_DRAW);

			int numCmds = drawData.getCmdListCmdBufferSize(i);
			for (int j = 0; j < numCmds; j++) {
				final int elemCount =
					drawData.getCmdListCmdBufferElemCount(i, j);
				final int idxBufferOffset =
					drawData.getCmdListCmdBufferIdxOffset(i, j);
				final int indices =
					idxBufferOffset * ImDrawData.SIZEOF_IM_DRAW_IDX;

				this.texture.bind();
				GL11.glDrawElements(GL11.GL_TRIANGLES, elemCount,
					GL11.GL_UNSIGNED_SHORT, indices);
			}
		}
		
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		GL11.glEnable(GL11.GL_CULL_FACE);
		GL11.glDisable(GL11.GL_BLEND);
	}

	/**
	 * Update the GUI when the screen has been resized.
	 * 
	 * @param width The new screen width, in pixels.
	 * @param height The new screen height, in pixels.
	 */
	public void resize(int width, int height) {
		ImGuiIO imGuiIO = ImGui.getIO();
		imGuiIO.setDisplaySize(width, height);
	}
}
