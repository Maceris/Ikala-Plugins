package com.ikalagaming.graphics.frontend.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.frontend.gui.data.Context;
import com.ikalagaming.graphics.frontend.gui.data.Payload;
import com.ikalagaming.graphics.frontend.gui.enums.Condition;
import com.ikalagaming.graphics.frontend.gui.enums.Key;
import com.ikalagaming.graphics.frontend.gui.enums.MouseButton;
import com.ikalagaming.graphics.frontend.gui.flags.DragDropFlags;

import org.joml.Vector2f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/** Tests for drag and drop. These run headless, without any fonts loaded. */
class IkGuiDragDropTest {

    private static final String TYPE = "TEST_TYPE";

    private Context context;

    /** Center of the source button, filled in during frames. */
    private final Vector2f sourceCenter = new Vector2f();

    /** Center of the target button, filled in during frames. */
    private final Vector2f targetCenter = new Vector2f();

    /** Payloads delivered to the target. */
    private final List<Object> delivered = new ArrayList<>();

    @BeforeEach
    void setUp() {
        context = IkGui.createContext();
        // Don't read or write an .ini file
        context.io.iniFilename = null;
        context.io.displaySize.set(1280, 720);
        context.io.mouseInsideWindow = true;
        // Process one input event per frame for each input, so we can be precise about timing
        context.io.configInputTrickleEventQueue = true;
    }

    @AfterEach
    void tearDown() {
        IkGui.destroyContext();
    }

    private static void frame(Runnable ui) {
        IkGui.newFrame();
        ui.run();
        IkGui.render();
    }

    private static void frames(int count, Runnable ui) {
        for (int i = 0; i < count; ++i) {
            frame(ui);
        }
    }

    private void moveMouse(Vector2f position) {
        context.io.addMousePosEvent(position.x, position.y);
    }

    private void mouseDown() {
        context.io.addMouseButtonEvent(MouseButton.LEFT, true);
    }

    private void mouseUp() {
        context.io.addMouseButtonEvent(MouseButton.LEFT, false);
    }

    private static void window(Runnable body) {
        IkGui.setNextWindowPos(50, 50, Condition.FIRST_USE_EVER);
        IkGui.setNextWindowSize(400, 400, Condition.FIRST_USE_EVER);
        IkGui.begin("DragDrop");
        body.run();
        IkGui.end();
    }

    private static void center(Vector2f output) {
        IkGui.getItemRectMin(output);
        output.add(IkGui.getItemRectMax()).mul(0.5f);
    }

    /**
     * A UI with a source button that drags the given payload with the given source flags, and a
     * target button below it accepting TYPE.
     */
    private Runnable sourceAndTarget(Object payload, int sourceFlags, boolean submitSource) {
        return () ->
                window(
                        () -> {
                            IkGui.button("Source", 100, 30);
                            center(sourceCenter);
                            if (submitSource && IkGui.beginDragDropSource(sourceFlags)) {
                                IkGui.setDragDropPayload(TYPE, payload);
                                IkGui.text("Dragging");
                                IkGui.endDragDropSource();
                            }

                            IkGui.button("Target", 100, 30);
                            center(targetCenter);
                            if (IkGui.beginDragDropTarget()) {
                                Object accepted = IkGui.acceptDragDropPayload(TYPE);
                                if (accepted != null) {
                                    delivered.add(accepted);
                                }
                                IkGui.endDragDropTarget();
                            }
                        });
    }

    /** Press on the source and drag the mouse over the target, keeping the button held. */
    private void startDragToTarget(Runnable ui) {
        frames(2, ui);
        moveMouse(sourceCenter);
        frames(2, ui);
        mouseDown();
        frames(2, ui);
        moveMouse(new Vector2f(sourceCenter).add(0, 20));
        frames(2, ui);
        moveMouse(targetCenter);
        frames(3, ui);
    }

