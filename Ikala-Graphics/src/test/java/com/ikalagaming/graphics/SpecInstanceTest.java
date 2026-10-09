package com.ikalagaming.graphics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.data.Context;
import com.ikalagaming.graphics.gui.enums.Key;
import com.ikalagaming.graphics.gui.enums.MouseButton;
import com.ikalagaming.graphics.gui.util.RectFloat;
import com.ikalagaming.graphics.ui.Button;
import com.ikalagaming.graphics.ui.Column;
import com.ikalagaming.graphics.ui.Label;
import com.ikalagaming.graphics.ui.Length;
import com.ikalagaming.graphics.ui.Node;
import com.ikalagaming.graphics.ui.UiManager;
import com.ikalagaming.graphics.ui.spec.Observable;
import com.ikalagaming.graphics.ui.spec.ObservableList;
import com.ikalagaming.graphics.ui.spec.SpecBindings;
import com.ikalagaming.graphics.ui.spec.SpecEvent;
import com.ikalagaming.graphics.ui.spec.SpecException;
import com.ikalagaming.graphics.ui.spec.SpecInstance;
import com.ikalagaming.graphics.ui.spec.SpecLoader;
import com.ikalagaming.graphics.ui.spec.UiSpec;
import com.ikalagaming.graphics.ui.style.StyleKey;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.ListResourceBundle;

/** UI specs opened on a headless IkGui context. */
class SpecInstanceTest {

    /** A list item, read through reflection by bindings. */
    public record Item(String name, int count) {}

    private Context context;
    private UiManager manager;
    private GraphicsContext owner;

    @BeforeEach
    void setUp() {
        context = IkGui.createContext();
        context.io.iniFilename = null;
        context.io.displaySize.set(1280, 720);
        context.io.mouseInsideWindow = true;
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
        manager.dispatchEvents();
    }

    private void frames(int count) {
        for (int i = 0; i < count; ++i) {
            frame();
        }
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

    private static UiSpec spec(String content) {
        return spec("surface: { id: test, anchors: top-left, width: 400, height: 300 }\n", content);
    }

    private static UiSpec spec(String header, String content) {
        String yaml = header + content;
        return SpecLoader.load(
                new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), "test");
    }

    private SpecInstance open(UiSpec spec, SpecBindings bindings) {
        SpecInstance instance = SpecInstance.open(manager, owner, spec, bindings);
        frames(2);
        return instance;
    }

    @Test
    void openingBuildsTheTree() {
        SpecInstance instance =
                open(
                        spec(
                                """
                                content:
                                  type: column
                                  id: root
                                  children:
                                    - type: row
                                      id: buttons
                                      children:
                                        - { type: button, id: start, text: Start }
                                """),
                        new SpecBindings());

        assertEquals(1, manager.surfaceCount());
        assertEquals(1, manager.specCount());
        Button start = assertInstanceOf(Button.class, instance.find("buttons/start"));
        assertEquals("Start", start.getText());
        assertInstanceOf(Column.class, instance.find(""));
        assertNull(instance.find("buttons/missing"));
    }

    @Test
    void missingNamesFailBeforeAnythingShows() {
        UiSpec spec = spec("content: { type: button, id: go, text: Go, onClick: start }\n");

        String message =
                assertThrows(SpecException.class, () -> open(spec, new SpecBindings()))
                        .getMessage();
        assertTrue(message.contains("content.onClick: no handler 'start'"), message);
        assertEquals(0, manager.surfaceCount());

        assertTrue(
                assertThrows(
                                SpecException.class,
                                () ->
                                        open(
                                                spec(
                                                        "content: { type: label, id: l, text: \"{nope}\" }\n"),
                                                new SpecBindings()))
                        .getMessage()
                        .contains("no observable 'nope'"));
        assertTrue(
                assertThrows(
                                SpecException.class,
                                () ->
                                        open(
                                                spec(
                                                        "content: { type: label, id: l, colour: red }\n"),
                                                new SpecBindings()))
                        .getMessage()
                        .contains("content.colour: unknown property for a label node"));
        assertTrue(
                assertThrows(
                                SpecException.class,
                                () ->
                                        open(
                                                spec("content: { type: widget, id: w }\n"),
                                                new SpecBindings()))
                        .getMessage()
                        .contains("unknown node type 'widget'"));
        assertTrue(
                assertThrows(
                                SpecException.class,
                                () ->
                                        open(
                                                spec(
                                                        "content: { type: label, id: l, children: [ { type: label, id: c } ] }\n"),
                                                new SpecBindings()))
                        .getMessage()
                        .contains("a label can't have children"));
    }

