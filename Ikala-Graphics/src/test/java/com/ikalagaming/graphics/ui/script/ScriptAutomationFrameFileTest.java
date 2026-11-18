package com.ikalagaming.graphics.ui.script;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests where scripts may save frames, in {@link ScriptAutomation#frameFile(String)}. */
class ScriptAutomationFrameFileTest {

    @ParameterizedTest
    @ValueSource(strings = {"shots/front.png", "shots\\front.PNG", "C:/captures/a/front.png"})
    void allowsLocalPngFiles(String path) {
        assertTrue(ScriptAutomation.frameFile(path).isAbsolute());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "\\\\host\\share\\front.png",
                "//host/share/front.png",
                "\\\\?\\C:\\front.png",
                "shots/front.txt",
                "shots/front.png.bat",
                "notes.txt:hidden.png",
                "shots/front"
            })
    void rejectsSharesAndOtherFiles(String path) {
        assertThrows(IllegalArgumentException.class, () -> ScriptAutomation.frameFile(path));
    }
}
