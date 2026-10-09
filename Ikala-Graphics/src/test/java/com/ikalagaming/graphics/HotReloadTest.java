package com.ikalagaming.graphics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.data.Context;
import com.ikalagaming.graphics.ui.Label;
import com.ikalagaming.graphics.ui.Surface;
import com.ikalagaming.graphics.ui.UiManager;
import com.ikalagaming.graphics.ui.spec.Observable;
import com.ikalagaming.graphics.ui.spec.SpecBindings;
import com.ikalagaming.graphics.ui.spec.SpecInstance;
import com.ikalagaming.graphics.ui.spec.SpecLoader;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

/** Specs rebuilt when their files change. */
class HotReloadTest {

    private static final String SPEC =
            """
            surface: { id: hot, anchors: top-left }
            content:
              type: column
              id: root
              children: [ { type: label, id: l, text: "%s {count}" } ]
            """;

    @TempDir Path folder;

    private UiManager manager;
    private GraphicsContext owner;

    @BeforeEach
    void setUp() {
        Context context = IkGui.createContext();
        context.io.iniFilename = null;
        context.io.displaySize.set(1280, 720);
        manager = new UiManager();
        owner = new GraphicsContext("Test-Plugin", null);
    }

    @AfterEach
    void tearDown() {
        IkGui.destroyContext();
    }

    private void frame() {
        IkGui.newFrame();
        manager.draw();
        IkGui.render();
    }

    /** Write the spec, moving its time on so the change is seen even within one clock tick. */
    private void write(Path file, String text, int tick) throws IOException {
        Files.writeString(file, text);
        Files.setLastModifiedTime(file, FileTime.fromMillis(1_000_000L + tick * 10_000L));
    }

    @Test
    void changedFilesRebuildWithTheSameBindings() throws IOException {
        Path file = folder.resolve("hot.yml");
        write(file, SPEC.formatted("Before"), 0);
        Observable<Integer> count = Observable.of(3);
        SpecInstance instance =
                SpecInstance.open(
                        manager,
                        owner,
                        SpecLoader.load(file),
                        new SpecBindings().value("count", count));
        frame();
        Surface before = manager.get("hot");
        assertEquals("Before 3", ((Label) instance.find("l")).getText());

        write(file, SPEC.formatted("After"), 1);
        manager.reloadChangedSpecs();
        frame();

        assertEquals("After 3", ((Label) instance.find("l")).getText());
        assertNotSame(before, manager.get("hot"));
        assertSame(instance.getSurface(), manager.get("hot"));
        assertEquals(1, count.listenerCount(), "The old build stopped listening");
        count.set(4);
        frame();
        assertEquals("After 4", ((Label) instance.find("l")).getText());
    }

    @Test
    void brokenEditsKeepTheOldUi() throws IOException {
        Path file = folder.resolve("hot.yml");
        write(file, SPEC.formatted("Good"), 0);
        SpecInstance instance =
                SpecInstance.open(
                        manager,
                        owner,
                        SpecLoader.load(file),
                        new SpecBindings().value("count", Observable.of(1)));
        frame();

        write(file, "content: [this is not a node", 1);
        manager.reloadChangedSpecs();
        frame();

        assertFalse(instance.isClosed());
        assertEquals("Good 1", ((Label) instance.find("l")).getText());

        write(file, SPEC.formatted("Fixed"), 2);
        manager.reloadChangedSpecs();
        assertEquals("Fixed 1", ((Label) instance.find("l")).getText());
    }
}
