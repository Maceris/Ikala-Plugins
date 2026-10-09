package com.ikalagaming.graphics.gui.data;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The lines of the debug log, and the count of error events that were not logged. This is safe to
 * use from any thread, since errors can be reported by things like fonts loading in the background.
 * Lines are only ever appended, except when the whole log is cleared.
 */
public class DebugLogBuffer {
    /** Guards all the fields. */
    private final ReentrantLock lock = new ReentrantLock();

    /** The lines of the log. Guarded by {@link #lock}. */
    private final List<String> lines = new ArrayList<>();

    /**
     * How many error events were skipped because errors were disabled. Guarded by {@link #lock}.
     */
    private int skippedErrors;

    /**
     * Add lines to the end of the log. The lines are added together, so they won't be mixed up with
     * lines being added by another thread.
     *
     * @param newLines The lines to add.
     */
    public void addAll(@NonNull List<String> newLines) {
        lock.lock();
        try {
            lines.addAll(newLines);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Add a line to the end of the log.
     *
     * @param line The line to add.
     */
    public void add(@NonNull String line) {
        lock.lock();
        try {
            lines.add(line);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Fetch a line of the log.
     *
     * @param index The index of the line, where 0 is the oldest.
     * @return The line.
     */
    public String get(int index) {
        lock.lock();
        try {
            return lines.get(index);
        } finally {
            lock.unlock();
        }
    }

    /**
     * The number of lines in the log. Since lines are only appended, any index below this stays
     * valid until the log is cleared.
     *
     * @return How many lines there are.
     */
    public int size() {
        lock.lock();
        try {
            return lines.size();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Check if there are no lines.
     *
     * @return True if the log is empty.
     */
    public boolean isEmpty() {
        lock.lock();
        try {
            return lines.isEmpty();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Fetch a copy of all the lines, which won't change as more are logged.
     *
     * @return A copy of the lines.
     */
    public List<String> snapshot() {
        lock.lock();
        try {
            return List.copyOf(lines);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Fetch the whole log as a single string, one line after another.
     *
     * @return The text of the log.
     */
    public String getText() {
        lock.lock();
        try {
            return String.join("\n", lines);
        } finally {
            lock.unlock();
        }
    }

    /** Count an error that was not logged because error events are disabled. */
    public void addSkippedError() {
        lock.lock();
        try {
            ++skippedErrors;
        } finally {
            lock.unlock();
        }
    }

    /**
     * How many error events were skipped since the log was last cleared.
     *
     * @return The number of skipped errors.
     */
    public int getSkippedErrors() {
        lock.lock();
        try {
            return skippedErrors;
        } finally {
            lock.unlock();
        }
    }

    /** Remove all the lines, and reset the skipped error count. */
    public void clear() {
        lock.lock();
        try {
            lines.clear();
            skippedErrors = 0;
        } finally {
            lock.unlock();
        }
    }
}
