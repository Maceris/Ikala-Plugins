package com.ikalagaming.factory.gui;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** An enum specifying the names of components that we define in this plugin. */
@RequiredArgsConstructor
@Getter
public enum DefaultWindows {
    DEBUG("Debug"),
    NETWORK("Network"),
    ROOT_WINDOW("Root Window");
    private final String name;
}
