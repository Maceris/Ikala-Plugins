package com.ikalagaming.graphics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.graph.MaterialCache;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/** How a preview asks for frames, resizes, saves and closes, without a renderer. */
class PreviewViewTest {
    private static PreviewView preview() {
        return new PreviewView("Test#1", 3, 64, 32, new MaterialCache(), () -> null);
    }

    @Test
    void aNewPreviewWantsToSettle() {
        PreviewView view = preview();
        assertEquals(PreviewView.SETTLE_FRAMES, view.getFramesWanted());
        assertEquals(3, view.getScene().getViewID());
        assertFalse(view.getScene().isAutoExposure(), "Previews use a fixed exposure");
        for (int i = PreviewView.SETTLE_FRAMES - 1; i >= 0; --i) {
            assertEquals(i, view.frameDrawn());
        }
        assertEquals(0, view.frameDrawn(), "Never below zero");
    }

    @Test
    void requestsKeepTheLongerWait() {
        PreviewView view = preview();
        while (view.frameDrawn() > 0) {
            // settle
        }
        view.requestFrames(3);
        view.requestFrames(1);
        assertEquals(3, view.getFramesWanted());
        view.requestFrames(40);
        assertEquals(40, view.getFramesWanted());
    }

    @Test
    void resizingAsksForFrames() {
        PreviewView view = preview();
        while (view.frameDrawn() > 0) {
            // settle
        }
        view.resize(64, 32);
        assertEquals(0, view.getFramesWanted(), "Same size, nothing to draw");
        view.resize(128, 0);
        assertEquals(128, view.getWidth());
        assertEquals(1, view.getHeight(), "At least a pixel");
        assertEquals(PreviewView.SETTLE_FRAMES, view.getFramesWanted());
    }

    @Test
    void capturesWaitUntilTakenAndFailOnClose() {
        PreviewView view = preview();
        CompletableFuture<Path> first = view.capture(Path.of("a.png"));
        for (int i = 1; i < PreviewView.SETTLE_FRAMES; ++i) {
            view.requestFrames(2);
            view.frameDrawn();
            assertTrue(view.takeCaptures().isEmpty(), "Waits its frames");
        }
        view.requestFrames(2);
        view.frameDrawn();
        assertTrue(view.getFramesWanted() > 0, "Still drawn every frame");
        assertEquals(1, view.takeCaptures().size(), "Taken once it has waited");
        assertTrue(view.takeCaptures().isEmpty(), "Taken once");
        assertFalse(first.isDone(), "The renderer completes it");

        CompletableFuture<Path> second = view.capture(Path.of("b.png"));
        view.close();
        assertTrue(view.isClosed());
        assertThrows(ExecutionException.class, second::get);
    }
}
