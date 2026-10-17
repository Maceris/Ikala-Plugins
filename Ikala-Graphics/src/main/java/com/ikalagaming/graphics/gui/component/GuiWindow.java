package com.ikalagaming.graphics.gui.component;

import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.data.IkBoolean;
import com.ikalagaming.graphics.gui.data.Viewport;
import com.ikalagaming.graphics.gui.enums.Condition;

import lombok.NonNull;

public class GuiWindow extends Component {
    protected final String title;
    protected final int windowFlags;
    protected final IkBoolean windowOpen = new IkBoolean(true);

    /**
     * Create a window with the specified title and window flags. Windows are not visible by
     * default.
     *
     * @param title The title of the window.
     * @param windowFlags The IkGui flags for the window.
     * @see com.ikalagaming.graphics.gui.flags.WindowFlags
     */
    public GuiWindow(String title, int windowFlags) {
        this.title = title;
        this.windowFlags = windowFlags;
        this.visible = false;
    }

    /**
     * Set where the next window goes, relative to the top left of the main window. With IkGui
     * viewports on, positions are on the desktop rather than in the main window, so this offsets
     * them by where the main window is. The window isn't kept in the main window, so it can be
     * dragged out of it or docked afterward.
     *
     * @param x The distance from the left of the main window.
     * @param y The distance from the top of the main window.
     * @param condition When to apply the position.
     */
    protected static void setNextWindowPosInMainWindow(
            float x, float y, @NonNull Condition condition) {
        final Viewport mainViewport = IkGui.getMainViewport();
        IkGui.setNextWindowPos(mainViewport.position.x + x, mainViewport.position.y + y, condition);
    }

    @Override
    public void draw(final int width, final int height) {
        // TODO(ches) FACT-22 don't extend Component, windows are a separate thing
        setNextWindowPosInMainWindow(
                getActualDisplaceX() * width, getActualDisplaceY() * height, Condition.ONCE);
        IkGui.setNextWindowSize(
                getActualWidth() * width, getActualHeight() * height, Condition.ONCE);
        IkGui.begin(title, windowOpen, windowFlags);

        super.draw(width, height);

        IkGui.end();
    }
}
