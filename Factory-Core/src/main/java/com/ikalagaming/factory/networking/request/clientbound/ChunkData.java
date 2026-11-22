package com.ikalagaming.factory.networking.request.clientbound;

import com.ikalagaming.factory.kvt.Node;
import com.ikalagaming.factory.networking.base.Request;
import com.ikalagaming.factory.world.ChunkCodec;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * A chunk near the player, in the save format's chunk layout (see {@link ChunkCodec}).
 *
 * <p>Fields have setters and there is a no-argument constructor, so remote connections can
 * serialize it. A local connection passes the object itself, so nothing may change it after it is
 * sent.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChunkData implements Request {
    /** The chunk's x, in chunks. */
    private int x;

    /** The chunk's y, in chunks. */
    private int y;

    /** The chunk's z, in chunks. */
    private int z;

    /** The chunk, from {@link ChunkCodec#encode}. */
    private Node chunk;

    @Override
    public void handleUsing(@NonNull ClientBoundRequestHandler handler) {
        handler.handle(this);
    }
}
