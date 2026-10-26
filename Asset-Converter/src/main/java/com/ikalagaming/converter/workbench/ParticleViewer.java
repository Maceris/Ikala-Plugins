package com.ikalagaming.converter.workbench;

import com.ikalagaming.graphics.EffectHandle;
import com.ikalagaming.graphics.EmitterHandle;
import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.flags.TableFlags;
import com.ikalagaming.graphics.scene.Fog;
import com.ikalagaming.graphics.scene.ParticleClock;
import com.ikalagaming.graphics.scene.ParticleEffectException;
import com.ikalagaming.graphics.scene.ParticleEffectLoader;
import com.ikalagaming.graphics.scene.ParticleSamples;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.vulkan.FrameTimings;
import com.ikalagaming.graphics.vulkan.VulkanInstance;

import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector3d;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * Watches one particle effect on the workbench's particle stage, see {@link
 * com.ikalagaming.converter.scene.PreviewShapes#particleStage()}: a sample effect, or an effect
 * file opened in the workbench, which reloads as it is saved, so an effect is written in a text
 * editor while watching it. Plays, pauses, steps and restarts particle time, slows it, turns fog
 * on, and shows how many particles are alive and what simulating them costs on the GPU. A sample
 * can be saved as a file to start a new effect from. Particle time and hot reload are the scene's,
 * so the viewer puts them back as they were when the stage closes.
 */
@Slf4j
public class ParticleViewer {

    /** Where the emitter stands, just above the floor. */
    private static final Vector3d EMITTER = new Vector3d(0, 0.1, 0);

    /** How dense the fog is when turned on, per meter: thick, to see particles in it. */
    private static final float FOG_DENSITY = 0.04f;

    /** The fastest particle time goes on the slider, against real time. */
    private static final float MAX_TIME_SCALE = 2;

    /** The stage name the frame timings give the particle simulation. */
    private static final String SIMULATION_STAGE = "ParticleUpdate";

    /** Which entry in the effect list is the open file rather than a sample. */
    private static final int FILE_ENTRY = -1;

    /** The workbench's graphics context, which owns the emitter and the loaded file. */
    private final GraphicsContext graphics;

    /** The emitter, or null while the viewer isn't running. */
    private EmitterHandle emitter;

    /** Which sample is shown, an index into {@link ParticleSamples#ALL}, or {@link #FILE_ENTRY}. */
    private int shown;

    /** The open effect file and its loaded effect, or null for none. */
    private @Nullable Path file;

    private @Nullable EffectHandle fileEffect;

    /** Why the file couldn't be opened, or empty. */
    private String fileError = "";

    /** Whether the stage is shown. */
    private boolean active;

    /** Whether fog was on before the viewer turned it on, and how dense it was. */
    private boolean previousFog;

    private float previousFogDensity;

    /** Whether the viewer turned fog on. */
    private boolean fog;

    /** The folder a sample is saved into, the workbench's file list's. */
    @Setter private Supplier<Path> folder = () -> Path.of("");

    /** Told of a file the viewer saved, so the file list shows it. */
    @Setter private Consumer<Path> saved = file -> {};

    /** A slider's value. */
    private final float[] value = new float[1];

    /**
     * Make a viewer.
     *
     * @param graphics The workbench's graphics context.
     */
    public ParticleViewer(@NonNull GraphicsContext graphics) {
        this.graphics = graphics;
    }

    /**
     * Whether the particle stage is shown.
     *
     * @return True if the viewer is running.
     */
    public boolean isActive() {
        return active;
    }

    /** Start watching: place the emitter and start particle time afresh. Render thread. */
    public void start() {
        if (active) {
            return;
        }
        active = true;
        final ParticleClock clock = graphics.particles().clock();
        clock.setPaused(false);
        clock.setTimeScale(1);
        graphics.particles().setHotReload(true);
        if (shown == FILE_ENTRY && fileEffect == null) {
            shown = 0;
        }
        restart();
    }

