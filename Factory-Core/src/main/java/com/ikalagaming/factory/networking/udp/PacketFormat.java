package com.ikalagaming.factory.networking.udp;

/**
 * The layout of the game's UDP packets. Every packet starts with {@link #MAGIC} and a type byte;
 * numbers are big-endian.
 *
 * <ul>
 *   <li><b>Hello</b> (client): a random client salt, padded to {@link #HELLO_SIZE} so the server's
 *       answer is never bigger than what it answers, and the server can't be used to flood a
 *       spoofed address.
 *   <li><b>Challenge</b> (server): the salt and a token, made from a secret, the client's address,
 *       the salt and the time. The server keeps nothing for the client yet.
 *   <li><b>Response</b> (client): the salt and the token back, proving the address is real.
 *   <li><b>Accept</b> (server): the salt and the connection id every data packet then carries.
 *   <li><b>Data</b>: connection id, sequence, whether anything has been received yet, the latest
 *       sequence received, a 32-bit field of the ones before it, then entries: unreliable messages,
 *       and fragments of reliable ones.
 *   <li><b>Disconnect</b>: connection id. Sent a few times; there is no answer.
 * </ul>
 */
final class PacketFormat {

    /** The first four bytes of every packet: "IKF" and the protocol version. */
    static final int MAGIC = 0x494B_4601;

    /** A client asks to connect. */
    static final byte HELLO = 1;

    /** The server's challenge. */
    static final byte CHALLENGE = 2;

    /** The client's answer to the challenge. */
    static final byte RESPONSE = 3;

    /** The server accepts the connection. */
    static final byte ACCEPT = 4;

    /** Messages. */
    static final byte DATA = 5;

    /** Either end leaves. */
    static final byte DISCONNECT = 6;

    /** The largest packet, small enough to pass almost any network without splitting. */
    static final int MAX_PACKET = 1200;

    /** How big a hello is, padding included. */
    static final int HELLO_SIZE = MAX_PACKET;

    /** The magic and type at the start of every packet. */
    static final int PREFIX = Integer.BYTES + Byte.BYTES;

    /** A data packet's header: prefix, connection id, sequence, has-acks flag, ack and ack bits. */
    static final int DATA_HEADER =
            PREFIX + Long.BYTES + Short.BYTES + Byte.BYTES + Short.BYTES + Integer.BYTES;

    /** An entry holding an unreliable message. */
    static final byte UNRELIABLE = 1;

    /** An entry holding a fragment of a reliable message. */
    static final byte RELIABLE = 2;

    /** An unreliable entry's header: kind and length. */
    static final int UNRELIABLE_HEADER = Byte.BYTES + Short.BYTES;

    /** A fragment entry's header: kind, id, index, count and length. */
    static final int FRAGMENT_HEADER = Byte.BYTES + 4 * Short.BYTES;

    /** The most bytes of a reliable message in one fragment. */
    static final int MAX_FRAGMENT = MAX_PACKET - DATA_HEADER - FRAGMENT_HEADER;

    /** The largest unreliable message, which must fit one packet. */
    static final int MAX_UNRELIABLE = MAX_PACKET - DATA_HEADER - UNRELIABLE_HEADER;

    /** The most fragments one reliable message may have. */
    static final int MAX_FRAGMENTS = 1024;

    /** Not made. */
    private PacketFormat() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
