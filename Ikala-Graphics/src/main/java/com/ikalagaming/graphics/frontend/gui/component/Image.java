package com.ikalagaming.graphics.frontend.gui.component;

import static org.lwjgl.opengl.GL11.*;

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
        if (texture != null && glIsTexture(texture.id())) {
            // TODO(ches) move this to the render backend
            glBindTexture(GL_TEXTURE_2D, texture.id());
            ImGui.image(texture.id(), getActualWidth() * width, getActualHeight() * height);
        } else {
            ImGui.text("Texture somehow does not exist!");
        }
    }
}