    /** Stop watching: remove the emitter, the file and every particle, and put things back. */
    public void stop() {
        if (!active) {
            return;
        }
        active = false;
        graphics.particles().stop(emitter);
        emitter = null;
        closeFile();
        final ParticleClock clock = graphics.particles().clock();
        clock.setPaused(false);
        clock.setTimeScale(1);
        clock.clear();
        graphics.particles().setHotReload(false);
        setFog(false);
    }

    /**
     * Show an effect file, reloading it as it is saved. Render thread, with the stage shown.
     *
     * @param effectFile The file.
     */
    public void openFile(@NonNull Path effectFile) {
        closeFile();
        file = effectFile;
        try {
            fileEffect = graphics.particles().loadEffect(effectFile);
            fileError = "";
            shown = FILE_ENTRY;
        } catch (ParticleEffectException | UncheckedIOException e) {
            fileError = e.getMessage();
            log.warn("Could not open the particle effect {}: {}", effectFile, fileError);
        }
        if (active) {
            restart();
        }
    }

    /** Forget the open file, if any. */
    private void closeFile() {
        graphics.particles().unloadEffect(fileEffect);
        fileEffect = null;
        file = null;
        fileError = "";
        if (shown == FILE_ENTRY) {
            shown = 0;
        }
    }

    /** Draw the viewer's controls, in the scene view's properties. */
    public void draw() {
        final ParticleClock clock = graphics.particles().clock();
        drawEffectList();
        if (IkGui.button(clock.isPaused() ? "Play" : "Pause")) {
            clock.setPaused(!clock.isPaused());
        }
        IkGui.sameLine();
        IkGui.beginDisabled(!clock.isPaused());
        if (IkGui.button("Step")) {
            clock.step();
        }
        IkGui.endDisabled();
        IkGui.setItemTooltip("Moves particles one 60th of a second while paused");
        IkGui.sameLine();
        if (IkGui.button("Restart")) {
            restart();
        }
        value[0] = (float) clock.getTimeScale();
        if (IkGui.sliderFloat("Speed", value, 0, MAX_TIME_SCALE, "%.2fx")) {
            clock.setTimeScale(value[0]);
        }
        if (IkGui.checkbox("Fog", fog)) {
            setFog(!fog);
        }
        IkGui.setItemTooltip("Thick height fog, to see the particles in it");
        if (IkGui.beginTable("particles", 2, TableFlags.ROW_BACKGROUND)) {
            row("Alive", String.format("%,d", clock.getLiveParticles()));
            row("Dropped", String.format("%,d", clock.getDroppedSpawns()));
            row("Simulation", simulationTime());
            IkGui.endTable();
        }
        drawFile();
        IkGui.textDisabled("Glass stands in front of the emitter's left and behind its right");
    }

    /** Draw the list of effects: the samples, and the open file. */
    private void drawEffectList() {
        final String current =
                shown == FILE_ENTRY && file != null
                        ? fileLabel(file)
                        : ParticleSamples.ALL.get(shown).name();
        if (IkGui.beginCombo("Effect", current)) {
            for (int i = 0; i < ParticleSamples.ALL.size(); ++i) {
                if (IkGui.selectable(ParticleSamples.ALL.get(i).name(), i == shown)) {
                    shown = i;
                    restart();
                }
            }
            if (fileEffect != null && IkGui.selectable(fileLabel(file), shown == FILE_ENTRY)) {
                shown = FILE_ENTRY;
                restart();
            }
            IkGui.endCombo();
        }
    }