    @Test
    void handlersRunWithTheirEvent() {
        List<SpecEvent> events = new ArrayList<>();
        SpecInstance instance =
                open(
                        spec(
                                "content: { type: button, id: go, text: Go, onClick: start, width: 100 }\n"),
                        new SpecBindings().handler("start", events::add));

        click(instance.find(""));

        assertEquals(1, events.size());
        assertSame(instance.find(""), events.getFirst().node());
    }

    @Test
    void submittedTextReachesTheHandler() {
        List<String> submitted = new ArrayList<>();
        SpecInstance instance =
                open(
                        spec(
                                """
                                content:
                                  type: column
                                  id: root
                                  children:
                                    - { type: text-input, id: name, onSubmit: named }
                                """),
                        new SpecBindings().handler("named", event -> submitted.add(event.value())));

        click(instance.find("name"));
        context.io.addInputCharacters("Ikala");
        frame();
        context.io.addKeyEvent(Key.ENTER, true);
        frame();
        context.io.addKeyEvent(Key.ENTER, false);
        frames(2);

        assertEquals(List.of("Ikala"), submitted);
    }

    @Test
    void observablesUpdateFromAnyThread() throws InterruptedException {
        Observable<Integer> count = Observable.of(1);
        Observable<Boolean> shown = Observable.of(true);
        SpecInstance instance =
                open(
                        spec(
                                """
                                content:
                                  type: column
                                  id: root
                                  children:
                                    - { type: label, id: count, text: "Count: {count}", visible: "{shown}" }
                                """),
                        new SpecBindings().value("count", count).value("shown", shown));
        Label label = (Label) instance.find("count");
        assertEquals("Count: 1", label.getText());

        Thread other =
                new Thread(
                        () -> {
                            count.set(5);
                            shown.set(false);
                        });
        other.start();
        other.join();
        assertEquals("Count: 1", label.getText(), "Applied on the render thread, next frame");
        frame();

        assertEquals("Count: 5", label.getText());
        assertFalse(label.isVisible());
    }

    @Test
    void repeatsKeepNodesForUnchangedItems() {
        ObservableList<Item> items =
                new ObservableList<>(List.of(new Item("a", 1), new Item("b", 2)));
        List<Object> clicked = new ArrayList<>();
        SpecInstance instance =
                open(
                        spec(
                                """
                                templates:
                                  row:
                                    node: { type: button, id: row, text: "{item.name} x{item.count}", onClick: pick, width: 100 }
                                content:
                                  type: column
                                  id: root
                                  repeat: { list: items, as: item, template: row, key: "{item.name}" }
                                """),
                        new SpecBindings()
                                .list("items", items)
                                .handler("pick", event -> clicked.add(event.item())));

        Button a = (Button) instance.find("row#a");
        assertEquals("a x1", a.getText());

        items.set(List.of(new Item("c", 3), new Item("a", 1)));
        frame();

        assertSame(a, instance.find("row#a"), "Unchanged items keep their node");
        assertNull(instance.find("row#b"));
        Column root = (Column) instance.find("");
        assertEquals("row#c", root.getChildren().getFirst().getId(), "In list order");

        frames(2);
        click(instance.find("row#c"));
        assertEquals(List.of(new Item("c", 3)), clicked);
    }

    @Test
    void textCanBeLocalized() {
        ListResourceBundle bundle =
                new ListResourceBundle() {
                    @Override
                    protected Object[][] getContents() {
                        return new Object[][] {{"GREETING", "Hello, {name}"}};
                    }
                };
        SpecInstance instance =
                open(
                        spec(
                                """
                                content:
                                  type: column
                                  id: root
                                  children:
                                    - { type: label, id: hi, text: "@GREETING" }
                                    - { type: label, id: at, text: "@@home" }
                                """),
                        new SpecBindings().bundle(bundle).value("name", Observable.of("Ches")));

        assertEquals("Hello, Ches", ((Label) instance.find("hi")).getText());
        assertEquals("@home", ((Label) instance.find("at")).getText());
    }

