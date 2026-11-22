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

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.Optional;

/**
 * Requests as bytes, for packets. The first byte says how the rest is written:
 *
 * <ul>
 *   <li>{@link #INPUTS} and {@link #STATE}: the per-frame messages, written by hand to stay small,
 *       since they go 62.5 times a second.
 *   <li>{@link #GENERAL}: anything else, as its registered id and its fields as a tree, the same as
 *       the stream codec.
 * </ul>
 */
public final class MessageCodec {

    /** A request written as its id and a tree. */
    static final byte GENERAL = 0;

    /** {@link PlayerInputs}, by hand. */
    static final byte INPUTS = 1;

    /** {@link PlayerState}, by hand. */
    static final byte STATE = 2;

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
     */
    public static byte[] encode(@NonNull Request request, @NonNull RequestDirection direction) {
        if (request instanceof PlayerInputs inputs) {
            return encodeInputs(inputs);
        }
        if (request instanceof PlayerState state) {
            return encodeState(state);
        }
        final ByteBuf buffer = Unpooled.buffer();
        try {
            buffer.writeByte(GENERAL);
            RequestEncoder.encode(request, direction, buffer);
            final byte[] out = new byte[buffer.readableBytes()];
            buffer.readBytes(out);
            return out;
        } finally {
            buffer.release();
        }
    }

    /**
     * Read a request.
     *
     * @param data The bytes.
     * @param direction Which way it came, which picks the ids.
     * @return The request, or empty if the bytes don't make one.
     */
    public static Optional<Request> decode(
            byte @NonNull [] data, @NonNull RequestDirection direction) {
        if (data.length == 0) {
            return Optional.empty();
        }
        try {
            return switch (data[0]) {
                case INPUTS -> decodeInputs(data);
                case STATE -> decodeState(data);
                case GENERAL -> {
                    final ByteBuf buffer = Unpooled.wrappedBuffer(data, 1, data.length - 1);
                    try {
                        yield RequestDecoder.decode(buffer, direction);
                    } finally {
                        buffer.release();
                    }
                }
                default -> Optional.empty();
            };
        } catch (BufferUnderflowException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * Write inputs: the first frame, the send time, then each run's count, buttons, yaw and pitch.
     *
     * @param inputs The inputs.
     * @return The bytes.
     */
    private static byte[] encodeInputs(PlayerInputs inputs) {
        final int runs = inputs.getCounts() == null ? 0 : inputs.getCounts().length;
        final ByteBuffer out = ByteBuffer.allocate(1 + 2 * Long.BYTES + Short.BYTES + runs * 6);
        out.put(INPUTS).putLong(inputs.getFirstFrame()).putLong(inputs.getSentNanos());
        out.putShort((short) runs);
        for (int i = 0; i < runs; ++i) {
            out.put((byte) inputs.getCounts()[i])
                    .put((byte) inputs.getButtons()[i])
                    .putShort((short) inputs.getYaws()[i])
                    .putShort((short) inputs.getPitches()[i]);
        }
        return out.array();
    }

    /**
     * Read inputs.
     *
     * @param data The bytes.
     * @return The inputs, or empty if they don't fit.
     */
    private static Optional<Request> decodeInputs(byte[] data) {
        final ByteBuffer in = ByteBuffer.wrap(data, 1, data.length - 1);
        final long firstFrame = in.getLong();
        final long sentNanos = in.getLong();
        final int runs = Short.toUnsignedInt(in.getShort());
        if (runs > PlayerInputs.MAX_FRAMES) {
            return Optional.empty();
        }
        final int[] counts = new int[runs];
        final int[] buttons = new int[runs];
        final int[] yaws = new int[runs];
        final int[] pitches = new int[runs];
        for (int i = 0; i < runs; ++i) {
            counts[i] = Byte.toUnsignedInt(in.get());
            buttons[i] = Byte.toUnsignedInt(in.get());
            yaws[i] = Short.toUnsignedInt(in.getShort());
            pitches[i] = in.getShort();
        }
        if (in.hasRemaining()) {
            return Optional.empty();
        }
        return Optional.of(new PlayerInputs(firstFrame, counts, buttons, yaws, pitches, sentNanos));
    }

    /**
     * Write a state: the frame, the movement, then the buffer, misses and echo.
     *
     * @param state The state.
     * @return The bytes.
     */
    private static byte[] encodeState(PlayerState state) {
        final ByteBuffer out = ByteBuffer.allocate(1 + 10 * Long.BYTES + 3 * Short.BYTES + 2);
        out.put(STATE)
                .putLong(state.getFrame())
                .put((byte) state.getMode())
                .putLong(state.getX())
                .putLong(state.getY())
                .putLong(state.getZ())
                .putLong(state.getVx())
                .putLong(state.getVy())
                .putLong(state.getVz())
                .putShort((short) state.getYaw())
                .putShort((short) state.getPitch())
                .put((byte) (state.isOnGround() ? 1 : 0))
                .putShort((short) Math.min(Short.MAX_VALUE, state.getBuffered()))
                .putLong(state.getMissed())
                .putLong(state.getEchoNanos())
                .putLong(state.getHeldNanos());
        return out.array();
    }

    /**
     * Read a state.
     *
     * @param data The bytes.
     * @return The state, or empty if it doesn't fit.
     */
    private static Optional<Request> decodeState(byte[] data) {
        final ByteBuffer in = ByteBuffer.wrap(data, 1, data.length - 1);
        final PlayerState state =
                new PlayerState(
                        in.getLong(),
                        Byte.toUnsignedInt(in.get()),
                        in.getLong(),
                        in.getLong(),
                        in.getLong(),
                        in.getLong(),
                        in.getLong(),
                        in.getLong(),
                        Short.toUnsignedInt(in.getShort()),
                        in.getShort(),
                        in.get() != 0,
                        in.getShort(),
                        in.getLong(),
                        in.getLong(),
                        in.getLong());
        if (in.hasRemaining()) {
            return Optional.empty();
        }
        return Optional.of(state);
    }
}
