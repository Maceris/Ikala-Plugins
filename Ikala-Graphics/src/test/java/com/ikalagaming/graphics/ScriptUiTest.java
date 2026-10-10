package com.ikalagaming.graphics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.data.Context;
import com.ikalagaming.graphics.gui.enums.Key;
import com.ikalagaming.graphics.gui.enums.MouseButton;
import com.ikalagaming.graphics.gui.util.RectFloat;
import com.ikalagaming.graphics.ui.Container;
import com.ikalagaming.graphics.ui.Label;
import com.ikalagaming.graphics.ui.Node;
import com.ikalagaming.graphics.ui.Surface;
import com.ikalagaming.graphics.ui.UiManager;
import com.ikalagaming.graphics.ui.script.ScriptUi;
import com.ikalagaming.graphics.ui.spec.Observable;
import com.ikalagaming.graphics.ui.spec.SpecBindings;
import com.ikalagaming.graphics.ui.spec.SpecException;
import com.ikalagaming.graphics.ui.spec.SpecInstance;
import com.ikalagaming.graphics.ui.spec.SpecLoader;
import com.ikalagaming.graphics.ui.spec.UiSpec;
import com.ikalagaming.scripting.ScriptLaunch;
import com.ikalagaming.scripting.ScriptManager;
import com.ikalagaming.scripting.interpreter.ScriptRuntime;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Scripts driving retained UI through the {@code ui} global, and specs resuming and starting
 * scripts, on a headless IkGui context. Scripts run on the script thread, so the tests draw frames
 * on this thread until the script has done what they expect.
 */
public class ScriptUiTest {

    /** Collects what scripts report, given to them as the global {@code out}. */
    public static final class Recorder {
        /** What was reported, in order. */
        private final List<Object> values = new CopyOnWriteArrayList<>();

        /**
         * Report a value.
         *
         * @param value The value.
         */
        public void add(Object value) {
            values.add(value);
        }

        /**
         * What was reported.
         *
         * @return A copy of the values.
         */
        List<Object> values() {
            // Scripts can report null, which List.copyOf doesn't allow
            return new ArrayList<>(values);
        }
    }

    /** The plugin that owns the scripts and UI. */
    private static final String PLUGIN = "Script-Test-Plugin";

    /** Who provides the test globals. */
    private static final String PROVIDER = "script-ui-test";

    /** How long to wait for a script. */
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    /** The package of the stand-in plugin's classes. */
    private static final String PLUGIN_PACKAGE = "com.ikalagaming.graphics.testplugin.";

    /** The specs scripts can open, by path. */
    private static final Map<String, String> SPECS =
            Map.of(
                    "dialogue",
                    """
                    surface: { id: dialogue, anchors: top-left, width: 400, height: 300 }
                    content:
                      type: column
                      id: root
                      children:
                        - { type: label, id: line, text: "{line}" }
                        - { type: button, id: accept, text: "Yes", onClick: "resume(pick, 1)" }
                        - { type: button, id: decline, text: "No", onClick: "resume(pick, 2)" }
                        - { type: text-input, id: name, onSubmit: "resume(name)" }
                    """,
                    "rows",
                    """
                    surface: { id: rows, anchors: top-left, width: 400, height: 300 }
                    templates:
                      row: { node: { type: button, id: row, text: "{r}", onClick: "resume(row, {r})" } }
                    content:
                      type: column
                      id: root
                      repeat: { list: rows, as: r, template: row, key: "{r}" }
                    """);

    private Context context;
    private UiManager manager;
    private GraphicsContext owner;
    private Recorder recorder;

    @TempDir Path scripts;

    @BeforeEach
    void setUp() {
        context = IkGui.createContext();
        context.io.iniFilename = null;
        context.io.displaySize.set(1280, 720);
        context.io.mouseInsideWindow = true;
        manager = new UiManager();
        owner = new GraphicsContext(PLUGIN, null);
        recorder = new Recorder();

        ScriptUi ui = new ScriptUi(owner, () -> manager, ScriptUiTest::spec, () -> null, scripts);
        ScriptManager.registerGlobal("ui", PROVIDER, plugin -> ui);
        ScriptManager.registerGlobal("out", PROVIDER, plugin -> recorder);
    }

