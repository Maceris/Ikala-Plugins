package com.ikalagaming.factory.sim;

/**
 * What a player did in one command frame: which buttons were held, and where they looked. This is
 * all the server takes from a client; everything else comes from the simulation.
 *
 * @param buttons The buttons held, from the constants here.
 * @param yaw The turn about the vertical axis, a 16-bit angle (see {@link Angle}).
 * @param pitch The tilt up or down, a 16-bit angle from -{@link Angle#QUARTER} to {@link
 *     Angle#QUARTER}.
 */
public record PlayerInput(int buttons, int yaw, int pitch) {

    /** Move forward. */
    public static final int FORWARD = 1;

    /** Move back. */
    public static final int BACK = 1 << 1;

    /** Move left. */
    public static final int LEFT = 1 << 2;

    /** Move right. */
    public static final int RIGHT = 1 << 3;

    /** Jump, or rise when flying. */
    public static final int UP = 1 << 4;

    /** Sneak, or sink when flying. */
    public static final int DOWN = 1 << 5;

    /** Move faster. */
    public static final int SPRINT = 1 << 6;

    /** Every button there is. */
    public static final int ALL_BUTTONS = (1 << 7) - 1;

    /** Nothing held, looking straight ahead along yaw 0. */
    public static final PlayerInput NONE = new PlayerInput(0, 0, 0);

    /**
     * Whether a button is held.
     *
     * @param button The button.
     * @return True if it is.
     */
    public boolean held(int button) {
        return (buttons & button) != 0;
    }

    /**
     * This input with anything out of range brought in: unknown buttons dropped, the yaw wrapped
     * and the pitch clamped. The server does this to everything a client sends.
     *
     * @return An input in range, this one if it already was.
     */
    public PlayerInput sanitized() {
        final int cleanButtons = buttons & ALL_BUTTONS;
        final int cleanYaw = Angle.wrap(yaw);
        final int cleanPitch = Math.clamp(pitch, -Angle.QUARTER, Angle.QUARTER);
        if (cleanButtons == buttons && cleanYaw == yaw && cleanPitch == pitch) {
            return this;
        }
        return new PlayerInput(cleanButtons, cleanYaw, cleanPitch);
    }
}
