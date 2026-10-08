package com.ikalagaming.graphics.frontend.gui.component;

import com.ikalagaming.graphics.frontend.Texture;
import com.ikalagaming.graphics.frontend.gui.IkGui;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class Image extends Component {

    private Texture texture;

    @Override
    public void draw(final int width, final int height) {
        IkGui.setCursorPosX(getActualDisplaceX() * width - IkGui.getWindowPosX());
        IkGui.setCursorPosY(getActualDisplaceY() * height - IkGui.getWindowPosY());
        if (texture != null) {
            IkGui.image(texture.info(), getActualWidth() * width, getActualHeight() * height);
        } else {
            IkGui.text("x");
        }
    }
}
