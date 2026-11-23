package com.ikalagaming.factory.networking.request.clientbound;

import com.ikalagaming.factory.networking.base.Request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * The server's answer to {@code Hello}: who it is, and proof that it holds its host key for this
 * connection, a signature over {@code SignInProof.host}.
 *
 * <p>Fields have setters and there is a no-argument constructor, so remote connections can
 * serialize it. A local connection passes the object itself, so nothing may change it after it is
 * sent. Keys, nonces and signatures are Base64 text.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ServerHello implements Request {
    /** The server's id, which the player's key for it is derived from. */
    private String serverId;

    /** The host public key, X.509 encoded. */
    private String hostKey;

    /** The server's random nonce, which the client's proof must cover. */
    private String serverNonce;

    /** The host key's signature over the host proof. */
    private String signature;

    @Override
    public void handleUsing(@NonNull ClientBoundRequestHandler handler) {
        handler.handle(this);
    }
}
