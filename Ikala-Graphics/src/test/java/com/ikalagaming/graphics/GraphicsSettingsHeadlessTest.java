package com.ikalagaming.graphics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Turning on headless rendering from the {@code ikala.headless} setting. */
class GraphicsSettingsHeadlessTest {

    /** Unset or false leaves windows alone. */
    @Test
    void offByDefault() {
        GraphicsSettings settings = new GraphicsSettings();
        assertFalse(settings.applyHeadless(null));
        assertFalse(settings.applyHeadless("false"));
        assertFalse(settings.headless);
        assertTrue(settings.guiViewports);
    }

    /** True uses the default size, and turns off viewports and the saved layout. */
    @Test
    void trueUsesDefaultSize() {
        GraphicsSettings settings = new GraphicsSettings();
        settings.guiLayoutFile = "gui.ini";
        assertTrue(settings.applyHeadless("true"));
        assertTrue(settings.headless);
        assertEquals(GraphicsSettings.HEADLESS_WIDTH, settings.requestedWindowWidth);
        assertEquals(GraphicsSettings.HEADLESS_HEIGHT, settings.requestedWindowHeight);
        assertFalse(settings.guiViewports);
        assertNull(settings.guiLayoutFile);
    }

    /** A size sets the scene's size. */
    @Test
    void sizeIsRead() {
        GraphicsSettings settings = new GraphicsSettings();
        assertTrue(settings.applyHeadless(" 1024X768 "));
        assertEquals(1024, settings.requestedWindowWidth);
        assertEquals(768, settings.requestedWindowHeight);
    }

    /** A size that can't be read is refused rather than ignored. */
    @Test
    void badSizeIsRefused() {
        GraphicsSettings settings = new GraphicsSettings();
        assertThrows(IllegalArgumentException.class, () -> settings.applyHeadless("1024"));
        assertThrows(IllegalArgumentException.class, () -> settings.applyHeadless("0x10"));
        assertThrows(IllegalArgumentException.class, () -> settings.applyHeadless("big"));
        assertFalse(settings.headless);
    }
}
