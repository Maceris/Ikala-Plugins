package com.ikalagaming.graphics.frontend.gui;

import com.ikalagaming.graphics.frontend.gui.data.Context;
import com.ikalagaming.graphics.frontend.gui.data.DrawList;
import com.ikalagaming.graphics.frontend.gui.data.Viewport;
import com.ikalagaming.graphics.frontend.gui.enums.MouseCursor;
import com.ikalagaming.graphics.frontend.gui.util.Color;
import com.ikalagaming.graphics.frontend.gui.util.RectFloat;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Draws a software mouse cursor, for io.configMouseDrawCursor. Dear ImGui draws its cursors from
 * bitmaps in its font atlas. Our atlas is a glyph cache, so instead we draw the same pixel art as
 * horizontal runs of filled rectangles, which gives the same shapes.
 */
class IkGuiImplMouseCursor {
    static Context context;

    /**
     * The cursor art from Dear ImGui. '.' pixels are the white (fill) layer, 'X' pixels are the
     * black (border) layer, and other pixels are blank.
     */
    private static final String[] PIXELS = {
        "..-         -XXXXXXX-    X    -           X           -XXXXXXX          -          XXXXXXX-     XX          - XX       XX ",
        "..-         -X.....X-   X.X   -          X.X          -X.....X          -          X.....X-    X..X         -X..X     X..X",
        "---         -XXX.XXX-  X...X  -         X...X         -X....X           -           X....X-    X..X         -X...X   X...X",
        "X           -  X.X  - X.....X -        X.....X        -X...X            -            X...X-    X..X         - X...X X...X ",
        "XX          -  X.X  -X.......X-       X.......X       -X..X.X           -           X.X..X-    X..X         -  X...X...X  ",
        "X.X         -  X.X  -XXXX.XXXX-       XXXX.XXXX       -X.X X.X          -          X.X X.X-    X..XXX       -   X.....X   ",
        "X..X        -  X.X  -   X.X   -          X.X          -XX   X.X         -         X.X   XX-    X..X..XXX    -    X...X    ",
        "X...X       -  X.X  -   X.X   -    XX    X.X    XX    -      X.X        -        X.X      -    X..X..X..XX  -     X.X     ",
        "X....X      -  X.X  -   X.X   -   X.X    X.X    X.X   -       X.X       -       X.X       -    X..X..X..X.X -    X...X    ",
        "X.....X     -  X.X  -   X.X   -  X..X    X.X    X..X  -        X.X      -      X.X        -XXX X..X..X..X..X-   X.....X   ",
        "X......X    -  X.X  -   X.X   - X...XXXXXX.XXXXXX...X -         X.X   XX-XX   X.X         -X..XX........X..X-  X...X...X  ",
        "X.......X   -  X.X  -   X.X   -X.....................X-          X.X X.X-X.X X.X          -X...X...........X- X...X X...X ",
        "X........X  -  X.X  -   X.X   - X...XXXXXX.XXXXXX...X -           X.X..X-X..X.X           - X..............X-X...X   X...X",
        "X.........X -XXX.XXX-   X.X   -  X..X    X.X    X..X  -            X...X-X...X            -  X.............X-X..X     X..X",
        "X..........X-X.....X-   X.X   -   X.X    X.X    X.X   -           X....X-X....X           -  X.............X- XX       XX ",
        "X......XXXXX-XXXXXXX-   X.X   -    XX    X.X    XX    -          X.....X-X.....X          -   X............X--------------",
        "X...X..X    ---------   X.X   -          X.X          -          XXXXXXX-XXXXXXX          -   X...........X -             ",
        "X..X X..X   -       -XXXX.XXXX-       XXXX.XXXX       -------------------------------------    X..........X -             ",
        "X.X  X..X   -       -X.......X-       X.......X       -    XX           XX    -           -    X..........X -             ",
        "XX    X..X  -       - X.....X -        X.....X        -   X.X           X.X   -           -     X........X  -             ",
        "      X..X  -       -  X...X  -         X...X         -  X..X           X..X  -           -     X........X  -             ",
        "       XX   -       -   X.X   -          X.X          - X...XXXXXXXXXXXXX...X -           -     XXXXXXXXXX  -             ",
        "-------------       -    X    -           X           -X.....................X-           -------------------             ",
        "                    ----------------------------------- X...XXXXXXXXXXXXX...X -                                           ",
        "                                                      -  X..X           X..X  -                                           ",
        "                                                      -   X.X           X.X   -                                           ",
        "                                                      -    XX           XX    -                                           ",
    };

    /**
     * The position in the art, the size, and the hotspot offset of each cursor, as {x, y, width,
     * height, offsetX, offsetY}.
     */
    private static final Map<MouseCursor, int[]> CURSOR_DATA = new EnumMap<>(MouseCursor.class);

