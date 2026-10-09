package com.ikalagaming.graphics.ui;

import lombok.NonNull;

/**
 * A container that lays its children out in a line, with a gap between them. Along the line,
 * leftover space goes to children with {@link Sizing.Grow} sizing, and {@link Justify} places what
 * remains. Across the line, {@link Align} places each child.
 *
 * @param <S> The container's own type, so chained setters keep it.
 * @see Row
 * @see Column
 */
public abstract class Flex<S extends Flex<S>> extends Container<S> {
    /** The axis children are laid out along. */
    final Axis mainAxis;

    /** Space between neighboring children. */
    Length gap = Length.ZERO;

    /** Where children sit across the line. */
    Align align = Align.START;

    /** How leftover space along the line is used. */
    Justify justify = Justify.START;

    /**
     * Create a line container.
     *
     * @param id The ID, which must be unique among its siblings.
     * @param mainAxis The axis children are laid out along.
     */
    Flex(@NonNull String id, @NonNull Axis mainAxis) {
        super(id);
        this.mainAxis = mainAxis;
    }

    /**
     * Set the space between neighboring children.
     *
     * @param length The gap.
     * @return This container.
     */
    public S gap(@NonNull Length length) {
        gap = length;
        markDirty();
        return self();
    }

    /**
     * Set the space between neighboring children.
     *
     * @param units The gap in UI units.
     * @return This container.
     */
    public S gap(float units) {
        return gap(Length.u(units));
    }

    /**
     * Set where children sit across the line.
     *
     * @param newAlign The alignment.
     * @return This container.
     */
    public S align(@NonNull Align newAlign) {
        align = newAlign;
        markDirty();
        return self();
    }

    /**
     * Set how leftover space along the line is used.
     *
     * @param newJustify The justification.
     * @return This container.
     */
    public S justify(@NonNull Justify newJustify) {
        justify = newJustify;
        markDirty();
        return self();
    }
}
