package com.ikalagaming.factory.auth;

import lombok.NonNull;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * What each side signs when a player signs in, so a signature for one purpose, server or connection
 * can't be used for another. See the Accounts and Identity design doc.
 *
 * <ol>
 *   <li>The client sends a random nonce in {@code Hello}.
 *   <li>The server answers with its id, its host key, its own nonce, and a signature by the host
 *       key over {@link #host}: that proves it holds the host key, for this connection.
 *   <li>The client signs {@link #signIn} with its key for that server, which covers the host key
 *       and both nonces, so a fake server can't pass a real server's challenge on.
 * </ol>
 */
public final class SignInProof {

    /** How long each nonce is, in bytes. */
    public static final int NONCE_BYTES = 32;

    /** How long a server id is, in bytes. */
    public static final int SERVER_ID_BYTES = 32;

    /** Marks a server's proof. */
    private static final String HOST_CONTEXT = "lotomation host proof v1";

    /** Marks a player's proof. */
    private static final String SIGN_IN_CONTEXT = "lotomation sign in v1";

    /** Not made. */
    private SignInProof() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }

    /**
     * What a server signs with its host key.
     *
     * @param serverId The server's id.
     * @param hostKey The host public key, encoded.
     * @param clientNonce The client's nonce.
     * @param serverNonce The server's nonce.
     * @return The bytes to sign.
     */
    public static byte[] host(
            byte @NonNull [] serverId,
            byte @NonNull [] hostKey,
            byte @NonNull [] clientNonce,
            byte @NonNull [] serverNonce) {
        return join(HOST_CONTEXT, serverId, hostKey, clientNonce, serverNonce);
    }

    /**
     * What a player signs with their key for the server.
     *
     * @param serverId The server's id.
     * @param hostKey The host public key, encoded.
     * @param clientNonce The client's nonce.
     * @param serverNonce The server's nonce.
     * @param username The name they sign in as.
     * @param playerKey Their public key for this server, encoded.
     * @return The bytes to sign.
     */
    public static byte[] signIn(
            byte @NonNull [] serverId,
            byte @NonNull [] hostKey,
            byte @NonNull [] clientNonce,
            byte @NonNull [] serverNonce,
            @NonNull String username,
            byte @NonNull [] playerKey) {
        return join(
                SIGN_IN_CONTEXT,
                serverId,
                hostKey,
                clientNonce,
                serverNonce,
                username.getBytes(StandardCharsets.UTF_8),
                playerKey);
    }

    /**
     * Join a context and fields, each field after its length, so no two lists of fields give the
     * same bytes.
     *
     * @param context What the bytes are for.
     * @param fields The fields.
     * @return The bytes.
     */
    private static byte[] join(@NonNull String context, byte[]... fields) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final byte[] marker = context.getBytes(StandardCharsets.UTF_8);
        writeField(out, marker);
        for (byte[] field : fields) {
            writeField(out, field);
        }
        return out.toByteArray();
    }

    /**
     * Write a field after its length.
     *
     * @param out Where to write.
     * @param field The field.
     */
    private static void writeField(@NonNull ByteArrayOutputStream out, byte @NonNull [] field) {
        out.write(field.length >>> 24);
        out.write(field.length >>> 16);
        out.write(field.length >>> 8);
        out.write(field.length);
        out.writeBytes(field);
    }
}
