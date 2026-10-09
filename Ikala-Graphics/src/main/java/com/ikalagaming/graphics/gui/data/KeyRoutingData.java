package com.ikalagaming.graphics.gui.data;

import com.ikalagaming.graphics.gui.flags.KeyModFlags;

/** Routing for a key chord, used by shortcut() to decide who receives a shortcut. */
public class KeyRoutingData {
    /** Accept a key regardless of the owner. */
    public static final int KEY_OWNER_ANY = 0;

    /** Require that a key has no owner. */
    public static final int KEY_OWNER_NO_OWNER = -1;

    /**
     * The modifiers of the chord.
     *
     * @see KeyModFlags
     */
    public int mods;

    /** The score of the current route. */
    public int routingCurrScore;

    /** The best score submitted for the next frame. */
    public int routingNextScore;

    /** The owner of the route for the current frame. */
    public int routingCurr;

    /** The owner of the best route submitted for the next frame. */
    public int routingNext;

    /**
     * Create routing data for a set of modifiers.
     *
     * @param mods The modifiers.
     */
    public KeyRoutingData(int mods) {
        this.mods = mods;
        routingCurrScore = routingNextScore = 0;
        routingCurr = routingNext = KEY_OWNER_NO_OWNER;
    }
}
