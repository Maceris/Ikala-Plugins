package com.ikalagaming.factory.networking.request.clientbound;

import com.ikalagaming.factory.networking.base.Request;
import com.ikalagaming.factory.world.WorldTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * The world's clock (see {@link WorldTime}), sent when a player joins and every few seconds after,
 * so the client's sky stays in step. Between updates the client advances it on its own.
 *
 * <p>Fields have setters and there is a no-argument constructor, so remote connections can
 * serialize it. A local connection passes the object itself, so nothing may change it after it is
 * sent.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TimeUpdate implements Request {
    /** The time, in ticks. */
    private long ticks;

    /** How many ticks pass each second while the world runs. */
    private int ticksPerSecond;

    @Override
    public void handleUsing(@NonNull ClientBoundRequestHandler handler) {
        handler.handle(this);
    }
}
