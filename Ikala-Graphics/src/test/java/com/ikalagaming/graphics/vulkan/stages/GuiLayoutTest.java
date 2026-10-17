package com.ikalagaming.graphics.vulkan.stages;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.gui.data.DrawData;
import com.ikalagaming.graphics.gui.data.DrawList;
import com.ikalagaming.graphics.gui.data.DrawTextures;

import org.junit.jupiter.api.Test;

import java.util.List;

/** How every window's draw lists are packed into the same GUI buffers. */
class GuiLayoutTest {

    /**
     * Make a draw list that holds some number of quads, commands, points and point details.
     *
     * @param quads The number of quads, each a command with 6 vertices.
     * @param points The number of points.
     * @param details The number of point details.
     * @return The draw list.
     */
    private static DrawList drawList(int quads, int points, int details) {
        DrawList list = new DrawList("test");
        list.vertexBuffer.limit(quads * 6 * DrawData.SIZE_OF_VERTEX);
        list.commandBuffer.limit(quads * DrawData.SIZE_OF_DRAW_COMMAND);
        list.pointBuffer.limit(points * DrawData.SIZE_OF_POINT);
        list.pointDetailBuffer.limit(details * DrawData.SIZE_OF_POINT_DETAIL);
        return list;
    }

    @Test
    void testDrawDataAreLaidOutOneAfterAnother() {
        final DrawTextures textures = new DrawTextures();
        DrawData main = new DrawData(textures);
        main.drawLists.add(drawList(2, 5, 3));
        main.drawLists.add(drawList(1, 4, 1));
        DrawData other = new DrawData(textures);
        other.drawLists.add(drawList(3, 2, 2));

        GuiLayout layout = GuiLayout.of(List.of(main, other));

        GuiLayout.DrawListOffsets[] mainOffsets = layout.offsetsOf(main);
        assertEquals(2, mainOffsets.length);
        assertEquals(new GuiLayout.DrawListOffsets(0, 0, 0, 0), mainOffsets[0]);
        assertEquals(
                new GuiLayout.DrawListOffsets(2 * 6 * DrawData.SIZE_OF_VERTEX, 2, 5, 3),
                mainOffsets[1]);

        // The other window's lists start after everything the main window drew
        GuiLayout.DrawListOffsets[] otherOffsets = layout.offsetsOf(other);
        assertEquals(1, otherOffsets.length);
        assertEquals(
                new GuiLayout.DrawListOffsets(3 * 6 * DrawData.SIZE_OF_VERTEX, 3, 9, 4),
                otherOffsets[0]);

        assertEquals(6L * 6 * DrawData.SIZE_OF_VERTEX, layout.vertexBytes);
        assertEquals(6L * DrawData.SIZE_OF_DRAW_COMMAND, layout.commandBytes);
        assertEquals(11L * DrawData.SIZE_OF_POINT, layout.pointBytes);
        assertEquals(6L * DrawData.SIZE_OF_POINT_DETAIL, layout.detailBytes);
        assertEquals(List.of(main, other), layout.drawData());
    }

    @Test
    void testDrawDataAreOnlyLaidOutOnce() {
        DrawData main = new DrawData(new DrawTextures());
        main.drawLists.add(drawList(1, 1, 1));

        GuiLayout layout = GuiLayout.of(List.of(main, main));

        assertEquals(1, layout.drawData().size());
        assertSame(main, layout.drawData().getFirst());
        assertEquals(DrawData.SIZE_OF_DRAW_COMMAND, layout.commandBytes);
    }

    @Test
    void testEmptyLayout() {
        GuiLayout layout = GuiLayout.of(List.of());

        assertTrue(layout.drawData().isEmpty());
        assertEquals(0, layout.vertexBytes);
        assertNull(layout.offsetsOf(new DrawData(new DrawTextures())));
    }
}
