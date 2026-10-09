package com.ikalagaming.graphics.ui;

import lombok.NonNull;

/** Lays its children out top to bottom. */
public class Column extends Flex<Column> {

    /**
     * Create a column.
     *
     * @param id The ID, which must be unique among its siblings.
     */
    public Column(@NonNull String id) {
        super(id, Axis.Y);
    }
}
