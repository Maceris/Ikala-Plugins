package com.ikalagaming.graphics.gui.windows;

import com.ikalagaming.graphics.gui.component.GuiWindow;
import com.ikalagaming.graphics.gui.flags.WindowFlags;
import com.ikalagaming.graphics.gui.util.Alignment;

/** Used to show the IkGui demo window. */
public class IkGuiDemo extends GuiWindow {

    public static final String WINDOW_NAME = "IkGUI Demo";

    public IkGuiDemo() {
        super(WINDOW_NAME, WindowFlags.NONE);
        setScale(0.20f, 0.20f);
        setDisplacement(0.01f, 0.01f);
        setAlignment(Alignment.CENTER);
    }

    @Override
    public void draw(int width, int height) {
        // don't draw anything here, we just use this for tracking that we want it to show.
    }
}
