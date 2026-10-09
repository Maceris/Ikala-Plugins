package com.ikalagaming.graphics.ui;

import lombok.NonNull;

/** Lays its children out left to right. */
public class Row extends Flex<Row> {

    /**
     * Create a row.
     *
     * @param id The ID, which must be unique among its siblings.
     */
    public Row(@NonNull String id) {
        super(id, Axis.X);
    }

    @Override
    public String styleType() {
        return "row";
    }
}
