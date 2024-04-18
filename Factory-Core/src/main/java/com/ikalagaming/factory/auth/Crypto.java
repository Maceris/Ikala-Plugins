package com.ikalagaming.factory.auth;

import com.ikalagaming.random.RandomGen;

import java.security.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Cryptography related utilities. */
public class Crypto {

    /** The algorithm used to generate public/private key pairs. */
    private static final String KEY_ALGORITHM = "RSA";

    /** The length of the key in bits. */
    private static final int KEY_SIZE = 2048;

    /** Used to ensure unique challenge payloads within the lifetime of an application. */
    private static final AtomicInteger challengeCounter = new AtomicInteger();

    /**
     * Generate a secure public+private key pair for use in authentication.
     *
     * @return The key pair.
     * @throws NoSuchAlgorithmException If we can't generate keys.
     */
    public static KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance(KEY_ALGORITHM);
        keyGen.initialize(KEY_SIZE, RandomGen.getSecureRandom());
        return keyGen.generateKeyPair();
    }

    /**
     * Return a 64-bit (8 byte) value to be sent as a challenge for authentication. These are
     * guaranteed to be unique within one run of the application, but not universally.
     *
     * @return The 8 byte array value.
     */
    public static byte[] getChallenge() {
        final int random = RandomGen.getFastRandom().nextInt();
        final int counter = challengeCounter.getAndIncrement();
        return new byte[] {
            (byte) (random >>> 24),
            (byte) (random >>> 16),
            (byte) (random >>> 8),
            (byte) random,
            (byte) (counter >>> 24),
            (byte) (counter >>> 16),
            (byte) (counter >>> 8),
            (byte) counter
        };
    }

    /** Private constructor so that this class is not instantiated. */
    private Crypto() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
