package com.ikalagaming.graphics.vulkan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/** Saving finished frames: the pixel conversion and what happens to requests. */
class FrameCaptureTest {

    /** Rows stay top first, colors keep their bytes, and alpha is dropped. */
    @Test
    void imageKeepsColorsAndRows() {
        byte[] rgba = {
            (byte) 255,
            0,
            0,
            (byte) 255,
            0,
            (byte) 128,
            0,
            0,
            1,
            2,
            3,
            (byte) 255,
            (byte) 200,
            (byte) 201,
            (byte) 202,
            7
        };
        BufferedImage image = FrameCapture.toImage(rgba, 2, 2);
        assertEquals(BufferedImage.TYPE_INT_RGB, image.getType());
        assertEquals(0xFF0000, image.getRGB(0, 0) & 0xFFFFFF);
        assertEquals(0x008000, image.getRGB(1, 0) & 0xFFFFFF);
        assertEquals(0x010203, image.getRGB(0, 1) & 0xFFFFFF);
        assertEquals(0xC8C9CA, image.getRGB(1, 1) & 0xFFFFFF);
    }

    /**
     * Blue-first pixels, as swapchain images and their headless stand-in hold them, come out right.
     */
    @Test
    void imageFromBgra() {
        byte[] bgra = {(byte) 0x30, (byte) 0x20, (byte) 0x10, (byte) 255};
        BufferedImage image = FrameCapture.toImage(bgra, 1, 1, true);
        assertEquals(0x102030, image.getRGB(0, 0) & 0xFFFFFF);
    }

    /** Too few bytes for the size is refused rather than read past. */
    @Test
    void imageNeedsEveryPixel() {
        assertThrows(
                IllegalArgumentException.class, () -> FrameCapture.toImage(new byte[15], 2, 2));
    }

    /** Requests wait for a frame, and are cancelled if the renderer stops first. */
    @Test
    void requestsWaitThenCancelOnCleanup() {
        FrameCapture capture = new FrameCapture();
        assertFalse(capture.isWaiting());
        CompletableFuture<Path> first = capture.request(Path.of("a.png"));
        CompletableFuture<Path> second = capture.request(Path.of("b.png"));
        assertTrue(capture.isWaiting());
        assertFalse(first.isDone());

        // No buffers were made, so cleanup has nothing to free on the device
        capture.cleanup(new VulkanState());
        assertTrue(first.isCancelled());
        assertTrue(second.isCancelled());
        assertFalse(capture.isWaiting());
    }

    /** A frame index with nothing copied has nothing to collect. */
    @Test
    void collectWithoutCopyDoesNothing() {
        FrameCapture capture = new FrameCapture();
        CompletableFuture<Path> waiting = capture.request(Path.of("a.png"));
        capture.collect(0);
        assertFalse(waiting.isDone());
        assertTrue(capture.isWaiting());
    }
}
