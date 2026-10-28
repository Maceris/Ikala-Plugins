package com.ikalagaming.characters.editor;

import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.data.Range;
import com.ikalagaming.characters.definition.BodyPlan;
import com.ikalagaming.characters.definition.FxUse;
import com.ikalagaming.characters.definition.Palette;
import com.ikalagaming.characters.definition.Slider;
import com.ikalagaming.characters.view.PartSlots;
import com.ikalagaming.random.SeededRandom;

import lombok.NonNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
     * @param variant Which variant of the body plan, or -1 for none.
     * @param colors Each palette channel's color code, for the channels colored.
     * @param fx The effects the design wears.
     */
    record Snapshot(
            int[] choices,
            Map<Id, float[]> values,
            int variant,
            Map<String, Integer> colors,
            List<FxUse> fx) {}

    /** The slots. */
    private final PartSlots slots;

    /** The sliders, in the order they are shown. */
    private final List<Slider> sliders;

    /** The position in each slot's options of what fills it, or {@link #EMPTY}. */
    private final int[] choices;

    /** Each slider's values, by ID. */
    private final Map<Id, float[]> values = new LinkedHashMap<>();

    /** The body plan's variants, like male and female. */
    private final List<BodyPlan.Variant> variants;

    /** Which variant the body is, or -1 if the plan has none. */
    private int variant = -1;

    /** For each slot, the places each of its options fills, its own among them. */
    private final List<List<Set<String>>> fills;

    /** The palette channels the slots' parts take, in the order editors list them. */
    private final List<String> channels;

    /** Each palette channel's color code; channels left out keep the parts' own colors. */
    private final Map<String, Integer> colors = new LinkedHashMap<>();

    /** The effects the design wears, on top of its parts' own. */
    private final List<FxUse> fx = new ArrayList<>();

    /** Earlier looks, the latest first. */
    private final Deque<Snapshot> history = new ArrayDeque<>();

    /** Counts changes, so callers can tell when the look changed. */
    private int version;

    /**
     * Start a look with the slots' initial parts and the sliders where the first variant starts
     * them, or at 0.
     *
     * @param slots The slots.
     * @param sliders The sliders.
     * @param variants The body plan's variants, which may be none.
     */
    CharacterLook(
            @NonNull PartSlots slots,
            @NonNull List<Slider> sliders,
            @NonNull List<BodyPlan.Variant> variants) {
        this(slots, sliders, variants, derivedChannels(slots), ownFills(slots));
    }

    /**
     * The palette channels slots' parts take, by {@link PartSlots#channelOf}.
     *
     * @param slots The slots.
     * @return The channels, in the order editors list them.
     */
    private static List<String> derivedChannels(PartSlots slots) {
        List<String> used = new ArrayList<>();
        for (PartSlots.Slot slot : slots.slots()) {
            used.add(PartSlots.channelOf(slot));
        }
        return Palette.STANDARD_CHANNELS.stream().filter(used::contains).toList();
    }

    /**
     * Fills where every option fills only its own slot.
     *
     * @param slots The slots.
     * @return The fills.
     */
    private static List<List<Set<String>>> ownFills(PartSlots slots) {
        List<List<Set<String>>> fills = new ArrayList<>();
        for (PartSlots.Slot slot : slots.slots()) {
            List<Set<String>> options = new ArrayList<>();
            slot.options().forEach(option -> options.add(Set.of(slot.id())));
            fills.add(options);
        }
        return fills;
    }

    /**
     * Start a look with the slots' initial parts and the sliders where the first variant starts
     * them, or at 0, where options may fill more places than their own slot, like a body that fills
     * the torso, arms and legs. Choosing one empties the slots it covers.
     *
     * @param slots The slots.
     * @param sliders The sliders.
     * @param variants The body plan's variants, which may be none.
     * @param channels The palette channels to offer, in the order editors list them.
     * @param fills For each slot, the places each of its options fills, its own among them.
     */
    CharacterLook(
            @NonNull PartSlots slots,
            @NonNull List<Slider> sliders,
            @NonNull List<BodyPlan.Variant> variants,
            @NonNull List<String> channels,
            @NonNull List<List<Set<String>>> fills) {
        this.slots = slots;
        this.sliders = List.copyOf(sliders);
        this.variants = List.copyOf(variants);
        this.channels = List.copyOf(channels);
        this.fills = fills;
        choices = new int[slots.slots().size()];
        for (int i = 0; i < choices.length; ++i) {
            choices[i] = slots.slots().get(i).initial();
        }
        for (Slider slider : sliders) {
            values.put(slider.id(), new float[slider.vector() ? 3 : 1]);
        }
        if (!variants.isEmpty()) {
            variant = 0;
            startSliders();
        }
    }

    /**
     * Start from a design: its variant, its slider values over the variant's starting ones, and its
     * colors, with nothing to undo.
     *
     * @param variantIndex The variant's position in {@link #variants()}, or -1 for the first.
     * @param startValues Slider values by ID; sliders left out start where the variant says.
     * @param startColors Color codes by palette channel.
     * @param startFx The effects the design wears.
     */
    void start(
            int variantIndex,
            @NonNull Map<Id, float[]> startValues,
            @NonNull Map<String, Integer> startColors,
            @NonNull List<FxUse> startFx) {
        if (!variants.isEmpty()) {
            variant = variantIndex < 0 ? 0 : variantIndex;
            startSliders();
        }
        startValues.forEach(
                (id, value) -> {
                    float[] own = values.get(id);
                    if (own != null && own.length == value.length) {
                        System.arraycopy(value, 0, own, 0, own.length);
                    }
                });
        colors.clear();
        colors.putAll(startColors);
        fx.clear();
        fx.addAll(startFx);
        history.clear();
        ++version;
    }

    /**
     * The body plan's variants.
     *
     * @return The variants.
     */
    List<BodyPlan.Variant> variants() {
        return variants;
    }

    /**
     * Which variant the body is.
     *
     * @return Its position in {@link #variants()}, or -1 if there are none.
     */
    int variant() {
        return variant;
    }

    /**
     * Make the body another variant, starting the sliders where it says.
     *
     * @param chosen The variant's position in {@link #variants()}.
     */
    void chooseVariant(int chosen) {
        if (chosen == variant) {
            return;
        }
        remember();
        variant = chosen;
        startSliders();
        ++version;
    }

    /**
     * The palette channels the slots' parts take.
     *
     * @return The channels, in the order editors list them.
     */
    List<String> channels() {
        return channels;
    }

    /**
     * A palette channel's color.
     *
     * @param channel The channel.
     * @return Its color code, or null if its parts keep their own colors.
     */
    Integer color(@NonNull String channel) {
        return colors.get(channel);
    }

    /**
     * Color a palette channel, remembering the look before to undo.
     *
     * @param channel The channel.
     * @param code The color code, or null for the parts' own colors.
     */
    void setColor(@NonNull String channel, Integer code) {
        if (Objects.equals(colors.get(channel), code)) {
            return;
        }
        remember();
        putColor(channel, code);
    }

    /**
     * Color a palette channel without remembering the look before, for a drag that remembered it
     * when it started; see {@link #push}.
     *
     * @param channel The channel.
     * @param code The color code.
     */
    void dragColor(@NonNull String channel, int code) {
        putColor(channel, code);
    }

    /**
     * Change a channel's color.
     *
     * @param channel The channel.
     * @param code The color code, or null to remove it.
     */
    private void putColor(String channel, Integer code) {
        if (code == null) {
            colors.remove(channel);
        } else {
            colors.put(channel, code);
        }
        ++version;
    }

    /**
     * The effects the design wears.
     *
     * @return The effects, which can't be changed here.
     */
    List<FxUse> fx() {
        return Collections.unmodifiableList(fx);
    }

    /**
     * Add an effect, remembering the look before to undo.
     *
     * @param use The effect.
     */
    void addFx(@NonNull FxUse use) {
        remember();
        fx.add(use);
        ++version;
    }

    /**
     * Change or remove an effect, remembering the look before to undo.
     *
     * @param index The effect's position.
     * @param use The new effect, or null to remove it.
     */
    void setFx(int index, FxUse use) {
        if (Objects.equals(fx.get(index), use)) {
            return;
        }
        remember();
        putFx(index, use);
    }

    /**
     * Change an effect without remembering the look before, for a drag that remembered it when it
     * started; see {@link #push}.
     *
     * @param index The effect's position.
     * @param use The new effect.
     */
    void dragFx(int index, @NonNull FxUse use) {
        putFx(index, use);
    }

    /**
     * Change or remove an effect.
     *
     * @param index The effect's position.
     * @param use The new effect, or null to remove it.
     */
    private void putFx(int index, FxUse use) {
        if (use == null) {
            fx.remove(index);
        } else {
            fx.set(index, use);
        }
        ++version;
    }

    /** Give every channel back the parts' own colors. */
    void resetColors() {
        if (colors.isEmpty()) {
            return;
        }
        remember();
        colors.clear();
        ++version;
    }

    /**
     * The values a slider may take: its own range for the context, narrowed by the variant's.
     *
     * @param slider The slider.
     * @param context The context, like "player".
     * @return The range.
     */
    Range range(@NonNull Slider slider, @NonNull String context) {
        Range own = slider.range(context);
        BodyPlan.SliderSetting setting = setting(slider);
        if (setting == null || setting.range() == null) {
            return own;
        }
        return new Range(
                Math.max(own.min(), setting.range().min()),
                Math.min(own.max(), setting.range().max()));
    }

    /**
     * The variant's setting for a slider.
     *
     * @param slider The slider.
     * @return The setting, or null if it has none.
     */
    private BodyPlan.SliderSetting setting(Slider slider) {
        if (variant < 0 || variants.get(variant).sliders() == null) {
            return null;
        }
        return variants.get(variant).sliders().get(slider.id().toString());
    }

    /** Put every slider where the variant starts it, or at 0. */
    private void startSliders() {
        for (Slider slider : sliders) {
            BodyPlan.SliderSetting setting = setting(slider);
            Arrays.fill(values.get(slider.id()), setting == null ? 0 : setting.initial());
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
        uncover(slot);
        ++version;
    }

    /**
     * Empty the slots a slot's choice covers or overlaps.
     *
     * @param slot The slot just filled.
     */
    private void uncover(int slot) {
        Set<String> filled = filled(slot);
        for (int other = 0; other < choices.length; ++other) {
            if (other == slot) {
                continue;
            }
            boolean covered = filled.contains(slots.slots().get(other).id());
            boolean overlaps = filled(other).stream().anyMatch(filled::contains);
            if (covered || overlaps) {
                choices[other] = EMPTY;
            }
        }
    }

    /**
     * The places a slot's choice fills.
     *
     * @param slot The slot.
     * @return The places, or none if it is empty.
     */
    private Set<String> filled(int slot) {
        return choices[slot] == EMPTY ? Set.of() : fills.get(slot).get(choices[slot]);
    }

    /**
     * Which slot's choice covers a slot, like a body covering the arms.
     *
     * @param slot The slot.
     * @return The covering slot's position, or -1 if nothing else covers it.
     */
    int coveredBy(int slot) {
        String place = slots.slots().get(slot).id();
        for (int other = 0; other < choices.length; ++other) {
            if (other != slot && filled(other).contains(place)) {
                return other;
            }
        }
        return -1;
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
        return new Snapshot(
                choices.clone(), copied, variant, new LinkedHashMap<>(colors), List.copyOf(fx));
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
        variant = last.variant();
        colors.clear();
        colors.putAll(last.colors());
        fx.clear();
        fx.addAll(last.fx());
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
            Range range = range(slider, context);
            float[] value = values.get(slider.id());
            // Toward the middle of the range, so random bodies aren't all extremes
            float middle = (range.min() + range.max()) / 2;
            for (int axis = 0; axis < value.length; ++axis) {
                double picked = random.nextDouble(range.min(), range.max());
                value[axis] = (float) (middle + (picked - middle) * RANDOM_SPREAD);
            }
        }
        for (String channel : channels) {
            colors.put(channel, Swatches.random(channel, random));
        }
        // Earlier slots win where random choices overlap
        for (int i = 0; i < choices.length; ++i) {
            if (choices[i] != EMPTY && coveredBy(i) >= 0 && coveredBy(i) < i) {
                choices[i] = EMPTY;
            } else if (choices[i] != EMPTY) {
                uncover(i);
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
        fx.clear();
        ++version;
    }

    /** Put every slider back where the variant starts it, or to 0. */
    void resetSliders() {
        remember();
        startSliders();
        ++version;
    }
}
