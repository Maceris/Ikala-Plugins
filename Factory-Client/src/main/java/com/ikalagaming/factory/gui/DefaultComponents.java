package com.ikalagaming.factory.gui;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** An enum specifying the names of components that we define in this plugin. */
@RequiredArgsConstructor
@Getter
public enum DefaultComponents {
    ROOT_WINDOW("Root Window"),
    BIOME_DEBUG("Biome Debug"),
    MAIN_MENU("Main Menu");
    private final String name;
}
