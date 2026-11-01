package com.ikalagaming.characters.editor;

import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.data.Range;
import com.ikalagaming.characters.definition.BodyPlan;
import com.ikalagaming.characters.definition.FxUse;
import com.ikalagaming.characters.definition.Palette;
import com.ikalagaming.characters.definition.Part;
import com.ikalagaming.characters.definition.Slider;
import com.ikalagaming.characters.design.WearRules;
import com.ikalagaming.characters.view.PartSlots;
import com.ikalagaming.random.SeededRandom;

import lombok.NonNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * What a character in the editor looks like: the part in each slot and each slider's value, with a
 * history to undo changes. Not thread safe; the editor uses it on the render thread.
 *
 * <p>Given the parts behind the options, choosing follows the wear rules ({@link WearRules}): the
 * last part put on takes off what it clashes with, puts on what it requires and, with both sides
 * on, its pair; taking a part off takes off what needs it. What a choice took off is remembered
 * under its slot and comes back once nothing is in its way, so switching back restores earlier
 * picks. Options the variant doesn't allow, or whose required parts aren't offered, aren't
 * available, and worn parts hide the layers and sockets they cover.
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
     * @param style The way it moves, or null for the plan's own.
     * @param displaced What each slot's choice took off, by slot, to put back later.
     * @param bothSides Whether parts with a pair are put on along with it.
     */
    record Snapshot(
            int[] choices,
            Map<Id, float[]> values,
            int variant,
            Map<String, Integer> colors,
            List<FxUse> fx,
            Id style,
            Map<Integer, Map<Integer, Integer>> displaced,
            boolean bothSides) {}

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

    /** The way it moves, or null for the plan's own. */
    private Id style;

    /** Earlier looks, the latest first. */
    private final Deque<Snapshot> history = new ArrayDeque<>();

    /** The part behind each slot's options, or null if the look has no rules to follow. */
    private final List<List<Part>> parts;

    /** The body plan, for exclusive tags and sockets, or null with {@link #parts}. */
    private final BodyPlan plan;

    /** Where each part is offered: its slot and option positions. */
    private final Map<Id, int[]> offered = new HashMap<>();

    /**
     * What each slot's choice took off, by that slot: the slots emptied and what they held, to put
     * back once nothing is in the way.
     */
    private final Map<Integer, Map<Integer, Integer>> displaced = new LinkedHashMap<>();

    /** Whether parts with a pair are put on along with it. */
    private boolean bothSides = true;

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
        this(slots, sliders, variants, channels, fills, null, null);
    }

    /**
     * Start a look as {@link #CharacterLook(PartSlots, List, List, List, List)} does, following
     * the wear rules of the parts behind the options.
     *
     * @param slots The slots.
     * @param sliders The sliders.
     * @param variants The body plan's variants, which may be none.
     * @param channels The palette channels to offer, in the order editors list them.
     * @param fills For each slot, the places each of its options fills, its own among them.
     * @param parts For each slot, the part behind each option, or null for no rules.
     * @param plan The body plan, or null with no parts.
     */
    CharacterLook(
            @NonNull PartSlots slots,
            @NonNull List<Slider> sliders,
            @NonNull List<BodyPlan.Variant> variants,
            @NonNull List<String> channels,
            @NonNull List<List<Set<String>>> fills,
            List<List<Part>> parts,
            BodyPlan plan) {
        this.parts = parts == null || plan == null ? null : parts;
        this.plan = this.parts == null ? null : plan;
        if (this.parts != null) {
            for (int slot = 0; slot < this.parts.size(); ++slot) {
                List<Part> options = this.parts.get(slot);
                for (int option = 0; option < options.size(); ++option) {
                    offered.putIfAbsent(options.get(option).id(), new int[] {slot, option});
                }
            }
        }
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
     * @param startStyle The way it moves, or null for the plan's own.
     */
    void start(
            int variantIndex,
            @NonNull Map<Id, float[]> startValues,
            @NonNull Map<String, Integer> startColors,
            @NonNull List<FxUse> startFx,
            Id startStyle) {
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
        style = startStyle;
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
        for (int slot = 0; slot < choices.length; ++slot) {
            if (choices[slot] != EMPTY && !isAvailable(slot, choices[slot])) {
                choices[slot] = fallback(slot);
            }
        }
        settle(Set.of());
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

    /**
     * The way it moves.
     *
     * @return The style, or null for the plan's own.
     */
    Id style() {
        return style;
    }

    /**
     * Change the way it moves, remembering the look before to undo.
     *
     * @param newStyle The style, or null for the plan's own.
     */
    void setStyle(Id newStyle) {
        if (Objects.equals(style, newStyle)) {
            return;
        }
        remember();
        style = newStyle;
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
        Set<Integer> changed = new HashSet<>();
        put(slot, choice, changed);
        settle(changed);
        ++version;
    }

    /**
     * Fill a slot, then take off what is in the way of what it now holds and put on what that
     * needs, and its pair with both sides on. The user decided this slot, so nothing remembered
     * for it is put back.
     *
     * @param slot The slot.
     * @param choice The position in its options, or {@link #EMPTY}.
     * @param changed The slots set so far by this change, added to.
     */
    private void put(int slot, int choice, Set<Integer> changed) {
        if (!changed.add(slot)) {
            return;
        }
        int before = choices[slot];
        choices[slot] = choice;
        displaced.values().forEach(taken -> taken.remove(slot));
        uncover(slot);
        Part part = part(slot, choice);
        if (part != null) {
            for (int other = 0; other < choices.length; ++other) {
                Part worn = part(other, choices[other]);
                if (other != slot && worn != null && WearRules.clash(plan, part, worn)) {
                    takeOff(slot, other);
                }
            }
            for (Id needed : part.requires()) {
                int[] where = offered.get(needed);
                if (where != null && choices[where[0]] != where[1]) {
                    put(where[0], where[1], changed);
                }
            }
        }
        if (!bothSides) {
            return;
        }
        Part was = part(slot, before);
        if (part != null && part.pair() != null) {
            int[] where = offered.get(part.pair());
            if (where != null && choices[where[0]] != where[1]) {
                put(where[0], where[1], changed);
            }
        } else if (was != null && was.pair() != null) {
            // Taking one side off takes off the other, where it can be taken off
            int[] where = offered.get(was.pair());
            if (where != null && choices[where[0]] == where[1]) {
                put(where[0], fallback(where[0]), changed);
            }
        }
    }

    /**
     * Empty the slots a slot's choice covers or overlaps, remembering what they held.
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
            if ((covered || overlaps) && choices[other] != EMPTY) {
                displaced.computeIfAbsent(slot, s -> new LinkedHashMap<>()).put(other, choices[other]);
                choices[other] = EMPTY;
            }
        }
    }

    /**
     * Take what a slot holds off because another slot's choice clashes with it, remembering it
     * under that slot. A slot that can't be empty goes back to what it starts with, unless that
     * clashes too.
     *
     * @param by The slot whose choice is in the way.
     * @param slot The slot to empty.
     */
    private void takeOff(int by, int slot) {
        displaced.computeIfAbsent(by, s -> new LinkedHashMap<>()).put(slot, choices[slot]);
        int start = fallback(slot);
        Part starting = part(slot, start);
        Part blocking = part(by, choices[by]);
        boolean clashes =
                starting != null && blocking != null && WearRules.clash(plan, starting, blocking);
        choices[slot] = clashes ? EMPTY : start;
    }

    /**
     * What a slot holds when what it held has to go: nothing if it can be empty, else what it
     * starts with if that is available, else its first available option.
     *
     * @param slot The slot.
     * @return The position in its options, or {@link #EMPTY}.
     */
    private int fallback(int slot) {
        PartSlots.Slot info = slots.slots().get(slot);
        if (info.optional()) {
            return EMPTY;
        }
        if (info.initial() != EMPTY && isAvailable(slot, info.initial())) {
            return info.initial();
        }
        List<Integer> shown = available(slot);
        return shown.isEmpty() ? info.initial() : shown.get(0);
    }

    /**
     * After a change: take off parts whose required parts are gone, then put back what was taken
     * off where nothing is in its way any more. Slots the change set are left as they are.
     *
     * @param changed The slots the change set.
     */
    private void settle(Set<Integer> changed) {
        if (parts == null) {
            return;
        }
        boolean dropped = true;
        while (dropped) {
            dropped = false;
            Set<Id> worn = new HashSet<>();
            for (int slot = 0; slot < choices.length; ++slot) {
                Part part = part(slot, choices[slot]);
                if (part != null) {
                    worn.add(part.id());
                }
            }
            for (int slot = 0; slot < choices.length; ++slot) {
                Part part = part(slot, choices[slot]);
                if (part != null && !worn.containsAll(part.requires())) {
                    int replacement = fallback(slot);
                    dropped = replacement != choices[slot];
                    choices[slot] = replacement;
                    if (dropped) {
                        break;
                    }
                }
            }
        }
        for (Map.Entry<Integer, Map<Integer, Integer>> entry : displaced.entrySet()) {
            entry.getValue()
                    .entrySet()
                    .removeIf(
                            taken -> {
                                int slot = taken.getKey();
                                if (changed.contains(slot)
                                        || (choices[slot] != EMPTY && choices[slot] != fallback(slot))
                                        || !fits(slot, taken.getValue())) {
                                    return false;
                                }
                                choices[slot] = taken.getValue();
                                return true;
                            });
        }
        displaced.values().removeIf(Map::isEmpty);
    }

    /**
     * Whether an option could go back in its slot without anything else changing: it is
     * available, no other choice covers or overlaps it, and it clashes with nothing worn.
     *
     * @param slot The slot.
     * @param choice The position in its options.
     * @return True if it fits.
     */
    private boolean fits(int slot, int choice) {
        if (choice == EMPTY || choice >= slots.slots().get(slot).options().size()) {
            return false;
        }
        if (!isAvailable(slot, choice)) {
            return false;
        }
        String place = slots.slots().get(slot).id();
        Set<String> wouldFill = fills.get(slot).get(choice);
        Part part = part(slot, choice);
        for (int other = 0; other < choices.length; ++other) {
            if (other == slot || choices[other] == EMPTY) {
                continue;
            }
            Set<String> filledThere = filled(other);
            if (filledThere.contains(place) || filledThere.stream().anyMatch(wouldFill::contains)) {
                return false;
            }
            Part worn = part(other, choices[other]);
            if (part != null && worn != null && WearRules.clash(plan, part, worn)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The part behind an option.
     *
     * @param slot The slot.
     * @param choice The position in its options, or {@link #EMPTY}.
     * @return The part, or null if it is empty or the look has no rules.
     */
    Part part(int slot, int choice) {
        if (parts == null || choice == EMPTY) {
            return null;
        }
        return parts.get(slot).get(choice);
    }

    /**
     * Whether an option can be chosen: the variant allows its part, and the parts it requires are
     * offered.
     *
     * @param slot The slot.
     * @param choice The position in its options.
     * @return True if it can.
     */
    boolean isAvailable(int slot, int choice) {
        Part part = part(slot, choice);
        if (part == null) {
            return true;
        }
        if (variant >= 0 && !variants.get(variant).allows(part)) {
            return false;
        }
        return part.requires().stream().allMatch(offered::containsKey);
    }

    /**
     * The options of a slot that can be chosen.
     *
     * @param slot The slot.
     * @return Their positions, in order.
     */
    List<Integer> available(int slot) {
        List<Integer> shown = new ArrayList<>();
        for (int option = 0; option < slots.slots().get(slot).options().size(); ++option) {
            if (isAvailable(slot, option)) {
                shown.add(option);
            }
        }
        return shown;
    }

    /**
     * The worn part that hides a slot: a layer it hides, or the socket an accessory goes on.
     *
     * @param slot The slot.
     * @return The part, or null if nothing hides it.
     */
    Part hiddenBy(int slot) {
        if (parts == null) {
            return null;
        }
        String place = slots.slots().get(slot).id();
        BodyPlan.Socket socket = plan.socket(place);
        for (int other = 0; other < choices.length; ++other) {
            Part worn = part(other, choices[other]);
            if (other == slot || worn == null) {
                continue;
            }
            if (worn.hidesLayers().contains(place)
                    || (socket != null && WearRules.hides(socket, Set.copyOf(worn.hidesSockets())))) {
                return worn;
            }
        }
        return null;
    }

    /**
     * The worn part that locks a slider.
     *
     * @param slider The slider.
     * @return The part, or null if none locks it.
     */
    Part lockedBy(@NonNull Slider slider) {
        for (int slot = 0; slot < choices.length; ++slot) {
            Part worn = part(slot, choices[slot]);
            if (worn != null && WearRules.locks(worn, slider)) {
                return worn;
            }
        }
        return null;
    }

    /**
     * Whether parts with a pair are put on along with it.
     *
     * @return True if they are.
     */
    boolean bothSides() {
        return bothSides;
    }

    /**
     * Put parts with a pair on along with it, or choose sides alone.
     *
     * @param both Whether to put pairs on together.
     */
    void setBothSides(boolean both) {
        if (both != bothSides) {
            remember();
            bothSides = both;
            ++version;
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
        List<Integer> shown = new ArrayList<>();
        if (info.optional()) {
            shown.add(EMPTY);
        }
        shown.addAll(available(slot));
        if (shown.isEmpty()) {
            return;
        }
        int at = shown.indexOf(choices[slot]);
        choose(slot, shown.get(Math.floorMod((at < 0 ? 0 : at) + step, shown.size())));
    }

    /**
     * The parts worn, with one slot showing another choice for a preview.
     *
     * @param previewSlot The slot to preview, or {@link #NO_PREVIEW}.
     * @param previewChoice What it would hold.
     * @return The indices of the parts.
     */
    List<Integer> worn(int previewSlot, int previewChoice) {
        // What worn parts cover shows nothing: hidden layers, accessories on covered sockets
        Set<String> hiddenLayers = new HashSet<>();
        Set<String> hiddenSockets = new HashSet<>();
        for (int i = 0; i < choices.length; ++i) {
            Part part = part(i, i == previewSlot ? previewChoice : choices[i]);
            if (part != null) {
                hiddenLayers.addAll(part.hidesLayers());
                hiddenSockets.addAll(part.hidesSockets());
            }
        }
        List<Integer> worn = new ArrayList<>();
        for (int i = 0; i < choices.length; ++i) {
            int choice = i == previewSlot ? previewChoice : choices[i];
            if (choice == EMPTY) {
                continue;
            }
            String place = slots.slots().get(i).id();
            BodyPlan.Socket socket = plan == null ? null : plan.socket(place);
            if (hiddenLayers.contains(place)
                    || (socket != null && WearRules.hides(socket, hiddenSockets))) {
                continue;
            }
            worn.addAll(slots.slots().get(i).options().get(choice));
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
        Map<Integer, Map<Integer, Integer>> taken = new LinkedHashMap<>();
        displaced.forEach((slot, emptied) -> taken.put(slot, new LinkedHashMap<>(emptied)));
        return new Snapshot(
                choices.clone(),
                copied,
                variant,
                new LinkedHashMap<>(colors),
                List.copyOf(fx),
                style,
                taken,
                bothSides);
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
        style = last.style();
        displaced.clear();
        last.displaced().forEach((slot, emptied) -> displaced.put(slot, new LinkedHashMap<>(emptied)));
        bothSides = last.bothSides();
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
            List<Integer> shown = available(i);
            boolean empty = shown.isEmpty() || (slot.optional() && random.nextBoolean(EMPTY_CHANCE));
            choices[i] = empty ? fallback(i) : shown.get(random.nextInt(shown.size()));
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
        // Earlier slots win where random choices overlap or clash
        displaced.clear();
        for (int i = 0; i < choices.length; ++i) {
            if (choices[i] != EMPTY && coveredBy(i) >= 0 && coveredBy(i) < i) {
                choices[i] = EMPTY;
            } else if (choices[i] != EMPTY) {
                uncover(i);
                Part part = part(i, choices[i]);
                for (int other = i + 1; part != null && other < choices.length; ++other) {
                    Part worn = part(other, choices[other]);
                    if (worn != null && WearRules.clash(plan, part, worn)) {
                        choices[other] = fallback(other);
                    }
                }
            }
        }
        displaced.clear();
        settle(Set.of());
        displaced.clear();
        ++version;
    }

    /** Take off everything that can be taken off, and put the rest back to how it started. */
    void clear() {
        remember();
        for (int i = 0; i < choices.length; ++i) {
            PartSlots.Slot slot = slots.slots().get(i);
            choices[i] = slot.optional() ? EMPTY : slot.initial();
        }
        displaced.clear();
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
