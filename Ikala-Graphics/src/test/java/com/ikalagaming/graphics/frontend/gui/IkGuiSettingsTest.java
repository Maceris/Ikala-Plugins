package com.ikalagaming.graphics.frontend.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.frontend.gui.data.Context;
import com.ikalagaming.graphics.frontend.gui.data.SettingsHandler;
import com.ikalagaming.graphics.frontend.gui.data.Window;
import com.ikalagaming.graphics.frontend.gui.data.WindowSettings;
import com.ikalagaming.graphics.frontend.gui.enums.Condition;
import com.ikalagaming.graphics.frontend.gui.flags.ChildFlags;
import com.ikalagaming.graphics.frontend.gui.flags.WindowFlags;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Tests for .ini settings loading and saving. */
class IkGuiSettingsTest {

    private static final float DELTA = 0.001f;
    private static final int SESSION_DATE = 20_261_005;

    private Context context;

    @BeforeEach
    void setUp() {
        createContext();
    }

    @AfterEach
    void tearDown() {
        IkGui.destroyContext();
    }

    private void createContext() {
        context = IkGui.createContext();
        context.io.displaySize.set(1280, 720);
        // Don't read or write an .ini file unless a test asks for it
        context.io.iniFilename = null;
        context.platformIO.platformSessionDate = SESSION_DATE;
    }

    private static void frame(Runnable ui) {
        IkGui.newFrame();
        ui.run();
        IkGui.render();
    }

    private static void frames(int count, Runnable ui) {
        for (int i = 0; i < count; ++i) {
            frame(ui);
        }
    }

    private static Runnable window(String name, float x, float y, float w, float h, int flags) {
        return () -> {
            IkGui.setNextWindowPos(x, y, Condition.FIRST_USE_EVER);
            IkGui.setNextWindowSize(w, h, Condition.FIRST_USE_EVER);
            IkGui.begin(name, flags);
            IkGui.text("contents");
            IkGui.end();
        };
    }

    @Test
    void testSaveToMemory() {
        Runnable ui =
                () -> {
                    window("Saved", 100, 120, 200, 150, WindowFlags.NONE).run();
                    window("Unsaved", 300, 120, 200, 150, WindowFlags.NO_SAVED_SETTINGS).run();
                };
        frames(2, ui);

        String ini = IkGui.saveIniSettingsToMemory();
        assertTrue(ini.contains("[Window][Saved]\nPos=100,120\nSize=200,150\nCollapsed=0\n"), ini);
        assertTrue(ini.contains("LastUsed=" + SESSION_DATE), ini);
        assertFalse(ini.contains("Unsaved"), ini);
        // The implicit debug window is saved too, like in ImGui
        assertTrue(ini.contains("[Window][Debug##Default]"), ini);
    }

    @Test
    void testLastUsedDateCanBeDisabled() {
        context.io.configIniSettingsSaveLastUsedDate = false;
        frames(2, window("Saved", 100, 120, 200, 150, WindowFlags.NONE));
        assertFalse(IkGui.saveIniSettingsToMemory().contains("LastUsed"));
    }

    @Test
    void testLoadBeforeFirstFrameOverridesFirstUseEver() {
        IkGui.loadIniSettingsFromMemory(
                "; comment\n[Window][Loaded]\nPos=40,50\nSize=300,200\nCollapsed=0\n\n");
        frames(2, window("Loaded", 100, 120, 200, 150, WindowFlags.NONE));

        Window window = IkGuiInternal.findWindowByName("Loaded");
        assertNotNull(window);
        assertEquals(40, window.position.x, DELTA);
        assertEquals(50, window.position.y, DELTA);
        assertEquals(300, window.sizeFull.x, DELTA);
        assertEquals(200, window.sizeFull.y, DELTA);
        assertNotNull(window.settings);
    }

    @Test
    void testAlwaysConditionStillApplies() {
        IkGui.loadIniSettingsFromMemory("[Window][Loaded]\nPos=40,50\nSize=300,200\n");
        frames(
                2,
                () -> {
                    IkGui.setNextWindowPos(10, 20, Condition.ALWAYS);
                    IkGui.begin("Loaded");
                    IkGui.end();
                });
        Window window = IkGuiInternal.findWindowByName("Loaded");
        assertEquals(10, window.position.x, DELTA);
        assertEquals(20, window.position.y, DELTA);
    }

