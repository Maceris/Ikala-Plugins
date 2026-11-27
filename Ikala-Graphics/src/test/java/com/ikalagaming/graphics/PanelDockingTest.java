package com.ikalagaming.graphics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.IkGuiInternal;
import com.ikalagaming.graphics.gui.data.Context;
import com.ikalagaming.graphics.gui.data.Window;
import com.ikalagaming.graphics.gui.flags.ConfigFlags;
import com.ikalagaming.graphics.gui.util.Hash;
import com.ikalagaming.graphics.gui.util.RectFloat;
import com.ikalagaming.graphics.ui.Anchors;
import com.ikalagaming.graphics.ui.Button;
import com.ikalagaming.graphics.ui.Column;
import com.ikalagaming.graphics.ui.DockLayout;
import com.ikalagaming.graphics.ui.DockSpace;
import com.ikalagaming.graphics.ui.DockSpaceSettings;
import com.ikalagaming.graphics.ui.Image;
import com.ikalagaming.graphics.ui.Insets;
import com.ikalagaming.graphics.ui.Label;
import com.ikalagaming.graphics.ui.MenuItem;
import com.ikalagaming.graphics.ui.Panel;
import com.ikalagaming.graphics.ui.Sizing;
import com.ikalagaming.graphics.ui.Surface;
import com.ikalagaming.graphics.ui.UiManager;
import com.ikalagaming.graphics.ui.automation.UiTestDriver;
import com.ikalagaming.graphics.ui.spec.SpecBindings;
import com.ikalagaming.graphics.ui.spec.SpecInstance;
import com.ikalagaming.graphics.ui.spec.SpecLoader;
import com.ikalagaming.graphics.ui.spec.UiSpec;
import com.ikalagaming.graphics.ui.style.Style;
import com.ikalagaming.graphics.ui.style.StyleKey;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Panels docking into a dock space, keeping the user's layout, and node drag and drop. */
class PanelDockingTest {
    private UiManager manager;
    private GraphicsContext owner;
    private UiTestDriver driver;
    private DockSpace dockSpace;

    /** What the drop target was given. */
    private final List<String> dropped = new ArrayList<>();

    /** Which context menu entries were chosen. */
    private final List<String> chosen = new ArrayList<>();

    @BeforeEach
    void setUp() {
        newContext();
        manager = new UiManager();
        owner = new GraphicsContext("Test-Plugin", null);
        driver = new UiTestDriver(manager, owner);
    }

    /** Make a fresh IkGui context with docking on and no ini file. */
    private static void newContext() {
        Context context = IkGui.createContext();
        DockSpaceSettings.install();
        context.io.iniFilename = null;
        context.io.displaySize.set(1280, 720);
        context.io.mouseInsideWindow = true;
        context.io.configFlags |= ConfigFlags.DOCKING_ENABLE;
    }

    @AfterEach
    void tearDown() {
        IkGui.destroyContext();
    }

    /** Show a see-through surface holding a dock space, then four panels. */
    private void showWorkspace() {
        showWorkspace(
                new DockLayout()
                        .split(DockLayout.Side.LEFT, 0.25f, "files")
                        .splitPrevious(DockLayout.Side.BOTTOM, 0.4f, "outliner")
                        .split(DockLayout.Side.RIGHT, 0.3f, "details", "notes")
                        .center());
    }

    /**
     * Show a see-through surface holding a dock space, then the four panels and any others.
     *
     * @param layout The dock space's layout.
     * @param extra The IDs of more panels to show.
     */
    private void showWorkspace(DockLayout layout, String... extra) {
        dockSpace = new DockSpace("dock", layout);
        manager.add(
                owner,
                new Surface("workspace")
                        .anchors(Anchors.fill())
                        .transparent()
                        .style(Style.builder().set(StyleKey.PADDING, Insets.NONE).build())
                        .content(new Column("root").add(dockSpace)));
        manager.add(owner, panel("files", "Files"));
        manager.add(owner, panel("outliner", "Outliner"));
        manager.add(owner, panel("details", "Details"));
        manager.add(owner, panel("notes", "Notes"));
        for (String id : extra) {
            manager.add(owner, panel(id, id));
        }
        driver.frames(3);
    }

    /**
     * A panel holding one label.
     *
     * @param id The panel ID.
     * @param title The title.
     * @return The panel.
     */
    private static Panel panel(String id, String title) {
        Panel panel = new Panel(id, title);
        panel.content(new Column("root").add(new Label("text", title + " text")));
        return panel;
    }

