package com.ikalagaming.characters.editor;

import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.data.Range;
import com.ikalagaming.characters.definition.Slider;
import com.ikalagaming.characters.view.PartSlots;
import com.ikalagaming.random.SeededRandom;

import lombok.NonNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a character in the editor looks like: the part in each slot and each slider's value, with a
 * history to undo changes. Not thread safe; the editor uses it on the render thread.
 *
 * @author Ches Burks
 */
final class CharacterLook {
    /** A slot's choice meaning it holds nothing. */
    static final int EMPTY = -1;

    /** A preview slot meaning nothing is previewed. */
    static final int NO_PREVIEW = -1;

    /** The most changes kept to undo. */
    static final int MAX_UNDO = 100;

    /** How likely randomizing leaves a slot that can be empty empty. */
    static final double EMPTY_CHANCE = 0.35;

    /** How much of each slider's range randomizing uses, so random bodies aren't all extremes. */
    static final double RANDOM_SPREAD = 0.6;

    /**
     * The look at one time, to go back to.
     *
     * @param choices Each slot's choice.
     * @param values Each slider's values, by ID.
     */
    record Snapshot(int[] choices, Map<Id, float[]> values) {}

    /** The slots. */
    private final PartSlots slots;

    /** The sliders, in the order they are shown. */
    private final List<Slider> sliders;

    /** The position in each slot's options of what fills it, or {@link #EMPTY}. */
    private final int[] choices;

    /** Each slider's values, by ID. */
    private final Map<Id, float[]> values = new LinkedHashMap<>();

    /** Earlier looks, the latest first. */
    private final Deque<Snapshot> history = new ArrayDeque<>();

    /** Counts changes, so callers can tell when the look changed. */
    private int version;

    /**
     * Start a look with the slots' initial parts and every slider at 0.
     *
     * @param slots The slots.
     * @param sliders The sliders.
     */
    CharacterLook(@NonNull PartSlots slots, @NonNull List<Slider> sliders) {
        this.slots = slots;
        this.sliders = List.copyOf(sliders);
        choices = new int[slots.slots().size()];
        for (int i = 0; i < choices.length; ++i) {
            choices[i] = slots.slots().get(i).initial();
        }
        for (Slider slider : sliders) {
            values.put(slider.id(), new float[slider.vector() ? 3 : 1]);
        }
    }

    /**
     * The slots.
     *
     * @return The slots.
     */
    PartSlots slots() {
        return slots;
    }

    /**
     * The sliders, in the order they are shown.
     *
     * @return The sliders.
     */
    List<Slider> sliders() {
        return sliders;
    }

    /**
     * What fills a slot.
     *
     * @param slot The slot's position.
     * @return The position in its options, or {@link #EMPTY}.
     */
    int choice(int slot) {
        return choices[slot];
    }

    /**
     * Whether a slot only ever holds the one part.
     *
     * @param slot The slot's position.
     * @return True if there is nothing to choose.
     */
    boolean isFixed(int slot) {
        PartSlots.Slot info = slots.slots().get(slot);
        return !info.optional() && info.options().size() == 1;
    }

    /**
     * Fill a slot, remembering the look before to undo.
     *
     * @param slot The slot's position.
     * @param choice The position in its options, or {@link #EMPTY} if it can be empty.
     */
    void choose(int slot, int choice) {
        if (choices[slot] == choice) {
            return;
        }
        remember();
        choices[slot] = choice;
        ++version;
    }

    /**
     * Fill a slot with the next or previous option, going through empty for slots that can be.
     *
     * @param slot The slot's position.
     * @param step 1 for the next, -1 for the one before.
     */
    void step(int slot, int step) {
        PartSlots.Slot info = slots.slots().get(slot);
        int first = info.optional() ? EMPTY : 0;
        int count = info.options().size() - first;
        choose(slot, Math.floorMod(choices[slot] - first + step, count) + first);
    }

    /**
     * The parts worn, with one slot showing another choice for a preview.
     *
     * @param previewSlot The slot to preview, or {@link #NO_PREVIEW}.
     * @param previewChoice What it would hold.
     * @return The indices of the parts.
     */
    List<Integer> worn(int previewSlot, int previewChoice) {
        List<Integer> worn = new ArrayList<>();
        for (int i = 0; i < choices.length; ++i) {
            int choice = i == previewSlot ? previewChoice : choices[i];
            if (choice != EMPTY) {
                worn.addAll(slots.slots().get(i).options().get(choice));
            }
        }
        return worn;
    }

    /**
     * Each slider's values, by ID, which slider widgets change in place; call {@link #touch} after.
     *
     * @return The values.
     */
    Map<Id, float[]> values() {
        return values;
    }

    /** Note that slider values were changed in place. */
    void touch() {
        ++version;
    }

    /**
     * Counts changes, so callers can tell when the look changed.
     *
     * @return The count.
     */
    int version() {
        return version;
    }

    /**
     * The look now.
     *
     * @return A copy.
     */
    Snapshot snapshot() {
        Map<Id, float[]> copied = new LinkedHashMap<>();
        values.forEach((id, value) -> copied.put(id, value.clone()));
        return new Snapshot(choices.clone(), copied);
    }

    /** Remember the look now, to undo the next change. */
    void remember() {
        push(snapshot());
    }

    /**
     * Remember an earlier look, to undo back to it, like the one before a slider drag.
     *
     * @param snapshot The look.
     */
    void push(@NonNull Snapshot snapshot) {
        history.addFirst(snapshot);
        while (history.size() > MAX_UNDO) {
            history.removeLast();
        }
    }

    /**
     * Whether there is a change to undo.
     *
     * @return True if there is.
     */
    boolean canUndo() {
        return !history.isEmpty();
    }

    /**
     * Go back to the look before the last change.
     *
     * @return False if there was nothing to undo.
     */
    boolean undo() {
        Snapshot last = history.pollFirst();
        if (last == null) {
            return false;
        }
        System.arraycopy(last.choices(), 0, choices, 0, choices.length);
        last.values().forEach((id, value) -> values.put(id, value.clone()));
        ++version;
        return true;
    }

    /**
     * Pick random parts and slider values.
     *
     * @param random Where the randomness comes from.
     * @param context The context whose slider ranges to stay in, like "player".
     */
    void randomize(@NonNull SeededRandom random, @NonNull String context) {
        remember();
        for (int i = 0; i < choices.length; ++i) {
            if (isFixed(i)) {
                continue;
            }
            PartSlots.Slot slot = slots.slots().get(i);
            boolean empty = slot.optional() && random.nextBoolean(EMPTY_CHANCE);
            choices[i] = empty ? EMPTY : random.nextInt(slot.options().size());
        }
        for (Slider slider : sliders) {
            Range range = slider.range(context);
            float[] value = values.get(slider.id());
            for (int axis = 0; axis < value.length; ++axis) {
                value[axis] = (float) (random.nextDouble(range.min(), range.max()) * RANDOM_SPREAD);
            }
        }
        ++version;
    }

    /** Take off everything that can be taken off, and put the rest back to how it started. */
    void clear() {
        remember();
        for (int i = 0; i < choices.length; ++i) {
            PartSlots.Slot slot = slots.slots().get(i);
            choices[i] = slot.optional() ? EMPTY : slot.initial();
        }
        ++version;
    }

    /** Put every slider back to 0. */
    void resetSliders() {
        remember();
        values.values().forEach(value -> Arrays.fill(value, 0));
        ++version;
    }
}
