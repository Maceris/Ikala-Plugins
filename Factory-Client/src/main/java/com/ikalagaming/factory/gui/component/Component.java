package com.ikalagaming.factory.gui.component;

import com.ikalagaming.factory.gui.SizeConstants;
import lombok.NonNull;

/** Something that can be rendered. */
public interface Component {
    /**
     * Draw a component with the specified width and height.
     *
     * @param size The size to use for the component.
     */
    void draw(final @NonNull SizeConstants size);
}