    /**
     * The window a panel draws.
     *
     * @param id The panel ID.
     * @return The window.
     */
    private static Window window(String id) {
        Window window = IkGuiInternal.findWindowByID(Hash.getID("###ui/" + id));
        assertNotNull(window, "No window for panel " + id);
        return window;
    }

    @Test
    void layoutDocksPanels() {
        showWorkspace();
        Window files = window("files");
        Window details = window("details");
        Window notes = window("notes");
        assertTrue(files.dockIsActive, "files docked");
        assertTrue(details.dockIsActive, "details docked");
        assertSame(details.dockNode, notes.dockNode, "details and notes share a node as tabs");
        assertNotSame(files.dockNode, details.dockNode);
        assertTrue(files.position.x < details.position.x, "files left of details");
        assertEquals(0, files.position.x, 1, "files at the left edge");
        // The left split takes a quarter
        assertEquals(1280 * 0.25f, files.size.x, 8);
        // The outliner takes the bottom of the left split, under the files
        Window outliner = window("outliner");
        assertTrue(outliner.dockIsActive, "outliner docked");
        assertEquals(files.position.x, outliner.position.x, 1);
        assertTrue(outliner.position.y > files.position.y, "outliner under files");
        assertEquals(720 * 0.4f, outliner.size.y, 8);
    }

    @Test
    void savedLayoutIsKept() {
        showWorkspace();
        // The user pulls notes out of the dock, which a rebuilt layout would undo
        IkGuiInternal.dockContextQueueUndockWindow(window("notes"));
        driver.frames(2);
        assertFalse(window("notes").dockIsActive);
        float detailsWidth = window("details").size.x;
        String ini = IkGui.saveIniSettingsToMemory();
        assertTrue(ini.contains("[Docking]"), ini);
        IkGui.destroyContext();

        newContext();
        IkGui.loadIniSettingsFromMemory(ini);
        manager = new UiManager();
        driver = new UiTestDriver(manager, owner);
        showWorkspace();
        assertTrue(window("files").dockIsActive);
        assertTrue(window("details").dockIsActive);
        assertFalse(window("notes").dockIsActive, "still floating after the reload");
        assertEquals(detailsWidth, window("details").size.x, 1);
    }

    @Test
    void panelsAddedToTheLayoutLaterAreDocked() {
        showWorkspace();
        IkGuiInternal.dockContextQueueUndockWindow(window("notes"));
        driver.frames(2);
        String ini = IkGui.saveIniSettingsToMemory();
        assertTrue(ini.contains("[DockSpace]"), ini);
        IkGui.destroyContext();

        // A later version adds a tab beside details, and one in the middle
        newContext();
        IkGui.loadIniSettingsFromMemory(ini);
        manager = new UiManager();
        driver = new UiTestDriver(manager, owner);
        showWorkspace(
                new DockLayout()
                        .split(DockLayout.Side.LEFT, 0.25f, "files")
                        .splitPrevious(DockLayout.Side.BOTTOM, 0.4f, "outliner")
                        .split(DockLayout.Side.RIGHT, 0.3f, "details", "notes", "history")
                        .center("viewer"),
                "history",
                "viewer");
        assertTrue(window("history").dockIsActive, "the new tab is docked");
        assertSame(window("details").dockNode, window("history").dockNode, "beside details");
        assertTrue(window("viewer").dockIsActive, "the new middle panel is docked");
        assertTrue(window("viewer").dockNode.isCentralNode(), "in the middle");
        assertFalse(window("notes").dockIsActive, "what the user pulled out stays out");
    }

    @Test
    void bringToFrontPicksATab() {
        showWorkspace();
        Window details = window("details");
        Window notes = window("notes");
        assertSame(details.dockNode, notes.dockNode, "tabs of one node");
        String hiddenID = details.dockTabIsVisible ? "notes" : "details";
        String frontID = details.dockTabIsVisible ? "details" : "notes";
        assertFalse(window(hiddenID).dockTabIsVisible, "one tab shows at a time");
        ((Panel) manager.get(hiddenID)).bringToFront();
        driver.frames(2);
        assertTrue(window(hiddenID).dockTabIsVisible, "its tab is picked");
        assertFalse(window(frontID).dockTabIsVisible, "the other tab is behind it");
    }

