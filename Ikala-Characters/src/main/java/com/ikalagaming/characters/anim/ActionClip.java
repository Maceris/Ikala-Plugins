package com.ikalagaming.characters.anim;

import com.ikalagaming.characters.definition.AnimationSet;
import com.ikalagaming.characters.pose.Clip;

import lombok.Getter;
import lombok.NonNull;

import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * A clip as an action plays it: bound to the plan's skeleton, with the action's rate, speed, weight
 * and timed events. Immutable, so characters share them.
 *
 * @author Ches Burks
 */
public final class ActionClip {
    /** The most loops one step reports events for, so a long pause can't flood listeners. */
    private static final int MAX_LOOPS = 4;

    /** The clip. */
    @Getter private final Clip clip;

    /** How fast it plays, times its authored speed. */
    @Getter private final float rate;

    /** How fast it moves the character, in meters per second, or NaN if it has no speed. */
    @Getter private final float speed;

    /** How likely it is picked among the action's clips. */
    @Getter private final float weight;

    /** Event times in seconds of clip time, in order. */
    private final float[] eventTimes;

    /** The events, in the same order. */
    private final AnimationSet.Event[] events;

    /**
     * Bind a clip use.
     *
     * @param clip The clip, already with the use's looping.
     * @param use The use.
     */
    public ActionClip(@NonNull Clip clip, @NonNull AnimationSet.ClipUse use) {
        this.clip = clip;
        rate = use.rate();
        speed = use.speed() == null ? Float.NaN : use.speed();
        weight = use.weight();
        List<AnimationSet.Event> events =
                use.events().stream()
                        .sorted(Comparator.comparingDouble(AnimationSet.Event::at))
                        .toList();
        eventTimes = new float[events.size()];
        this.events = events.toArray(new AnimationSet.Event[0]);
        for (int i = 0; i < events.size(); ++i) {
            eventTimes[i] = events.get(i).at();
        }
    }

    /**
     * How long it lasts, in seconds of clip time.
     *
     * @return The duration.
     */
    public float duration() {
        return clip.getDuration();
    }

    /**
     * Its events.
     *
     * @return The events, in time order.
     */
    public List<AnimationSet.Event> events() {
        return List.of(events);
    }

    /**
     * Whether it has a speed to blend by.
     *
     * @return True if it has one.
     */
    public boolean hasSpeed() {
        return !Float.isNaN(speed);
    }

    /**
     * Report the events between two times, at or after the first and before the second, through
     * every loop between them. A clip that doesn't loop reports each event once.
     *
     * @param from The earlier time, in seconds of clip time since it started.
     * @param to The later time.
     * @param sink Gets each event, in order.
     */
    public void events(float from, float to, @NonNull Consumer<AnimationSet.Event> sink) {
        if (eventTimes.length == 0 || to <= from) {
            return;
        }
        float duration = clip.getDuration();
        if (!clip.isLooping()) {
            fire(from, to, 0, sink);
            return;
        }
        long first = (long) Math.floor(from / duration);
        long last = Math.min((long) Math.floor(to / duration), first + MAX_LOOPS);
        for (long loop = first; loop <= last; ++loop) {
            fire(from, to, loop * duration, sink);
        }
    }

    /**
     * Report the events of one pass through the clip that fall in a window.
     *
     * @param from The window's start, included.
     * @param to The window's end, left out.
     * @param start When this pass started.
     * @param sink Gets each event.
     */
    private void fire(float from, float to, float start, Consumer<AnimationSet.Event> sink) {
        for (int i = 0; i < eventTimes.length; ++i) {
            float at = start + eventTimes[i];
            if (at >= from && at < to) {
                sink.accept(events[i]);
            }
        }
    }
}
