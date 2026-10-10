package com.ikalagaming.graphics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.data.Context;
import com.ikalagaming.graphics.gui.enums.Key;
import com.ikalagaming.graphics.gui.enums.MouseButton;
import com.ikalagaming.graphics.gui.flags.BackendFlags;
import com.ikalagaming.graphics.gui.flags.ConfigFlags;
import com.ikalagaming.graphics.gui.util.RectFloat;
import com.ikalagaming.graphics.ui.Anchors;
import com.ikalagaming.graphics.ui.Column;
import com.ikalagaming.graphics.ui.Grid;
import com.ikalagaming.graphics.ui.LayoutContext;
import com.ikalagaming.graphics.ui.Node;
import com.ikalagaming.graphics.ui.Scroll;
import com.ikalagaming.graphics.ui.Selectable;
import com.ikalagaming.graphics.ui.Sizing;
import com.ikalagaming.graphics.ui.Surface;
import com.ikalagaming.graphics.ui.UiFrame;
import com.ikalagaming.graphics.ui.UiManager;
import com.ikalagaming.graphics.ui.VirtualGrid;

import lombok.NonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Grids and virtual grids on a headless IkGui context. */
class UIGridsTest {

    /** A cell that counts how often it is submitted. */
    private static final class Counted extends Node<Counted> {
        private final int[] submits;

        Counted(String id, int[] submits) {
            super(id);
            this.submits = submits;
            width(Sizing.fixed(40));
            height(Sizing.fixed(20));
        }

        @Override
        protected void measure(@NonNull LayoutContext context, float[] out) {
            out[0] = 40;
            out[1] = 20;
        }

        @Override
        protected void submit(@NonNull UiFrame frame) {
            ++submits[0];
        }
    }

    private Context context;
    private UiManager manager;
    private GraphicsContext owner;

