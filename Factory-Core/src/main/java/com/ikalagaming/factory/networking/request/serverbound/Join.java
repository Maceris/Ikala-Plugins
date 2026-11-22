package com.ikalagaming.factory.networking.request.serverbound;

import com.ikalagaming.factory.networking.base.Request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * A client asks to join the world. Single player joins as the save's owner.
 *
 * <p>Fields have setters and there is a no-argument constructor, so remote connections can
 * serialize it. A local connection passes the object itself, so nothing may change it after it is
 * sent.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Join implements Request {
    /** The name the player joins as. */
    private String name;

    @Override
    public void handleUsing(@NonNull ServerBoundRequestHandler handler) {
        handler.handle(this);
    }
}
