package com.ikalagaming.graphics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.gui.WindowManager;
import com.ikalagaming.graphics.gui.component.GuiWindow;
import com.ikalagaming.graphics.gui.component.MainToolbar;
import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WindowOwnershipTest {

    private static class TestToolbar extends MainToolbar {
        @Override
        public void draw(int width, int height) {}

        @Override
        public boolean handleGuiInput(@NonNull Scene scene, @NonNull Window window) {
            return false;
        }

        @Override
        public void updateValues(@NonNull Scene scene, @NonNull Window window) {}
    }

    private WindowManager manager;

    private static GuiWindow window(String name) {
        GuiWindow window = new GuiWindow(name, 0);
        window.setVisible(true);
        return window;
    }

    @BeforeEach
    void setUp() {
        manager = new WindowManager();
    }

    @Test
    void unloadRemovesOnlyThatPluginsWindows() {
        GraphicsContext first = new GraphicsContext("First", null);
        GraphicsContext second = new GraphicsContext("Second", null);
        manager.addWindow(first, "Menu", window("Menu"));
        manager.addWindow(first, "Debug", window("Debug"));
        manager.addWindow(second, "Map", window("Map"));

        assertEquals(2, manager.removeAllOwnedBy(first));

        assertFalse(manager.isVisible("Menu"));
        assertFalse(manager.isVisible("Debug"));
        assertTrue(manager.isVisible("Map"));
    }

    @Test
    void unloadRemovesTheToolbarItOwns() {
        GraphicsContext first = new GraphicsContext("First", null);
        GraphicsContext second = new GraphicsContext("Second", null);
        TestToolbar toolbar = new TestToolbar();
        manager.setToolbar(first, toolbar);

        manager.removeAllOwnedBy(second);
        assertSame(toolbar, manager.getToolbar());

        manager.removeAllOwnedBy(first);
        assertNull(manager.getToolbar());
    }

    @Test
    void reloadedPluginKeepsItsNewWindows() {
        // The new instance enables before the old instance's unload is handled
        GraphicsContext oldInstance = new GraphicsContext("Plugin", null);
        GraphicsContext newInstance = new GraphicsContext("Plugin", null);
        manager.addWindow(oldInstance, "Menu", window("Menu"));
        manager.setToolbar(oldInstance, new TestToolbar());
        manager.addWindow(newInstance, "Menu", window("Menu"));
        TestToolbar newToolbar = new TestToolbar();
        manager.setToolbar(newInstance, newToolbar);

        assertEquals(0, manager.removeAllOwnedBy(oldInstance));

        assertTrue(manager.isVisible("Menu"));
        assertSame(newToolbar, manager.getToolbar());
    }

    @Test
    void closedContextCannotAddWindows() {
        GraphicsContext context = new GraphicsContext("Plugin", null);
        context.close();

        assertThrows(
                IllegalStateException.class,
                () -> manager.addWindow(context, "Menu", window("Menu")));
        assertThrows(
                IllegalStateException.class, () -> manager.setToolbar(context, new TestToolbar()));
    }
}
