package com.ikalagaming.factory.auth;

import lombok.NonNull;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.NamedParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

/**
 * Ed25519 keys and signatures through the JDK, including key pairs made from a 32-byte seed, which
 * is how a player's key for each server is derived from their master secret.
 */
public final class Ed25519 {

    /** The JDK's name for the algorithm. */
    public static final String ALGORITHM = "Ed25519";

    /** How long a seed is, in bytes. */
    public static final int SEED_BYTES = 32;

    /** Not made. */
    private Ed25519() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }

    /**
     * Make the key pair for a seed. The same seed always gives the same keys.
     *
     * @param seed 32 bytes.
     * @return The key pair.
     * @throws IllegalArgumentException If the seed isn't 32 bytes.
     */
    public static KeyPair fromSeed(byte @NonNull [] seed) {
        if (seed.length != SEED_BYTES) {
            throw new IllegalArgumentException("An Ed25519 seed is 32 bytes");
        }
        try {
            final KeyPairGenerator generator = KeyPairGenerator.getInstance(ALGORITHM);
            // The generator takes the private key as the next 32 random bytes
            generator.initialize(NamedParameterSpec.ED25519, new SeedRandom(seed));
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Ed25519 isn't available", e);
        }
    }

    /**
     * Make a new random key pair.
     *
     * @return The key pair.
     */
    public static KeyPair generate() {
        final byte[] seed = new byte[SEED_BYTES];
        new SecureRandom().nextBytes(seed);
        try {
            return fromSeed(seed);
        } finally {
            Arrays.fill(seed, (byte) 0);
        }
    }

    /**
     * Sign data.
     *
     * @param key The private key.
     * @param data The data.
     * @return The signature, 64 bytes.
     */
    public static byte[] sign(@NonNull PrivateKey key, byte @NonNull [] data) {
        try {
            final Signature signature = Signature.getInstance(ALGORITHM);
            signature.initSign(key);
            signature.update(data);
            return signature.sign();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not sign", e);
        }
    }

    /**
     * Check a signature.
     *
     * @param key The public key.
     * @param data The data.
     * @param signed The signature.
     * @return True if the key signed the data.
     */
    public static boolean verify(
            @NonNull PublicKey key, byte @NonNull [] data, byte @NonNull [] signed) {
        try {
            final Signature signature = Signature.getInstance(ALGORITHM);
            signature.initVerify(key);
            signature.update(data);
            return signature.verify(signed);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Read a public key in its X.509 encoding, as {@link PublicKey#getEncoded()} gives it.
     *
     * @param encoded The bytes.
     * @return The key.
     * @throws IllegalArgumentException If the bytes aren't an Ed25519 public key.
     */
    public static PublicKey publicKey(byte @NonNull [] encoded) {
        try {
            return KeyFactory.getInstance(ALGORITHM)
                    .generatePublic(new X509EncodedKeySpec(encoded));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Not an Ed25519 public key", e);
        }
    }

    /**
     * Read a private key in its PKCS #8 encoding, as {@link PrivateKey#getEncoded()} gives it.
     *
     * @param encoded The bytes.
     * @return The key.
     * @throws IllegalArgumentException If the bytes aren't an Ed25519 private key.
     */
    public static PrivateKey privateKey(byte @NonNull [] encoded) {
        try {
            return KeyFactory.getInstance(ALGORITHM)
                    .generatePrivate(new PKCS8EncodedKeySpec(encoded));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Not an Ed25519 private key", e);
        }
    }

    /** Hands out a fixed seed as its random bytes, once. */
    private static final class SeedRandom extends SecureRandom {
        private static final long serialVersionUID = 1L;

        /** The seed, cleared once handed out. */
        private final byte[] seed;

        /** Whether the seed has been handed out. */
        private boolean used;

        /**
         * A source for one seed.
         *
         * @param seed The seed; copied.
         */
        SeedRandom(byte[] seed) {
            this.seed = seed.clone();
        }

        @Override
        public void nextBytes(byte[] bytes) {
            if (used || bytes.length != seed.length) {
                throw new IllegalStateException("A seeded key pair asked for unexpected bytes");
            }
            System.arraycopy(seed, 0, bytes, 0, seed.length);
            Arrays.fill(seed, (byte) 0);
            used = true;
        }
    }
}
