package com.ikalagaming.factory.crafting;

import com.ikalagaming.factory.item.ItemStack;
import com.ikalagaming.factory.kvt.KVT;
import com.ikalagaming.factory.kvt.NodeType;

import lombok.NonNull;

import java.util.Objects;

/** Used to check if an ingredient matches a recipe. */
@FunctionalInterface
public interface ItemMatchCondition {

    /**
     * Check for an exact match. The tags, material, KVT data, everything has to be exactly the
     * same.
     */
    ItemMatchCondition EXACT = (recipe, actual) -> recipe.getItemStack().equals(actual);

    /**
     * Checks the name, then for other properties (tags, material, KVT data) it will check only if
     * present and ignore if missing.
     */
    ItemMatchCondition DEFAULT =
            (recipe, actual) -> {
                var item = recipe.getItemStack().getItem();
                if (!Objects.equals(item.getName(), actual.getItem().getName())) {
                    return false;
                }
                var material = item.getMaterial();
                if (material != null && !Objects.equals(material, actual.getItem().getMaterial())) {
                    return false;
                }
                var tags = item.getTags();
                if (tags != null
                        && !tags.isEmpty()
                        && !actual.getItem().getTags().containsAll(item.getTags())) {
                    return false;
                }
                var kvt = item.getKvt();
                return kvt == null
                        || kvt.getKeys().isEmpty()
                        || allKvtFound(kvt, actual.getItem().getKvt());
            };

    /**
     * Check that all the KVT tags listed in the criteria are listed in the actual KVT data. Extra
     * tags in the data are ignored.
     *
     * @param criteria The KVT tags that must be present.
     * @param tree The tree to check.
     * @return Whether we found all the expected tags and they matched.
     */
    private static boolean allKvtFound(@NonNull KVT criteria, @NonNull KVT tree) {
        for (String key : criteria.getKeys()) {
            var expectedType = criteria.getType(key);
            if (!tree.hasChild(key) || !Objects.equals(expectedType, tree.getType(key))) {
                return false;
            }
            if (expectedType.map(NodeType.NODE::equals).orElse(false)
                    && !allKvtFound(criteria.getNode(key), tree.getNode(key))) {
                return false;
            }
            if (!Objects.equals(criteria.get(key), tree.get(key))) {
                return false;
            }
        }

        return true;
    }

    /** Only check the item name. */
    ItemMatchCondition MATCH_NAME =
            (recipe, actual) ->
                    Objects.equals(
                            recipe.getItemStack().getItem().getName(), actual.getItem().getName());

    /** Only check the material. */
    ItemMatchCondition MATCH_MATERIAL =
            (recipe, actual) ->
                    Objects.equals(
                            recipe.getItemStack().getItem().getMaterial(),
                            actual.getItem().getMaterial());

    /** Check that any of the tags in the condition are on the item. */
    ItemMatchCondition MATCH_ANY_TAGS =
            (recipe, actual) ->
                    recipe.getItemStack().getItem().getTags().stream()
                            .anyMatch(actual.getItem().getTags()::contains);

    /** Checks that all the tags in the condition are on the item. */
    ItemMatchCondition MATCH_ALL_TAGS =
            (recipe, actual) ->
                    actual.getItem()
                            .getTags()
                            .containsAll(recipe.getItemStack().getItem().getTags());

    boolean matches(@NonNull InputItem recipeItem, @NonNull ItemStack actualItem);
}
