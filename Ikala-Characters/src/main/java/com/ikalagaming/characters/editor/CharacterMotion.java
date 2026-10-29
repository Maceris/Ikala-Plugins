package com.ikalagaming.characters.editor;

import com.ikalagaming.characters.anim.ActionClip;
import com.ikalagaming.characters.anim.ActionLookup;
import com.ikalagaming.characters.anim.Animator;
import com.ikalagaming.characters.anim.ClipLibrary;
import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.definition.AnimationSet;
import com.ikalagaming.characters.definition.BodyPlan;
import com.ikalagaming.characters.definition.FxUse;
import com.ikalagaming.characters.registry.CharacterRegistry;
import com.ikalagaming.characters.view.CharacterFx;
import com.ikalagaming.characters.view.ShownCharacter;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.data.DrawList;
import com.ikalagaming.graphics.gui.enums.ColorType;
import com.ikalagaming.graphics.gui.util.Color;

import lombok.NonNull;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * The character editor's animation: an {@link Animator} for the shown character, built from its
 * plan, variant and style, and the Motion tab that drives it, choosing the movement and its speed,
 * playing actions over it, and showing each layer's clip with its events. Effects' states, like
 * combat and toggles, are set from here too. Only the render thread uses it, except building, which
 * reads clip files on another thread.
 *
 * @author Ches Burks
 */
final class CharacterMotion {
    /** The fastest speed the slider offers, in meters per second. */
    static final float MAX_SPEED = 8;

    /** The idle played while ready to fight. */
    static final String COMBAT_IDLE = ActionLookup.IDLE + "." + FxUse.COMBAT;

    /** The speed movement starts at, a walk. */
    static final float START_SPEED = 1.4f;

    /** How long to wait before repeating an action that ended, in seconds. */
    static final float REPEAT_PAUSE = 0.25f;

    /** The height of a clip's timeline. */
    static final float TIMELINE_HEIGHT = 18;

    /** How far an event's notches reach into a timeline from its top and bottom. */
    static final float EVENT_NOTCH = 5;

    /** The color of an event's tick on a timeline. */
    static final int EVENT_COLOR = Color.rgba(1.0f, 0.75f, 0.2f, 0.95f);

    /** Readers of the plans' clips, by plan, for the registry they were made for. */
    private final Map<Id, ClipLibrary> libraries = new HashMap<>();

    /** The registry the libraries read for. */
    private CharacterRegistry librariesFor;

    /** Counts builds, so a slow one doesn't replace a newer one. */
    private int builds;

    /** The animator, or null if the plan has no actions or it is still being built. */
    private Animator animator;

    /** Whether the character's plan has actions, so an animator is coming. */
    private boolean hasActions;

    /** The character it poses, or null. */
    private ShownCharacter shown;

    /** Whether the character stands at rest, as on the Body tab. */
    private boolean standing;

    /** The movement action. */
    private String movement = ActionLookup.IDLE;

    /** How fast the character moves, in meters per second. */
    private final float[] speed = {START_SPEED};

    /** The action chosen last, which the clip bar steps from. */
    private String cursor = ActionLookup.IDLE;

    /** The action playing over the movement, or null. */
    private String action;

    /** Whether the action plays again after it ends. */
    private boolean repeat = true;

    /** Seconds since the action ended, for repeating it. */
    private float sinceEnded;

    /** When the last frame was drawn, in nanoseconds, or 0. */
    private long lastFrame;

    /** Whether a weapon is drawn, so the character idles ready to fight. */
    private boolean combat;

    /** States effects are shown in that are on, like combat. */
    private final Set<String> states = new TreeSet<>();

    /**
     * Build an animator for the shown character, reading its actions' clips on another thread first
     * so the character doesn't stall. Until it is ready, the character plays its file's clip.
     *
     * @param registry The loaded definitions.
     * @param plan The character's plan.
     * @param variant Its variant.
     * @param styles Its styles, the first winning, like its items' and then its design's.
     * @param reference The rig's reference file.
     * @param character The shown character.
     * @param post Runs a task on the render thread.
     */
    void build(
            @NonNull CharacterRegistry registry,
            @NonNull BodyPlan plan,
            @NonNull String variant,
            @NonNull List<Id> styles,
            @NonNull Path reference,
            @NonNull ShownCharacter character,
            @NonNull Consumer<Runnable> post) {
        detach();
        shown = character;
        if (librariesFor != registry) {
            libraries.clear();
            librariesFor = registry;
        }
        ActionLookup lookup = new ActionLookup(registry, plan.id(), variant, styles);
        hasActions = !lookup.isEmpty();
        if (!hasActions) {
            return;
        }
        ClipLibrary library =
                libraries.computeIfAbsent(
                        plan.id(),
                        id -> new ClipLibrary(registry, reference, character.getSkeleton()));
        final int build = ++builds;
        CompletableFuture.runAsync(
                        () -> lookup.actions().forEach(a -> library.clips(lookup.resolve(a))))
                .whenComplete(
                        (done, failure) ->
                                post.accept(
                                        () -> {
                                            if (build != builds || shown != character) {
                                                return;
                                            }
                                            animator =
                                                    new Animator(
                                                            lookup,
                                                            library::clips,
                                                            character.getSkeleton(),
                                                            plan,
                                                            new SplittableRandom());
                                            animator.setMovement(movementAction(), speed[0]);
                                            if (action != null) {
                                                animator.play(action, false);
                                            }
                                            attach();
                                        }));
    }

