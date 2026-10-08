package com.ikalagaming.rpg.windows;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.frontend.gui.IkGui;
import com.ikalagaming.graphics.frontend.gui.data.IkIO;
import com.ikalagaming.graphics.frontend.gui.enums.Condition;
import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;
import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * Controls for lights and fog.
 *
 * @author Ches Burks
 */
public class DebugWindow implements GUIWindow {

    @Override
    public void draw() {
        IkGui.setNextWindowPos(10, 30, Condition.ONCE);
        IkGui.setNextWindowSize(450, 400, Condition.ONCE);
        IkGui.begin("Debug");

        IkGui.text(String.format("FPS: %.1f", IkGui.getIO().framerate));

        Vector3f position = GraphicsManager.getCameraManager().getCamera().getPosition();
        IkGui.text(
                String.format("Camera position: (%f, %f, %f)", position.x, position.y, position.z));
        Vector2f rotation = GraphicsManager.getCameraManager().getCamera().getRotation();
        IkGui.text(String.format("Camera rotation: (%f, %f)", rotation.x, rotation.y));
        IkIO io = IkGui.getIO();
        IkGui.text(
                String.format("Mouse position: (%f, %f)", io.mousePosition.x, io.mousePosition.y));
        IkGui.text(String.format("Mouse delta: (%f, %f)", io.mouseDelta.x, io.mouseDelta.y));

        IkGui.text(
                String.format(
                        "Current window position: (%f, %f)",
                        IkGui.getWindowPosX(), IkGui.getWindowPosY()));
        IkGui.text(
                String.format(
                        "Current window size: (%f, %f)",
                        IkGui.getWindowWidth(), IkGui.getWindowHeight()));

        IkGui.end();
    }

    @Override
    public void handleGuiInput(@NonNull Scene scene, @NonNull Window window) {}

    @Override
    public void setup(@NonNull Scene scene) {}
}
