package com.ikalagaming.graphics.frontend.gui.data;

import com.ikalagaming.graphics.frontend.gui.util.Rect;

import org.joml.Vector2f;

public class BoxSelectState {
    private int ID;
    private boolean active;
    private boolean starting;
    private boolean clearRequested;
    private int keyChord;
    private Vector2f relativeStartPosition;
    private Vector2f relativeEndPosition;
    private Vector2f scrollingAccumulator;
    private Window window;

    private boolean unclipMode;
    private Rect unclipRect;
    private Rect selectionRectPrevious;
    private Rect selectionRectCurrent;
}
