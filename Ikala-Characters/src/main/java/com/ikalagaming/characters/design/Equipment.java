package com.ikalagaming.characters.design;

import com.ikalagaming.characters.data.Color;
import com.ikalagaming.characters.data.Default;
import com.ikalagaming.characters.data.Doc;
import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.data.JsonPath;
import com.ikalagaming.characters.data.Optional;
import com.ikalagaming.characters.data.Problem;
import com.ikalagaming.characters.definition.BodyPlan;
import com.ikalagaming.characters.definition.Item;
import com.ikalagaming.characters.registry.CharacterRegistry;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * What a character has equipped, by equipment slot: each item's look, the state it is in, a look
 * shown in its place (transmog), and its dye colors. Kept apart from the {@link
 * com.ikalagaming.characters.definition.Design design}, which changes rarely, since equipment
 * changes all through play. The gameplay item decides stats; this only decides what is drawn.
 *
 * @param slots What is in each slot, by slot name.
 * @author Ches Burks
 */
@Doc("What a character has equipped, by equipment slot.")
public record Equipment(
        @Doc("What is in each slot, like {\"mainHand\": {...}}.") Map<String, Equipped> slots) {
    /** Nothing equipped. */
    public static final Equipment NONE = new Equipment(Map.of());

    /**
     * One equipped item.
     *
     * @param item The item's look.
     * @param state The state it is in, like {@code sheathed}, or null for its first.
     * @param lookAs Another item's look shown in its place, or null.
     * @param hidden Whether it is drawn as nothing at all.
     * @param dye Its dye colors, by palette channel.
     */
    @Doc("One equipped item.")
    public record Equipped(
            @Doc("The item's look, like \"ikala:item/iron_helm\".") Id item,
            @Doc("The state it is in, like \"sheathed\". Left out, its first.") @Optional
                    String state,
            @Doc("Another item's look shown in its place.") @Optional Id lookAs,
            @Doc("Whether it is drawn as nothing at all.") @Default("false") boolean hidden,
            @Doc("Dye colors, by palette channel, like {\"primary\": \"#7a2e2e\"}.") @Optional
                    Map<String, Color> dye) {
        /** The field for the item. */
        public static final String FIELD_ITEM = "item";

        /** The field for the state. */
        public static final String FIELD_STATE = "state";

        /** The field for the look shown instead. */
        public static final String FIELD_LOOK_AS = "lookAs";

        /** The field for the dye colors. */
        public static final String FIELD_DYE = "dye";

        /**
         * The look drawn: the one shown in its place, or its own.
         *
         * @return The look's ID.
         */
        public Id shown() {
            return lookAs == null ? item : lookAs;
        }

        /**
         * The same item in another state.
         *
         * @param newState The state, or null for its first.
         * @return The changed item.
         */
        public Equipped withState(String newState) {
            return new Equipped(item, newState, lookAs, hidden, dye);
        }
    }

    /**
     * Check equipment for a plan.
     *
     * @param registry The loaded definitions.
     * @param plan The character's plan.
     * @return What is wrong, if anything.
     */
    public List<Problem> validate(@NonNull CharacterRegistry registry, @NonNull Id plan) {
        List<Problem> problems = new ArrayList<>();
        BodyPlan body = registry.get(BodyPlan.class, plan);
        if (body == null) {
            problems.add(error(JsonPath.ROOT, "No loaded plan " + plan));
            return problems;
        }
        slots.forEach(
                (slotName, equipped) -> {
                    String path = JsonPath.at(slotName);
                    if (body.equipmentSlot(slotName) == null) {
                        problems.add(
                                error(path, plan + " has no equipment slot '" + slotName + "'"));
                        return;
                    }
                    Item item = registry.get(Item.class, equipped.item());
                    if (item == null) {
                        problems.add(
                                error(
                                        JsonPath.field(path, Equipped.FIELD_ITEM),
                                        "No loaded item " + equipped.item()));
                        return;
                    }
                    if (!item.slots().contains(slotName)) {
                        problems.add(
                                error(
                                        JsonPath.field(path, Equipped.FIELD_ITEM),
                                        equipped.item() + " doesn't go in " + slotName));
                    }
                    if (item.lookOn(plan) == null) {
                        problems.add(
                                error(
                                        JsonPath.field(path, Equipped.FIELD_ITEM),
                                        equipped.item() + " has no look on " + plan));
                    }
                    Item shown = item;
                    if (equipped.lookAs() != null) {
                        String lookPath = JsonPath.field(path, Equipped.FIELD_LOOK_AS);
                        shown = registry.get(Item.class, equipped.lookAs());
                        if (shown == null) {
                            problems.add(error(lookPath, "No loaded item " + equipped.lookAs()));
                            return;
                        }
                        if (!shown.slots().contains(slotName) || shown.lookOn(plan) == null) {
                            problems.add(
                                    error(
                                            lookPath,
                                            equipped.lookAs()
                                                    + " has no look for "
                                                    + slotName
                                                    + " on "
                                                    + plan));
                            return;
                        }
                    }
                    Item.Look look = shown.lookOn(plan);
                    if (look != null
                            && equipped.state() != null
                            && !look.states().containsKey(equipped.state())) {
                        problems.add(
                                error(
                                        JsonPath.field(path, Equipped.FIELD_STATE),
                                        shown.id() + " has no state '" + equipped.state() + "'"));
                    }
                    for (String channel : equipped.dye().keySet()) {
                        if (!shown.dyeChannels().contains(channel)) {
                            problems.add(
                                    error(
                                            JsonPath.field(
                                                    JsonPath.field(path, Equipped.FIELD_DYE),
                                                    channel),
                                            shown.id() + " can't be dyed in '" + channel + "'"));
                        }
                    }
                });
        return problems;
    }

    /**
     * An error at a path.
     *
     * @param path Where.
     * @param message What.
     * @return The problem.
     */
    private static Problem error(String path, String message) {
        return new Problem(Problem.Severity.ERROR, "equipment", 0, 0, path, message);
    }
}
