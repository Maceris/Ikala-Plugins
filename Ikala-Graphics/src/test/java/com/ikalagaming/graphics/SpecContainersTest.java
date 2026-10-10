package com.ikalagaming.graphics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.data.Context;
import com.ikalagaming.graphics.gui.enums.MouseButton;
import com.ikalagaming.graphics.gui.util.RectFloat;
import com.ikalagaming.graphics.ui.Canvas;
import com.ikalagaming.graphics.ui.Grid;
import com.ikalagaming.graphics.ui.Label;
import com.ikalagaming.graphics.ui.Node;
import com.ikalagaming.graphics.ui.UiManager;
import com.ikalagaming.graphics.ui.VirtualGrid;
import com.ikalagaming.graphics.ui.graph.Edge;
import com.ikalagaming.graphics.ui.graph.GraphNode;
import com.ikalagaming.graphics.ui.graph.Port;
import com.ikalagaming.graphics.ui.graph.Route;
import com.ikalagaming.graphics.ui.spec.Observable;
import com.ikalagaming.graphics.ui.spec.ObservableList;
import com.ikalagaming.graphics.ui.spec.SpecBindings;
import com.ikalagaming.graphics.ui.spec.SpecEvent;
import com.ikalagaming.graphics.ui.spec.SpecException;
import com.ikalagaming.graphics.ui.spec.SpecInstance;
import com.ikalagaming.graphics.ui.spec.SpecLoader;
import com.ikalagaming.graphics.ui.spec.UiSpec;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Grids, virtual grids, canvases and graph nodes in UI specs, on a headless IkGui context. */
class SpecContainersTest {

    /** A list item, read through reflection by bindings. */
    public record Item(String name) {}

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

    private void frames(int count) {
        for (int i = 0; i < count; ++i) {
            IkGui.newFrame();
            manager.draw();
            IkGui.render();
            manager.dispatchEvents();
        }
    }

