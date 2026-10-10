package com.ikalagaming.graphics.vulkan;

import static com.ikalagaming.graphics.vulkan.VulkanInstance.checkError;
import static org.lwjgl.vulkan.VK13.*;

import com.ikalagaming.graphics.GraphicsManager;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkQueryPoolCreateInfo;

import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import javax.annotation.Nullable;

/**
 * How long each part of a frame takes, on the GPU from timestamps written between the render
 * stages, and on the CPU from recording to submitting. The swapchain waits for vertical sync, so
 * the time between frames only ever shows the refresh rate; these show the real cost.
 *
 * <p>Each frame in flight has its own range of queries. A frame writes a timestamp when it starts
 * and after each named part, and its results are read back the next time its frame index comes
 * round, once its fence says the GPU is done, so reading never waits.
 *
 * <p>Render thread only, except {@link #snapshot()}, which is safe from any thread.
 */
@Slf4j
public class FrameTimings {

    /** The most timestamps one frame can write. */
    public static final int MAX_MARKS = 64;

    /** How many frames the rolling statistics cover. */
    public static final int WINDOW = 120;

    /** The name of the whole frame on the GPU, from its first timestamp to its last. */
    public static final String GPU_TOTAL = "GPU total";

    /** The name of the CPU time spent recording and submitting a frame. */
    public static final String CPU = "CPU";

    /**
     * One part of a frame's timings.
     *
     * @param name What it is.
     * @param average The average over the window, in milliseconds.
     * @param p95 The 95th percentile over the window, in milliseconds.
     * @param max The longest in the window, in milliseconds.
     */
    public record Timing(@NonNull String name, double average, double p95, double max) {}

    /** The last {@link #WINDOW} samples of one part of a frame. */
    static final class Rolling {
        /** The samples, as a ring. */
        private final double[] samples = new double[WINDOW];

        /** How many samples have been added, ever. */
        private long added;

        /**
         * Add a sample.
         *
         * @param milliseconds The sample.
         */
        void add(double milliseconds) {
            samples[(int) (added % WINDOW)] = milliseconds;
            added += 1;
        }

        /**
         * Summarize the window.
         *
         * @param name The part's name.
         * @return The average, 95th percentile and maximum.
         */
        Timing summarize(@NonNull String name) {
            final int count = (int) Math.min(added, WINDOW);
            return FrameTimings.summarize(name, Arrays.copyOf(samples, count));
        }
    }

    /** The query pool, VK_NULL_HANDLE if timestamps aren't supported. */
    private long queryPool = VK_NULL_HANDLE;

    /** How many nanoseconds one timestamp tick is. */
    private final float tickNanoseconds;

    /** How many bits of a timestamp are valid. */
    private final int validBits;

    /**
     * Whether the GPU can write timestamps. -- GETTER -- Whether timings are available.
     *
     * @return False if the graphics queue can't write timestamps.
     */
    @Getter private final boolean supported;

    /** The name after each mark of each frame in flight, the first being the frame's start. */
    private final List<List<String>> names = new ArrayList<>();

    /** Whether each frame in flight has results waiting to be read. */
    private final boolean[] pending = new boolean[GraphicsManager.MAX_FRAMES_IN_FLIGHT];

    /** The CPU time of each frame in flight, in milliseconds, read along with its GPU results. */
    private final double[] cpuMilliseconds = new double[GraphicsManager.MAX_FRAMES_IN_FLIGHT];

    /** When the current frame's CPU work started, from {@link System#nanoTime()}. */
    private long cpuStart;

    /** Guards {@link #rolling}. */
    private final ReentrantLock lock = new ReentrantLock();

    /** Each part's samples, in the order first seen. */
    private final Map<String, Rolling> rolling = new LinkedHashMap<>();

    /** Told about every frame's timings as they are read back, by part name, or null. */
    @Nullable private volatile Consumer<Map<String, Double>> listener;

    /**
     * Set up timings for the selected device.
     *
     * @param state The Vulkan state, with its device created.
     */
    public FrameTimings(@NonNull VulkanState state) {
        final VulkanState.PhysicalDeviceInfo physical = state.device.physical;
        tickNanoseconds = physical.deviceProperties.limits().timestampPeriod();
        validBits = physical.graphicsTimestampValidBits;
        supported = validBits > 0 && tickNanoseconds > 0;
        for (int i = 0; i < GraphicsManager.MAX_FRAMES_IN_FLIGHT; ++i) {
            names.add(new ArrayList<>());
        }
        if (!supported) {
            log.info(
                    "The graphics queue can't write timestamps ({} valid bits, {} ns a tick), so GPU"
                            + " timings are off",
                    validBits,
                    tickNanoseconds);
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longOutput = stack.callocLong(1);
            checkError(
                    vkCreateQueryPool(
                            state.device.logical,
                            VkQueryPoolCreateInfo.calloc(stack)
                                    .sType$Default()
                                    .queryType(VK_QUERY_TYPE_TIMESTAMP)
                                    .queryCount(MAX_MARKS * GraphicsManager.MAX_FRAMES_IN_FLIGHT),
                            null,
                            longOutput));
            queryPool = longOutput.get(0);
        }
    }

    /**
     * Milliseconds between two timestamps, allowing for the counter wrapping.
     *
     * @param start The earlier timestamp.
     * @param end The later timestamp.
     * @param validBits How many bits of a timestamp are valid.
     * @param tickNanoseconds How many nanoseconds one tick is.
     * @return The time between them.
     */
    static double millisecondsBetween(long start, long end, int validBits, float tickNanoseconds) {
        final long mask = validBits >= Long.SIZE ? -1L : (1L << validBits) - 1;
        final long ticks = (end - start) & mask;
        return ticks * (double) tickNanoseconds / 1_000_000.0;
    }