    /**
     * Whether actions drive the character, rather than its file's clips.
     *
     * @return True if an animator is ready.
     */
    boolean active() {
        return animator != null;
    }

    /**
     * Stand the character at rest, or let it move again.
     *
     * @param still Whether it stands at rest.
     */
    void setStanding(boolean still) {
        standing = still;
        attach();
    }

    /** Give the shown character the animator, unless it stands at rest. */
    private void attach() {
        if (shown == null || animator == null) {
            return;
        }
        shown.play(null);
        shown.setAnimator(standing ? null : animator);
    }

    /** Forget the character and animator, stopping any build on the way. */
    void detach() {
        ++builds;
        if (shown != null) {
            shown.setAnimator(null);
        }
        shown = null;
        animator = null;
    }

    /**
     * Turn effects' states on the character's effects: combat and the toggles that are on.
     *
     * @param fx The effects, or null.
     * @param uses The effects' uses, whose toggles are offered.
     */
    void applyStates(CharacterFx fx, @NonNull List<FxUse> uses) {
        if (fx == null) {
            return;
        }
        for (String state : toggles(uses)) {
            fx.setState(state, states.contains(state));
        }
    }

    /**
     * The states effects can be toggled in: combat, and any others they name.
     *
     * @param uses The effects.
     * @return The states, sorted.
     */
    static List<String> toggles(@NonNull List<FxUse> uses) {
        Set<String> found = new TreeSet<>();
        found.add(FxUse.COMBAT);
        for (FxUse use : uses) {
            if (!FxUse.ALWAYS.equals(use.when()) && !FxUse.MOVING.equals(use.when())) {
                found.add(use.when());
            }
        }
        return List.copyOf(found);
    }

    /**
     * Ready the character to fight, as when a weapon is drawn, or stand it down: it idles in its
     * combat idle, and effects shown in combat show.
     *
     * @param on Whether it is ready to fight.
     */
    void setCombat(boolean on) {
        if (on == combat) {
            return;
        }
        combat = on;
        if (on) {
            states.add(FxUse.COMBAT);
        } else {
            states.remove(FxUse.COMBAT);
        }
        if (animator != null) {
            animator.setMovement(movementAction(), speed[0]);
        }
    }

    /**
     * The movement to play: the one chosen, or the combat idle in place of the idle while ready to
     * fight.
     *
     * @return The action.
     */
    private String movementAction() {
        return combat && ActionLookup.IDLE.equals(movement) ? COMBAT_IDLE : movement;
    }

    /**
     * Whether an action is a movement, played on the whole body under other actions.
     *
     * @param name The action.
     * @return True for idles and moves.
     */
    static boolean isMovement(@NonNull String name) {
        return ActionLookup.IDLE.equals(name)
                || name.startsWith(ActionLookup.IDLE + ".")
                || "move".equals(name)
                || name.startsWith("move.");
    }

    /**
     * Choose an action: a movement replaces the movement, anything else plays over it.
     *
     * @param name The action.
     */
    void choose(@NonNull String name) {
        cursor = name;
        if (isMovement(name)) {
            movement = name;
            if (animator != null) {
                animator.setMovement(movementAction(), speed[0]);
            }
            return;
        }
        action = name;
        sinceEnded = 0;
        if (animator != null) {
            animator.play(name, false);
        }
    }

    /** Stop the action playing over the movement. */
    void stopAction() {
        action = null;
        if (animator != null) {
            animator.stop();
        }
    }

    /**
     * Step through the character's actions from the one chosen last.
     *
     * @param step How far, like -1 for the one before.
     */
    void cycle(int step) {
        if (animator == null) {
            return;
        }
        List<String> all = animator.lookup().actions();
        if (all.isEmpty()) {
            return;
        }
        int at = all.indexOf(cursor);
        choose(all.get(Math.floorMod(at + step, all.size())));
    }

    /**
     * The clip bar's text: the action and movement and what they play.
     *
     * @return The text.
     */
    String barText() {
        if (animator == null) {
            return "";
        }
        if (standing) {
            return "Actions play on the Costume tab";
        }
        String text = describe(animator.movement(), false);
        Animator.Playing over = animator.action();
        return over == null ? text : describe(over, false) + " over " + text;
    }

    /**
     * The clip bar's position text: which action of how many was chosen.
     *
     * @return The text.
     */
    String positionText() {
        if (animator == null) {
            return "";
        }
        List<String> all = animator.lookup().actions();
        return (all.indexOf(cursor) + 1) + " / " + all.size();
    }

