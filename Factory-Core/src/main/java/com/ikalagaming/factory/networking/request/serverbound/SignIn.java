package com.ikalagaming.factory.networking.request.serverbound;

import com.ikalagaming.factory.networking.base.Request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * A client signs in, or registers on its first visit, with its key for this server and a signature
 * over {@code SignInProof.signIn}. It is sent after checking the server's {@code ServerHello}.
 *
 * <p>Fields have setters and there is a no-argument constructor, so remote connections can
 * serialize it. A local connection passes the object itself, so nothing may change it after it is
 * sent. Keys, nonces and signatures are Base64 text.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SignIn implements Request {
    /** The name to sign in as. */
    private String username;

    /** The player's public key for this server, X.509 encoded. */
    private String publicKey;

    /** The signature over the sign-in proof. */
    private String signature;

    @Override
    public void handleUsing(@NonNull ServerBoundRequestHandler handler) {
        handler.handle(this);
    }
}
