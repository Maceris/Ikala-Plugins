package com.ikalagaming.graphics.benchmark;

import com.ikalagaming.graphics.vulkan.FrameTimings;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/**
 * Keeps a {@link GateBenchmark} run's measured frames: each part's time as the renderer reads it
 * back, and what the render thread allocated in between, logging frames slow enough to be hitches.
 * Driven on the render thread as {@link FrameTimings}' listener.
 */
@Slf4j
final class GateFrameRecorder {

    /** The row of the summary holding what the render thread allocated each frame, in KiB. */
    static final String RENDER_THREAD_KB = "Render thread KiB allocated";

    /** How long a frame may take before it counts as a hitch, in milliseconds: a 60 Hz frame. */
    static final double HITCH_MS = 1000.0 / 60;

    /** How many of a hitch's slowest GPU stages are logged. */
    private static final int HITCH_STAGES = 3;

    /** The parts of a frame's timings that aren't GPU stages. */
    private static final Set<String> NOT_STAGES =
            Set.of(
                    FrameTimings.CPU,
                    FrameTimings.FRAME_INTERVAL,
                    FrameTimings.WAIT,
                    FrameTimings.BETWEEN_FRAMES,
                    FrameTimings.GPU_TOTAL);

    /** Bytes in a KiB. */
    private static final double BYTES_PER_KB = 1024;

    /** Counts what each thread allocates. */
    private static final com.sun.management.ThreadMXBean THREADS =
            (com.sun.management.ThreadMXBean)
                    java.lang.management.ManagementFactory.getThreadMXBean();

    /** Whether frames read back now are measured; timings read back late are dropped otherwise. */
    private final BooleanSupplier measuring;

    /** Every measured frame's timings, by part. */
    private final Map<String, List<Double>> samples = new LinkedHashMap<>();

    /** How many frames were measured. */
    private final AtomicInteger frames = new AtomicInteger();

    /** What the render thread had allocated by the last frame recorded, or -1 before the first. */
    private long lastAllocated = -1;

    /**
     * Make a recorder.
     *
     * @param measuring Whether the run is measuring, asked for each frame read back.
     */
    GateFrameRecorder(@NonNull BooleanSupplier measuring) {
        this.measuring = measuring;
    }

    /**
     * Keep one frame's timings. On the render thread, as they are read back.
     *
     * @param frame Each part's time, in milliseconds.
     */
    void record(@NonNull Map<String, Double> frame) {
        if (!measuring.getAsBoolean()) {
            return;
        }
        frames.incrementAndGet();
        frame.forEach((name, ms) -> samples.computeIfAbsent(name, n -> new ArrayList<>()).add(ms));
        // What the render thread allocated since the last frame, this bookkeeping included
        final long allocated = THREADS.getCurrentThreadAllocatedBytes();
        if (lastAllocated >= 0) {
            samples.computeIfAbsent(RENDER_THREAD_KB, n -> new ArrayList<>())
                    .add((allocated - lastAllocated) / BYTES_PER_KB);
        }
        lastAllocated = allocated;
        logHitch(frame);
    }

    /**
     * How many frames were measured.
     *
     * @return The count.
     */
    int frames() {
        return frames.get();
    }

    /**
     * Each part's timings over the measured frames: the GPU total, the CPU, then the rest in the
     * order they were first read back.
     *
     * @return The timings.
     */
    List<FrameTimings.Timing> timings() {
        final List<FrameTimings.Timing> timings = new ArrayList<>();
        final List<String> order = new ArrayList<>();
        order.add(FrameTimings.GPU_TOTAL);
        order.add(FrameTimings.CPU);
        samples.keySet().stream().filter(name -> !order.contains(name)).forEach(order::add);
        for (String name : order) {
            final List<Double> values = samples.get(name);
            if (values != null) {
                timings.add(
                        FrameTimings.summarize(
                                name, values.stream().mapToDouble(Double::doubleValue).toArray()));
            }
        }
        return timings;
    }

    /**
     * Log a measured frame that took longer than {@link #HITCH_MS}, from start to start or on the
     * CPU, with when it was read back, to the millisecond, for matching against a GC log or a
     * profile, and its slowest GPU stages. The timings are read back a frame or two late.
     *
     * @param frame The frame's timings, in milliseconds.
     */
    private static void logHitch(@NonNull Map<String, Double> frame) {
        final double interval = frame.getOrDefault(FrameTimings.FRAME_INTERVAL, 0.0);
        final double cpu = frame.getOrDefault(FrameTimings.CPU, 0.0);
        if (interval <= HITCH_MS && cpu <= HITCH_MS) {
            return;
        }
        final StringBuilder stages = new StringBuilder();
        frame.entrySet().stream()
                .filter(entry -> !NOT_STAGES.contains(entry.getKey()))
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(HITCH_STAGES)
                .forEach(
                        entry ->
                                stages.append(' ')
                                        .append(entry.getKey())
                                        .append(' ')
                                        .append(String.format("%.2f", entry.getValue())));
        log.info(
                "Gate benchmark hitch at {}: interval {} ms, CPU {} ms, wait {} ms, GPU {} ms;"
                        + " slowest:{}",
                LocalTime.now(),
                String.format("%.1f", interval),
                String.format("%.1f", cpu),
                String.format("%.1f", frame.getOrDefault(FrameTimings.WAIT, 0.0)),
                String.format("%.1f", frame.getOrDefault(FrameTimings.GPU_TOTAL, 0.0)),
                stages);
    }
}
