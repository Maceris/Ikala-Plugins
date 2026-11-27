package com.ikalagaming.graphics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.data.Context;
import com.ikalagaming.graphics.ui.SavedState;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

/** View state kept in the GUI's ini text. */
class SavedStateTest {
    @AfterEach
    void tearDown() {
        IkGui.destroyContext();
    }

    /** Make a context with the handler, as the graphics plugin does before the ini is read. */
    private static void newContext() {
        Context context = IkGui.createContext();
        SavedState.install();
        context.io.iniFilename = null;
    }

    @Test
    void entriesSurviveTheIniText() {
        newContext();
        assertNull(SavedState.get("Texture graph a.yml"));
        Map<String, String> view = new LinkedHashMap<>();
        view.put("x", "120");
        view.put("zoom", "1.500");
        view.put("shown", "noise1");
        SavedState.put("Texture graph a.yml", view);
        SavedState.put("Removed", Map.of("k", "v"));
        SavedState.put("Removed", Map.of());
        String ini = IkGui.saveIniSettingsToMemory();
        assertTrue(
                ini.contains("[Saved][Texture graph a.yml]\nx=120\nzoom=1.500\nshown=noise1\n"),
                ini);
        IkGui.destroyContext();

        newContext();
        IkGui.loadIniSettingsFromMemory(ini);
        assertEquals(view, SavedState.get("Texture graph a.yml"));
        assertNull(SavedState.get("Removed"));
    }
}