    private static UiSpec spec(String content) {
        String yaml =
                "surface: { id: test, anchors: top-left, width: 600, height: 400 }\n" + content;
        return SpecLoader.load(
                new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), "test");
    }

    private SpecInstance open(UiSpec spec, SpecBindings bindings) {
        SpecInstance instance = SpecInstance.open(manager, owner, spec, bindings);
        frames(3);
        return instance;
    }

    @Test
    void gridsTakeColumnsCellsAndGaps() {
        SpecInstance instance =
                open(
                        spec(
                                """
                                content:
                                  type: grid
                                  id: slots
                                  columns: 3
                                  cell: [40, 30]
                                  gap: 5
                                  children:
                                    - { type: label, id: a, text: A }
                                    - { type: label, id: b, text: B }
                                    - { type: label, id: c, text: C }
                                    - { type: label, id: d, text: D }
                                """),
                        new SpecBindings());

        Grid grid = assertInstanceOf(Grid.class, instance.find(""));
        assertEquals(3, grid.getResolvedColumns());
        RectFloat a = instance.find("a").getRect();
        RectFloat d = instance.find("d").getRect();
        // The fourth cell starts the second row, a cell and a gap below the first
        assertEquals(a.getLeft(), d.getLeft(), 1e-3);
        assertEquals(35, d.getTop() - a.getTop(), 1e-3);
        assertEquals(45, instance.find("b").getRect().getLeft() - a.getLeft(), 1e-3);
    }

    @Test
    void gridsRejectFractionalColumns() {
        UiSpec spec = spec("content: { type: grid, id: g, columns: 2.5 }\n");
        String message =
                assertThrows(SpecException.class, () -> open(spec, new SpecBindings()))
                        .getMessage();
        assertTrue(message.contains("content.columns: expected a whole number"), message);
    }

    /** A virtual grid of labels over a list, whose text also binds an observable. */
    private static final String VIRTUAL =
            """
            templates:
              cell: { node: { type: label, id: cell, text: "{item.name}{suffix}" } }
            content:
              type: virtual-grid
              id: assets
              cell: [80, 20]
              repeat: { list: items, as: item, template: cell }
            """;

    @Test
    void virtualGridsOnlyBuildTheCellsThatCanBeSeen() {
        List<Item> items = new ArrayList<>();
        for (int i = 0; i < 10_000; ++i) {
            items.add(new Item("asset" + i));
        }
        ObservableList<Item> list = new ObservableList<>(items);
        Observable<String> suffix = Observable.of("!");
        SpecInstance instance =
                open(spec(VIRTUAL), new SpecBindings().list("items", list).value("suffix", suffix));

        VirtualGrid grid = assertInstanceOf(VirtualGrid.class, instance.find(""));
        Label first = assertInstanceOf(Label.class, grid.getCell(0));
        assertEquals("asset0!", first.getText());
        assertTrue(grid.getMadeCount() < 500, "Only a few made, was " + grid.getMadeCount());
        // Each made cell listens to the suffix, and nothing else does
        assertEquals(grid.getMadeCount(), suffix.listenerCount());

        suffix.set("?");
        frames(2);
        assertEquals("asset0?", first.getText());
    }

    @Test
    void virtualGridsFollowTheirList() {
        ObservableList<Item> list = new ObservableList<>(List.of(new Item("a"), new Item("b")));
        Observable<String> suffix = Observable.of("");
        SpecInstance instance =
                open(spec(VIRTUAL), new SpecBindings().list("items", list).value("suffix", suffix));
        VirtualGrid grid = assertInstanceOf(VirtualGrid.class, instance.find(""));
        assertEquals(2, grid.getCount());

        list.set(List.of(new Item("c")));
        frames(3);
        assertEquals(1, grid.getCount());
        assertEquals("c", ((Label) grid.getCell(0)).getText());
        // The released cells stopped listening
        assertEquals(1, suffix.listenerCount());

        instance.close();
        frames(2);
        assertEquals(0, suffix.listenerCount());
        assertEquals(0, list.listenerCount());
    }

    @Test
    void canvasesPlaceChildrenAndReadTheirEdges() {
        SpecInstance instance =
                open(
                        spec(
                                """
                                content:
                                  type: canvas
                                  id: tree
                                  zoomRange: [0.5, 2]
                                  zoom: 1.5
                                  highlight: ancestors
                                  edges:
                                    - { from: root, to: fire, route: curve, ports: [bottom, top 0.25] }
                                    - { from: fire, to: inferno, classes: [locked] }
                                  children:
                                    - { type: graph-node, id: root, position: [0, 0], shape: octagon }
                                    - { type: graph-node, id: fire, position: [0, 100], badge: "3/3" }
                                    - { type: graph-node, id: inferno, position: [100, 100] }
                                """),
                        new SpecBindings());

        Canvas canvas = assertInstanceOf(Canvas.class, instance.find(""));
        assertEquals(1.5f, canvas.getZoom(), 1e-4);
        assertEquals(100, instance.find("fire").getPositionY(), 1e-4);
        List<Edge> edges = canvas.getEdges();
        assertEquals(2, edges.size());
        assertEquals(Route.CURVE, edges.get(0).getRoute());
        assertEquals(new Port(Port.Side.TOP, 0.25f), edges.get(0).getToPort());
        assertEquals(List.of("locked"), edges.get(1).getClasses());
        GraphNode root = assertInstanceOf(GraphNode.class, instance.find("root"));
        assertEquals(GraphNode.Shape.OCTAGON, root.getShape());
        assertEquals("3/3", ((GraphNode) instance.find("fire")).getBadge());
        assertNull(((GraphNode) instance.find("inferno")).getBadge());
    }

    @Test
    void edgeClassesCanBeBound() {
        Observable<String> state = Observable.of("locked");
        SpecInstance instance =
                open(
                        spec(
                                """
                                content:
                                  type: canvas
                                  id: tree
                                  edges:
                                    - { from: a, to: b, classes: "{state}" }
                                  children:
                                    - { type: graph-node, id: a }
                                    - { type: graph-node, id: b, position: [100, 0] }
                                """),
                        new SpecBindings().value("state", state));
        Edge edge = ((Canvas) instance.find("")).getEdges().getFirst();
        assertEquals(List.of("locked"), edge.getClasses());

        state.set("done bright");
        frames(2);
        assertEquals(List.of("done", "bright"), edge.getClasses());
    }

    @Test
    void edgesWrittenWrongAreReported() {
        UiSpec missing =
                spec(
                        """
                        content:
                          type: canvas
                          id: tree
                          edges: [{ from: a }]
                        """);
        String message =
                assertThrows(SpecException.class, () -> open(missing, new SpecBindings()))
                        .getMessage();
        assertTrue(message.contains("content.edges[0]: an edge needs from and to"), message);

        UiSpec unknown =
                spec(
                        """
                        content:
                          type: canvas
                          id: tree
                          edges: [{ from: a, to: b, colour: red }]
                        """);
        message =
                assertThrows(SpecException.class, () -> open(unknown, new SpecBindings()))
                        .getMessage();
        assertTrue(message.contains("content.edges[0].colour: unknown property"), message);
    }

    @Test
    void movingAChildRunsTheMoveHandler() {
        List<SpecEvent> moves = new ArrayList<>();
        SpecInstance instance =
                open(
                        spec(
                                """
                                content:
                                  type: canvas
                                  id: tree
                                  draggable: true
                                  onMove: moved
                                  children:
                                    - { type: graph-node, id: a }
                                    - { type: graph-node, id: b, position: [200, 100] }
                                """),
                        new SpecBindings().handler("moved", moves::add));
        Node<?> a = instance.find("a");
        RectFloat start = a.getRect();

        context.io.addMousePosEvent(start.getCenterX(), start.getCenterY());
        frames(1);
        context.io.addMouseButtonEvent(MouseButton.LEFT, true);
        frames(1);
        for (int step = 1; step <= 4; ++step) {
            context.io.addMousePosEvent(start.getCenterX() + 10 * step, start.getCenterY());
            frames(1);
        }
        context.io.addMouseButtonEvent(MouseButton.LEFT, false);
        frames(2);

        assertEquals(1, moves.size());
        assertSame(a, moves.getFirst().node());
        String[] position = moves.getFirst().value().split(",");
        assertEquals(a.getPositionX(), Float.parseFloat(position[0]), 1e-3);
        assertTrue(a.getPositionX() > 30, "It moved, to " + a.getPositionX());
        assertNotNull(position[1]);
    }
}
