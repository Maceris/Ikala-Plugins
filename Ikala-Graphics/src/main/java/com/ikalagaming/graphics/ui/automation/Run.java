package com.ikalagaming.graphics.ui.automation;

import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.data.Context;
import com.ikalagaming.graphics.gui.data.IkIO;
import com.ikalagaming.graphics.ui.Node;
import com.ikalagaming.graphics.ui.Surface;

import lombok.Getter;
import lombok.NonNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** A list of steps being run, and what they share. Render thread only, apart from the future. */
final class Run {
    /**
     * What looking for a selector found.
     *
     * @param shown The one shown node it selects, or null.
     * @param hidden The one node it selects if that node exists but isn't on screen, or null.
     * @param problem Why there is no shown node, or null if there is.
     */
    record Lookup(Node<?> shown, Node<?> hidden, String problem) {}

    /**
     * The plugin that started the run. -- GETTER -- The plugin that started the run.
     *
     * @return Its context.
     */
    @Getter private final GraphicsContext owner;

    /** The steps, in order. */
    private final List<Step> steps;

    /**
     * Finishes when the run does. -- GETTER -- Finishes when the run does.
     *
     * @return The future.
     */
    @Getter private final CompletableFuture<Void> future = new CompletableFuture<>();

    /** The step being run. */
    private int index;

    /** When the current step started, in IkGui milliseconds, or -1 before it starts. */
    private long stepStart = -1;

    /** The shown surfaces this frame. */
    private List<Surface> surfaces = List.of();

    /** Why the run failed, once it has. */
    private UiAutomationException failure;

    /**
     * Create a run.
     *
     * @param owner The plugin that started it.
     * @param steps The steps.
     */
    Run(@NonNull GraphicsContext owner, @NonNull List<Step> steps) {
        this.owner = owner;
        this.steps = List.copyOf(steps);
    }

    /**
     * Advance the current step. At most one step finishes per frame, so each one's input is seen by
     * IkGui before the next starts.
     *
     * @param shownSurfaces The surfaces shown this frame.
     * @return True once the run has finished or failed.
     */
    boolean advance(@NonNull List<Surface> shownSurfaces) {
        if (index >= steps.size()) {
            return true;
        }
        surfaces = shownSurfaces;
        Step step = steps.get(index);
        if (stepStart < 0) {
            stepStart = now();
        }
        try {
            if (step.advance(this)) {
                ++index;
                stepStart = -1;
            }
        } catch (UiAutomationException e) {
            failure = new UiAutomationException(step.describe() + ": " + e.getMessage());
            return true;
        }
        return index >= steps.size();
    }

    /**
     * Complete the future, once the run has finished. Called after the actions the run's input
     * fired have run.
     */
    void complete() {
        if (failure != null) {
            future.completeExceptionally(failure);
        } else {
            future.complete(null);
        }
    }

    /**
     * Stop the run without finishing it.
     *
     * @param reason Why.
     */
    void cancel(@NonNull String reason) {
        future.completeExceptionally(new UiAutomationException(reason));
    }

    /**
     * IkGui's time.
     *
     * @return Milliseconds since the IkGui context was made.
     */
    long now() {
        return IkGui.getContext().time;
    }

    /**
     * How long the current step has been running.
     *
     * @return Milliseconds of IkGui time.
     */
    long elapsed() {
        return stepStart < 0 ? 0 : now() - stepStart;
    }

    /**
     * IkGui's state.
     *
     * @return The context.
     */
    Context gui() {
        return IkGui.getContext();
    }

    /**
     * Where steps queue input.
     *
     * @return IkGui's IO.
     */
    IkIO io() {
        return IkGui.getIO();
    }

    /**
     * The surfaces shown this frame.
     *
     * @return The surfaces.
     */
    List<Surface> surfaces() {
        return surfaces;
    }

    /**
     * Look for the node a selector picks out.
     *
     * @param selector The selector.
     * @return What was found.
     * @throws UiAutomationException If it picks out more than one shown node.
     */
    Lookup find(@NonNull Selector selector) {
        Selector.Match match = selector.find(surfaces);
        if (match.problem() != null) {
            return new Lookup(null, null, match.problem());
        }
        List<Node<?>> shown = match.nodes().stream().filter(Node::isShown).toList();
        if (shown.size() == 1) {
            return new Lookup(shown.getFirst(), null, null);
        }
        List<Node<?>> candidates = shown.isEmpty() ? match.nodes() : shown;
        if (candidates.size() > 1) {
            List<String> paths =
                    candidates.stream().map(node -> Selector.pathOf(node, surfaces)).toList();
            throw new UiAutomationException(
                    "'" + selector + "' is ambiguous: " + String.join(", ", paths));
        }
        Node<?> only = candidates.getFirst();
        return new Lookup(
                null, only, "'" + Selector.pathOf(only, surfaces) + "' exists but isn't on screen");
    }

    /**
     * The path of a node, for messages.
     *
     * @param node The node.
     * @return Its path, like {@code about/buttons/close}.
     */
    String pathOf(@NonNull Node<?> node) {
        return Selector.pathOf(node, surfaces);
    }
}
