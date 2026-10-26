package com.ikalagaming.converter.gui.window;

import com.ikalagaming.converter.ConverterPlugin;
import com.ikalagaming.graphics.UI;
import com.ikalagaming.graphics.benchmark.GateBenchmark;
import com.ikalagaming.graphics.ui.spec.Observable;
import com.ikalagaming.graphics.ui.spec.ObservableList;
import com.ikalagaming.graphics.ui.spec.SpecBindings;
import com.ikalagaming.graphics.ui.spec.SpecEvent;
import com.ikalagaming.graphics.ui.spec.SpecInstance;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * The benchmark screen the main menu opens: the gate benchmark's ready-made runs to pick from, each
 * opened with its world kept to look around in. While one runs, a small bar offers the way back to
 * the menu, which removes the world. Its layouts live in the specs {@code ui/benchmarks.yml} and
 * {@code ui/benchmark-running.yml} in the plugin's data folder.
 */
@Slf4j
public class BenchmarkMenu {
    /** Where the list's spec is, within the plugin's data folder. */
    public static final String SPEC = "ui/benchmarks.yml";

    /** Where the running bar's spec is. */
    public static final String RUNNING_SPEC = "ui/benchmark-running.yml";

    /** The list the screen shows, by the name its spec binds. */
    public static final String PRESETS = "presets";

    /** The name of the shown run, by the name the running bar binds. */
    public static final String RUNNING_NAME = "running.name";

    /** The UI the screens are shown through. */
    private final UI ui;

    /** Brings the main menu back. */
    private final Runnable back;

    /** The open list, or null. */
    private SpecInstance list;

    /** The open running bar, or null. */
    private SpecInstance running;

    /** What the running bar names. */
    private final Observable<String> runningName = new Observable<>("");

    /**
     * Create the screen.
     *
     * @param ui The converter plugin's UI.
     * @param back Brings the main menu back.
     */
    public BenchmarkMenu(@NonNull UI ui, @NonNull Runnable back) {
        this.ui = ui;
        this.back = back;
    }

    /** Show the list of runs. Render thread only. */
    public void open() {
        close();
        list =
                ui.open(
                        ui.loadSpec(SPEC),
                        new SpecBindings()
                                .bundle(ConverterPlugin.getResourceBundle())
                                .list(PRESETS, new ObservableList<>(GateBenchmark.PRESETS))
                                .handler("run-benchmark", this::run)
                                .handler("back", this::backToMenu));
    }

    /**
     * Start the picked run, and show the bar that comes back from it.
     *
     * @param event The click, its item the run.
     */
    private void run(@NonNull SpecEvent event) {
        final GateBenchmark.Preset preset = (GateBenchmark.Preset) event.item();
        close();
        log.info("Opening the {} benchmark", preset.name());
        GateBenchmark.start(preset);
        runningName.set(preset.name());
        running =
                ui.open(
                        ui.loadSpec(RUNNING_SPEC),
                        new SpecBindings()
                                .bundle(ConverterPlugin.getResourceBundle())
                                .value(RUNNING_NAME, runningName)
                                .handler("leave-benchmark", this::leave));
    }

    /** Stop the run, remove its world, and go back to the list. */
    private void leave() {
        GateBenchmark.stop();
        GateBenchmark.removeWorld();
        open();
    }

    /** Close the list and bring the main menu back. */
    private void backToMenu() {
        close();
        back.run();
    }

    /** Close whatever of the screen is open. */
    private void close() {
        if (list != null) {
            list.close();
            list = null;
        }
        if (running != null) {
            running.close();
            running = null;
        }
    }
}
