package com.ikalagaming.graphics.gui.callback;

import com.ikalagaming.graphics.gui.data.Context;
import com.ikalagaming.graphics.gui.data.DockNode;
import com.ikalagaming.graphics.gui.data.TabBar;

import lombok.NonNull;

/**
 * Displays the contents of the window menu of a dock node (the one that replaces the collapse
 * button). Stored in {@link Context#dockNodeWindowMenuHandler} so applications can tweak the menu,
 * e.g. to decorate, group, or sort entries.
 */
@FunctionalInterface
public interface DockNodeWindowMenuHandler {
    /**
     * Submit the menu contents. Called between beginPopup() and endPopup().
     *
     * @param context The context.
     * @param node The dock node the menu is for.
     * @param tabBar The tab bar of the node.
     */
    void windowMenu(@NonNull Context context, @NonNull DockNode node, @NonNull TabBar tabBar);
}
