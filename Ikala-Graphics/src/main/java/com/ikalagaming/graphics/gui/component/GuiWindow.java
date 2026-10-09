package com.ikalagaming.graphics.gui.component;

import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.data.IkBoolean;
import com.ikalagaming.graphics.gui.enums.Condition;

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

    @Override
    public void draw(final int width, final int height) {
        // TODO(ches) FACT-22 don't extend Component, windows are a separate thing
        IkGui.setNextWindowViewport(IkGui.getMainViewport().id);
        IkGui.setNextWindowPos(
                getActualDisplaceX() * width, getActualDisplaceY() * height, Condition.ONCE);
        IkGui.setNextWindowSize(
                getActualWidth() * width, getActualHeight() * height, Condition.ONCE);
        IkGui.begin(title, windowOpen, windowFlags);

        super.draw(width, height);

        IkGui.end();
    }
}
