package com.ikalagaming.graphics.ui.graph;

import com.ikalagaming.graphics.ui.style.Style;

import lombok.Getter;
import lombok.NonNull;

import java.util.List;
import java.util.function.Consumer;

/**
 * A link between two children of a canvas, by their IDs, drawn from one to the other. Its look
 * comes from the theme, as the style type {@code edge} with its classes, like {@code locked} or
 * {@code done}, and the {@code related} state while it lights up.
 *
 * <p>Change an edge on the render thread once its canvas is shown, like its nodes.
 */
public final class Edge {
    /**
     * The ID of the child the edge leaves.
     *
     * @return The ID.
     */
    @Getter private final String from;

    /**
     * The ID of the child the edge enters.
     *
     * @return The ID.
     */
    @Getter private final String to;

    /**
     * Where the edge leaves its first child.
     *
     * @return The port.
     */
    @Getter private Port fromPort = Port.AUTO;

    /**
     * Where the edge enters its second child.
     *
     * @return The port.
     */
    @Getter private Port toPort = Port.AUTO;

    /**
     * How the edge is routed, or null to use the style's route.
     *
     * @return The route, which may be null.
     */
    @Getter private Route route;

    /**
     * The style classes, in order; later ones win.
     *
     * @return The class names.
     */
    @Getter private List<String> classes = List.of();

    /**
     * The edge's own style, which wins over its classes.
     *
     * @return The style.
     */
    @Getter private Style style = Style.EMPTY;

    /** Removes the edge from its canvas. */
    private final Consumer<Edge> remover;

    /**
     * Create an edge, for its canvas.
     *
     * @param from The ID of the child it leaves.
     * @param to The ID of the child it enters.
     * @param remover Removes it from its canvas.
     */
    public Edge(@NonNull String from, @NonNull String to, @NonNull Consumer<Edge> remover) {
        this.from = from;
        this.to = to;
        this.remover = remover;
    }

    /**
     * Set where the edge leaves and enters its children.
     *
     * @param leaving The port on the child it leaves.
     * @param entering The port on the child it enters.
     * @return This edge.
     */
    public Edge ports(@NonNull Port leaving, @NonNull Port entering) {
        fromPort = leaving;
        toPort = entering;
        return this;
    }

    /**
     * Set how the edge is routed, instead of the style's route.
     *
     * @param newRoute The route, or null to use the style's.
     * @return This edge.
     */
    public Edge route(Route newRoute) {
        route = newRoute;
        return this;
    }

    /**
     * Set the style classes, replacing any already set.
     *
     * @param names The class names.
     * @return This edge.
     */
    public Edge classes(@NonNull String... names) {
        classes = List.of(names);
        return this;
    }

    /**
     * Set the style classes, replacing any already set.
     *
     * @param names The class names.
     * @return This edge.
     */
    public Edge classes(@NonNull List<String> names) {
        classes = List.copyOf(names);
        return this;
    }

    /**
     * Set the edge's own style, which wins over its classes.
     *
     * @param newStyle The style.
     * @return This edge.
     */
    public Edge style(@NonNull Style newStyle) {
        style = newStyle;
        return this;
    }

    /** Remove the edge from its canvas. */
    public void remove() {
        remover.accept(this);
    }

    @Override
    public String toString() {
        return "Edge[" + from + " -> " + to + "]";
    }
}
