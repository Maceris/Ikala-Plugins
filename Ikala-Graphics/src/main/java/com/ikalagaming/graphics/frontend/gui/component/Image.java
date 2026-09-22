package com.ikalagaming.graphics.frontend.gui.component;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.backend.opengl.TextureInfoOpenGL;
import com.ikalagaming.graphics.backend.vulkan.TextureInfoVulkan;
import com.ikalagaming.graphics.frontend.BackendType;
import com.ikalagaming.graphics.frontend.Texture;

import imgui.ImGui;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class Image extends Component {

    private Texture texture;

    @Override
    public void draw(final int width, final int height) {
        ImGui.setCursorPosX(getActualDisplaceX() * width - ImGui.getWindowPosX());
        ImGui.setCursorPosY(getActualDisplaceY() * height - ImGui.getWindowPosY());
        if (texture != null) {
            // TODO(ches) Fix this, needs to not know or care about the backend
            int id = 0;
            if (GraphicsManager.getBackendType() == BackendType.OPENGL) {
                id = (int) ((TextureInfoOpenGL) texture.info()).id;
            } else {
                id = (int) ((TextureInfoVulkan) texture.info()).texture;
            }
            ImGui.image(id, getActualWidth() * width, getActualHeight() * height);
        } else {
            ImGui.text("x");
        }
    }
}