    @Test
    void testDragAndDropDeliversPayload() {
        Runnable ui = sourceAndTarget(42, DragDropFlags.NONE, true);
        startDragToTarget(ui);

        assertTrue(context.dragDropActive);
        Payload payload = IkGui.getDragDropPayloadInfo();
        assertNotNull(payload);
        assertTrue(payload.isDataType(TYPE));
        assertEquals(42, (Integer) IkGui.getDragDropPayload(TYPE));
        assertNull(IkGui.getDragDropPayload("OTHER_TYPE"));
        // Nothing is delivered until the button is released
        assertTrue(delivered.isEmpty());
        assertTrue(IkGuiInternal.isDragDropPayloadBeingAccepted());

        mouseUp();
        frames(2, ui);

        assertEquals(List.of(42), delivered);
        assertFalse(context.dragDropActive);
        assertNull(IkGui.getDragDropPayloadInfo());
    }

    @Test
    void testReleaseAwayFromTargetDoesNotDeliver() {
        Runnable ui = sourceAndTarget("text", DragDropFlags.NONE, true);
        startDragToTarget(ui);
        moveMouse(new Vector2f(350, 350));
        frames(2, ui);
        mouseUp();
        frames(2, ui);

        assertTrue(delivered.isEmpty());
        assertFalse(context.dragDropActive);
    }

    @Test
    void testEscapeCancelsDrag() {
        Runnable ui = sourceAndTarget("text", DragDropFlags.NONE, true);
        startDragToTarget(ui);
        assertTrue(context.dragDropActive);

        context.io.addKeyEvent(Key.ESCAPE, true);
        frames(2, ui);
        context.io.addKeyEvent(Key.ESCAPE, false);
        frames(2, ui);
        assertFalse(context.dragDropActive);

        mouseUp();
        frames(2, ui);
        assertTrue(delivered.isEmpty());
    }

    @Test
    void testSourceDoesNotReportHovered() {
        final boolean[] sourceHovered = {false};
        Runnable ui =
                () ->
                        window(
                                () -> {
                                    IkGui.button("Source", 100, 30);
                                    center(sourceCenter);
                                    if (IkGui.beginDragDropSource()) {
                                        IkGui.setDragDropPayload(TYPE, 1);
                                        IkGui.endDragDropSource();
                                    }
                                    sourceHovered[0] = IkGui.isItemHovered();
                                });
        frames(2, ui);
        moveMouse(sourceCenter);
        frames(2, ui);
        assertTrue(sourceHovered[0]);

        mouseDown();
        frames(2, ui);
        moveMouse(new Vector2f(sourceCenter).add(10, 0));
        frames(2, ui);
        assertTrue(context.dragDropActive);
        assertFalse(sourceHovered[0]);
    }

    @Test
    void testPayloadPersistsUntilReleasedWithoutAutoExpire() {
        startDragToTarget(sourceAndTarget(7, DragDropFlags.NONE, true));
        // Stop submitting the source, the payload stays while the mouse is held
        Runnable noSource = sourceAndTarget(7, DragDropFlags.NONE, false);
        frames(3, noSource);
        assertTrue(context.dragDropActive);

        mouseUp();
        frames(2, noSource);
        assertEquals(List.of(7), delivered);
    }

    @Test
    void testPayloadAutoExpires() {
        startDragToTarget(sourceAndTarget(7, DragDropFlags.PAYLOAD_AUTO_EXPIRE, true));
        assertTrue(context.dragDropActive);

        frames(3, sourceAndTarget(7, DragDropFlags.PAYLOAD_AUTO_EXPIRE, false));
        assertFalse(context.dragDropActive);
    }

    @Test
    void testAcceptBeforeDeliveryPreviews() {
        final List<Boolean> deliveries = new ArrayList<>();
        Runnable ui =
                () ->
                        window(
                                () -> {
                                    IkGui.button("Source", 100, 30);
                                    center(sourceCenter);
                                    if (IkGui.beginDragDropSource()) {
                                        IkGui.setDragDropPayload(new StringBuilder("sb"));
                                        IkGui.endDragDropSource();
                                    }

                                    IkGui.button("Target", 100, 30);
                                    center(targetCenter);
                                    if (IkGui.beginDragDropTarget()) {
                                        StringBuilder accepted =
                                                IkGui.acceptDragDropPayload(
                                                        StringBuilder.class,
                                                        DragDropFlags.ACCEPT_BEFORE_DELIVERY);
                                        if (accepted != null) {
                                            deliveries.add(
                                                    IkGui.getDragDropPayloadInfo().isDelivery());
                                        }
                                        IkGui.endDragDropTarget();
                                    }
                                });
        startDragToTarget(ui);
        assertFalse(deliveries.isEmpty());
        assertFalse(deliveries.contains(true));

        mouseUp();
        frames(2, ui);
        assertTrue(deliveries.getLast());
        assertFalse(context.dragDropActive);
    }

