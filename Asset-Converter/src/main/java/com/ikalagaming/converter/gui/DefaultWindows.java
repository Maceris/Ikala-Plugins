package com.ikalagaming.converter.gui;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** An enum specifying the names of components that we define in this plugin. */
@RequiredArgsConstructor
@Getter
public enum DefaultWindows {
    DEBUG("Converter Debug"),
    GRAPHICS_DEBUG("Converter Graphics Debug"),
    IMGUI_DEMO("Converter ImGui Demo"),
    MAIN_MENU("Converter Main Menu"),
    ROOT_WINDOW("Converter Root Window");
    private final String name;
}
