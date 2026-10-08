package com.ikalagaming.graphics.frontend.gui.component;

import com.ikalagaming.graphics.frontend.gui.IkGui;

import lombok.RequiredArgsConstructor;

/** Any regular text that we need to position on the GUI. */
@RequiredArgsConstructor
public class Text extends Component {

    /** The text to show. */
    private final String contents;

    @Override
    public void draw(final int width, final int height) {
        IkGui.setCursorPosX(getActualDisplaceX() * width - IkGui.getWindowPosX());
        IkGui.setCursorPosY(getActualDisplaceY() * height - IkGui.getWindowPosY());

        IkGui.text(contents);
    }

    public void drawFormatted(final int width, final int height, Object... args) {}
}
