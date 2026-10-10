package com.ikalagaming.graphics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.data.Context;
import com.ikalagaming.graphics.gui.enums.Key;
import com.ikalagaming.graphics.gui.flags.ConfigFlags;
import com.ikalagaming.graphics.ui.Anchors;
import com.ikalagaming.graphics.ui.Button;
import com.ikalagaming.graphics.ui.Canvas;
import com.ikalagaming.graphics.ui.Column;
import com.ikalagaming.graphics.ui.Label;
import com.ikalagaming.graphics.ui.Layer;
import com.ikalagaming.graphics.ui.Node;
import com.ikalagaming.graphics.ui.Scroll;
import com.ikalagaming.graphics.ui.Selectable;
import com.ikalagaming.graphics.ui.Sizing;
import com.ikalagaming.graphics.ui.Surface;
import com.ikalagaming.graphics.ui.TextInput;
import com.ikalagaming.graphics.ui.UiManager;
import com.ikalagaming.graphics.ui.VirtualGrid;
import com.ikalagaming.graphics.ui.automation.UiAutomationException;
import com.ikalagaming.graphics.ui.automation.UiTestDriver;
import com.ikalagaming.graphics.ui.graph.GraphNode;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

/** Automation runs driving retained UI with real IkGui input, on a headless IkGui context. */
class UiAutomationTest {
    private Context context;
    private UiManager manager;
    private GraphicsContext owner;
    private UiTestDriver driver;

    /** What buttons were clicked, in order. */
    private final List<String> clicks = new ArrayList<>();

    @BeforeEach
    void setUp() {
        context = IkGui.createContext();
        context.io.iniFilename = null;
        context.io.displaySize.set(1280, 720);
        context.io.mouseInsideWindow = true;
        manager = new UiManager();
        owner = new GraphicsContext("Test-Plugin", null);
        driver = new UiTestDriver(manager, owner);
    }

    @AfterEach
    void tearDown() {
        IkGui.destroyContext();
    }

    private Button button(String id, String text) {
        return new Button(id, text).onClick(() -> clicks.add(id));
    }

    private Surface show(String id, Node<?> content) {
        Surface surface = new Surface(id).anchors(Anchors.topLeft()).content(content);
        manager.add(owner, surface);
        driver.frames(2);
        return surface;
    }

    private Surface showMenu() {
        return show(
                "menu",
                new Column("root")
                        .add(
                                new Label("title", "Main menu"),
                                button("play", "Play"),
                                button("quit", "Quit")));
    }

    private String failure(Consumer<com.ikalagaming.graphics.ui.automation.Steps> build) {
        return assertThrows(UiAutomationException.class, () -> driver.run(build)).getMessage();
    }

    @Test
    void clickingFiresTheButtonOnce() {
        showMenu();
        driver.run(steps -> steps.click("menu//play"));
        assertEquals(List.of("play"), clicks);
    }

    @Test
    void selectorsFindByPathIdTextAndAnySurface() {
        showMenu();
        driver.run(
                steps ->
                        steps.click("menu/play")
                                .click("menu//text=Quit")
                                .click("*//play")
                                .assertText("menu//title", "Main menu")
                                .assertVisible("menu"));
        assertEquals(List.of("play", "quit", "play"), clicks);
    }

    @Test
    void missesExplainWhatIsThere() {
        showMenu();
        String path = failure(steps -> steps.click("menu/plya"));
        assertTrue(path.contains("'menu' has no child with ID 'plya'"), path);
        assertTrue(path.contains("children: title, play, quit"), path);

        String anywhere = failure(steps -> steps.assertVisible("menu//text=Options"));
        assertTrue(
                anywhere.startsWith("assertVisible(menu//text=Options): nothing under 'menu'"),
                anywhere);

        String surface = failure(steps -> steps.assertVisible("options//play"));
        assertTrue(
                surface.contains("no shown surface matches 'options//play' (shown: menu)"),
                surface);

        show("other", new Column("root").add(new Label("note", "Elsewhere")));
        String elsewhere = failure(steps -> steps.assertVisible("other//play"));
        assertTrue(
                elsewhere.contains("nothing under 'other' has ID 'play'; found at menu/play"),
                elsewhere);
    }