    @Test
    void spacingTokensBecomeInlineStyle() {
        SpecInstance instance =
                open(
                        spec(
                                """
                                content:
                                  type: column
                                  id: root
                                  gap: $spacing.medium
                                  padding: 4
                                  children: [ { type: label, id: a, text: A }, { type: label, id: b, text: B } ]
                                """),
                        new SpecBindings());

        Node<?> root = instance.find("");
        assertEquals(Length.u(8), root.getStyle().length(StyleKey.GAP), "From the default theme");
        float gap =
                instance.find("b").getRect().getTop() - instance.find("a").getRect().getBottom();
        assertEquals(8, gap, 1e-3);
        assertEquals(root.getRect().getLeft() + 4, instance.find("a").getRect().getLeft(), 1e-3);
    }

    @Test
    void closingDetachesListenersAndRemovesTheSurface() {
        Observable<String> text = Observable.of("one");
        ObservableList<String> list = new ObservableList<>(List.of("x"));
        SpecInstance instance =
                open(
                        spec(
                                """
                                templates:
                                  entry: { node: { type: label, id: e, text: "{s}" } }
                                content:
                                  type: column
                                  id: root
                                  children: [ { type: label, id: t, text: "{text}" } ]
                                  repeat: { list: items, as: s, template: entry }
                                """),
                        new SpecBindings().value("text", text).list("items", list));
        Label label = (Label) instance.find("t");
        assertEquals(1, text.listenerCount());
        assertEquals(1, list.listenerCount());

        instance.close();
        frame();
        text.set("two");
        frame();

        assertTrue(instance.isClosed());
        assertEquals(0, text.listenerCount());
        assertEquals(0, list.listenerCount());
        assertEquals("one", label.getText());
        assertEquals(0, manager.surfaceCount());
        assertEquals(0, manager.specCount());
    }

    @Test
    void unloadingClosesSpecsAndNodeTypes() {
        GraphicsContext library = new GraphicsContext("Library-Plugin", null);
        manager.getNodeTypes()
                .register(
                        library, "library.banner", (id, p) -> new Label(id, p.string("text", "")));
        Observable<String> text = Observable.of("mine");
        SpecInstance mine =
                open(
                        spec("content: { type: label, id: l, text: \"{text}\" }\n"),
                        new SpecBindings().value("text", text));
        GraphicsContext user = new GraphicsContext("User-Plugin", null);
        SpecInstance usesBanner =
                SpecInstance.open(
                        manager,
                        user,
                        spec(
                                "surface: { id: other }\n",
                                "content: { type: library.banner, id: b, text: Hi }\n"),
                        new SpecBindings());
        frames(2);
        assertEquals(2, manager.specCount());

        manager.removeAllOwnedBy(library);
        frame();

        assertTrue(usesBanner.isClosed(), "Built from the unloaded plugin's node type");
        assertFalse(mine.isClosed());
        assertFalse(manager.getNodeTypes().names().contains("library.banner"));

        manager.removeAllOwnedBy(owner);
        frame();
        assertTrue(mine.isClosed());
        assertEquals(0, text.listenerCount());
        assertEquals(0, manager.specCount());
        assertEquals(0, manager.surfaceCount());
    }

    @Test
    void changingBindingsAfterOpeningDoesNothing() {
        List<String> heard = new ArrayList<>();
        SpecBindings bindings = new SpecBindings().handler("go", () -> heard.add("original"));
        SpecInstance instance =
                open(
                        spec(
                                "content: { type: button, id: go, text: Go, onClick: go, width: 100 }\n"),
                        bindings);

        bindings.handler("go", () -> heard.add("replaced"));
        click(instance.find(""));

        assertEquals(List.of("original"), heard, "The spec keeps what it was opened with");
    }

    @Test
    void closingFromAnotherThreadTearsDownOnTheRenderThread() throws InterruptedException {
        Observable<String> text = Observable.of("one");
        SpecInstance instance =
                open(
                        spec("content: { type: label, id: l, text: \"{text}\" }\n"),
                        new SpecBindings().value("text", text));

        Label label = (Label) instance.find("");
        Thread other = new Thread(instance::close);
        other.start();
        other.join();
        assertTrue(instance.isClosed(), "Closed straight away");
        text.set("two");
        frame();

        assertEquals("one", label.getText(), "The queued update was skipped");
        assertEquals(0, text.listenerCount(), "Torn down on the next frame");
        assertEquals(0, manager.surfaceCount());
    }

    @Test
    void replacingTheSurfaceClosesTheSpec() {
        SpecInstance instance =
                open(spec("content: { type: label, id: l, text: Hi }\n"), new SpecBindings());

        manager.add(owner, new com.ikalagaming.graphics.ui.Surface("test"));

        assertTrue(instance.isClosed());
        assertEquals(0, manager.specCount());
        assertNotNull(manager.get("test"));
    }
}
