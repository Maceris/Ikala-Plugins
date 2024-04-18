package com.ikalagaming.factory.auth;

import com.ikalagaming.factory.FactoryPlugin;
import com.ikalagaming.random.RandomGen;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/** Cryptography related utilities. */
@Slf4j
public class Crypto {

    /** The algorithm used to generate public/private key pairs. */
    public static final String KEY_ALGORITHM = "RSA";

    /** The length of the key in bits. */
    public static final int KEY_SIZE = 2048;

    /** Used to ensure unique challenge payloads within the lifetime of an application. */
    private static final AtomicInteger challengeCounter = new AtomicInteger();

    /**
     * The header we use before the private key in our key file. Partially there to fit the PEM
     * format, but mostly to assist with parsing in general.
     */
    private static final String RSA_PRIVATE_HEADER = "-----BEGIN RSA PRIVATE KEY-----";

    /**
     * The footer we use after the private key in our key file. Partially there to fit the PEM
     * format, but mostly to assist with parsing in general.
     */
    private static final String RSA_PRIVATE_FOOTER = "-----END RSA PRIVATE KEY-----";

    /**
     * The header we use before the public key in our key file. Partially there to fit the PEM
     * format, but mostly to assist with parsing in general.
     */
    private static final String RSA_PUBLIC_HEADER = "-----BEGIN RSA PUBLIC KEY-----";

    /**
     * The footer we use after the public key in our key file. Partially there to fit the PEM
     * format, but mostly to assist with parsing in general.
     */
    private static final String RSA_PUBLIC_FOOTER = "-----END RSA PUBLIC KEY-----";

    /** The number of base64 characters to place on a single line within the key blocks. */
    private static final int BASE64_LINE_WIDTH = 64;

    /**
     * Take a base64 encoded value, and split it every {@link #BASE64_LINE_WIDTH} characters into
     * separate strings. The last string may be shorter than the rest.
     *
     * @param key The key to split up into multiple sections.
     * @return All sections of the split up key.
     */
    private static List<String> chopUpKey(@NonNull String key) {
        List<String> results = new ArrayList<>();
        for (int start = 0; start < key.length(); start += BASE64_LINE_WIDTH) {
            final int end = Math.min(start + BASE64_LINE_WIDTH, key.length());
            results.add(key.substring(start, end));
        }
        return results;
    }

    /**
     * Generate a secure public+private key pair for use in authentication.
     *
     * @return The key pair.
     */
    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance(KEY_ALGORITHM);
            keyGen.initialize(KEY_SIZE, RandomGen.getSecureRandom());
            return keyGen.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            log.warn(
                    SafeResourceLoader.getString(
                            "KEY_PAIR_CREATION_FAILED", FactoryPlugin.getResourceBundle()),
                    e);
            throw new RuntimeException(e);
        }
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

    /**
     * Read a private+public key pair from file in the specific format that is written by {@link
     * #writeKeysToFile(KeyPair, File)}. If there is a problem or the file is not found, an empty
     * optional is returned.
     *
     * @param file The file we want to read from.
     * @return An optional containing the public+private key pair, or an empty optional.
     */
    public static Optional<KeyPair> readKeysFromFile(@NonNull File file) {

        if (!file.exists() || !file.canRead()) {
            log.debug(
                    SafeResourceLoader.getString(
                            "KEY_FILE_MISSING", FactoryPlugin.getResourceBundle()));
            return Optional.empty();
        }

        try (var fileReader = new FileReader(file);
                var reader = new BufferedReader(fileReader)) {
            var decoder = Base64.getDecoder();
            var keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
            if (!RSA_PRIVATE_HEADER.equals(reader.readLine())) {
                log.warn(
                        SafeResourceLoader.getString(
                                "KEY_FILE_UNEXPECTED_FORMAT", FactoryPlugin.getResourceBundle()));
                return Optional.empty();
            }
            String line;
            List<String> keyParts = new ArrayList<>();
            while ((line = reader.readLine()) != null) {
                if (RSA_PRIVATE_FOOTER.equals(line)) {
                    break;
                }
                keyParts.add(line);
            }
            byte[] privateBytes = decoder.decode(String.join("", keyParts));
            PrivateKey priv = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(privateBytes));
            if (!RSA_PUBLIC_HEADER.equals(reader.readLine())) {
                log.warn(
                        SafeResourceLoader.getString(
                                "KEY_FILE_UNEXPECTED_FORMAT", FactoryPlugin.getResourceBundle()));
                return Optional.empty();
            }
            keyParts.clear();
            while ((line = reader.readLine()) != null) {
                if (RSA_PUBLIC_FOOTER.equals(line)) {
                    break;
                }
                keyParts.add(line);
            }
            byte[] publicBytes = decoder.decode(String.join("", keyParts));
            PublicKey pub = keyFactory.generatePublic(new X509EncodedKeySpec(publicBytes));

            return Optional.of(new KeyPair(pub, priv));
        } catch (FileNotFoundException e) {
            log.warn(
                    SafeResourceLoader.getString(
                            "KEY_FILE_MISSING", FactoryPlugin.getResourceBundle()),
                    e);
            return Optional.empty();
        } catch (IOException e) {
            log.warn(
                    SafeResourceLoader.getString(
                            "KEY_FILE_IO_EXCEPTION", FactoryPlugin.getResourceBundle()),
                    e);
            return Optional.empty();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            log.warn(
                    SafeResourceLoader.getString(
                            "KEY_FILE_CRYPTO_ERROR", FactoryPlugin.getResourceBundle()),
                    e);
            return Optional.empty();
        }
    }

    /**
     * Write a private+public key pair to a specified file in a format resembling PEM, so that
     * {@link #readKeysFromFile(File)} can read the keys back out again.
     *
     * @param keyPair The private+public key pair to write.
     * @param file The file to write to.
     * @return True if we have written the keys successfully, false if there was some problem.
     */
    public static boolean writeKeysToFile(@NonNull KeyPair keyPair, @NonNull File file) {
        try (PrintWriter out = new PrintWriter(file)) {
            byte[] privateBytes = keyPair.getPrivate().getEncoded();
            byte[] publicBytes = keyPair.getPublic().getEncoded();
            var encoder = Base64.getEncoder();
            var privateBase64 = encoder.encodeToString(privateBytes);
            var publicBase64 = encoder.encodeToString(publicBytes);

            out.println(RSA_PRIVATE_HEADER);
            chopUpKey(privateBase64).forEach(out::println);
            out.println(RSA_PRIVATE_FOOTER);
            out.println(RSA_PUBLIC_HEADER);
            chopUpKey(publicBase64).forEach(out::println);
            out.println(RSA_PUBLIC_FOOTER);
        } catch (FileNotFoundException e) {
            log.warn(
                    SafeResourceLoader.getString(
                            "KEY_FILE_MISSING", FactoryPlugin.getResourceBundle()),
                    e);
            return false;
        }
        return true;
    }

    /** Private constructor so that this class is not instantiated. */
    private Crypto() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
