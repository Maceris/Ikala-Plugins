package com.ikalagaming.graphics.frontend.gui.component;

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
            ImGui.image((int) texture.id(), getActualWidth() * width, getActualHeight() * height);
        } else {
            ImGui.text("x");
        }
    }
}
