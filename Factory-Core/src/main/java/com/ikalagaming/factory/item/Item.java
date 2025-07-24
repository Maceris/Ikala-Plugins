package com.ikalagaming.factory.item;

import com.ikalagaming.factory.kvt.KVT;
import com.ikalagaming.factory.kvt.TreeStringSerialization;

import lombok.*;

import java.util.*;

/**
 * Represents an item in the game.
 *
 * @author Ches Burks
 */
@Getter
@Setter
@EqualsAndHashCode
@Builder(toBuilder = true)
public class Item {

    public static boolean isSameType(Item a, Item b) {
        if (a == null || b == null) {
            return a == b;
        }
        if (!a.getName().equals(b.getName())) {
            return false;
        }
        if (!Objects.equals(a.getMaterial(), b.getMaterial())) {
            return false;
        }
        // TODO(ches) tags, KVT
        return true;
    }

    /** The fully qualified name of the item. */
    private final @NonNull String name;

    /**
     * The material the item is primarily made out of. May be null if that is not a valid concept or
     * irrelevant for an item.
     */
    @Builder.Default private String material = null;

    /** Tags associated with the item. This can be modified if desired. */
    private final Set<String> tags;

    /**
     * Data associated with the item. Null by default, but can contain an arbitrary amount of
     * information.
     */
    @Builder.Default private KVT kvt = null;

    /**
     * Construct an item with just a name and nothing else.
     *
     * @param name The name of the item. Should follow naming standards for fully qualified item
     *     names.
     */
    public Item(@NonNull String name) {
        this(name, null, null, null);
    }

    /**
     * Construct a new item.
     *
     * @param name The name of the item. Should follow naming standards for fully qualified item
     *     names.
     * @param material The material the item is primarily made out of. May be null.
     * @param tags Tags to add to the item. These are copied to an internal ordered set, so the
     *     collection not need to be modifiable.
     * @param kvt The data for the item, which may be null.
     */
    public Item(@NonNull String name, String material, Collection<String> tags, KVT kvt) {
        this.name = name;
        this.material = material;
        this.tags = new TreeSet<>();
        if (tags != null && !tags.isEmpty()) {
            this.tags.addAll(tags);
        }
        this.kvt = kvt;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder("Item[name=");
        result.append(name);
        if (material != null) {
            result.append(", material=");
            result.append(material);
        }
        if (tags != null) {
            result.append(", tags=");
            result.append(Arrays.toString(tags.toArray()));
        }
        if (kvt != null) {
            result.append(", kvt=");
            result.append(TreeStringSerialization.toString(kvt));
        }
        result.append(']');
        return result.toString();
    }
}
