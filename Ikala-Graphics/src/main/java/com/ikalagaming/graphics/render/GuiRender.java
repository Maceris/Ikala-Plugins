/*
 * NOTICE: This file is a modified version of contents from
 * https://github.com/lwjglgamedev/lwjglbook, which was licensed under Apache
 * v2.0. Changes have been made related to formatting, functionality, and
 * naming.
 */
package com.ikalagaming.graphics.render;

import com.ikalagaming.graphics.GraphicsManager;
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
import imgui.ImVec2;
import imgui.ImVec4;
import imgui.flag.ImGuiKey;
import imgui.type.ImInt;
import lombok.NonNull;
import org.joml.Vector2f;
import org.lwjgl.glfw.GLFW;
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
	 * The texture we store font data in.
	 */
	private Texture font;
	/**
	 * The uniforms for the shader program.
	 */
	private UniformsMap uniformsMap;

	/**
	 * Set up a new GUI renderer for the window.
	 *
	 * @param window The window we are rendering in.
	 */
	public GuiRender(@NonNull Window window) {
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
		this.font.cleanup();
	}

	/**
	 * Set up imgui and create fonts, textures, meshes, etc.
	 *
	 * @param window The window we are using.
	 */
	private void createUIResources(@NonNull Window window) {
		ImGui.createContext();

		ImGuiIO imGuiIO = ImGui.getIO();
		imGuiIO.setIniFilename(null);
		imGuiIO.setDisplaySize(window.getWidth(), window.getHeight());
		this.setUpImGuiKeys();

		ImFontAtlas fontAtlas = ImGui.getIO().getFonts();
		ImInt width = new ImInt();
		ImInt height = new ImInt();
		ByteBuffer buf = fontAtlas.getTexDataAsRGBA32(width, height);
		this.font = new Texture(width.get(), height.get(), buf);
		fontAtlas.setTexID(this.font.getTextureID());

		this.guiMesh = new GuiMesh();
	}

	/**
	 * Set up uniforms for the GUI shader.
	 */
	private void createUniforms() {
		this.uniformsMap = new UniformsMap(this.shaderProgram.getProgramID());
		this.uniformsMap.createUniform(ShaderUniforms.GUI.SCALE);
		this.scale = new Vector2f();
	}

	/**
	 * Render the GUI over the given scene.
	 *
	 * @param scene The scene we are drawing.
	 */
	public void render(@NonNull Scene scene) {
		GuiInstance guiInstance = GraphicsManager.getGuiInstance();
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

		GL30.glBindVertexArray(this.guiMesh.getVaoID());

		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.guiMesh.getVerticesVBO());
		GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER,
			this.guiMesh.getIndicesVBO());

		ImGuiIO io = ImGui.getIO();
		this.scale.x = 2.0f / io.getDisplaySizeX();
		this.scale.y = -2.0f / io.getDisplaySizeY();
		this.uniformsMap.setUniform(ShaderUniforms.GUI.SCALE, this.scale);

		ImDrawData drawData = ImGui.getDrawData();
		ImVec2 bufScale = drawData.getFramebufferScale();
		ImVec2 displSize = drawData.getDisplaySize();

		int fbHeight = (int) (displSize.y * bufScale.y);

		int numLists = drawData.getCmdListsCount();
		for (int i = 0; i < numLists; ++i) {
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

				int id = drawData.getCmdListCmdBufferTextureId(i, j);

				ImVec4 clipRect = drawData.getCmdListCmdBufferClipRect(i, j);

				GL11.glScissor((int) clipRect.x, (int) (fbHeight - clipRect.w),
					(int) (clipRect.z - clipRect.x),
					(int) (clipRect.w - clipRect.y));

				GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);

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

	/**
	 * Set up nonstandard key codes to make sure they work.
	 */
	private void setUpImGuiKeys() {
		ImGuiIO io = ImGui.getIO();
		io.setKeyMap(ImGuiKey.Tab, GLFW.GLFW_KEY_TAB);
		io.setKeyMap(ImGuiKey.LeftArrow, GLFW.GLFW_KEY_LEFT);
		io.setKeyMap(ImGuiKey.RightArrow, GLFW.GLFW_KEY_RIGHT);
		io.setKeyMap(ImGuiKey.UpArrow, GLFW.GLFW_KEY_UP);
		io.setKeyMap(ImGuiKey.DownArrow, GLFW.GLFW_KEY_DOWN);
		io.setKeyMap(ImGuiKey.PageUp, GLFW.GLFW_KEY_PAGE_UP);
		io.setKeyMap(ImGuiKey.PageDown, GLFW.GLFW_KEY_PAGE_DOWN);
		io.setKeyMap(ImGuiKey.Home, GLFW.GLFW_KEY_HOME);
		io.setKeyMap(ImGuiKey.End, GLFW.GLFW_KEY_END);
		io.setKeyMap(ImGuiKey.Insert, GLFW.GLFW_KEY_INSERT);
		io.setKeyMap(ImGuiKey.Delete, GLFW.GLFW_KEY_DELETE);
		io.setKeyMap(ImGuiKey.Backspace, GLFW.GLFW_KEY_BACKSPACE);
		io.setKeyMap(ImGuiKey.Space, GLFW.GLFW_KEY_SPACE);
		io.setKeyMap(ImGuiKey.Enter, GLFW.GLFW_KEY_ENTER);
		io.setKeyMap(ImGuiKey.Escape, GLFW.GLFW_KEY_ESCAPE);
		io.setKeyMap(ImGuiKey.KeyPadEnter, GLFW.GLFW_KEY_KP_ENTER);
	}
}