    /**
     * Summarize samples.
     *
     * @param name What they are of.
     * @param samples The samples, in milliseconds.
     * @return The average, 95th percentile and maximum, all 0 if there are none.
     */
    public static Timing summarize(@NonNull String name, double @NonNull [] samples) {
        if (samples.length == 0) {
            return new Timing(name, 0, 0, 0);
        }
        final double[] sorted = samples.clone();
        Arrays.sort(sorted);
        double sum = 0;
        for (double sample : sorted) {
            sum += sample;
        }
        // The nearest-rank percentile
        final int rank = (int) Math.ceil(0.95 * sorted.length) - 1;
        return new Timing(
                name, sum / sorted.length, sorted[Math.max(0, rank)], sorted[sorted.length - 1]);
    }

    /**
     * Start a frame: read back the last results of this frame index, which the GPU is done with,
     * then reset its queries and write its first timestamp. Call right after the command buffer
     * begins.
     *
     * @param commandBuffer The frame's command buffer, recording.
     * @param frame The frame index.
     * @param state The Vulkan state.
     */
    public void begin(
            @NonNull VkCommandBuffer commandBuffer, int frame, @NonNull VulkanState state) {
        cpuStart = System.nanoTime();
        if (!supported) {
            return;
        }
        if (pending[frame]) {
            readBack(frame, state);
        }
        vkCmdResetQueryPool(commandBuffer, queryPool, frame * MAX_MARKS, MAX_MARKS);
        names.get(frame).clear();
        mark(commandBuffer, frame, "");
    }

    /**
     * Note that a part of the frame finished, writing a timestamp once the GPU has done everything
     * recorded so far.
     *
     * @param commandBuffer The frame's command buffer.
     * @param frame The frame index.
     * @param name What finished.
     */
    public void mark(@NonNull VkCommandBuffer commandBuffer, int frame, @NonNull String name) {
        final List<String> frameNames = names.get(frame);
        if (!supported || frameNames.size() >= MAX_MARKS) {
            return;
        }
        vkCmdWriteTimestamp2(
                commandBuffer,
                VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT,
                queryPool,
                frame * MAX_MARKS + frameNames.size());
        frameNames.add(name);
    }

    /**
     * Finish a frame's CPU work. Call right after submitting it.
     *
     * @param frame The frame index.
     */
    public void end(int frame) {
        cpuMilliseconds[frame] = (System.nanoTime() - cpuStart) / 1_000_000.0;
        pending[frame] = true;
        if (!supported) {
            record(Map.of(CPU, cpuMilliseconds[frame]));
            pending[frame] = false;
        }
    }

    /**
     * Read a frame's timestamps and add them to the statistics.
     *
     * @param frame The frame index, which the GPU is done with.
     * @param state The Vulkan state.
     */
    private void readBack(int frame, @NonNull VulkanState state) {
        pending[frame] = false;
        final List<String> frameNames = names.get(frame);
        final int count = frameNames.size();
        if (count < 2) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer results = stack.mallocLong(count);
            final int result =
                    vkGetQueryPoolResults(
                            state.device.logical,
                            queryPool,
                            frame * MAX_MARKS,
                            count,
                            results,
                            Long.BYTES,
                            VK_QUERY_RESULT_64_BIT);
            if (result != VK_SUCCESS) {
                return;
            }
            final Map<String, Double> frameTimes = new LinkedHashMap<>();
            for (int i = 1; i < count; ++i) {
                frameTimes.merge(
                        frameNames.get(i),
                        millisecondsBetween(
                                results.get(i - 1), results.get(i), validBits, tickNanoseconds),
                        Double::sum);
            }
            frameTimes.put(
                    GPU_TOTAL,
                    millisecondsBetween(
                            results.get(0), results.get(count - 1), validBits, tickNanoseconds));
            frameTimes.put(CPU, cpuMilliseconds[frame]);
            record(frameTimes);
        }
    }

    /**
     * Add one frame's timings to the statistics, and tell the listener.
     *
     * @param frameTimes Each part's time, in milliseconds.
     */
    private void record(@NonNull Map<String, Double> frameTimes) {
        lock.lock();
        try {
            frameTimes.forEach(
                    (name, ms) -> rolling.computeIfAbsent(name, n -> new Rolling()).add(ms));
        } finally {
            lock.unlock();
        }
        final Consumer<Map<String, Double>> current = listener;
        if (current != null) {
            current.accept(frameTimes);
        }
    }

    /**
     * Listen for every frame's timings as they are read back, a frame or two after it was drawn.
     * Called on the render thread.
     *
     * @param listener Takes each part's time in milliseconds, or null to stop listening.
     */
    public void setListener(@Nullable Consumer<Map<String, Double>> listener) {
        this.listener = listener;
    }

    /**
     * The rolling statistics: the GPU total and CPU first, then each part in the order first seen.
     *
     * @return The timings.
     */
    public List<Timing> snapshot() {
        lock.lock();
        try {
            final List<Timing> timings = new ArrayList<>();
            for (String first : new String[] {GPU_TOTAL, CPU}) {
                final Rolling samples = rolling.get(first);
                if (samples != null) {
                    timings.add(samples.summarize(first));
                }
            }
            rolling.forEach(
                    (name, samples) -> {
                        if (!GPU_TOTAL.equals(name) && !CPU.equals(name)) {
                            timings.add(samples.summarize(name));
                        }
                    });
            return timings;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Destroy the query pool, when shutting down. The GPU must be idle.
     *
     * @param state The Vulkan state.
     */
    public void cleanup(@NonNull VulkanState state) {
        if (queryPool != VK_NULL_HANDLE) {
            vkDestroyQueryPool(state.device.logical, queryPool, null);
            queryPool = VK_NULL_HANDLE;
        }
    }
}