    @Test
    void testTargetRectIsDrawnWhenPreviewing() {
        Runnable ui = sourceAndTarget(1, DragDropFlags.NONE, true);
        startDragToTarget(ui);
        // The target was accepted last frame, so it shows the preview rectangle
        assertTrue(context.dragDropPayload.isPreview());
    }

    @Test
    void testSmallestTargetWins() {
        final List<String> accepted = new ArrayList<>();
        Runnable ui =
                () ->
                        window(
                                () -> {
                                    IkGui.button("Source", 100, 30);
                                    center(sourceCenter);
                                    if (IkGui.beginDragDropSource()) {
                                        IkGui.setDragDropPayload(TYPE, 1);
                                        IkGui.endDragDropSource();
                                    }

                                    IkGui.beginGroup();
                                    IkGui.button("Target", 100, 30);
                                    center(targetCenter);
                                    if (IkGui.beginDragDropTarget()) {
                                        if (IkGui.acceptDragDropPayload(TYPE) != null) {
                                            accepted.add("inner");
                                        }
                                        IkGui.endDragDropTarget();
                                    }
                                    IkGui.button("Other", 200, 60);
                                    IkGui.endGroup();
                                    if (IkGui.beginDragDropTarget()) {
                                        if (IkGui.acceptDragDropPayload(TYPE) != null) {
                                            accepted.add("outer");
                                        }
                                        IkGui.endDragDropTarget();
                                    }
                                });
        startDragToTarget(ui);
        mouseUp();
        frames(2, ui);
        assertEquals(List.of("inner"), accepted);
    }

    @Test
    void testHoldingPayloadOverTreeNodeOpensIt() {
        final boolean[] open = {false};
        final Vector2f nodeCenter = new Vector2f();
        Runnable ui =
                () ->
                        window(
                                () -> {
                                    IkGui.button("Source", 100, 30);
                                    center(sourceCenter);
                                    if (IkGui.beginDragDropSource()) {
                                        IkGui.setDragDropPayload(TYPE, 1);
                                        IkGui.endDragDropSource();
                                    }
                                    open[0] = IkGui.treeNode("Node");
                                    center(nodeCenter);
                                    if (open[0]) {
                                        IkGui.treePop();
                                    }
                                });
        frames(2, ui);
        moveMouse(sourceCenter);
        frames(2, ui);
        mouseDown();
        frames(2, ui);
        moveMouse(new Vector2f(sourceCenter).add(0, 20));
        frames(2, ui);
        moveMouse(nodeCenter);
        frames(2, ui);
        assertTrue(context.dragDropActive);
        assertFalse(open[0]);

        // Hold over the node for more than the hold-to-open delay
        for (int i = 0; i < 12; ++i) {
            context.frameStartTime -= 100;
            frame(ui);
        }
        assertTrue(open[0]);

        // Holding longer doesn't close it again
        for (int i = 0; i < 12; ++i) {
            context.frameStartTime -= 100;
            frame(ui);
        }
        assertTrue(open[0]);
    }

    @Test
    void testDragSourceWithoutIDRequiresFlag() {
        final boolean[] began = {false};
        final Vector2f textCenter = new Vector2f();
        Runnable ui =
                () ->
                        window(
                                () -> {
                                    IkGui.text("No ID item");
                                    center(textCenter);
                                    if (IkGui.beginDragDropSource(
                                            DragDropFlags.SOURCE_ALLOW_NULL_ID)) {
                                        began[0] = true;
                                        IkGui.setDragDropPayload(TYPE, "text");
                                        IkGui.endDragDropSource();
                                    }
                                });
        frames(2, ui);
        moveMouse(textCenter);
        frames(2, ui);
        mouseDown();
        frames(2, ui);
        moveMouse(new Vector2f(textCenter).add(20, 0));
        frames(2, ui);
        assertTrue(began[0]);
        assertTrue(context.dragDropActive);
        assertEquals("text", IkGui.getDragDropPayload(TYPE));
    }
}
