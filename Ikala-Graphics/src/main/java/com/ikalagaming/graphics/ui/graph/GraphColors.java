package com.ikalagaming.graphics.ui.graph;

import com.ikalagaming.graphics.gui.util.Color;

/**
 * The colors graphs fall back to when the theme doesn't set one, shared by links and nodes so they
 * match each other.
 */
public final class GraphColors {
    /** Links, and the borders of nodes: a mid grey. */
    public static final int LINE = Color.rgb(140, 140, 140);

    /** Lit up links and badges: a warm yellow that stands out against the grey. */
    public static final int HIGHLIGHT = Color.rgb(255, 200, 60);

    /** The inside of a node: a dark grey a little bluer than black. */
    public static final int NODE_BACKGROUND = Color.rgb(43, 43, 48);

    /** Text on a node. */
    public static final int TEXT = Color.WHITE;

    /** Text on a badge: near black, so it reads on the highlight color. */
    public static final int BADGE_TEXT = Color.rgb(20, 20, 20);

    /** Constants only. */
    private GraphColors() {}
}
