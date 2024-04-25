package com.ikalagaming.factory.gui;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** An enum specifying the names of components that we define in this plugin. */
@RequiredArgsConstructor
@Getter
public enum DefaultWindows {
    BIOME_DEBUG("Biome Debug"),
    DEBUG("Debug"),
    MAIN_MENU("Main Menu"),
    ROOT_WINDOW("Root Window"),
    SINGLE_PLAYER("Single Player");
    private final String name;
}