    static {
        CURSOR_DATA.put(MouseCursor.ARROW, new int[] {0, 3, 12, 19, 0, 0});
        CURSOR_DATA.put(MouseCursor.TEXT_INPUT, new int[] {13, 0, 7, 16, 1, 8});
        CURSOR_DATA.put(MouseCursor.RESIZE_ALL, new int[] {31, 0, 23, 23, 11, 11});
        CURSOR_DATA.put(MouseCursor.RESIZE_NS, new int[] {21, 0, 9, 23, 4, 11});
        CURSOR_DATA.put(MouseCursor.RESIZE_EW, new int[] {55, 18, 23, 9, 11, 4});
        CURSOR_DATA.put(MouseCursor.RESIZE_NE_SW, new int[] {73, 0, 17, 17, 8, 8});
        CURSOR_DATA.put(MouseCursor.RESIZE_NW_SE, new int[] {55, 0, 17, 17, 8, 8});
        CURSOR_DATA.put(MouseCursor.HAND, new int[] {91, 0, 17, 22, 5, 0});
        CURSOR_DATA.put(MouseCursor.NOT_ALLOWED, new int[] {109, 0, 13, 15, 6, 7});
    }

    /**
     * The pixels of one layer of a cursor, as horizontal runs relative to the top left of the
     * cursor.
     *
     * @param runs Each run as {x, y, length}.
     */
    record Layer(List<int[]> runs) {}

    /**
     * The layers of a cursor.
     *
     * @param fill The white pixels.
     * @param border The black pixels.
     * @param width The width in pixels.
     * @param height The height in pixels.
     * @param offsetX The hotspot X position.
     * @param offsetY The hotspot Y position.
     */
    record CursorShape(Layer fill, Layer border, int width, int height, int offsetX, int offsetY) {}

    /** The shape of each cursor, built from the pixel art. */
    private static final Map<MouseCursor, CursorShape> SHAPES = new EnumMap<>(MouseCursor.class);

    static {
        for (Map.Entry<MouseCursor, int[]> entry : CURSOR_DATA.entrySet()) {
            final int[] data = entry.getValue();
            SHAPES.put(
                    entry.getKey(),
                    new CursorShape(
                            buildLayer(data, '.'),
                            buildLayer(data, 'X'),
                            data[2],
                            data[3],
                            data[4],
                            data[5]));
        }
    }

    /**
     * Find the runs of one kind of pixel in a cursor's part of the art.
     *
     * @param data The cursor data, {x, y, width, height, offsetX, offsetY}.
     * @param pixel The pixel character to find.
     * @return The layer.
     */
    private static Layer buildLayer(int[] data, char pixel) {
        final List<int[]> runs = new ArrayList<>();
        for (int y = 0; y < data[3]; ++y) {
            final String row = PIXELS[data[1] + y];
            int x = 0;
            while (x < data[2]) {
                if (row.charAt(data[0] + x) != pixel) {
                    x++;
                    continue;
                }
                final int start = x;
                while (x < data[2] && row.charAt(data[0] + x) == pixel) {
                    x++;
                }
                runs.add(new int[] {start, y, x - start});
            }
        }
        return new Layer(List.copyOf(runs));
    }

    /**
     * Fetch the shape of a cursor.
     *
     * @param cursor The cursor. Values without a shape use the arrow.
     * @return The shape.
     */
    static CursorShape getShape(@NonNull MouseCursor cursor) {
        final CursorShape shape = SHAPES.get(cursor);
        return shape != null ? shape : SHAPES.get(MouseCursor.ARROW);
    }

    /**
     * Draw a mouse cursor on the foreground draw list of every viewport it overlaps.
     *
     * @param x The X position of the mouse.
     * @param y The Y position of the mouse.
     * @param baseScale The scale, typically style.mouseCursorScale.
     * @param cursor The cursor to draw. NONE and values without a shape draw the arrow.
     * @param fillColor The fill color.
     * @param borderColor The border color.
     * @param shadowColor The shadow color.
     */
    static void renderMouseCursor(
            float x,
            float y,
            float baseScale,
            @NonNull MouseCursor cursor,
            int fillColor,
            int borderColor,
            int shadowColor) {
        final CursorShape shape = getShape(cursor);
        final RectFloat viewportRect = new RectFloat();
        for (Viewport viewport : context.viewports) {
            // We scale the cursor with the current viewport/monitor
            final float scale = baseScale * (viewport.dpiScale > 0.0f ? viewport.dpiScale : 1.0f);
            final float posX = x - shape.offsetX();
            final float posY = y - shape.offsetY();
            viewport.getMainRect(viewportRect);
            if (!viewportRect.overlaps(
                    new RectFloat(
                            posX,
                            posY,
                            posX + (shape.width() + 2) * scale,
                            posY + (shape.height() + 2) * scale))) {
                continue;
            }
            final DrawList drawList = IkGuiImplViewports.getViewportForegroundDrawList(viewport);
            drawLayer(drawList, shape.border(), posX + scale, posY, scale, shadowColor);
            drawLayer(drawList, shape.border(), posX + 2 * scale, posY, scale, shadowColor);
            drawLayer(drawList, shape.border(), posX, posY, scale, borderColor);
            drawLayer(drawList, shape.fill(), posX, posY, scale, fillColor);
        }
    }

    private static void drawLayer(
            DrawList drawList, Layer layer, float x, float y, float scale, int color) {
        for (int[] run : layer.runs()) {
            final float minX = x + run[0] * scale;
            final float minY = y + run[1] * scale;
            drawList.addRectFilled(minX, minY, minX + run[2] * scale, minY + scale, color);
        }
    }

    /** The default colors, as in Dear ImGui. */
    static final int FILL_COLOR = Color.WHITE;

    static final int BORDER_COLOR = Color.BLACK;

    static final int SHADOW_COLOR = Color.rgba(0, 0, 0, 48);

    /** Private constructor so this is not instantiated. */
    private IkGuiImplMouseCursor() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
