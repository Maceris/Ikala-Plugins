package com.ikalagaming.factory.gui;

import com.ikalagaming.factory.chat.ChatText;
import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.graphics.gui.util.Color;
import com.ikalagaming.graphics.ui.Align;
import com.ikalagaming.graphics.ui.Anchors;
import com.ikalagaming.graphics.ui.Column;
import com.ikalagaming.graphics.ui.Label;
import com.ikalagaming.graphics.ui.Layer;
import com.ikalagaming.graphics.ui.Length;
import com.ikalagaming.graphics.ui.Row;
import com.ikalagaming.graphics.ui.Sizing;
import com.ikalagaming.graphics.ui.Surface;
import com.ikalagaming.graphics.ui.TextInput;
import com.ikalagaming.graphics.ui.style.Style;
import com.ikalagaming.graphics.ui.style.StyleKey;

import lombok.NonNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * The chat box in the bottom left corner. Closed, it shows the last few lines for a while without
 * taking any input; open, it shows more of them and a line to type in, where Enter sends and Escape
 * closes. Lines are colored spans. Main thread only.
 */
public final class ChatBox {

    /** The chat's surface. */
    public static final String SURFACE_ID = "factory/chat";

    /** How many lines are kept. */
    static final int HISTORY = 100;

    /** How many lines show while closed. */
    static final int CLOSED_LINES = 10;

    /** How many lines show while open. */
    static final int OPEN_LINES = 20;

    /** How long a line shows while the chat is closed. */
    static final long SHOW_NANOS = TimeUnit.SECONDS.toNanos(10);

    /** How wide the box is, in UI units. */
    private static final float WIDTH = 640;

    /** How far it sits from the corner, in UI units. */
    private static final float MARGIN = 8;

    /** The color behind the lines: translucent black. */
    private static final int BACKING = Color.rgba(0, 0, 0, 110);

    /** The text color when a span says to use the default. */
    private static final int DEFAULT_TEXT = 0xFFFFFF;

    /** A line and when it arrived. */
    private record Line(@NonNull ChatText text, long arrivedNanos) {}

    /** The graphics that own the surface. */
    private final GraphicsContext graphics;

    /** Sends what the player types. */
    private final Consumer<String> send;

    /** The lines, oldest first. */
    private final Deque<Line> lines = new ArrayDeque<>();

    /** The text the box opened with. */
    private String typed = "";

    /** The input's node ID. */
    static final String INPUT_ID = "input";

    /** Checks whether shown lines have expired. */
    private final Runnable tick = this::tick;

    /** Whether the box is open for typing. */
    private boolean open;

    /** Whether the input takes the keyboard on its next frame. */
    private boolean focusNext;

    /** Whether the surface needs building again. */
    private boolean dirty = true;

    /** How many lines showed when it was last built, to rebuild when one expires. */
    private int shownCount = -1;

    /** How many times the box has ticked, once per drawn frame. */
    private long ticks;

    /** The text the opening key would leave if it were typed too, or null. */
    private String echo;

    /** The tick to stop watching for the opening key's echo after. */
    private long echoUntil;

    /** The input while open, or null. */
    private TextInput input;

    /** How many frames after opening the opening key's character is undone. */
    private static final int ECHO_FRAMES = 3;

    /**
     * A chat box.
     *
     * @param graphics The plugin's graphics.
     * @param send Sends a line the player typed.
     */
    public ChatBox(@NonNull GraphicsContext graphics, @NonNull Consumer<String> send) {
        this.graphics = graphics;
        this.send = send;
        graphics.addFrameTask(tick);
    }

    /**
     * Add a line, as when the server sends one.
     *
     * @param text The line.
     */
    public void add(@NonNull ChatText text) {
        lines.addLast(new Line(text, System.nanoTime()));
        while (lines.size() > HISTORY) {
            lines.removeFirst();
        }
        dirty = true;
    }

    /**
     * Open the box for typing.
     *
     * @param start What the line starts with, like {@code /} for a command.
     */
    public void open(@NonNull String start) {
        open(start, "");
    }

