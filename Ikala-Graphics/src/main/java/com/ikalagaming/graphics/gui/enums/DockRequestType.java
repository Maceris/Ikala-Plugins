package com.ikalagaming.graphics.gui.enums;

/** The types of requests that can be queued for the docking system. */
public enum DockRequestType {
    NONE,
    DOCK,
    UNDOCK,
    /** Split is the same as dock, but without a payload. */
    SPLIT
}