    /** Draw the open file's state, or the button that saves a sample as a file. */
    private void drawFile() {
        if (!fileError.isEmpty()) {
            IkGui.textWrapped("Can't open " + fileLabel(file) + ": " + fileError);
        }
        if (shown == FILE_ENTRY) {
            final String error = graphics.particles().effectError(fileEffect);
            if (error != null) {
                IkGui.textWrapped("The last save didn't load, so the one before shows: " + error);
            } else {
                IkGui.textWrapped("Showing " + file + "; saving it shows the change at once");
            }
            return;
        }
        if (IkGui.button("Save as file")) {
            saveSample();
        }
        IkGui.setItemTooltip(
                "Writes this sample into the file list's folder as an effect file and opens it,"
                        + " to edit in a text editor while watching it");
    }

    /** Write the shown sample into the file list's folder and open it. */
    private void saveSample() {
        final ParticleSamples.Sample sample = ParticleSamples.ALL.get(shown);
        final Path target = freeName(folder.get(), sample.name().toLowerCase(Locale.ROOT));
        try {
            Files.writeString(target, ParticleEffectLoader.write(sample.effect()));
        } catch (IOException e) {
            fileError = "can't write " + target.getFileName() + ": " + e.getMessage();
            return;
        }
        saved.accept(target);
        openFile(target);
    }

    /**
     * A file name in a folder that isn't taken yet.
     *
     * @param in The folder.
     * @param base The name before the effect suffix.
     * @return The file, numbered if the plain name is taken.
     */
    private static Path freeName(Path in, String base) {
        Path candidate = in.resolve(base + AssetKind.PARTICLE_EFFECT_SUFFIX);
        for (int number = 2; Files.exists(candidate); ++number) {
            candidate = in.resolve(base + "-" + number + AssetKind.PARTICLE_EFFECT_SUFFIX);
        }
        return candidate.toAbsolutePath().normalize();
    }

    /**
     * How the effect list names a file.
     *
     * @param effectFile The file.
     * @return Its name.
     */
    private static String fileLabel(@Nullable Path effectFile) {
        return effectFile == null ? "" : "File: " + effectFile.getFileName();
    }

    /** Remove every particle and place the emitter again, so its burst shows from the start. */
    private void restart() {
        graphics.particles().stop(emitter);
        graphics.particles().clock().clear();
        emitter =
                shown == FILE_ENTRY
                        ? graphics.particles().spawn(fileEffect, EMITTER)
                        : graphics.particles()
                                .spawn(ParticleSamples.ALL.get(shown).effect(), EMITTER);
    }

    /**
     * Turn the viewer's fog on or off, keeping the scene's own fog to put back.
     *
     * @param on True to turn it on.
     */
    private void setFog(boolean on) {
        final Scene scene = GraphicsManager.getScene();
        if (scene == null || on == fog) {
            return;
        }
        final Fog sceneFog = scene.getFog();
        if (on) {
            previousFog = sceneFog.isActive();
            previousFogDensity = sceneFog.getDensity();
            sceneFog.setDensity(FOG_DENSITY);
            sceneFog.setActive(true);
        } else {
            sceneFog.setDensity(previousFogDensity);
            sceneFog.setActive(previousFog);
        }
        fog = on;
    }

    /**
     * What the particle simulation costs on the GPU, from the frame timings.
     *
     * @return Its average and 95th percentile, or a dash if the GPU can't time it.
     */
    private static String simulationTime() {
        final VulkanInstance renderer = GraphicsManager.getRenderInstance();
        if (renderer == null || renderer.getState().frameTimings == null) {
            return "-";
        }
        for (FrameTimings.Timing timing : renderer.getState().frameTimings.snapshot()) {
            if (SIMULATION_STAGE.equals(timing.name())) {
                return String.format("%.3f ms, p95 %.3f ms", timing.average(), timing.p95());
            }
        }
        return "-";
    }

    /**
     * A table row.
     *
     * @param name What it shows.
     * @param text Its value.
     */
    private static void row(String name, String text) {
        IkGui.tableNextRow();
        IkGui.tableNextColumn();
        IkGui.text(name);
        IkGui.tableNextColumn();
        IkGui.text(text);
    }
}
