package com.ikalagaming.graphics.frontend.gui.windows;

import com.ikalagaming.graphics.frontend.gui.IkGui;
import com.ikalagaming.graphics.frontend.gui.component.GuiWindow;
import com.ikalagaming.graphics.frontend.gui.util.Alignment;

import imgui.flag.ImGuiWindowFlags;

/** Used to show the IkGui demo window. */
public class IkGuiDemo extends GuiWindow {

    public static final String WINDOW_NAME = "IkGUI Demo";

    public IkGuiDemo() {
        super(WINDOW_NAME, ImGuiWindowFlags.None);
        setScale(0.20f, 0.20f);
        setDisplacement(0.01f, 0.01f);
        setAlignment(Alignment.CENTER);
    }

    @Override
    public void draw(int width, int height) {
        IkGui.showDemoWindow();
    }
}