    @Test
    void testLoadCollapsed() {
        IkGui.loadIniSettingsFromMemory("[Window][Loaded]\nPos=40,50\nSize=300,200\nCollapsed=1\n");
        frames(2, window("Loaded", 100, 120, 200, 150, WindowFlags.NONE));
        assertTrue(IkGuiInternal.findWindowByName("Loaded").collapsed);
    }

    @Test
    void testLoadIntoRunningContextAppliesToExistingWindows() {
        Runnable ui = window("Existing", 100, 120, 200, 150, WindowFlags.NONE);
        frames(2, ui);

        IkGui.loadIniSettingsFromMemory("[Window][Existing]\nPos=10,15\nSize=250,180\n");
        frames(2, ui);

        Window window = IkGuiInternal.findWindowByName("Existing");
        assertEquals(10, window.position.x, DELTA);
        assertEquals(15, window.position.y, DELTA);
        assertEquals(250, window.sizeFull.x, DELTA);
    }

    @Test
    void testUnknownEntriesAndLinesAreIgnored() {
        IkGui.loadIniSettingsFromMemory(
                "[Unknown][Thing]\nFoo=1\n[Window][Ok]\nNonsense\nPos=5,6\n[Broken\nSize=x,y\n");
        frames(1, window("Ok", 100, 120, 200, 150, WindowFlags.NONE));
        Window window = IkGuiInternal.findWindowByName("Ok");
        assertEquals(5, window.position.x, DELTA);
        assertEquals(6, window.position.y, DELTA);
    }

    @Test
    void testTripleHashNamesStoreOnlyTheID() {
        frames(2, window("Title A###Stable", 100, 120, 200, 150, WindowFlags.NONE));
        String ini = IkGui.saveIniSettingsToMemory();
        assertTrue(ini.contains("[Window][###Stable]"), ini);
        assertFalse(ini.contains("Title A"), ini);

        // A different title with the same ID uses the same settings
        IkGui.destroyContext();
        createContext();
        IkGui.loadIniSettingsFromMemory(ini);
        frames(2, window("Title B###Stable", 0, 0, 50, 50, WindowFlags.NONE));
        assertEquals(100, IkGuiInternal.findWindowByName("Title B###Stable").position.x, DELTA);
    }

    @Test
    void testDiskRoundTripThroughIniFilename(@TempDir Path directory) throws IOException {
        Path ini = directory.resolve("test.ini");
        context.io.iniFilename = ini.toString();
        frames(2, window("Disk", 100, 120, 200, 150, WindowFlags.NONE));
        IkGuiInternal.findWindowByName("Disk").position.set(70, 80);

        // Settings are saved when the context is destroyed
        IkGui.destroyContext();
        assertTrue(Files.readString(ini).contains("[Window][Disk]\nPos=70,80\n"));

        // And loaded automatically on the first frame
        createContext();
        context.io.iniFilename = ini.toString();
        frames(2, window("Disk", 100, 120, 200, 150, WindowFlags.NONE));
        Window window = IkGuiInternal.findWindowByName("Disk");
        assertEquals(70, window.position.x, DELTA);
        assertEquals(80, window.position.y, DELTA);
    }

    @Test
    void testNoFileWrittenWithoutNewFrame(@TempDir Path directory) {
        Path ini = directory.resolve("never.ini");
        context.io.iniFilename = ini.toString();
        IkGui.destroyContext();
        assertFalse(Files.exists(ini));
    }

    @Test
    void testDirtySettingsRequestSaveAfterDelay() {
        Runnable ui =
                () -> {
                    IkGui.setNextWindowPos(100, 100, Condition.FIRST_USE_EVER);
                    IkGui.begin("Moving");
                    IkGui.end();
                };
        frames(2, ui);
        context.settingsDirtyTimer = 0;
        context.io.wantSaveIniSettings = false;

        IkGuiInternal.markIniSettingsDirty(IkGuiInternal.findWindowByName("Moving"));
        frame(ui);
        assertTrue(context.settingsDirtyTimer > 0);
        assertFalse(context.io.wantSaveIniSettings);

        // Without an .ini file, the application is told to save the settings itself
        context.frameStartTime -= context.io.iniSavingRate + 1000;
        frame(ui);
        assertTrue(context.io.wantSaveIniSettings);
        assertEquals(0, context.settingsDirtyTimer);
    }

