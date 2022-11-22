package com.ikalagaming.rpg.windows;

import com.ikalagaming.graphics.scene.Scene;

import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiStyleVar;
import imgui.type.ImInt;
import lombok.NonNull;
import org.lwjgl.opengl.GL11;

/**
 * Show images to see if texture loading works.
 *
 * @author Ches Burks
 *
 */
public class ImageWindow implements GUIWindow {
	private ImInt textureID;

	@Override
	public void draw() {
		ImGui.setNextWindowPos(410, 10, ImGuiCond.Once);
		ImGui.setNextWindowSize(800, 400, ImGuiCond.Once);
		ImGui.begin("Textures");

		if (ImGui.arrowButton("Decr ID", 0)) {
			this.textureID.set(this.textureID.get() - 1);
		}
		ImGui.sameLine();
		ImGui.text("Texture ID: " + this.textureID.get());
		ImGui.sameLine();
		if (ImGui.arrowButton("Incr ID", 1)) {
			this.textureID.set(this.textureID.get() + 1);
		}

		ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1);
		if (GL11.glIsTexture(this.textureID.get())) {
			ImGui.image(this.textureID.get(), 100, 100);
		}
		else {
			ImGui.text("Not a texture!");
		}

		ImGui.popStyleVar();

		ImGui.end();
	}

	@Override
	public void setup(@NonNull Scene scene) {
		this.textureID = new ImInt(0);
	}

}
