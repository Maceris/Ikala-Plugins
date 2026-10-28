package com.ikalagaming.characters.definition;

import com.ikalagaming.characters.data.Default;
import com.ikalagaming.characters.data.Doc;
import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.data.JsonPath;
import com.ikalagaming.characters.data.Optional;

import lombok.NonNull;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The clips a body plan plays for each action, like {@code idle}, {@code move} or {@code
 * attack.melee.1}. Gameplay asks for actions, never files. Several sets can be for one plan, so
 * mods add actions without editing it; where two define an action, the one first in ID order wins.
 * Variants can override actions, and {@link AnimStyle styles} override them again.
 *
 * @param id This set's ID.
 * @param plan The body plan it animates.
 * @param actions Clips by action.
 * @param variants Actions that differ for a variant, by variant name.
 * @author Ches Burks
 */
@Doc(
        "The clips a body plan plays for each action, like idle, move or attack.melee.1. Sets for"
                + " the same plan merge, the first in ID order winning where two define an"
                + " action.")
public record AnimationSet(
        @Doc("This set's ID, like \"ikala:anim/biped\".") Id id,
        @Doc("The body plan it animates.") Id plan,
        @Doc("Clips by action, like {\"idle\": {...}}.") Map<String, Action> actions,
        @Doc("Actions that differ for a variant, by variant name, then action.") @Optional
                Map<String, Map<String, Action>> variants)
        implements Definition {

    /** The field for the plan. */
    public static final String FIELD_PLAN = "plan";

    /** The field for the actions. */
    public static final String FIELD_ACTIONS = "actions";

    /** The field for the variants' actions. */
    public static final String FIELD_VARIANTS = "variants";

    /** What action IDs look like: lowercase words joined by dots, like {@code attack.melee.1}. */
    public static final Pattern ACTION_ID = Pattern.compile("[a-z0-9_]+(\\.[a-z0-9_]+)*");

    /** What separates a clip's file from its name in the file. */
    public static final char CLIP_SEPARATOR = '#';

    /**
     * What an action plays.
     *
     * @param clips Its clips: one, several to pick from, or several moving at different speeds to
     *     blend between.
     * @param mask The bone group it plays on, or null for the whole body.
     * @param fade How long it takes to blend in, in seconds.
     */
    @Doc("What an action plays.")
    public record Action(
            @Doc(
                            "Its clips: one, several picked from at random by weight, or several"
                                    + " with speeds, blended by how fast the character moves.")
                    List<ClipUse> clips,
            @Doc(
                            "The plan's bone group it plays on, like \"upperBody\", so the legs"
                                    + " keep walking. Left out, it plays on the whole body.")
                    @Optional
                    String mask,
            @Doc("How long it takes to blend in, in seconds.") @Default("0.2") float fade) {
        /** The field for the clips. */
        public static final String FIELD_CLIPS = "clips";

        /** The field for the mask. */
        public static final String FIELD_MASK = "mask";

        /** The field for the fade. */
        public static final String FIELD_FADE = "fade";

        /**
         * Whether its clips are blended by speed rather than picked from.
         *
         * @return True if every clip has a speed and there are several.
         */
        public boolean blendsBySpeed() {
            return clips.size() > 1 && clips.stream().allMatch(clip -> clip.speed() != null);
        }
    }

    /**
     * One clip an action plays.
     *
     * @param clip The clip, as a file and the clip's name in it.
     * @param speed How fast it moves the character, for blending by speed, or null.
     * @param rate How fast it plays, times its own speed.
     * @param loop Whether it repeats, or null for the file's own setting.
     * @param weight How likely it is picked among the action's clips.
     * @param events Timed events it fires.
     */
    @Doc("One clip an action plays.")
    public record ClipUse(
            @Doc(
                            "The clip, as its file and its name in that file, like"
                                    + " \"rpg_human/rpg_male.glb#run\", relative to its"
                                    + " namespace's content folder.")
                    String clip,
            @Doc(
                            "How fast it moves the character, in meters per second, for actions"
                                    + " blended by speed.")
                    @Optional
                    Float speed,
            @Doc("How fast it plays, times its authored speed.") @Default("1") float rate,
            @Doc("Whether it repeats. Left out, the file decides.") @Optional Boolean loop,
            @Doc("How likely it is picked among the action's clips.") @Default("1") float weight,
            @Doc("Timed events it fires, like footsteps and hits.") @Optional List<Event> events) {
        /** The field for the clip. */
        public static final String FIELD_CLIP = "clip";

        /** The field for the speed. */
        public static final String FIELD_SPEED = "speed";

        /** The field for the rate. */
        public static final String FIELD_RATE = "rate";

        /** The field for the weight. */
        public static final String FIELD_WEIGHT = "weight";

        /** The field for the events. */
        public static final String FIELD_EVENTS = "events";

        /**
         * The clip's file.
         *
         * @return The file, relative to the namespace's content folder.
         */
        public String file() {
            int split = clip.lastIndexOf(CLIP_SEPARATOR);
            return split < 0 ? clip : clip.substring(0, split);
        }

        /**
         * The clip's name in its file.
         *
         * @return The name, or empty if there isn't one.
         */
        public String name() {
            int split = clip.lastIndexOf(CLIP_SEPARATOR);
            return split < 0 ? "" : clip.substring(split + 1);
        }
    }

    /**
     * Something that happens at a moment of a clip, which gameplay and effects listen for.
     *
     * @param at When, in seconds from the clip's start.
     * @param event What, like {@code foot.L.down}, {@code hit} or {@code fx:ikala:fx/dust}.
     * @param bone The bone an {@code fx:} event bursts at, or null for the root.
     */
    @Doc("Something that happens at a moment of a clip.")
    public record Event(
            @Doc("When, in seconds from the clip's start.") float at,
            @Doc(
                            "What, like \"foot.L.down\", \"hit\", or \"fx:\" and an effect ID to"
                                    + " burst it.")
                    String event,
            @Doc(
                            "The bone an fx: event bursts its effect at, like \"foot.L\". Left"
                                    + " out, the root.")
                    @Optional
                    String bone) {
        /** The field for the time. */
        public static final String FIELD_AT = "at";

        /** The field for the event. */
        public static final String FIELD_EVENT = "event";

        /** The field for the bone. */
        public static final String FIELD_BONE = "bone";

        /** What starts an event that bursts an effect. */
        public static final String FX_PREFIX = "fx:";

        /**
         * The effect an {@code fx:} event bursts.
         *
         * @return The effect's ID, or null if this isn't an {@code fx:} event.
         * @throws IllegalArgumentException If what follows {@code fx:} isn't an ID.
         */
        public Id effect() {
            return event.startsWith(FX_PREFIX)
                    ? Id.parse(event.substring(FX_PREFIX.length()))
                    : null;
        }
    }

    @Override
    public void check(Checks checks) {
        BodyPlan body = checks.require(BodyPlan.class, plan, JsonPath.at(FIELD_PLAN));
        checkActions(actions, body, checks, JsonPath.at(FIELD_ACTIONS));
        String variantsPath = JsonPath.at(FIELD_VARIANTS);
        variants.forEach(
                (variant, overrides) -> {
                    String path = JsonPath.field(variantsPath, variant);
                    if (body != null && body.variant(variant) == null) {
                        checks.error(path, "The plan has no variant '" + variant + "'");
                    }
                    checkActions(overrides, body, checks, path);
                });
        for (AnimationSet other : checks.all(AnimationSet.class)) {
            if (other.id().equals(id)) {
                break;
            }
            if (!other.plan().equals(plan)) {
                continue;
            }
            for (String action : actions.keySet()) {
                if (other.actions().containsKey(action)) {
                    checks.warning(
                            JsonPath.field(JsonPath.at(FIELD_ACTIONS), action),
                            other.id() + " comes first and also defines it, so this is unused");
                }
            }
        }
    }

    /**
     * Check actions against their plan.
     *
     * @param actions The actions.
     * @param body The plan, or null if it is missing.
     * @param checks Where problems go.
     * @param path Where the actions are.
     */
    static void checkActions(
            @NonNull Map<String, Action> actions,
            BodyPlan body,
            @NonNull Checks checks,
            @NonNull String path) {
        Rig rig =
                body == null
                        ? null
                        : checks.all(Rig.class).stream()
                                .filter(r -> r.id().equals(body.rig()))
                                .findFirst()
                                .orElse(null);
        actions.forEach(
                (name, action) -> {
                    String actionPath = JsonPath.field(path, name);
                    if (!ACTION_ID.matcher(name).matches()) {
                        checks.error(
                                actionPath,
                                "Action IDs are lowercase words joined by dots, like"
                                        + " attack.melee.1");
                    }
                    checkAction(action, body, rig, checks, actionPath);
                });
    }

    /**
     * Check one action.
     *
     * @param action The action.
     * @param body The plan, or null if it is missing.
     * @param rig The plan's rig, or null if it is missing.
     * @param checks Where problems go.
     * @param path Where it is.
     */
    private static void checkAction(
            Action action, BodyPlan body, Rig rig, Checks checks, String path) {
        if (action.clips().isEmpty()) {
            checks.error(JsonPath.field(path, Action.FIELD_CLIPS), "An action needs a clip");
        }
        if (action.mask() != null
                && body != null
                && !body.boneGroups().containsKey(action.mask())) {
            checks.error(
                    JsonPath.field(path, Action.FIELD_MASK),
                    "The plan has no bone group '" + action.mask() + "'");
        }
        if (action.fade() < 0) {
            checks.error(JsonPath.field(path, Action.FIELD_FADE), "A fade can't be negative");
        }
        String clipsPath = JsonPath.field(path, Action.FIELD_CLIPS);
        long speeds = action.clips().stream().filter(clip -> clip.speed() != null).count();
        if (speeds > 0 && speeds < action.clips().size()) {
            checks.warning(
                    clipsPath,
                    "Only some clips have speeds, so they are picked from rather than blended");
        }
        for (int i = 0; i < action.clips().size(); ++i) {
            checkClip(action.clips().get(i), rig, checks, JsonPath.index(clipsPath, i));
        }
    }

    /**
     * Check one clip use.
     *
     * @param use The clip use.
     * @param rig The plan's rig, or null if it is missing.
     * @param checks Where problems go.
     * @param path Where it is.
     */
    private static void checkClip(ClipUse use, Rig rig, Checks checks, String path) {
        if (use.file().isBlank() || use.name().isBlank()) {
            checks.error(
                    JsonPath.field(path, ClipUse.FIELD_CLIP),
                    "A clip is its file, " + CLIP_SEPARATOR + " and its name, like run.glb#run");
        }
        if (use.speed() != null && use.speed() < 0) {
            checks.error(JsonPath.field(path, ClipUse.FIELD_SPEED), "A speed can't be negative");
        }
        if (use.rate() <= 0) {
            checks.error(JsonPath.field(path, ClipUse.FIELD_RATE), "A rate is above 0");
        }
        if (use.weight() <= 0) {
            checks.error(JsonPath.field(path, ClipUse.FIELD_WEIGHT), "A weight is above 0");
        }
        String eventsPath = JsonPath.field(path, ClipUse.FIELD_EVENTS);
        for (int i = 0; i < use.events().size(); ++i) {
            Event event = use.events().get(i);
            String eventPath = JsonPath.index(eventsPath, i);
            if (event.at() < 0) {
                checks.error(
                        JsonPath.field(eventPath, Event.FIELD_AT), "Events can't come before 0");
            }
            if (event.event().isBlank()) {
                checks.error(JsonPath.field(eventPath, Event.FIELD_EVENT), "An event needs a name");
            } else if (event.event().startsWith(Event.FX_PREFIX)) {
                String eventField = JsonPath.field(eventPath, Event.FIELD_EVENT);
                try {
                    checks.require(Fx.class, event.effect(), eventField);
                } catch (IllegalArgumentException e) {
                    checks.error(eventField, "fx: events name an effect ID, like fx:ikala:fx/dust");
                }
            }
            if (event.bone() != null && rig != null && rig.bone(event.bone()) == null) {
                checks.error(
                        JsonPath.field(eventPath, Event.FIELD_BONE),
                        "The rig has no bone named '" + event.bone() + "'");
            }
        }
    }
}
