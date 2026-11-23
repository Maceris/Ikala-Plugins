package com.ikalagaming.factory.auth;

import lombok.NonNull;

import java.security.GeneralSecurityException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** HKDF with HMAC-SHA256 (RFC 5869), for deriving keys from a secret. */
public final class Hkdf {

    /** The JDK's name for the MAC. */
    private static final String MAC = "HmacSHA256";

    /** How long one block of output is, in bytes. */
    private static final int HASH_BYTES = 32;

    /** Not made. */
    private Hkdf() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }

    /**
     * Derive bytes from a secret.
     *
     * @param secret The input key material.
     * @param salt The salt; empty means a block of zeros.
     * @param info What the output is for, so different uses get unrelated output.
     * @param length How many bytes to make, at most 255 blocks.
     * @return The output.
     */
    public static byte[] derive(
            byte @NonNull [] secret, byte @NonNull [] salt, byte @NonNull [] info, int length) {
        if (length <= 0 || length > 255 * HASH_BYTES) {
            throw new IllegalArgumentException("HKDF can't make " + length + " bytes");
        }
        try {
            final Mac extract = Mac.getInstance(MAC);
            extract.init(new SecretKeySpec(salt.length == 0 ? new byte[HASH_BYTES] : salt, MAC));
            final byte[] pseudoRandomKey = extract.doFinal(secret);
            final Mac expand = Mac.getInstance(MAC);
            expand.init(new SecretKeySpec(pseudoRandomKey, MAC));
            final byte[] output = new byte[length];
            byte[] block = new byte[0];
            int filled = 0;
            for (int counter = 1; filled < length; ++counter) {
                expand.update(block);
                expand.update(info);
                expand.update((byte) counter);
                block = expand.doFinal();
                final int take = Math.min(block.length, length - filled);
                System.arraycopy(block, 0, output, filled, take);
                filled += take;
            }
            return output;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 isn't available", e);
        }
    }
}
