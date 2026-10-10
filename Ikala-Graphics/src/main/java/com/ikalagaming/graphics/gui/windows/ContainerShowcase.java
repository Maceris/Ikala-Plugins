package com.ikalagaming.graphics.gui.windows;

import com.ikalagaming.graphics.UI;
import com.ikalagaming.graphics.gui.util.Color;
import com.ikalagaming.graphics.ui.Align;
import com.ikalagaming.graphics.ui.Anchors;
import com.ikalagaming.graphics.ui.Canvas;
import com.ikalagaming.graphics.ui.Column;
import com.ikalagaming.graphics.ui.Grid;
import com.ikalagaming.graphics.ui.Insets;
import com.ikalagaming.graphics.ui.Label;
import com.ikalagaming.graphics.ui.Length;
import com.ikalagaming.graphics.ui.Selectable;
import com.ikalagaming.graphics.ui.Sizing;
import com.ikalagaming.graphics.ui.Surface;
import com.ikalagaming.graphics.ui.VirtualGrid;
import com.ikalagaming.graphics.ui.graph.GraphNode;
import com.ikalagaming.graphics.ui.graph.Highlight;
import com.ikalagaming.graphics.ui.graph.Port;
import com.ikalagaming.graphics.ui.graph.Route;
import com.ikalagaming.graphics.ui.style.Style;
import com.ikalagaming.graphics.ui.style.StyleKey;
import com.ikalagaming.graphics.ui.style.StyleState;
import com.ikalagaming.graphics.ui.style.Theme;

import lombok.NonNull;

import java.util.List;

/**
 * A surface that shows off the grid, virtual grid and canvas containers: an inventory, a long list
 * of cells, a skill tree and a quest chain. Shown next to {@link UiShowcase}.
 */
public final class ContainerShowcase {
    /** The ID of the surface. */
    public static final String SURFACE_ID = "graphics/ui-containers";

    /** Cells in the virtual grid. */
    private static final int VIRTUAL_CELLS = 100_000;

    /** Links to locked nodes: the default theme's border grey, without its transparency. */
    private static final int LOCKED_LINE = Color.rgb(110, 110, 128);

    /** The border of a locked node: darker than its links, so the node reads as out of reach. */
    private static final int LOCKED_BORDER = Color.rgb(85, 85, 95);

    /** The inside of a locked node: close to the window background. */
    private static final int LOCKED_BACKGROUND = Color.rgb(26, 26, 32);

    /** Done links and nodes: the default theme's ok green. */
    private static final int DONE = Color.rgb(115, 230, 115);

    /** How opaque the glow around a done node is. */
    private static final float DONE_GLOW_OPACITY = 0.375f;

    /** Done links while lit up: the default theme's warning yellow. */
    private static final int DONE_RELATED = Color.rgb(255, 217, 77);

    /**
     * Styles for the showcase's graphs: locked and done links and nodes. The names are prefixed so
     * they don't clash with a game's own classes.
     *
     * @return The styles.
     */
    public static Theme styles() {
        return Theme.builder("container-showcase")
                .styleClass(
                        "showcase-locked",
                        Style.builder()
                                .set(StyleKey.LINE, LOCKED_LINE)
                                .set(StyleKey.DASH, List.of(6f, 4f))
                                .set(StyleKey.BORDER, LOCKED_BORDER)
                                .set(StyleKey.BACKGROUND, LOCKED_BACKGROUND)
                                .build())
                .styleClass(
                        "showcase-done",
                        Style.builder()
                                .set(StyleKey.LINE, DONE)
                                .set(StyleKey.STROKES, 3f)
                                .set(StyleKey.LINE_SIZE, Length.u(1))
                                .set(StyleKey.STROKE_GAP, Length.u(1.5f))
                                .set(StyleKey.CORNER, "chamfer 6")
                                .set(StyleKey.BORDER, DONE)
                                .set(StyleKey.GLOW, Color.multiplyAlpha(DONE, DONE_GLOW_OPACITY))
                                .state(
                                        StyleState.RELATED,
                                        Style.builder().set(StyleKey.LINE, DONE_RELATED).build())
                                .build())
                .styleClass(
                        "showcase-arrow",
                        Style.builder()
                                .set(StyleKey.ARROW, "end")
                                .set(StyleKey.CORNER, "round 8")
                                .build())
                .build();
    }

    /**
     * Build the showcase surface.
     *
     * @param ui The UI to create it through.
     * @return The surface, ready to show.
     */
    public static Surface build(@NonNull UI ui) {
        Column content =
                new Column("content")
                        .gap(8)
                        .padding(Insets.all(4))
                        .align(Align.STRETCH)
                        .add(
                                new Label("grid-title", "Grid: 40 slots, 8 columns"),
                                inventory(),
                                new Label(
                                        "virtual-title",
                                        "Virtual grid: " + VIRTUAL_CELLS + " cells, made as shown"),
                                virtualGrid(),
                                new Label(
                                        "tree-title",
                                        "Canvas skill tree: wheel zooms, drag pans,"
                                                + " hover lights the path to the root"),
                                skillTree(),
                                new Label(
                                        "quest-title", "Canvas quest chain: drag the nodes around"),
                                questChain());
        return ui.surface(SURFACE_ID)
                .anchors(Anchors.at(0, 0.5f).offset(Length.u(20), Length.ZERO))
                .width(Sizing.fixed(460))
                .movable()
                .content(content);
    }