    @BeforeEach
    void setUp() {
        context = IkGui.createContext();
        context.io.iniFilename = null;
        context.io.displaySize.set(1280, 720);
        context.io.mouseInsideWindow = true;
        context.io.configFlags |= ConfigFlags.NAV_ENABLE_KEYBOARD | ConfigFlags.NAV_ENABLE_GAMEPAD;
        context.io.backendFlags |= BackendFlags.HAS_GAMEPAD;
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

    private void press(Key key) {
        context.io.addKeyEvent(key, true);
        frame();
        context.io.addKeyEvent(key, false);
        frame();
    }

    private void click(RectFloat rect) {
        context.io.addMousePosEvent(rect.getCenterX(), rect.getCenterY());
        frame();
        context.io.addMouseButtonEvent(MouseButton.LEFT, true);
        frame();
        context.io.addMouseButtonEvent(MouseButton.LEFT, false);
        frame();
    }

    /** A 400 by 300 surface in the top left, holding some content. */
    private void show(String id, Node<?> content) {
        manager.add(
                owner,
                new Surface(id)
                        .anchors(Anchors.topLeft())
                        .width(Sizing.fixed(400))
                        .height(Sizing.fixed(300))
                        .content(new Column("content").add(content)));
    }

    @Test
    void gridsOnlySubmitTheRowsThatCanBeSeen() {
        int[] submits = new int[1];
        Grid grid = new Grid("grid").columns(5).cellSize(40, 20);
        for (int i = 0; i < 500; ++i) {
            grid.add(new Counted("cell" + i, submits));
        }
        show("cells", new Scroll("scroll").content(grid));
        frames(3);

        submits[0] = 0;
        frame();
        // 100 rows of 20 pixels, but only about 15 fit in the view
        assertTrue(submits[0] > 0, "Some cells are submitted");
        assertTrue(submits[0] <= 5 * 20, "Only the visible rows, was " + submits[0]);
    }

    /** A scroll with a grid of 200 selectables, five to a row, reporting which was clicked. */
    private List<Selectable> selectableGrid(int[] clicked) {
        Grid grid = new Grid("grid").columns(5).cellSize(40, 20);
        List<Selectable> cells = new ArrayList<>();
        for (int i = 0; i < 200; ++i) {
            final int index = i;
            Selectable cell =
                    new Selectable("cell" + i, Integer.toString(i))
                            .width(Sizing.grow())
                            .height(Sizing.grow())
                            .onClick(() -> clicked[0] = index);
            grid.add(cell);
            cells.add(cell);
        }
        show("cells", new Scroll("scroll").content(grid));
        return cells;
    }

    @Test
    void gamepadNavigationReachesGridRowsThatWereClipped() {
        int[] clicked = {-1};
        List<Selectable> cells = selectableGrid(clicked);
        frame();
        IkGui.setWindowFocus("###ui/cells");
        frames(2);
        click(cells.getFirst().getRect());

        for (int i = 0; i < 25; ++i) {
            press(Key.GAMEPAD_DPAD_DOWN);
        }
        press(Key.GAMEPAD_FACE_DOWN);

        // Down moves a row, which is five cells
        assertEquals(125, clicked[0]);
        assertTrue(cells.get(125).getRect().getBottom() <= 300, "The focused row was scrolled to");
    }

    /** A virtual grid of 100,000 selectables that records what it made and released. */
    private VirtualGrid virtualGrid(Set<Integer> live, int[] made, int[] clicked) {
        VirtualGrid grid = new VirtualGrid("virtual").cellSize(40, 20);
        grid.items(
                100_000,
                new VirtualGrid.Factory() {
                    @Override
                    public Node<?> create(int index) {
                        ++made[0];
                        live.add(index);
                        return new Selectable("cell", Integer.toString(index))
                                .width(Sizing.grow())
                                .height(Sizing.grow())
                                .onClick(() -> clicked[0] = index);
                    }

                    @Override
                    public void release(int index, @NonNull Node<?> node) {
                        live.remove(index);
                    }
                });
        show("virtual", grid);
        return grid;
    }

    @Test
    void virtualGridsOnlyMakeTheCellsThatCanBeSeen() {
        Set<Integer> live = new HashSet<>();
        int[] made = new int[1];
        VirtualGrid grid = virtualGrid(live, made, new int[] {-1});
        frames(3);

        assertNotNull(grid.getCell(0));
        assertNull(grid.getCell(99_999));
        // Around 15 rows can be seen, of nine or so columns
        assertTrue(made[0] < 300, "Only a few cells were made, was " + made[0]);
        assertEquals(live.size(), grid.getMadeCount());
    }

    @Test
    void scrollingAVirtualGridReleasesCellsFarOutOfView() {
        Set<Integer> live = new HashSet<>();
        int[] made = new int[1];
        VirtualGrid grid = virtualGrid(live, made, new int[] {-1});
        frames(3);
        int madeBefore = made[0];

        context.io.addMousePosEvent(100, 100);
        frame();
        for (int i = 0; i < 10; ++i) {
            context.io.addMouseWheelEvent(0, -50);
            frame();
        }
        frames(2);

        assertTrue(made[0] > madeBefore, "Scrolling made new cells");
        assertNull(grid.getCell(0), "The first cell was released");
        assertTrue(live.size() < 300, "Old cells were released, " + live.size() + " are live");
        assertEquals(live.size(), grid.getMadeCount());
    }

    @Test
    void gamepadNavigationMovesIntoCellsNotYetMade() {
        Set<Integer> live = new HashSet<>();
        int[] clicked = {-1};
        VirtualGrid grid = virtualGrid(live, new int[1], clicked);
        frame();
        IkGui.setWindowFocus("###ui/virtual");
        frames(2);
        int columns = grid.getResolvedColumns();
        assertTrue(columns > 1);
        click(grid.getCell(0).getRect());

        for (int i = 0; i < 30; ++i) {
            press(Key.GAMEPAD_DPAD_DOWN);
        }
        press(Key.GAMEPAD_FACE_DOWN);

        assertEquals(30 * columns, clicked[0]);
    }

    @Test
    void changingTheCountKeepsCellsThatStillExist() {
        Set<Integer> live = new HashSet<>();
        int[] made = new int[1];
        VirtualGrid grid = virtualGrid(live, made, new int[] {-1});
        frames(3);
        Node<?> first = grid.getCell(0);

        grid.count(3);
        assertEquals(Set.of(0, 1, 2), live);
        assertEquals(first, grid.getCell(0));
        frames(2);
        assertEquals(3, grid.getMadeCount());

        grid.refresh();
        assertTrue(live.isEmpty());
        frames(2);
        assertTrue(grid.getCell(0) != first, "Refreshing makes the cells again");
    }
}
