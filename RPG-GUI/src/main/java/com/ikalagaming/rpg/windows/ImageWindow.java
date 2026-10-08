package com.ikalagaming.rpg.windows;

import static org.lwjgl.opengl.GL11.*;

import com.ikalagaming.graphics.backend.opengl.TextureInfoOpenGL;
import com.ikalagaming.graphics.frontend.gui.IkGui;
import com.ikalagaming.graphics.frontend.gui.data.IkInt;
import com.ikalagaming.graphics.frontend.gui.enums.Condition;
import com.ikalagaming.graphics.frontend.gui.enums.Direction;
import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;

/**
 * Show images to see if texture loading works.
 *
 * @author Ches Burks
 */
public class ImageWindow implements GUIWindow {
    private IkInt textureID;

    /** The texture info for the texture ID being shown, reused between frames. */
    private final TextureInfoOpenGL textureInfo = new TextureInfoOpenGL();

    @Override
    public void draw() {
        IkGui.setNextWindowPos(410, 10, Condition.ONCE);
        IkGui.setNextWindowSize(600, 500, Condition.ONCE);
        IkGui.begin("Textures");

        if (IkGui.arrowButton("Decr ID", Direction.LEFT)) {
            textureID.set(textureID.get() - 1);
        }
        IkGui.sameLine();
        IkGui.text("Texture ID: " + textureID.get());
        IkGui.sameLine();
        if (IkGui.arrowButton("Incr ID", Direction.RIGHT)) {
            textureID.set(textureID.get() + 1);
        }

        final int id = textureID.get();
        if (glIsTexture(id)) {
            if (textureInfo.id != id) {
                textureInfo.id = id;
                textureInfo.bindlessHandle = 0;
            }
            IkGui.image(textureInfo, 500, 500);
        } else {
            IkGui.text("Not a texture!");
        }

        IkGui.end();
    }

    @Override
    public void setup(@NonNull Scene scene) {
        textureID = new IkInt(0);
    }
}