    /**
     * Open the box for typing, undoing the character of the key that opened it if the field gets it
     * too.
     *
     * @param start What the line starts with, like {@code /} for a command.
     * @param opener The character the opening key types, like {@code t}, or blank.
     */
    public void open(@NonNull String start, @NonNull String opener) {
        echo = opener.isEmpty() ? null : start + opener;
        echoUntil = ticks + ECHO_FRAMES;
        typed = start;
        open = true;
        focusNext = true;
        dirty = true;
    }

    /** Close the box without sending. */
    public void close() {
        if (open) {
            open = false;
            input = null;
            dirty = true;
        }
    }

    /**
     * Whether the box is open for typing.
     *
     * @return True if open.
     */
    public boolean isOpen() {
        return open;
    }

    /** Forget every line and close, as when leaving a game. */
    public void clear() {
        lines.clear();
        open = false;
        dirty = true;
    }

    /**
     * The lines kept, oldest first, for tests.
     *
     * @return The lines.
     */
    List<ChatText> history() {
        return lines.stream().map(Line::text).toList();
    }

    /** Rebuild the surface when something changed or a line expired. */
    private void tick() {
        if (graphics.isClosed()) {
            return;
        }
        ++ticks;
        if (echo != null && input != null) {
            if (input.text().equals(echo)) {
                input.text(typed);
                echo = null;
            } else if (ticks > echoUntil) {
                echo = null;
            }
        }
        final List<Line> shown = shownLines();
        if (!dirty && shown.size() == shownCount) {
            return;
        }
        dirty = false;
        shownCount = shown.size();
        if (shown.isEmpty() && !open) {
            graphics.ui().remove(SURFACE_ID);
            return;
        }
        graphics.ui().show(build(shown));
    }

    /**
     * The lines to show now.
     *
     * @return The lines, oldest first.
     */
    private List<Line> shownLines() {
        final long now = System.nanoTime();
        final List<Line> shown = new ArrayList<>();
        for (Line line : lines) {
            if (open || now - line.arrivedNanos() < SHOW_NANOS) {
                shown.add(line);
            }
        }
        final int most = open ? OPEN_LINES : CLOSED_LINES;
        return shown.subList(Math.max(0, shown.size() - most), shown.size());
    }

    /**
     * Build the surface.
     *
     * @param shown The lines to show.
     * @return The surface.
     */
    private Surface build(@NonNull List<Line> shown) {
        final Column content =
                new Column("lines")
                        .gap(2)
                        .align(Align.STRETCH)
                        .width(Sizing.grow())
                        // A dark backing so lines read over a bright world
                        .style(Style.builder().color(StyleKey.BACKGROUND, BACKING).build());
        int index = 0;
        for (Line line : shown) {
            final Row row = new Row("line" + index++).gap(0);
            int spanIndex = 0;
            for (ChatText.Span span : line.text().spans()) {
                final int rgb = span.color() == ChatText.DEFAULT ? DEFAULT_TEXT : span.color();
                row.add(
                        new Label("span" + spanIndex++, span.text())
                                .style(
                                        Style.builder()
                                                .color(
                                                        StyleKey.TEXT,
                                                        Color.rgba(
                                                                rgb >> 16 & 0xFF,
                                                                rgb >> 8 & 0xFF,
                                                                rgb & 0xFF,
                                                                255))
                                                .build()));
            }
            content.add(row);
        }
        if (open) {
            // A new line arriving rebuilds the box; keep what the player has typed so far
            input =
                    new TextInput(INPUT_ID, input == null ? typed : input.text())
                            .submitOnEnterOnly(true)
                            .onSubmit(this::submit)
                            .onCancel(this::close);
            if (focusNext) {
                input.focus();
                focusNext = false;
            }
            content.add(input);
        }
        final Surface surface =
                graphics.ui()
                        .surface(SURFACE_ID)
                        .anchors(
                                Anchors.bottomLeft()
                                        .offset(Length.u(MARGIN), Length.u(-MARGIN * 8)))
                        .width(Sizing.fixed(WIDTH))
                        .height(Sizing.fit())
                        .layer(Layer.NORMAL)
                        .content(content);
        if (!open) {
            surface.hud();
        }
        return surface;
    }

    /**
     * Send what was typed, and close.
     *
     * @param text The text.
     */
    private void submit(@NonNull String text) {
        final String line = text.strip();
        if (!line.isEmpty()) {
            send.accept(line);
        }
        close();
    }
}