    /**
     * A grid of inventory slots.
     *
     * @return The grid.
     */
    private static Grid inventory() {
        Grid grid = new Grid("inventory").columns(8).cellSize(48, 48).gap(4);
        for (int i = 0; i < 40; ++i) {
            grid.add(new GraphNode("slot" + i).shape(GraphNode.Shape.ROUNDED));
        }
        return grid;
    }

    /**
     * A virtual grid of numbered cells, only made as they are scrolled to.
     *
     * @return The grid.
     */
    private static VirtualGrid virtualGrid() {
        return new VirtualGrid("cells")
                .cellSize(96, 22)
                .gap(Length.u(2))
                .border(true)
                .height(Sizing.fixed(120))
                .items(
                        VIRTUAL_CELLS,
                        index ->
                                new Selectable("cell", "Cell " + index)
                                        .width(Sizing.grow())
                                        .height(Sizing.grow()));
    }

    /**
     * A small skill tree: a root with three branches, routed as a bus, some of it locked.
     *
     * @return The canvas.
     */
    private static Canvas skillTree() {
        Canvas canvas =
                new Canvas("tree")
                        .height(Sizing.fixed(260))
                        .border(true)
                        .gridSpacing(Length.u(40))
                        .highlight(Highlight.ANCESTORS);
        canvas.add(
                perk("root", 140, 0, "1/1", "showcase-done"),
                perk("fire", 20, 110, "3/3", "showcase-done"),
                perk("ice", 140, 110, "1/3", null),
                perk("storm", 260, 110, "0/3", "showcase-locked"),
                perk("inferno", 20, 220, "2/2", "showcase-done"),
                perk("glacier", 140, 220, "0/2", "showcase-locked"));
        link(canvas, "root", "fire", "showcase-done");
        link(canvas, "root", "ice", null);
        link(canvas, "root", "storm", "showcase-locked");
        link(canvas, "fire", "inferno", "showcase-done");
        link(canvas, "ice", "glacier", "showcase-locked");
        return canvas;
    }

    /**
     * A skill tree node.
     *
     * @param id The ID.
     * @param x The left edge, in canvas units.
     * @param y The top edge, in canvas units.
     * @param rank The badge.
     * @param state A state class, or null.
     * @return The node.
     */
    private static GraphNode perk(String id, float x, float y, String rank, String state) {
        GraphNode node = new GraphNode(id).shape(GraphNode.Shape.OCTAGON).badge(rank).label(id);
        node.position(x, y);
        if (state != null) {
            node.classes(state);
        }
        return node;
    }

    /**
     * Link two skill tree nodes, bottom to top.
     *
     * @param canvas The canvas.
     * @param from The parent.
     * @param to The child.
     * @param state A state class, or null.
     */
    private static void link(Canvas canvas, String from, String to, String state) {
        var edge =
                canvas.connect(from, to)
                        .ports(Port.of(Port.Side.BOTTOM), Port.of(Port.Side.TOP))
                        .route(Route.ORTHOGONAL);
        if (state != null) {
            edge.classes(state);
        }
    }

    /**
     * A quest chain with draggable nodes and straight and curved links.
     *
     * @return The canvas.
     */
    private static Canvas questChain() {
        Canvas canvas =
                new Canvas("quests")
                        .height(Sizing.fixed(200))
                        .border(true)
                        .draggable(true)
                        .highlight(Highlight.DESCENDANTS);
        String[] names = {"start", "gather", "craft", "deliver", "bonus"};
        float[][] places = {{0, 60}, {110, 0}, {110, 120}, {230, 60}, {340, 60}};
        for (int i = 0; i < names.length; ++i) {
            GraphNode node = new GraphNode(names[i]).shape(GraphNode.Shape.HEXAGON).label(names[i]);
            node.width(Sizing.fixed(72)).height(Sizing.fixed(40));
            node.position(places[i][0], places[i][1]);
            if (i < 2) {
                node.classes("showcase-done");
            }
            canvas.add(node);
        }
        canvas.connect("start", "gather")
                .route(Route.CURVE)
                .classes("showcase-done", "showcase-arrow");
        canvas.connect("start", "craft").route(Route.CURVE).classes("showcase-arrow");
        canvas.connect("gather", "deliver").route(Route.STRAIGHT).classes("showcase-arrow");
        canvas.connect("craft", "deliver").route(Route.STRAIGHT).classes("showcase-arrow");
        canvas.connect("deliver", "bonus")
                .route(Route.ORTHOGONAL)
                .classes("showcase-locked", "showcase-arrow");
        return canvas;
    }

    /** Static builders only. */
    private ContainerShowcase() {}
}
