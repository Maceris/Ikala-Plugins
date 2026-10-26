package com.ikalagaming.converter.workbench;

import com.ikalagaming.graphics.EmitterHandle;
import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.flags.TableFlags;
import com.ikalagaming.graphics.scene.Fog;
import com.ikalagaming.graphics.scene.ParticleClock;
import com.ikalagaming.graphics.scene.ParticleSamples;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.vulkan.FrameTimings;
import com.ikalagaming.graphics.vulkan.VulkanInstance;

import lombok.NonNull;
import org.joml.Vector3d;

/**
 * Watches one particle effect on the workbench's particle stage, see {@link
 * com.ikalagaming.converter.scene.PreviewShapes#particleStage()}: picks a sample effect, plays,
 * pauses, steps and restarts particle time, slows it, turns fog on, and shows how many particles
 * are alive and what simulating them costs on the GPU. Particle time is the scene's, so the viewer
 * puts it back as it was when the stage closes.
 */
public class ParticleViewer {

    /** Where the emitter stands, just above the floor. */
    private static final Vector3d EMITTER = new Vector3d(0, 0.1, 0);

    /** How dense the fog is when turned on, per meter: thick, to see particles in it. */
    private static final float FOG_DENSITY = 0.04f;

    /** The fastest particle time goes on the slider, against real time. */
    private static final float MAX_TIME_SCALE = 2;

    /** The stage name the frame timings give the particle simulation. */
    private static final String SIMULATION_STAGE = "ParticleUpdate";

    /** The workbench's graphics context, which owns the emitter. */
    private final GraphicsContext graphics;

    /** The emitter, or null while the viewer isn't running. */
    private EmitterHandle emitter;

    /** Which sample is shown, an index into {@link ParticleSamples#ALL}. */
    private int sample;

    /** Whether the stage is shown. */
    private boolean active;

    /** Whether fog was on before the viewer turned it on, and how dense it was. */
    private boolean previousFog;

    private float previousFogDensity;

    /** Whether the viewer turned fog on. */
    private boolean fog;

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
        clock.clear();
        emitter = graphics.particles().spawn(ParticleSamples.ALL.get(sample).effect(), EMITTER);
    }

    /** Stop watching: remove the emitter and every particle, and put time and fog back. */
    public void stop() {
        if (!active) {
            return;
        }
        active = false;
        graphics.particles().stop(emitter);
        emitter = null;
        final ParticleClock clock = graphics.particles().clock();
        clock.setPaused(false);
        clock.setTimeScale(1);
        clock.clear();
        setFog(false);
    }

    /** Draw the viewer's controls, in the scene view's properties. */
    public void draw() {
        final ParticleClock clock = graphics.particles().clock();
        if (IkGui.beginCombo("Effect", ParticleSamples.ALL.get(sample).name())) {
            for (int i = 0; i < ParticleSamples.ALL.size(); ++i) {
                if (IkGui.selectable(ParticleSamples.ALL.get(i).name(), i == sample)) {
                    sample = i;
                    restart();
                }
            }
            IkGui.endCombo();
        }
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
        IkGui.textDisabled("Glass stands in front of the emitter's left and behind its right");
    }

    /** Remove every particle and place the emitter again, so its burst shows from the start. */
    private void restart() {
        graphics.particles().stop(emitter);
        graphics.particles().clock().clear();
        emitter = graphics.particles().spawn(ParticleSamples.ALL.get(sample).effect(), EMITTER);
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
