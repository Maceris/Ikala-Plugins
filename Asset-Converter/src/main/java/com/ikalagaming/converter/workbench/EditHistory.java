package com.ikalagaming.converter.workbench;

import lombok.NonNull;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/**
 * The workbench's one undo stack: every editor's edits as steps in the order they were made, so
 * undo and redo cross files and editors. A step knows how to undo and redo itself. Changes to the
 * same thing while the mouse is held join one step, so a slider drag is undone in one go. Render
 * thread.
 */
public final class EditHistory {
    /** The most steps kept. */
    public static final int DEPTH = 200;

    /** An edit that can be undone and redone. */
    public interface Step {
        /**
         * What it is called, for messages, like "Set roughness".
         *
         * @return The name.
         */
        String name();

        /** Put things back as they were before the edit. */
        void undo();

        /** Make the edit again. */
        void redo();

        /**
         * This step joined with a later one of the same thing, as a drag goes on: this step's
         * start, the later one's end.
         *
         * @param later The later step.
         * @return The joined step, or null if they can't join.
         */
        default @Nullable Step join(@NonNull Step later) {
            return null;
        }
    }

    /** Steps to undo, newest first. */
    private final Deque<Step> undo = new ArrayDeque<>();

    /** Steps undone, to redo, newest first. */
    private final Deque<Step> redo = new ArrayDeque<>();

    /** What the newest step was of, while it can still be joined, or null. */
    private @Nullable Object joining;

    /**
     * Record an edit already made.
     *
     * @param step The edit.
     * @param key What it changes, so a drag's changes join one step, or null to never join.
     * @param held Whether the edit goes on, like a slider still held, so a next step of the same
     *     key joins this one.
     */
    public void push(@NonNull Step step, @Nullable Object key, boolean held) {
        redo.clear();
        if (key != null && Objects.equals(key, joining) && !undo.isEmpty()) {
            final Step joined = undo.peekFirst().join(step);
            if (joined != null) {
                undo.removeFirst();
                undo.addFirst(joined);
                joining = held ? key : null;
                return;
            }
        }
        undo.addFirst(step);
        while (undo.size() > DEPTH) {
            undo.removeLast();
        }
        joining = held ? key : null;
    }

    /** End the step being joined, as when the mouse is let go. */
    public void endJoin() {
        joining = null;
    }

    /**
     * Undo the newest step.
     *
     * @return Its name, or null if there was none.
     */
    public @Nullable String undo() {
        joining = null;
        final Step step = undo.pollFirst();
        if (step == null) {
            return null;
        }
        step.undo();
        redo.addFirst(step);
        return step.name();
    }

    /**
     * Redo the newest step undone.
     *
     * @return Its name, or null if there was none.
     */
    public @Nullable String redo() {
        joining = null;
        final Step step = redo.pollFirst();
        if (step == null) {
            return null;
        }
        step.redo();
        undo.addFirst(step);
        return step.name();
    }

    /**
     * Forget steps that can't be undone any more, as when an editor stops showing what they
     * changed.
     *
     * @param which Whether to forget a step.
     */
    public void forget(@NonNull Predicate<Step> which) {
        undo.removeIf(which);
        redo.removeIf(which);
        joining = null;
    }

    /**
     * The newest step's name, for an undo button's tooltip.
     *
     * @return The name, or null if there is nothing to undo.
     */
    public @Nullable String nextUndo() {
        return undo.isEmpty() ? null : undo.peekFirst().name();
    }

    /**
     * The newest undone step's name.
     *
     * @return The name, or null if there is nothing to redo.
     */
    public @Nullable String nextRedo() {
        return redo.isEmpty() ? null : redo.peekFirst().name();
    }
}