    @Test
    void bringToFrontWorksAsThePanelsShowAgain() {
        showWorkspace();
        String hiddenID = window("details").dockTabIsVisible ? "notes" : "details";
        String frontID = "notes".equals(hiddenID) ? "details" : "notes";
        manager.setVisible(owner, "details", false);
        manager.setVisible(owner, "notes", false);
        driver.frames(2);
        manager.setVisible(owner, "details", true);
        manager.setVisible(owner, "notes", true);
        ((Panel) manager.get(hiddenID)).bringToFront();
        driver.frames(4);
        assertTrue(window(hiddenID).dockTabIsVisible, "its tab is picked");
        assertFalse(window(frontID).dockTabIsVisible, "the other tab is behind it");
    }

    @Test
    void resetPutsUndockedPanelsBack() {
        showWorkspace();
        IkGuiInternal.dockContextQueueUndockWindow(window("files"));
        driver.frames(2);
        assertFalse(window("files").dockIsActive, "undocked");
        dockSpace.resetLayout();
        driver.frames(3);
        assertTrue(window("files").dockIsActive, "docked again");
    }

    @Test
    void titleChangeKeepsWindow() {
        showWorkspace();
        Window before = window("files");
        Panel files = (Panel) manager.get("files");
        files.title("All files");
        driver.frames(2);
        Window after = window("files");
        assertSame(before, after);
        assertTrue(after.name.startsWith("All files###"), after.name);
        assertTrue(after.dockIsActive);
    }

    @Test
    void specsBuildPanelsAndDockSpaces() {
        SpecInstance.open(
                manager,
                owner,
                spec(
                        "surface: { id: workspace, anchors: fill, transparent: true }\n"
                                + "content:\n"
                                + "  type: column\n"
                                + "  id: root\n"
                                + "  children:\n"
                                + "    - type: dock-space\n"
                                + "      id: dock\n"
                                + "      layout:\n"
                                + "        splits:\n"
                                + "          - { side: left, fraction: 0.3, panels: [files] }\n"
                                + "        center: [view]\n"),
                new SpecBindings());
        SpecInstance.open(
                manager,
                owner,
                spec(
                        "panel: { id: files, title: Files }\n"
                                + "content: { type: label, id: text, text: Files }\n"),
                new SpecBindings());
        SpecInstance.open(
                manager,
                owner,
                spec(
                        "panel: { id: view, title: View }\n"
                                + "content: { type: label, id: text, text: View }\n"),
                new SpecBindings());
        driver.frames(4);
        assertTrue(manager.get("files") instanceof Panel);
        assertTrue(window("files").dockIsActive, "files docked");
        assertTrue(window("view").dockIsActive, "view docked");
        assertTrue(window("files").position.x < window("view").position.x);
        assertTrue(window("files").name.startsWith("Files###"), window("files").name);
    }

    /**
     * Load a spec from text.
     *
     * @param yaml The spec.
     * @return The spec.
     */
    private static UiSpec spec(String yaml) {
        return SpecLoader.load(
                new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), "test");
    }

    @Test
    void dragImageOntoButton() {
        Image source =
                new Image("swatch")
                        .size(64, 64)
                        .dragSource("color", () -> "red", "Red")
                        .tooltip("A swatch");
        Button target =
                new Button("slot", "Drop here")
                        .dropTarget("color", String.class, dropped::add)
                        .contextMenu(
                                new MenuItem("Clear", () -> chosen.add("clear")),
                                new MenuItem("Open", () -> chosen.add("open")));
        manager.add(
                owner,
                new Surface("drag")
                        .anchors(Anchors.topLeft())
                        .content(
                                new Column("root")
                                        .gap(40)
                                        .width(Sizing.fixed(300))
                                        .add(source, target)));
        driver.frames(2);
        RectFloat from = source.getRect();
        RectFloat to = target.getRect();
        driver.run(
                steps ->
                        steps.mouseDrag(
                                from.getCenterX(),
                                from.getCenterY(),
                                to.getCenterX(),
                                to.getCenterY()));
        driver.frames(2);
        assertEquals(List.of("red"), dropped);

        // The tooltip shows once the mouse rests on the image
        driver.run(steps -> steps.mouseMove(from.getCenterX(), from.getCenterY()));
        driver.frames(30);
        assertTrue(
                IkGui.getContext().windowDisplayOrder.stream()
                        .anyMatch(w -> w.active && w.name.startsWith("##Tooltip_")),
                "tooltip shown");

        // Right click opens the menu; choose its second entry
        driver.run(
                steps ->
                        steps.mouseClick(
                                to.getCenterX(),
                                to.getCenterY(),
                                com.ikalagaming.graphics.gui.enums.MouseButton.RIGHT));
        driver.frames(2);
        driver.run(steps -> steps.clickItem("*", "Open"));
        driver.frames(2);
        assertEquals(List.of("open"), chosen);
    }
}
