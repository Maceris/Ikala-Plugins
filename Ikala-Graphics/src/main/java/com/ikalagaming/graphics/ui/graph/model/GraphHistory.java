package com.ikalagaming.graphics.ui.graph.model;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;
import javax.annotation.Nullable;

/**
 * Undo and redo for a graph: every edit goes through {@link #edit}, which keeps a copy of the graph
 * as it was. Graphs are small, so whole copies are simpler and safer than undoing each kind of
 * change by hand. Not thread safe.
 */
public final class GraphHistory {
    /** How many edits can be undone. */
    public static final int DEPTH = 200;

    /** The graph edited. */
    @Getter private final Graph graph;

    /** The graph before each edit, newest first, with the edit's name. */
    private final Deque<Step> undo = new ArrayDeque<>();

    /** The graph before each undo, newest first, with the edit's name. */
    private final Deque<Step> redo = new ArrayDeque<>();

    /**
     * How many edits there were to undo when it was saved, so undoing back to it is clean again, or
     * -1 when that state can't be reached any more.
     */
    private int savedAt;

    /** Whether something outside the graph's edits changed since it was saved. */
    private boolean forced;

    /**
     * Told the name of each edit recorded, so an editor-wide history can keep it among others' and
     * undo it through this one. -- SETTER -- Set the listener.
     *
     * @param onRecord The listener, or null for none.
     */
    @Setter private @Nullable Consumer<String> onRecord;

    /** Counts changes, so a listener can tell the graph changed. */
    @Getter private long changes;

    /**
     * Whether the graph can't be edited, as when it is shown for something made from it: edits are
     * dropped, and one applied as the mouse moved is put back when recorded. -- GETTER -- Whether
     * it is locked. -- SETTER -- Lock or unlock it.
     *
     * @param locked Whether it is locked.
     * @return True if locked.
     */
    @Getter @Setter private boolean locked;

    /**
     * A copy of the graph, and the name of the edit it was taken before.
     *
     * @param name The edit's name, like "Delete".
     * @param before The graph before it.
     */
    private record Step(String name, Graph before) {}

    /**
     * Keep the history of a graph.
     *
     * @param graph The graph.
     */
    public GraphHistory(@NonNull Graph graph) {
        this.graph = graph;
    }

    /**
     * Make an edit that can be undone.
     *
     * @param name What it is called, for the undo menu, like "Move" or "Link".
     * @param change The change.
     */
    public void edit(@NonNull String name, @NonNull Consumer<Graph> change) {
        if (locked) {
            return;
        }
        final Graph before = graph.copy();
        change.accept(graph);
        record(name, before);
    }

    /**
     * Note an edit already made to the graph, like a drag applied as the mouse moved, given the
     * graph as it was before.
     *
     * @param name What it is called.
     * @param before A copy of the graph from before the edit.
     */
    public void record(@NonNull String name, @NonNull Graph before) {
        if (locked) {
            graph.restore(before);
            ++changes;
            return;
        }
        if (savedAt > undo.size()) {
            // The saved state was among the edits undone, which are gone now
            savedAt = -1;
        }
        undo.push(new Step(name, before));
        while (undo.size() > DEPTH) {
            undo.removeLast();
            savedAt = savedAt > 0 ? savedAt - 1 : -1;
        }
        redo.clear();
        ++changes;
        if (onRecord != null) {
            onRecord.accept(name);
        }
    }

    /**
     * Whether the graph differs from when it was last saved: an edit since, not undone, or a change
     * outside its edits.
     *
     * @return True if changed since saved.
     */
    public boolean isDirty() {
        return forced || undo.size() != savedAt;
    }

    /**
     * Undo the last edit.
     *
     * @return The edit's name, or null if there was none.
     */
    public String undo() {
        final Step step = undo.poll();
        if (step == null) {
            return null;
        }
        redo.push(new Step(step.name(), graph.copy()));
        graph.restore(step.before());
        ++changes;
        return step.name();
    }

    /**
     * Redo the last edit undone.
     *
     * @return The edit's name, or null if there was none.
     */
    public String redo() {
        final Step step = redo.poll();
        if (step == null) {
            return null;
        }
        undo.push(new Step(step.name(), graph.copy()));
        graph.restore(step.before());
        ++changes;
        return step.name();
    }

    /**
     * The name of the edit undo would undo.
     *
     * @return The name, or null if there is none.
     */
    public String nextUndo() {
        final Step step = undo.peek();
        return step == null ? null : step.name();
    }

    /**
     * The name of the edit redo would redo.
     *
     * @return The name, or null if there is none.
     */
    public String nextRedo() {
        final Step step = redo.peek();
        return step == null ? null : step.name();
    }

    /** Forget every edit, as when a graph is opened. */
    public void clear() {
        undo.clear();
        redo.clear();
        savedAt = 0;
        forced = false;
        ++changes;
    }

    /** Note that the graph was saved as it is. */
    public void markSaved() {
        savedAt = undo.size();
        forced = false;
    }

    /**
     * Note that something saved with the graph but not part of it, like a setting the caller keeps,
     * changed, so the graph needs saving.
     */
    public void markDirty() {
        forced = true;
        ++changes;
    }
}
