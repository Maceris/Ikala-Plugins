package com.ikalagaming.rpg.windows;

import static org.lwjgl.opengl.GL11.*;

import com.ikalagaming.graphics.scene.Scene;

import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.type.ImInt;
import lombok.NonNull;

/**
 * Show images to see if texture loading works.
 *
 * @author Ches Burks
 */
public class ImageWindow implements GUIWindow {
    private ImInt textureID;

    @Override
    public void draw() {
        ImGui.setNextWindowPos(410, 10, ImGuiCond.Once);
        ImGui.setNextWindowSize(600, 500, ImGuiCond.Once);
        ImGui.begin("Textures");

        if (ImGui.arrowButton("Decr ID", 0)) {
            textureID.set(textureID.get() - 1);
        }
        ImGui.sameLine();
        ImGui.text("Texture ID: " + textureID.get());
        ImGui.sameLine();
        if (ImGui.arrowButton("Incr ID", 1)) {
            textureID.set(textureID.get() + 1);
        }

        final int id = textureID.get();
        if (glIsTexture(id)) {
            ImGui.image(id, 500, 500);
        } else {
            ImGui.text("Not a texture!");
        }

        ImGui.end();
    }

    @Override
    public void setup(@NonNull Scene scene) {
        textureID = new ImInt(0);
    }
}
