package com.ikalagaming.graphics.frontend.gui.component;

/**
 * Something that we can interact with on the UI, which returns a boolean result indicating whether
 * it has been changed. Things like buttons, menus, dropdowns, sliders.
 */
public interface Interactive {
    /**
     * Check to see if the component was interacted with since we last called this method. This
     * should reset the value.
     *
     * <p>The intent is to be able to have the rendering and interaction logic separate.
     *
     * @return Whether the component was interacted with.
     */
    boolean checkResult();
}
