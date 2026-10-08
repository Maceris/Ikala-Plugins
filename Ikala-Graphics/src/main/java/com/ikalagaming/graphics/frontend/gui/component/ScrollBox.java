package com.ikalagaming.graphics.frontend.gui.component;

import com.ikalagaming.graphics.frontend.gui.IkGui;
import com.ikalagaming.graphics.frontend.gui.flags.WindowFlags;

/** A scrollable area that contains other components. */
public class ScrollBox extends Component {

    private final String title;
    private final int windowFlags;

    /**
     * Add a scrollable area.
     *
     * @param title The unique name for the component, which does not actually show up on the
     *     screen.
     */
    public ScrollBox(String title) {
        this.title = title;
        this.windowFlags = WindowFlags.NONE;
    }

    @Override
    public void draw(int width, int height) {
        IkGui.setCursorPosX(getActualDisplaceX() * width - IkGui.getWindowPosX());
        IkGui.setCursorPosY(getActualDisplaceY() * height - IkGui.getWindowPosY());

        IkGui.beginChild(title, getActualWidth() * width, getActualHeight() * height, windowFlags);

        super.draw(width, height);
        IkGui.endChild();
    }
}