    @AfterEach
    void tearDown() {
        ScriptManager.terminateAllOwnedBy(PLUGIN);
        ScriptManager.terminateAllOwnedBy(PROVIDER);
        IkGui.destroyContext();
    }

    @AfterAll
    static void afterAll() {
        ScriptManager.shutdown();
    }

    private static UiSpec spec(String path) {
        String yaml = SPECS.get(path);
        if (yaml == null) {
            throw new SpecException("no spec " + path);
        }
        return SpecLoader.load(
                new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), path);
    }

    private void frame() {
        IkGui.newFrame();
        manager.draw();
        IkGui.render();
        manager.dispatchEvents();
    }

    private void frames(int count) {
        for (int i = 0; i < count; ++i) {
            frame();
        }
    }

    /** Draw frames on this thread until the condition holds. */
    private void framesUntil(Callable<Boolean> condition) {
        Awaitility.await()
                .atMost(TIMEOUT)
                .pollInSameThread()
                .pollInterval(Duration.ofMillis(5))
                .until(
                        () -> {
                            frame();
                            return condition.call();
                        });
    }

    private void click(Node<?> node) {
        RectFloat rect = node.getRect();
        context.io.addMousePosEvent(rect.getCenterX(), rect.getCenterY());
        frame();
        context.io.addMouseButtonEvent(MouseButton.LEFT, true);
        frame();
        context.io.addMouseButtonEvent(MouseButton.LEFT, false);
        frame();
    }

    private void submit(Node<?> input, String text) {
        click(input);
        context.io.addInputCharacters(text);
        frame();
        context.io.addKeyEvent(Key.ENTER, true);
        frame();
        context.io.addKeyEvent(Key.ENTER, false);
        frame();
    }

    /** Find a node of a shown surface by its ID path from the root. */
    private Node<?> find(String surfaceId, String path) {
        Surface surface = manager.get(surfaceId);
        if (surface == null) {
            return null;
        }
        Node<?> node = surface.getContent();
        for (String id : path.split("/")) {
            if (!(node instanceof Container<?> container)) {
                return null;
            }
            node = container.find(id);
            if (node == null) {
                return null;
            }
        }
        return node;
    }

    private String labelText(String surfaceId, String path) {
        return find(surfaceId, path) instanceof Label label ? label.getText() : null;
    }

    private ScriptRuntime start(String program) {
        return ScriptManager.start(ScriptLaunch.source(program).owner(PLUGIN)).orElseThrow();
    }

    @Test
    void scriptOpensASpecAndAwaitsTheChoice() {
        ScriptRuntime runtime =
                start(
                        """
                        Object d = ui.open("dialogue");
                        d.set("line", "Did you bring the crystal?");
                        int choice = await("pick");
                        out.add(choice);
                        d.close();
                        """);

        framesUntil(() -> "Did you bring the crystal?".equals(labelText("dialogue", "line")));
        assertEquals("pick", runtime.getAwaitTag());

        click(find("dialogue", "decline"));
        framesUntil(() -> !recorder.values().isEmpty());
        assertEquals(List.of(2), recorder.values());

        framesUntil(() -> manager.surfaceCount() == 0);
        assertEquals(0, manager.specCount());
    }

    @Test
    void submittedTextResumesTheScript() {
        start(
                """
                Object d = ui.open("dialogue");
                string name = await("name");
                out.add("Hello, " + name);
                d.close();
                """);
        framesUntil(() -> find("dialogue", "name") != null);

        submit(find("dialogue", "name"), "Ikala");
        framesUntil(() -> !recorder.values().isEmpty());
        assertEquals(List.of("Hello, Ikala"), recorder.values());
    }

    @Test
    void repeatItemsResumeWithTheirItem() {
        start(
                """
                Object d = ui.open("rows");
                d.setList("rows", List.of("north", "south"));
                out.add(await("row"));
                """);
        framesUntil(() -> find("rows", "row#south") != null);

        click(find("rows", "row#south"));
        framesUntil(() -> !recorder.values().isEmpty());
        assertEquals(List.of("south"), recorder.values());
    }

    @Test
    void scriptsDriveTheUiThroughTest() {
        start(
                """
                Object d = ui.open("dialogue");
                d.set("line", "Did you bring the crystal?");
                Object t = ui.test();
                await(t.assertText("dialogue//line", "Did you bring the crystal?"));
                await(t.click("dialogue//accept"));
                out.add(await("pick"));
                await(t.type("dialogue//name", "Ikala", true));
                out.add(await("name"));
                out.add(await(t.exists("dialogue//decline")));
                out.add(await(t.exists("dialogue//nothing")));
                d.close();
                await(t.waitForGone("dialogue"));
                out.add("done");
                """);
        framesUntil(() -> recorder.values().contains("done"));
        assertEquals(List.of(1, "Ikala", true, false, "done"), recorder.values());
    }

    @Test
    void aFailedCheckStopsTheScript() {
        ScriptRuntime runtime =
                start(
                        """
                        Object d = ui.open("dialogue");
                        d.set("line", "Hello");
                        await(ui.test().assertText("dialogue//line", "Goodbye"));
                        out.add("not reached");
                        """);
        framesUntil(
                () ->
                        !ScriptManager.getRunningScripts().contains(runtime)
                                && !ScriptManager.getYieldedScripts().containsKey(runtime));
        frames(3);
        assertTrue(recorder.values().isEmpty(), recorder.values().toString());
    }

    @Test
    void handlesAreDeadAfterClosing() {
        start(
                """
                Object d = ui.open("dialogue");
                d.close();
                out.add(d.isOpen());
                d.set("line", "ignored");
                out.add(d.get("line"));
                d.setVisible(true);
                out.add("done");
                """);
        framesUntil(() -> recorder.values().size() == 3);
        List<Object> expected = new ArrayList<>();
        expected.add(false);
        expected.add(null);
        expected.add("done");
        assertEquals(expected, recorder.values());
        framesUntil(() -> manager.surfaceCount() == 0);
    }

    @Test
    void specsStartScriptsAtALabel() throws IOException {
        Files.writeString(
                scripts.resolve("greet.iks"),
                """
                out.add("skipped");
                second:
                out.add("got " + event.value());
                """);
        UiSpec spec =
                SpecLoader.load(
                        new ByteArrayInputStream(
                                        """
                                surface: { id: greet, anchors: top-left, width: 400, height: 300 }
                                content:
                                  type: column
                                  id: root
                                  children:
                                    - { type: text-input, id: name, onSubmit: "script(greet.iks#second)" }
                                """
                                        .getBytes(StandardCharsets.UTF_8)),
                        "greet");
        SpecInstance.open(manager, owner, spec, new SpecBindings().scriptFolder(scripts));
        frames(2);

        submit(find("greet", "name"), "Mira");
        framesUntil(() -> !recorder.values().isEmpty());
        assertEquals(List.of("got Mira"), recorder.values());
    }

    @Test
    void resumeNeedsASpecOpenedByAScript() {
        String message =
                assertThrows(
                                SpecException.class,
                                () ->
                                        SpecInstance.open(
                                                manager,
                                                owner,
                                                spec("dialogue"),
                                                new SpecBindings()
                                                        .value("line", Observable.of(""))))
                        .getMessage();
        assertTrue(message.contains("needs a spec opened by a script"), message);
        assertEquals(0, manager.specCount());
    }

    @Test
    void scriptActionsNeedTheFile() {
        UiSpec spec =
                SpecLoader.load(
                        new ByteArrayInputStream(
                                        """
                                surface: { id: missing }
                                content: { type: button, id: go, text: Go, onClick: "script(../escape.iks)" }
                                """
                                        .getBytes(StandardCharsets.UTF_8)),
                        "missing");
        String message =
                assertThrows(
                                SpecException.class,
                                () ->
                                        SpecInstance.open(
                                                manager,
                                                owner,
                                                spec,
                                                new SpecBindings().scriptFolder(scripts)))
                        .getMessage();
        assertTrue(message.contains("no script '../escape.iks'"), message);
    }

    @Test
    void actionsWrittenWrongAreReported() {
        UiSpec spec =
                SpecLoader.load(
                        new ByteArrayInputStream(
                                        """
                                surface: { id: wrong }
                                content: { type: button, id: go, text: Go, onClick: "resume( )" }
                                """
                                        .getBytes(StandardCharsets.UTF_8)),
                        "wrong");
        String message =
                assertThrows(
                                SpecException.class,
                                () -> SpecInstance.open(manager, owner, spec, new SpecBindings()))
                        .getMessage();
        assertTrue(message.contains("content.onClick: resume(...) needs a tag"), message);
    }

    /**
     * Run a script of the stand-in plugin that holds one of its objects, shows it in a spec, and
     * has another queued for an await, then unload the plugin. In a method of its own so no local
     * keeps the plugin's classes alive afterwards.
     *
     * @return A weak reference to the plugin's class loader.
     */
    private WeakReference<ClassLoader> runPluginScriptAndUnload() throws Exception {
        PluginLoader loader = new PluginLoader(getClass().getClassLoader());
        Class<?> valueClass = loader.loadClass(PLUGIN_PACKAGE + "PluginSide$Value");
        assertNotEquals(getClass().getClassLoader(), valueClass.getClassLoader());
        Object value = valueClass.getConstructor(String.class).newInstance("plugin value");

        ScriptRuntime runtime =
                ScriptManager.start(
                                ScriptLaunch.source(
                                                """
                                                Object d = ui.open("dialogue");
                                                d.set("line", thing);
                                                out.add("opened");
                                                int c = await("pick");
                                                """)
                                        .owner(PLUGIN)
                                        .global("thing", value))
                        .orElseThrow();
        framesUntil(
                () ->
                        recorder.values().contains("opened")
                                && "Value[name=plugin value]"
                                        .equals(labelText("dialogue", "line")));
        // Queued for an await the script never reaches
        ScriptManager.resume(runtime, "later", value);

        ScriptManager.terminateAllOwnedBy(PLUGIN);
        manager.removeAllOwnedBy(owner);
        frames(3);

        assertTrue(runtime.hasTerminated());
        assertTrue(runtime.getSymbolTable().isEmpty());
        assertEquals(0, manager.surfaceCount());
        assertEquals(0, manager.specCount());
        return new WeakReference<>(loader);
    }

    @Test
    void unloadingReleasesThePluginsScriptsAndUi() throws Exception {
        WeakReference<ClassLoader> loader = runPluginScriptAndUnload();
        assertNotNull(manager);

        for (int i = 0; i < 50 && loader.get() != null; ++i) {
            System.gc();
            Thread.sleep(20);
        }
        if (loader.get() != null) {
            // Records' toString and equals are linked through softly cached method handles, so
            // apply memory pressure to clear them, as SpecUnloadTest does
            clearSoftReferences();
            System.gc();
        }
        assertNull(loader.get(), "Something still holds the unloaded plugin's classes");
        assertFalse(
                ScriptManager.getYieldedScripts().keySet().stream()
                        .anyMatch(script -> PLUGIN.equals(script.getOwner())));
    }

    /** Fill the heap until soft references are cleared, which the JVM does before running out. */
    private static void clearSoftReferences() {
        try {
            List<long[]> filler = new ArrayList<>();
            while (true) {
                filler.add(new long[16 << 20]);
            }
        } catch (OutOfMemoryError e) {
            // Expected: the filler is dropped here, and soft references were cleared to make room
        }
    }

    /**
     * Loads the stand-in plugin's classes itself, like a plugin class loader, and the rest from its
     * parent.
     */
    private static final class PluginLoader extends ClassLoader {
        private final ReentrantLock lock = new ReentrantLock();

        PluginLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.startsWith(PLUGIN_PACKAGE)) {
                return super.loadClass(name, resolve);
            }
            lock.lock();
            try {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    String resource = name.replace('.', '/') + ".class";
                    try (InputStream stream = getParent().getResourceAsStream(resource)) {
                        if (stream == null) {
                            throw new ClassNotFoundException(name);
                        }
                        byte[] bytes = stream.readAllBytes();
                        loaded = defineClass(name, bytes, 0, bytes.length);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
                return loaded;
            } finally {
                lock.unlock();
            }
        }
    }
}