    @Test
    void surfaceIdsMayHoldSlashes() {
        show("converter/about", new Column("root").add(button("close", "Close")));
        manager.add(
                owner,
                new Surface("converter")
                        .anchors(Anchors.topRight())
                        .content(new Column("root").add(button("close", "Close"))));
        driver.frames(2);
        driver.run(steps -> steps.click("converter/about//close").click("converter/close"));
        assertEquals(List.of("close", "close"), clicks);
        String message = failure(steps -> steps.click("converter/about/root"));
        assertTrue(message.contains("'converter/about' has no child with ID 'root'"), message);
    }

    @Test
    void twoMatchesAreAmbiguous() {
        show(
                "menu",
                new Column("root")
                        .add(
                                new Column("left").add(button("ok", "OK")),
                                new Column("right").add(button("ok", "OK"))));
        String message = failure(steps -> steps.click("menu//ok"));
        assertTrue(message.contains("ambiguous: menu/left/ok, menu/right/ok"), message);
        assertTrue(clicks.isEmpty());
    }

    @Test
    void labelsCantBeClicked() {
        showMenu();
        String message = failure(steps -> steps.click("menu//title"));
        assertTrue(message.contains("'menu/title' is a Label, which can't be clicked"), message);
    }

    @Test
    void aCoveredButtonIsNotClicked() {
        showMenu();
        manager.add(
                owner,
                new Surface("cover")
                        .anchors(Anchors.topLeft())
                        .width(Sizing.fixed(600))
                        .height(Sizing.fixed(600))
                        .layer(Layer.OVERLAY));
        driver.frames(2);

        String message = failure(steps -> steps.click("menu//play"));
        assertTrue(message.contains("is over window"), message);
        assertTrue(message.contains("but 'menu/play' isn't hovered"), message);
        assertTrue(clicks.isEmpty());
    }

    @Test
    void typingReplacesTheTextAndEnterSubmits() {
        List<String> submitted = new ArrayList<>();
        TextInput name = new TextInput("name", "old").onSubmit(submitted::add);
        show("form", new Column("root").width(Sizing.fixed(300)).add(name));

        driver.run(steps -> steps.type("form//name", "new"));
        assertEquals("new", name.text());

        driver.run(steps -> steps.type("form//name", "Ada", true));
        assertEquals("Ada", name.text());
        assertEquals("Ada", submitted.getLast());

        driver.run(steps -> steps.type("form//name", ""));
        assertEquals("", name.text());
    }

    @Test
    void waitsSeeSurfacesShownLater() {
        CompletableFuture<Void> run =
                manager.getAutomation()
                        .run(owner, steps -> steps.waitForVisible("late//go").click("late//go"));
        driver.frames(30);
        assertFalse(run.isDone());

        manager.add(
                owner, new Surface("late").anchors(Anchors.topLeft()).content(button("go", "Go")));
        driver.await(run);
        assertEquals(List.of("go"), clicks);
    }

    @Test
    void waitsTimeOutInIkGuiTime() {
        long start = context.time;
        String message = failure(steps -> steps.timeout(2).waitForVisible("never"));
        assertTrue(message.startsWith("waitForVisible(never): timed out after 2.00 s"), message);
        long waited = context.time - start;
        assertTrue(waited >= 2000 && waited < 2200, "Waited " + waited + " ms");
    }

    @Test
    void waitForTextAndGone() {
        Label status = new Label("status", "Working");
        Surface surface = show("job", new Column("root").add(status));
        CompletableFuture<Void> run =
                manager.getAutomation()
                        .run(
                                owner,
                                steps ->
                                        steps.waitForText("job//status", "Done")
                                                .waitForGone("job"));
        driver.frames(5);
        status.text("Done");
        driver.frames(5);
        manager.remove(owner, surface.getId());
        driver.await(run);
    }