    /** Repeat an action that ended, once a frame on the render thread. */
    void tick() {
        long now = System.nanoTime();
        float seconds = lastFrame == 0 ? 0 : (now - lastFrame) / 1e9f;
        lastFrame = now;
        if (animator == null || action == null || standing) {
            return;
        }
        if (animator.action() != null) {
            sinceEnded = 0;
            return;
        }
        sinceEnded += seconds;
        if (repeat && sinceEnded >= REPEAT_PAUSE) {
            sinceEnded = 0;
            animator.play(action, false);
        } else if (!repeat) {
            action = null;
        }
    }

    /**
     * Draw the Motion tab, after the style combo the editor draws itself.
     *
     * @param fx The character's effects, for their states, or null.
     * @param uses The effects' uses.
     */
    void draw(CharacterFx fx, @NonNull List<FxUse> uses) {
        if (animator == null) {
            IkGui.textDisabled(hasActions ? "Reading clips..." : "No animation set for this plan");
            return;
        }
        List<String> all = animator.lookup().actions();
        IkGui.separatorText("Movement");
        if (IkGui.beginCombo("Movement##movement", movement)) {
            for (String name : all) {
                if (isMovement(name) && IkGui.selectable(name, name.equals(movement))) {
                    choose(name);
                }
            }
            IkGui.endCombo();
        }
        if (IkGui.sliderFloat("Speed (m/s)##speed", speed, 0, MAX_SPEED)) {
            animator.setMovement(movementAction(), speed[0]);
        }
        timeline(animator.movement());

        IkGui.separatorText("Actions");
        for (String name : all) {
            if (!isMovement(name) && IkGui.selectable(name + "##action", name.equals(action))) {
                choose(name);
            }
        }
        if (IkGui.checkbox("Repeat##repeat", repeat)) {
            repeat = !repeat;
        }
        IkGui.sameLine();
        if (IkGui.button("Stop##stop")) {
            stopAction();
        }
        timeline(animator.action());

        IkGui.separatorText("Effects shown in");
        for (String state : toggles(uses)) {
            if (IkGui.checkbox(state + "##state", states.contains(state))) {
                if (!states.remove(state)) {
                    states.add(state);
                }
                applyStates(fx, uses);
            }
        }
        IkGui.textDisabled("Moving effects show whenever the speed is above 0.");
    }

    /**
     * A layer's action, where it came from, and its clip.
     *
     * @param playing What the layer plays, or null.
     * @param withSource Whether to say which set or style it came from.
     * @return The text.
     */
    private static String describe(Animator.Playing playing, boolean withSource) {
        if (playing == null) {
            return "nothing";
        }
        ActionLookup.Resolved resolved = playing.action();
        String clip = playing.clip() == null ? "no clip" : playing.clip().getClip().getName();
        String found = resolved.exact() ? "" : " (as " + resolved.found() + ")";
        String source = withSource ? " from " + resolved.source() : "";
        return resolved.requested() + found + ": " + clip + source;
    }

    /**
     * Draw a layer's clip as a bar filling as it plays, with a tick at each event.
     *
     * @param playing What the layer plays, or null.
     */
    private static void timeline(Animator.Playing playing) {
        if (playing == null || playing.clip() == null) {
            IkGui.textDisabled("Nothing playing");
            return;
        }
        IkGui.textWrapped(describe(playing, true));
        ActionClip clip = playing.clip();
        float duration = clip.duration();
        float time = clip.getClip().localTime(playing.time());
        float width = IkGui.getContentRegionAvailableX();
        float x = IkGui.getCursorScreenPosX();
        float y = IkGui.getCursorScreenPosY();
        // The time is drawn centered rather than as the bar's overlay, which follows the fill
        IkGui.progressBar(time / duration, width, TIMELINE_HEIGHT, "");
        DrawList draw = IkGui.getWindowDrawList();
        String label = String.format(Locale.ROOT, "%.2f / %.2f s", time, duration);
        int fontSize = IkGui.getFontSize();
        draw.addText(
                fontSize,
                x + (width - IkGui.calcTextSize(label).x) / 2,
                y + (TIMELINE_HEIGHT - fontSize) / 2,
                Color.rgba(IkGui.getStyleColorVec4(ColorType.TEXT)),
                label,
                0);
        List<String> names = new ArrayList<>();
        for (AnimationSet.Event event : clip.events()) {
            float at = x + width * Math.clamp(event.at() / duration, 0, 1);
            // Notches at the edges, clear of the time in the middle
            draw.addLine(at, y, at, y + EVENT_NOTCH, EVENT_COLOR, 2);
            draw.addLine(
                    at, y + TIMELINE_HEIGHT - EVENT_NOTCH, at, y + TIMELINE_HEIGHT, EVENT_COLOR, 2);
            names.add(String.format(Locale.ROOT, "%s at %.2f", event.event(), event.at()));
        }
        if (!names.isEmpty()) {
            IkGui.textDisabled(String.join(", ", names));
        }
    }
}