    @Test
    void testClearWindowSettings() {
        Runnable ui = window("Cleared", 100, 120, 200, 150, WindowFlags.NONE);
        IkGui.loadIniSettingsFromMemory("[Window][Cleared]\nPos=10,15\nSize=250,180\n");
        frames(2, ui);
        assertEquals(10, IkGuiInternal.findWindowByName("Cleared").position.x, DELTA);

        IkGuiInternal.clearWindowSettings("Cleared");
        assertFalse(IkGui.saveIniSettingsToMemory().contains("Cleared"));
        // FIRST_USE_EVER applies again
        frames(2, ui);
        assertEquals(100, IkGuiInternal.findWindowByName("Cleared").position.x, DELTA);
    }

    @Test
    void testAutoDiscardOldEntries() {
        context.io.configIniSettingsAutoDiscardMonths = 6;
        IkGui.loadIniSettingsFromMemory(
                "[Window][Old]\nPos=1,1\nLastUsed=20250101\n\n"
                        + "[Window][Recent]\nPos=2,2\nLastUsed=20260901\n\n"
                        + "[Window][Undated]\nPos=3,3\n\n");
        String ini = IkGui.saveIniSettingsToMemory();
        assertFalse(ini.contains("[Window][Old]"), ini);
        assertTrue(ini.contains("[Window][Recent]"), ini);
        // Entries without a date are always discarded when auto-discard is enabled
        assertFalse(ini.contains("[Window][Undated]"), ini);
    }

    @Test
    void testSubtractMonths() {
        assertEquals(20_260_405, IkGuiImplConfig.subtractMonths(20_261_005, 6));
        assertEquals(20_251_205, IkGuiImplConfig.subtractMonths(20_260_105, 1));
        assertEquals(20_240_105, IkGuiImplConfig.subtractMonths(20_260_105, 24));
    }

    @Test
    void testCustomSettingsHandler() {
        final List<String> lines = new ArrayList<>();
        final List<String> calls = new ArrayList<>();
        SettingsHandler handler = new SettingsHandler("MyApp");
        handler.readInitFunction = (ctx, h) -> calls.add("init");
        handler.readOpenFunction =
                (ctx, h, name) -> {
                    calls.add("open " + name);
                    return lines;
                };
        handler.readLineFunction = (ctx, h, entry, line) -> lines.add(line);
        handler.applyAllFunction = (ctx, h) -> calls.add("apply");
        handler.writeAllFunction = (ctx, h, out) -> out.append("[MyApp][Data]\nValue=42\n\n");
        IkGuiInternal.addSettingsHandler(handler);
        assertNotNull(IkGuiInternal.findSettingsHandler("MyApp"));

        IkGui.loadIniSettingsFromMemory("[MyApp][Data]\nValue=7\nOther=1\n");
        assertEquals(List.of("init", "open Data", "apply"), calls);
        assertEquals(List.of("Value=7", "Other=1"), lines);
        assertTrue(IkGui.saveIniSettingsToMemory().contains("[MyApp][Data]\nValue=42\n"));

        IkGuiInternal.removeSettingsHandler("MyApp");
        assertNull(IkGuiInternal.findSettingsHandler("MyApp"));
    }

    @Test
    void testChildWindowsSaveOnlySize() {
        frames(
                2,
                () -> {
                    window("Parent", 100, 100, 300, 300, WindowFlags.NONE).run();
                    IkGui.begin("Parent");
                    // Only resizable child windows are saved
                    IkGui.beginChild("Child", 100, 80, ChildFlags.RESIZE_Y);
                    IkGui.endChild();
                    IkGui.end();
                });
        String ini = IkGui.saveIniSettingsToMemory();
        int child = ini.indexOf("Child");
        assertTrue(child >= 0, ini);
        String entry = ini.substring(ini.lastIndexOf('[', child), ini.indexOf("\n\n", child));
        assertTrue(entry.contains("IsChild=1"), entry);
        assertFalse(entry.contains("Pos="), entry);
    }

    @Test
    void testWindowSettingsAreFoundByID() {
        frames(2, window("Find", 100, 120, 200, 150, WindowFlags.NONE));
        IkGui.saveIniSettingsToMemory();
        Window window = IkGuiInternal.findWindowByName("Find");
        WindowSettings settings = IkGuiInternal.findWindowSettingsByID(window.id);
        assertNotNull(settings);
        assertEquals(settings, IkGuiInternal.findWindowSettingsByWindow(window));
    }
}
