package com.ikalagaming.factory.networking.request.clientbound;

import com.ikalagaming.factory.networking.base.Request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * The server accepted a join: who the player is, where they are, and how far around them the server
 * sends chunks.
 *
 * <p>Fields have setters and there is a no-argument constructor, so remote connections can
 * serialize it. A local connection passes the object itself, so nothing may change it after it is
 * sent.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Joined implements Request {
    /** The player's id. */
    private String playerId;

    /** The feet's x, in blocks. */
    private double x;

    /** The feet's y, in blocks. */
    private double y;

    /** The feet's z, in blocks. */
    private double z;

    /** Turn about the vertical axis, in radians. */
    private float yaw;

    /** Tilt up or down, in radians. */
    private float pitch;

    /** How many chunks out from the player's chunk the server sends, sideways. */
    private int viewRadius;

    /** How many chunks up and down from the player's chunk the server sends. */
    private int verticalRadius;

    @Override
    public void handleUsing(@NonNull ClientBoundRequestHandler handler) {
        handler.handle(this);
    }
}
