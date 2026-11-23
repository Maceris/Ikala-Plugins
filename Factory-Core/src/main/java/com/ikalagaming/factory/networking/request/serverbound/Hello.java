package com.ikalagaming.factory.networking.request.serverbound;

import com.ikalagaming.factory.networking.base.Request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * The first thing a client sends: a random nonce the server's proof must cover, so a proof can't be
 * replayed on another connection. See {@code SignInProof}.
 *
 * <p>Fields have setters and there is a no-argument constructor, so remote connections can
 * serialize it. A local connection passes the object itself, so nothing may change it after it is
 * sent. Keys, nonces and signatures are Base64 text.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Hello implements Request {
    /** The client's random nonce. */
    private String clientNonce;

    @Override
    public void handleUsing(@NonNull ServerBoundRequestHandler handler) {
        handler.handle(this);
    }
}
