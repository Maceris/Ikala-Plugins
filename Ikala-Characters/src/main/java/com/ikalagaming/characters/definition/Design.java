package com.ikalagaming.characters.definition;

import com.ikalagaming.characters.data.Color;
import com.ikalagaming.characters.data.Doc;
import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.data.JsonPath;
import com.ikalagaming.characters.data.Optional;
import com.ikalagaming.characters.data.Problem;
import com.ikalagaming.characters.data.Range;
import com.ikalagaming.characters.data.Report;
import com.ikalagaming.characters.registry.CharacterRegistry;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * A saved character: its body plan and variant, the part in each slot and layer, its slider values
 * and its colors. The character editor writes these, NPCs embed or refer to them, and players keep
 * one or more. Slots left out fall back to the variant's default parts; a part that fills several
 * slots is listed once, under the first it fills.
 *
 * @param id This design's ID.
 * @param plan The body plan.
 * @param variant The plan's variant.
 * @param sliders Slider values by slider ID: one value, or three for sliders with width, height and
 *     depth.
 * @param parts Body parts by the slot they are listed under.
 * @param layers Layer parts by layer.
 * @param palette A palette whose colors fill channels without their own.
 * @param colors Colors by palette channel.
 * @param fx Effects the design wears, on top of its parts' own.
 * @author Ches Burks
 */
@Doc(
        "A saved character: its body plan and variant, the part in each slot and layer, its slider"
                + " values and its colors. Slots left out fall back to the variant's default"
                + " parts.")
