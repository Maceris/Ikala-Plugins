package com.ikalagaming.factory.networking.udp;

import com.ikalagaming.factory.networking.base.Request;
import com.ikalagaming.factory.networking.base.RequestDirection;
import com.ikalagaming.factory.networking.request.clientbound.PlayerState;
import com.ikalagaming.factory.networking.request.serverbound.PlayerInputs;
import com.ikalagaming.factory.networking.serialization.RequestDecoder;
import com.ikalagaming.factory.networking.serialization.RequestEncoder;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import lombok.NonNull;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Optional;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Requests as bytes, for packets. The first byte says how the rest is written:
 *
 * <ul>
 *   <li>{@link #INPUTS} and {@link #STATE}: the per-frame messages, packed by each connection's
 *       {@link FrameCodec} against what it last sent and received, so not here.
 *   <li>{@link #GENERAL}: anything else, as its registered id and its fields as a tree, the same as
 *       the stream codec.
 *   <li>{@link #DEFLATED}: the same, compressed, for messages of at least {@value #COMPRESS_FROM}
 *       bytes that compression makes smaller, like chunks. The length before compression comes
 *       first, as 4 bytes, and is at most {@value #MAX_INFLATED}.
 * </ul>
 */
public final class MessageCodec {

    /** A request written as its id and a tree. */
    static final byte GENERAL = 0;

    /** {@link PlayerInputs}, packed by {@link FrameCodec}. */
    static final byte INPUTS = 1;

    /** {@link PlayerState}, packed by {@link FrameCodec}. */
    static final byte STATE = 2;

    /** A {@link #GENERAL} request, compressed. */
    static final byte DEFLATED = 3;

    /** The smallest general request worth compressing, in bytes. */
    static final int COMPRESS_FROM = 128;

    /**
     * The largest a compressed request may be once inflated, so a hostile peer can't make the other
     * end inflate a few bytes into gigabytes.
     */
    static final int MAX_INFLATED = 16 * 1024 * 1024;

    /** Not made. */
    private MessageCodec() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }

    /**
     * Write a request.
     *
     * @param request The request.
     * @param direction Which way it goes, which picks the ids.
     * @return The bytes.
     * @throws io.netty.handler.codec.EncoderException If a general request can't be written.
     * @throws IllegalArgumentException For inputs or a state, which {@link FrameCodec} packs.
     */
    public static byte[] encode(@NonNull Request request, @NonNull RequestDirection direction) {
        if (FrameCodec.handles(request)) {
            throw new IllegalArgumentException(
                    "Per-frame messages are packed by the connection's FrameCodec");
        }
        final ByteBuf buffer = Unpooled.buffer();
        try {
            buffer.writeByte(GENERAL);
            RequestEncoder.encode(request, direction, buffer);
            final byte[] out = new byte[buffer.readableBytes()];
            buffer.readBytes(out);
            return out.length - 1 >= COMPRESS_FROM ? compressed(out) : out;
        } finally {
            buffer.release();
        }
    }

    /**
     * A general request compressed, if that makes it smaller.
     *
     * @param general The request as {@link #GENERAL} bytes.
     * @return {@link #DEFLATED} bytes, or the request as it was.
     */
    private static byte[] compressed(byte[] general) {
        final int length = general.length - 1;
        if (length > MAX_INFLATED) {
            return general;
        }
        final Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        try {
            deflater.setInput(general, 1, length);
            deflater.finish();
            final int header = 1 + Integer.BYTES;
            final byte[] out = new byte[general.length];
            final int size = deflater.deflate(out, header, out.length - header);
            if (!deflater.finished()) {
                return general;
            }
            ByteBuffer.wrap(out).put(DEFLATED).putInt(length);
            return Arrays.copyOf(out, header + size);
        } finally {
            deflater.end();
        }
    }

    /**
     * Inflate a compressed request.
     *
     * @param data The {@link #DEFLATED} bytes.
     * @return The request's tree bytes, without the type.
     * @throws IllegalArgumentException If they don't inflate to the length they give.
     */
    private static byte[] inflated(byte[] data) {
        final int header = 1 + Integer.BYTES;
        if (data.length < header) {
            throw new IllegalArgumentException("Cut off");
        }
        final int length = ByteBuffer.wrap(data, 1, Integer.BYTES).getInt();
        if (length < COMPRESS_FROM || length > MAX_INFLATED) {
            throw new IllegalArgumentException("Inflated length out of range: " + length);
        }
        final Inflater inflater = new Inflater(true);
        try {
            inflater.setInput(data, header, data.length - header);
            final byte[] out = new byte[length];
            int at = 0;
            while (at < length) {
                final int got = inflater.inflate(out, at, length - at);
                if (got == 0) {
                    break;
                }
                at += got;
            }
            // With the output full, raw deflate may need one more call to reach the stream's end
            final boolean longer =
                    at == length && !inflater.finished() && inflater.inflate(new byte[1]) != 0;
            if (longer || at != length || !inflater.finished() || inflater.getRemaining() != 0) {
                throw new IllegalArgumentException("Doesn't inflate to " + length + " bytes");
            }
            return out;
        } catch (DataFormatException e) {
            throw new IllegalArgumentException("Not deflated data", e);
        } finally {
            inflater.end();
        }
    }

    /**
     * Read a request.
     *
     * @param data The bytes.
     * @param direction Which way it came, which picks the ids.
     * @return The request, or empty if the bytes don't make one, inputs and states included.
     */
    public static Optional<Request> decode(
            byte @NonNull [] data, @NonNull RequestDirection direction) {
        if (data.length == 0) {
            return Optional.empty();
        }
        try {
            return switch (data[0]) {
                case GENERAL -> {
                    final ByteBuf buffer = Unpooled.wrappedBuffer(data, 1, data.length - 1);
                    try {
                        yield RequestDecoder.decode(buffer, direction);
                    } finally {
                        buffer.release();
                    }
                }
                case DEFLATED -> {
                    final ByteBuf buffer = Unpooled.wrappedBuffer(inflated(data));
                    try {
                        yield RequestDecoder.decode(buffer, direction);
                    } finally {
                        buffer.release();
                    }
                }
                default -> Optional.empty();
            };
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