    @Test
    void buttonsDeepInAScrollAreScrolledTo() {
        Column rows = new Column("rows");
        for (int i = 0; i < 50; ++i) {
            rows.add(button("b" + i, "Row " + i));
        }
        show(
                "list",
                new Column("root")
                        .width(Sizing.fixed(200))
                        .add(new Scroll("scroll").height(Sizing.fixed(100)).content(rows)));
        assertFalse(manager.get("list").getContent().getChildren().isEmpty());

        driver.run(steps -> steps.click("list//b40"));
        assertEquals(List.of("b40"), clicks);
    }

    @Test
    void virtualGridCellsAreMadeByScrolling() {
        VirtualGrid grid =
                new VirtualGrid("cells")
                        .cellSize(80, 20)
                        .height(Sizing.fixed(100))
                        .items(
                                10_000,
                                index ->
                                        new Selectable("c" + index, "Cell " + index)
                                                .onClick(() -> clicks.add("c" + index)));
        show("grid", new Column("root").width(Sizing.fixed(200)).add(grid));
        assertEquals(null, grid.getCell(5000));

        driver.run(steps -> steps.scrollToCell("grid//cells", 5000).click("grid//c5000"));
        assertEquals(List.of("c5000"), clicks);
    }

    @Test
    void graphNodesOnAZoomedCanvasAreClicked() {
        Canvas canvas =
                new Canvas("graph").zoom(2).width(Sizing.fixed(300)).height(Sizing.fixed(200));
        for (int i = 0; i < 3; ++i) {
            String id = "n" + i;
            GraphNode node = new GraphNode(id).label("Node " + i).onActivate(() -> clicks.add(id));
            node.position(i * 200, 0);
            canvas.add(node);
        }
        show("graph", new Column("root").add(canvas));

        // The last node starts out of view, so the canvas pans to it
        driver.run(steps -> steps.click("graph//text=Node 2").click("graph//n0"));
        assertEquals(List.of("n2", "n0"), clicks);
    }

    @Test
    void keysMoveNavigationFocus() {
        context.io.configFlags |= ConfigFlags.NAV_ENABLE_KEYBOARD;
        showMenu();
        driver.run(steps -> steps.click("menu//play").key(Key.ARROW_DOWN).key(Key.SPACE));
        assertEquals(List.of("play", "quit"), clicks);
    }

    @Test
    void runsTakeTurns() {
        showMenu();
        CompletableFuture<Void> first =
                manager.getAutomation().run(owner, steps -> steps.click("menu//play"));
        CompletableFuture<Void> second =
                manager.getAutomation().run(owner, steps -> steps.click("menu//quit"));
        assertTrue(manager.getAutomation().isDriving());
        driver.await(second);
        assertTrue(first.isDone());
        assertEquals(List.of("play", "quit"), clicks);
        driver.frames(1);
        assertFalse(manager.getAutomation().isDriving());
    }

    @Test
    void unloadingCancelsTheOwnersRuns() {
        showMenu();
        CompletableFuture<Void> run =
                manager.getAutomation().run(owner, steps -> steps.waitForVisible("never"));
        driver.frames(3);
        manager.removeAllOwnedBy(owner);
        driver.frames(2);

        assertTrue(run.isCompletedExceptionally());
        CompletionException thrown = assertThrows(CompletionException.class, run::join);
        assertEquals("Test-Plugin has unloaded", thrown.getCause().getMessage());
        assertFalse(manager.getAutomation().isDriving());
    }

    @Test
    void existsReportsWithoutFailing() {
        showMenu();
        List<Boolean> found = new ArrayList<>();
        driver.run(
                steps -> steps.exists("menu//play", found::add).exists("menu//nope", found::add));
        assertEquals(List.of(true, false), found);
    }
}
