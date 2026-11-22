package com.ikalagaming.factory.networking.request.clientbound;

import com.ikalagaming.factory.networking.base.Request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * A chunk the client can drop, because the player moved away from it.
 *
 * <p>Fields have setters and there is a no-argument constructor, so remote connections can
 * serialize it. A local connection passes the object itself, so nothing may change it after it is
 * sent.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChunkUnload implements Request {
    /** The chunk's x, in chunks. */
    private int x;

    /** The chunk's y, in chunks. */
    private int y;

    /** The chunk's z, in chunks. */
    private int z;

    @Override
    public void handleUsing(@NonNull ClientBoundRequestHandler handler) {
        handler.handle(this);
    }
}
