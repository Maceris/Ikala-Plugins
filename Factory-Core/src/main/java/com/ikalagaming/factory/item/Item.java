package com.ikalagaming.factory.item;

import com.ikalagaming.factory.kvt.KVT;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

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

    /** The fully qualified name of the item. */
    private final @NonNull String name;

    /**
     * The material the item is primarily made out of. May be null if that is not a valid concept or
     * irrelevant for an item.
     */
    @Builder.Default private String material = null;

    /** Tags associated with the item. This can be modified if desired. */
    private final List<String> tags;

    /**
     * Data associated with the item. Null by default, but can contain an arbitrary amount of
     * information.
     */
    @Builder.Default private KVT kvt = null;

    /**
     * Construct a new item.
     *
     * @param name The name of the item. Should follow naming standards for fully qualified item
     *     names.
     * @param material The material the item is primarily made out of. May be null.
     * @param tags Tags to add to the item. A copy of this list is made, so it does not need to be
     *     modifiable.
     * @param kvt The data for the item, which may be null.
     */
    public Item(@NonNull String name, String material, List<String> tags, KVT kvt) {
        this.name = name;
        this.material = material;
        this.tags = new ArrayList<>();
        if (tags != null && !tags.isEmpty()) {
            this.tags.addAll(tags);
        }
        this.kvt = kvt;
    }
}
