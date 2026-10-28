package com.ikalagaming.characters.anim;

import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.definition.AnimStyle;
import com.ikalagaming.characters.definition.AnimationSet;
import com.ikalagaming.characters.registry.CharacterRegistry;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Finds what a character plays for an action. Each place is tried in turn: its styles in priority
 * order (equipment first, then the design's), then its variant's overrides in the plan's animation
 * sets, then the sets themselves, earliest ID first. If none has the action, its parent is tried
 * ({@code attack.melee.2}, then {@code attack.melee}, then {@code attack}), and last of all {@value
 * #IDLE}, so gameplay never gets a hard failure. Immutable.
 *
 * @author Ches Burks
 */
public final class ActionLookup {
    /** The action everything falls back to. */
    public static final String IDLE = "idle";

    /**
     * What an action resolved to.
     *
     * @param requested The action asked for.
     * @param found The action that was found, the same or one it fell back to.
     * @param action What it plays.
     * @param source The set or style it came from, whose namespace its clip files are in.
     */
    public record Resolved(
            @NonNull String requested,
            @NonNull String found,
            @NonNull AnimationSet.Action action,
            @NonNull Id source) {
        /**
         * Whether the action asked for was found itself rather than fallen back from.
         *
         * @return True if it was found.
         */
        public boolean exact() {
            return requested.equals(found);
        }
    }

    /**
     * One place actions come from.
     *
     * @param source The set or style.
     * @param actions Its actions.
     */
    private record Layer(Id source, Map<String, AnimationSet.Action> actions) {}

    /** The places actions come from, first to last. */
    private final List<Layer> layers = new ArrayList<>();

    /**
     * Find the actions for a character.
     *
     * @param registry The loaded definitions.
     * @param plan The character's plan.
     * @param variant The character's variant.
     * @param styles Its styles, the first winning.
     */
    public ActionLookup(
            @NonNull CharacterRegistry registry,
            @NonNull Id plan,
            @NonNull String variant,
            @NonNull List<Id> styles) {
        for (Id styleId : styles) {
            AnimStyle style = registry.get(AnimStyle.class, styleId);
            if (style != null && style.plan().equals(plan)) {
                layers.add(new Layer(style.id(), style.actions()));
            }
        }
        List<AnimationSet> sets =
                registry.all(AnimationSet.class).stream()
                        .filter(set -> set.plan().equals(plan))
                        .toList();
        for (AnimationSet set : sets) {
            Map<String, AnimationSet.Action> overrides = set.variants().get(variant);
            if (overrides != null) {
                layers.add(new Layer(set.id(), overrides));
            }
        }
        for (AnimationSet set : sets) {
            layers.add(new Layer(set.id(), set.actions()));
        }
    }

    /**
     * Find what an action plays.
     *
     * @param action The action, like {@code attack.melee.2}.
     * @return What it resolved to, or null if not even {@value #IDLE} exists.
     */
    public Resolved resolve(@NonNull String action) {
        for (String tried = action; tried != null; tried = parent(tried)) {
            Resolved found = find(action, tried);
            if (found != null) {
                return found;
            }
        }
        return find(action, IDLE);
    }

    /**
     * Find an action exactly.
     *
     * @param requested The action asked for.
     * @param action The action to look for.
     * @return What it resolved to, or null if nothing has it.
     */
    private Resolved find(String requested, String action) {
        for (Layer layer : layers) {
            AnimationSet.Action found = layer.actions().get(action);
            if (found != null) {
                return new Resolved(requested, action, found, layer.source());
            }
        }
        return null;
    }

    /**
     * The action an action falls back to.
     *
     * @param action The action.
     * @return Its parent, like {@code attack.melee} for {@code attack.melee.2}, or null at the top.
     */
    public static String parent(@NonNull String action) {
        int dot = action.lastIndexOf('.');
        return dot < 0 ? null : action.substring(0, dot);
    }

    /**
     * Every action defined for the character, from any place.
     *
     * @return The actions, sorted.
     */
    public List<String> actions() {
        TreeSet<String> all = new TreeSet<>();
        layers.forEach(layer -> all.addAll(layer.actions().keySet()));
        return List.copyOf(all);
    }

    /**
     * Whether nothing defines any action, so the character can only stand at rest.
     *
     * @return True if there are no actions.
     */
    public boolean isEmpty() {
        return layers.stream().allMatch(layer -> layer.actions().isEmpty());
    }
}
