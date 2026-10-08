package com.ikalagaming.factory.gui.window;

import static com.ikalagaming.factory.gui.DefaultWindows.DEBUG;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.frontend.gui.IkGui;
import com.ikalagaming.graphics.frontend.gui.component.GuiWindow;
import com.ikalagaming.graphics.frontend.gui.enums.Condition;
import com.ikalagaming.graphics.frontend.gui.flags.WindowFlags;
import com.ikalagaming.graphics.frontend.gui.util.Alignment;
import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/** Used to show general debugging information. */
@Slf4j
public class Debug extends GuiWindow {

    public Debug() {
        super(DEBUG.getName(), WindowFlags.NONE);
        setScale(0.20f, 0.20f);
        setDisplacement(0.01f, 0.01f);
        setAlignment(Alignment.CENTER);
    }

    @Override
    public void draw(final int width, final int height) {
        IkGui.setNextWindowViewport(IkGui.getMainViewport().id);
        IkGui.setNextWindowPos(
                getActualDisplaceX() * width, getActualDisplaceY() * height, Condition.ONCE);
        IkGui.setNextWindowSize(
                getActualWidth() * width, getActualHeight() * height, Condition.ONCE);
        IkGui.begin(title, windowOpen, windowFlags);

        IkGui.text(String.format("Canvas size: %d, %d", width, height));
        IkGui.text(
                String.format(
                        "This window's relative size: %.2f, %.2f",
                        getActualWidth(), getActualHeight()));
        IkGui.text(
                String.format(
                        "This window's actual size: %.2f, %.2f",
                        getActualWidth() * width, getActualHeight() * height));
        IkGui.text(
                String.format(
                        "This window's relative position: %.2f, %.2f",
                        getActualDisplaceX(), getActualDisplaceY()));
        IkGui.text(
                String.format(
                        "This window's actual position: %.2f, %.2f",
                        getActualDisplaceX() * width, getActualDisplaceY() * height));

        var camera = GraphicsManager.getCameraManager().getCamera();
        IkGui.text(
                String.format(
                        "Camera position: x:%.2f, y:%.2f, z:%.2f",
                        camera.getPosition().x(),
                        camera.getPosition().y(),
                        camera.getPosition().z()));

        IkGui.text(
                String.format(
                        "Camera rotation: x:%.2f, y:%.2f",
                        camera.getRotation().x(), camera.getRotation().y()));

        IkGui.end();
    }

    @Override
    public boolean handleGuiInput(@NonNull Scene scene, @NonNull Window window) {
        return false;
    }
}
