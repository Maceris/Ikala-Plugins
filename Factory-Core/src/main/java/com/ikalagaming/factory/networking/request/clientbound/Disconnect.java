package com.ikalagaming.factory.networking.request.clientbound;

import com.ikalagaming.factory.networking.base.Request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * The server turns a client away or ends its session, with the reason to show the player. The
 * connection closes after it.
 *
 * <p>Fields have setters and there is a no-argument constructor, so remote connections can
 * serialize it. A local connection passes the object itself, so nothing may change it after it is
 * sent. Keys, nonces and signatures are Base64 text.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Disconnect implements Request {
    /** Why, to show the player. */
    private String reason;

    @Override
    public void handleUsing(@NonNull ClientBoundRequestHandler handler) {
        handler.handle(this);
    }
}
