package com.ikalagaming.factory.networking.request.serverbound;

import com.ikalagaming.factory.networking.base.Request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * Where a joined player is now: the position of their feet, and where they look.
 *
 * <p>Fields have setters and there is a no-argument constructor, so remote connections can
 * serialize it. A local connection passes the object itself, so nothing may change it after it is
 * sent.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlayerMove implements Request {
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

    @Override
    public void handleUsing(@NonNull ServerBoundRequestHandler handler) {
        handler.handle(this);
    }
}