public record Design(
        @Doc("This design's ID, like \"ikala:design/test_knight\".") Id id,
        @Doc("The body plan.") Id plan,
        @Doc("The plan's variant, like \"female\".") String variant,
        @Doc(
                        "Slider values by slider ID: one value, or three for sliders with width,"
                                + " height and depth. Sliders left out are where the variant"
                                + " starts them.")
                @Optional
                Map<String, List<Float>> sliders,
        @Doc(
                        "Body parts by slot. A part that fills several slots is listed once, under"
                                + " the first it fills.")
                @Optional
                Map<String, Id> parts,
        @Doc("Layer parts, like a jacket or boots, by layer.") @Optional Map<String, Id> layers,
        @Doc("A palette whose colors fill channels the design doesn't color itself.") @Optional
                Id palette,
        @Doc("Colors by palette channel, like {\"skin\": \"#c58c62\"}.") @Optional
                Map<String, Color> colors,
        @Doc("Effects it wears, like an aura, on top of its parts' own. Up to 4.") @Optional
                List<FxUse> fx)
        implements Definition {

    /** The field for the plan. */
    public static final String FIELD_PLAN = "plan";

    /** The field for the variant. */
    public static final String FIELD_VARIANT = "variant";

    /** The field for the slider values. */
    public static final String FIELD_SLIDERS = "sliders";

    /** The field for the body parts. */
    public static final String FIELD_PARTS = "parts";

    /** The field for the layer parts. */
    public static final String FIELD_LAYERS = "layers";

    /** The field for the palette. */
    public static final String FIELD_PALETTE = "palette";

    /** The field for the colors. */
    public static final String FIELD_COLORS = "colors";

    /** The field for the effects. */
    public static final String FIELD_FX = "fx";

    /** The most effects a design wears itself, so crowds stay within the scene's emitters. */
    public static final int MAX_FX = 4;

    /** Finds the definitions a design refers to. */
    private interface Lookup {
        /**
         * Find a definition, reporting it if it is missing.
         *
         * @param <T> The kind of definition.
         * @param type The kind of definition.
         * @param id Its ID.
         * @param path Where the reference is.
         * @return The definition, or null.
         */
        <T extends Definition> T find(Class<T> type, Id id, String path);

        /**
         * All definitions of a kind.
         *
         * @param <T> The kind of definition.
         * @param type The kind of definition.
         * @return The definitions.
         */
        <T extends Definition> List<T> all(Class<T> type);
    }

    @Override
    public void check(Checks checks) {
        // Designs in content are NPCs and templates, so they may use NPC slider ranges
        validate(
                new Lookup() {
                    @Override
                    public <T extends Definition> T find(Class<T> type, Id other, String path) {
                        return checks.require(type, other, path);
                    }

                    @Override
                    public <T extends Definition> List<T> all(Class<T> type) {
                        return checks.all(type);
                    }
                },
                checks,
                Slider.CONTEXT_NPC);
    }

    /**
     * Check a design against loaded definitions, like a player's design on save.
     *
     * @param registry The loaded definitions.
     * @param context Whose slider ranges apply, like {@link Slider#CONTEXT_PLAYER}.
     * @return What is wrong, if anything.
     */
    public List<Problem> validate(@NonNull CharacterRegistry registry, @NonNull String context) {
        List<Problem> problems = new ArrayList<>();
        String where = "design " + id;
        Report report =
                new Report() {
                    @Override
                    public void error(@NonNull String path, @NonNull String message) {
                        problems.add(
                                new Problem(Problem.Severity.ERROR, where, 0, 0, path, message));
                    }

                    @Override
                    public void warning(@NonNull String path, @NonNull String message) {
                        problems.add(
                                new Problem(Problem.Severity.WARNING, where, 0, 0, path, message));
                    }
                };
        validate(
                new Lookup() {
                    @Override
                    public <T extends Definition> T find(Class<T> type, Id other, String path) {
                        T found = registry.get(type, other);
                        if (found == null) {
                            report.error(path, "No loaded definition " + other);
                        }
                        return found;
                    }

                    @Override
                    public <T extends Definition> List<T> all(Class<T> type) {
                        return registry.all(type);
                    }
                },
                report,
                context);
        return problems;
    }

    /**
     * Check the design.
     *
     * @param lookup Finds what it refers to.
     * @param report Where problems go.
     * @param context Whose slider ranges apply.
     */
    private void validate(Lookup lookup, Report report, String context) {
        BodyPlan body = lookup.find(BodyPlan.class, plan, JsonPath.at(FIELD_PLAN));
        if (palette != null) {
            lookup.find(Palette.class, palette, JsonPath.at(FIELD_PALETTE));
        }
        if (body == null) {
            return;
        }
        BodyPlan.Variant chosen = body.variant(variant);
        if (chosen == null) {
            report.error(JsonPath.at(FIELD_VARIANT), "The plan has no variant '" + variant + "'");
        }

        Map<Id, String> used = new LinkedHashMap<>();
        Map<String, String> filledBy = new HashMap<>();
        checkParts(lookup, report, body, chosen, parts, FIELD_PARTS, true, used, filledBy);
        checkParts(lookup, report, body, chosen, layers, FIELD_LAYERS, false, used, filledBy);
        for (Map.Entry<Id, String> entry : used.entrySet()) {
            Part part = lookup.find(Part.class, entry.getKey(), entry.getValue());
            if (part == null) {
                continue;
            }
            for (Id other : part.conflicts()) {
                if (used.containsKey(other)) {
                    report.error(entry.getValue(), entry.getKey() + " can't be used with " + other);
                }
            }
            for (Id other : part.requires()) {
                if (!used.containsKey(other)) {
                    report.error(entry.getValue(), entry.getKey() + " needs " + other);
                }
            }
        }

        checkSliders(lookup, report, body, chosen, context);
        String colorsPath = JsonPath.at(FIELD_COLORS);
        for (String channel : colors.keySet()) {
            if (!body.hasChannel(channel)) {
                report.error(
                        JsonPath.field(colorsPath, channel),
                        "The plan has no palette channel '" + channel + "'");
            }
        }
        if (fx.size() > MAX_FX) {
            report.error(JsonPath.at(FIELD_FX), "A design wears up to " + MAX_FX + " effects");
        }
        Rig rig =
                lookup.all(Rig.class).stream()
                        .filter(r -> r.id().equals(body.rig()))
                        .findFirst()
                        .orElse(null);
        for (int i = 0; i < fx.size(); ++i) {
            fx.get(i)
                    .check(
                            body,
                            rig,
                            (effect, path) -> lookup.find(Fx.class, effect, path),
                            report,
                            JsonPath.at(FIELD_FX, i));
        }
    }

    /**
     * Check the parts listed by slot or by layer.
     *
     * @param lookup Finds the parts.
     * @param report Where problems go.
     * @param body The plan.
     * @param chosen The variant, or null if it is missing.
     * @param listed The parts, by where they are listed.
     * @param field The field they are in.
     * @param bodyParts Whether these are body parts in slots rather than layer parts.
     * @param used The parts used so far, with where they are listed, added to.
     * @param filledBy What fills each slot and layer so far, added to.
     */
    private static void checkParts(
            Lookup lookup,
            Report report,
            BodyPlan body,
            BodyPlan.Variant chosen,
            Map<String, Id> listed,
            String field,
            boolean bodyParts,
            Map<Id, String> used,
            Map<String, String> filledBy) {
        String listPath = JsonPath.at(field);
        listed.forEach(
                (place, partId) -> {
                    String path = JsonPath.field(listPath, place);
                    boolean exists =
                            bodyParts ? body.slot(place) != null : body.layer(place) != null;
                    if (!exists) {
                        report.error(
                                path,
                                "The plan has no "
                                        + (bodyParts ? "body slot" : "layer")
                                        + " '"
                                        + place
                                        + "'");
                        return;
                    }
                    Part part = lookup.find(Part.class, partId, path);
                    if (part == null) {
                        return;
                    }
                    Part.PartKind expected = bodyParts ? Part.PartKind.BODY : Part.PartKind.LAYER;
                    if (!part.plan().equals(body.id())) {
                        report.error(path, partId + " is for " + part.plan());
                        return;
                    }
                    if (part.kind() != expected || !part.fills().contains(place)) {
                        report.error(path, partId + " doesn't fill '" + place + "'");
                        return;
                    }
                    if (chosen != null && !chosen.allows(part)) {
                        report.error(
                                path, partId + " has none of the variant's tags " + chosen.tags());
                    }
                    used.put(partId, path);
                    for (String filled : part.fills()) {
                        String before = filledBy.putIfAbsent(filled, place);
                        if (before != null) {
                            report.error(
                                    path,
                                    "'"
                                            + filled
                                            + "' is already filled by the part listed under '"
                                            + before
                                            + "'");
                        }
                    }
                });
    }

    /**
     * Check the slider values: each slider is the plan's, has the right number of values, and they
     * are in range.
     *
     * @param lookup Finds the sliders.
     * @param report Where problems go.
     * @param body The plan.
     * @param chosen The variant, or null if it is missing.
     * @param context Whose slider ranges apply.
     */
    private void checkSliders(
            Lookup lookup, Report report, BodyPlan body, BodyPlan.Variant chosen, String context) {
        Map<String, Slider> planSliders = new HashMap<>();
        body.matchingSliders(lookup.all(Slider.class))
                .forEach(slider -> planSliders.put(slider.id().toString(), slider));
        String slidersPath = JsonPath.at(FIELD_SLIDERS);
        sliders.forEach(
                (sliderId, values) -> {
                    String path = JsonPath.field(slidersPath, sliderId);
                    Slider slider = planSliders.get(sliderId);
                    if (slider == null) {
                        report.error(path, "'" + sliderId + "' isn't one of the plan's sliders");
                        return;
                    }
                    int count = slider.vector() ? 3 : 1;
                    if (values.size() != count) {
                        report.error(path, "The slider takes " + count + " values");
                        return;
                    }
                    Range range =
                            chosen == null ? slider.range(context) : chosen.range(slider, context);
                    for (float value : values) {
                        if (!range.contains(value)) {
                            report.error(
                                    path,
                                    value
                                            + " is outside "
                                            + range.min()
                                            + " to "
                                            + range.max()
                                            + " for "
                                            + context);
                        }
                    }
                });
    }

    /**
     * The slots and layers every part fills, from the design and then the variant's defaults: the
     * design's parts first, then each default whose places the design left empty.
     *
     * @param body The plan.
     * @param find Finds a part by ID, or gives null.
     * @return The parts worn, by where they are listed.
     */
    public Map<String, Id> worn(@NonNull BodyPlan body, @NonNull Function<Id, Part> find) {
        Map<String, Id> worn = new LinkedHashMap<>();
        Set<String> filled = new HashSet<>();
        for (Map<String, Id> listed : List.of(parts, layers)) {
            listed.forEach(
                    (place, partId) -> {
                        Part part = find.apply(partId);
                        if (part != null) {
                            worn.put(place, partId);
                            filled.addAll(part.fills());
                        }
                    });
        }
        BodyPlan.Variant chosen = body.variant(variant);
        if (chosen != null) {
            chosen.parts()
                    .forEach(
                            (place, partId) -> {
                                Part part = find.apply(partId);
                                if (part != null
                                        && part.fills().stream().noneMatch(filled::contains)) {
                                    worn.put(place, partId);
                                    filled.addAll(part.fills());
                                }
                            });
        }
        return worn;
    }
}
