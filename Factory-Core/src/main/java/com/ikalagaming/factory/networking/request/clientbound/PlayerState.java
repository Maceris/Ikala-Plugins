package com.ikalagaming.factory.networking.request.clientbound;

import com.ikalagaming.factory.networking.base.Request;
import com.ikalagaming.factory.sim.MoveMode;
import com.ikalagaming.factory.sim.Movement;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * The server's answer for one command frame: the player's simulated state after it, and how the
 * player's inputs are arriving. The client checks its prediction for that frame against it.
 *
 * <p>Fields have setters and there is a no-argument constructor, so remote connections can
 * serialize it. A local connection passes the object itself, so nothing may change it after it is
 * sent.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlayerState implements Request {

    /** The frame this is the state after. */
    private long frame;

    /** The movement mode's ordinal. */
    private int mode;

    /** The feet's x, in fixed-point units. */
    private long x;

    /** The feet's y, in units. */
    private long y;

    /** The feet's z, in units. */
    private long z;

    /** The speed along x, in units per frame. */
    private long vx;

    /** The speed along y, in units per frame. */
    private long vy;

    /** The speed along z, in units per frame. */
    private long vz;

    /** The yaw, a 16-bit angle. */
    private int yaw;

    /** The pitch, a 16-bit angle. */
    private int pitch;

    /** Whether the player stands on something. */
    private boolean onGround;

    /** How many of the player's inputs for later frames the server holds. */
    private int buffered;

    /** How many frames so far had no input from the player when they ran. */
    private long missed;

    /**
     * A state.
     *
     * @param frame The frame this is the state after.
     * @param movement The player's movement after it.
     * @param buffered How many inputs for later frames the server holds.
     * @param missed How many frames so far had no input when they ran.
     * @return The message.
     */
    public static PlayerState of(
            long frame, @NonNull Movement movement, int buffered, long missed) {
        return new PlayerState(
                frame,
                movement.mode().ordinal(),
                movement.x(),
                movement.y(),
                movement.z(),
                movement.vx(),
                movement.vy(),
                movement.vz(),
                movement.yaw(),
                movement.pitch(),
                movement.onGround(),
                buffered,
                missed);
    }

    /**
     * The player's movement after the frame.
     *
     * @return The movement.
     * @throws IllegalArgumentException If the mode is not one this knows.
     */
    public Movement movement() {
        final MoveMode[] modes = MoveMode.values();
        if (mode < 0 || mode >= modes.length) {
            throw new IllegalArgumentException("Unknown movement mode " + mode);
        }
        return new Movement(modes[mode], x, y, z, vx, vy, vz, yaw, pitch, onGround);
    }

    @Override
    public void handleUsing(@NonNull ClientBoundRequestHandler handler) {
        handler.handle(this);
    }
}
