package com.ikalagaming.factory.networking.udp;

import com.ikalagaming.factory.networking.base.Request;
import com.ikalagaming.factory.networking.request.clientbound.PlayerState;
import com.ikalagaming.factory.networking.request.serverbound.PlayerInputs;

import lombok.NonNull;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Optional;

/**
 * The per-frame messages of one connection, packed small since they go 62.5 times a second each
 * way. One of these lives at each end of a connection and keeps what it needs to pack against.
 *
 * <ul>
 *   <li><b>Inputs</b> carry the first frame, the low 32 bits of the send time, the newest state the
 *       client holds (as an offset from the first frame), then each run's count, buttons, and the
 *       change of yaw and pitch from the run before.
 *   <li><b>States</b> are written as changes from one the client said it holds (its baseline), or
 *       from nothing when the server has none of those left: a mask of which fields changed, then
 *       each change. The echo is the low 32 bits of the client's send time, and the hold time is in
 *       microseconds.
 * </ul>
 *
 * <p>Whole numbers are variable length, 7 bits a byte, and signed ones are zigzag coded so small
 * changes either way stay one or two bytes. The 32-bit times are widened again from a clock close
 * to them, so a round trip must stay under about 2 seconds, which the connection's timeout already
 * keeps far beyond what play allows.
 *
 * <p>Network thread only.
 */
final class FrameCodec {

    /** How many recent states each end keeps to pack against, by frame. */
    static final int HISTORY = 64;

    /** The state fields that are packed as changes, in order. */
    private static final int FIELDS = 12;

    /** The baseline of a state written whole. */
    private static final long[] NOTHING = new long[FIELDS];

    /** The most bytes a variable-length long takes. */
    private static final int MAX_VARINT = 10;

    /** Nanoseconds a microsecond. */
    private static final long NANOS_PER_MICRO = 1000;

    /** Server: the frame of each state sent, by frame modulo {@link #HISTORY}; -1 for none. */
    private final long[] sentFrames = new long[HISTORY];

    /** Server: the fields of each state sent, by frame modulo {@link #HISTORY}. */
    private final long[][] sentFields = new long[HISTORY][];

    /** Server: the newest state the client said it holds; -1 for none. */
    private long peerHolds = -1;

    /** Server: the newest send time the client gave, widened; 0 for none. */
    private long peerSentNanos;

    /** Client: the frame of each state received, by frame modulo {@link #HISTORY}; -1 for none. */
    private final long[] receivedFrames = new long[HISTORY];

    /** Client: the fields of each state received, by frame modulo {@link #HISTORY}. */
    private final long[][] receivedFields = new long[HISTORY][];

    /** Client: the newest state received; -1 for none. */
    private long newestReceived = -1;

    /** Nothing sent or received yet. */
    FrameCodec() {
        Arrays.fill(sentFrames, -1);
        Arrays.fill(receivedFrames, -1);
    }

    /**
     * Whether a request is one this packs.
     *
     * @param request The request.
     * @return True for inputs and states.
     */
    static boolean handles(@NonNull Request request) {
        return request instanceof PlayerInputs || request instanceof PlayerState;
    }

    /**
     * Whether bytes are a message this packed.
     *
     * @param data The message.
     * @return True for inputs and states.
     */
    static boolean handles(byte @NonNull [] data) {
        return data.length > 0 && (data[0] == MessageCodec.INPUTS || data[0] == MessageCodec.STATE);
    }

    /**
     * Pack a message.
     *
     * @param request Inputs or a state.
     * @return The bytes.
     * @throws IllegalArgumentException If it is neither.
     */
    byte[] encode(@NonNull Request request) {
        if (request instanceof PlayerInputs inputs) {
            return encodeInputs(inputs);
        }
        if (request instanceof PlayerState state) {
            return encodeState(state);
        }
        throw new IllegalArgumentException("Not a per-frame message: " + request);
    }

    /**
     * Unpack a message.
     *
     * @param data The bytes.
     * @param now The time, to widen the echo with.
     * @return The message, or empty for a state packed against one this no longer holds, which can
     *     only be older than one it already has.
     * @throws IllegalArgumentException If the bytes don't make a message.
     */
    Optional<Request> decode(byte @NonNull [] data, long now) {
        if (!handles(data)) {
            throw new IllegalArgumentException("Not a per-frame message");
        }
        try {
            final ByteBuffer in = ByteBuffer.wrap(data, 1, data.length - 1);
            final Optional<Request> out =
                    data[0] == MessageCodec.INPUTS
                            ? Optional.of(decodeInputs(in))
                            : decodeState(in, now);
            if (in.hasRemaining()) {
                throw new IllegalArgumentException("Bytes left over");
            }
            return out;
        } catch (BufferUnderflowException e) {
            throw new IllegalArgumentException("Cut off", e);
        }
    }

    /**
     * Pack inputs.
     *
     * @param inputs The inputs.
     * @return The bytes.
     */
    private byte[] encodeInputs(PlayerInputs inputs) {
        final int runs = inputs.getCounts() == null ? 0 : inputs.getCounts().length;
        final ByteBuffer out =
                ByteBuffer.allocate(
                        1 + 3 * MAX_VARINT + Integer.BYTES + runs * (2 + 2 * MAX_VARINT));
        out.put(MessageCodec.INPUTS);
        putVarLong(out, inputs.getFirstFrame());
        out.putInt((int) inputs.getSentNanos());
        putVarLong(
                out, newestReceived < 0 ? 0 : zigzag(inputs.getFirstFrame() - newestReceived) + 1);
        putVarLong(out, runs);
        int yaw = 0;
        int pitch = 0;
        for (int i = 0; i < runs; ++i) {
            out.put((byte) inputs.getCounts()[i]).put((byte) inputs.getButtons()[i]);
            putVarLong(out, zigzag((short) (inputs.getYaws()[i] - yaw)));
            putVarLong(out, zigzag(inputs.getPitches()[i] - (long) pitch));
            yaw = inputs.getYaws()[i];
            pitch = inputs.getPitches()[i];
        }
        return Arrays.copyOf(out.array(), out.position());
    }

    /**
     * Unpack inputs, noting which state the client holds.
     *
     * @param in The bytes after the type.
     * @return The inputs.
     */
    private PlayerInputs decodeInputs(ByteBuffer in) {
        final long firstFrame = getVarLong(in);
        final int sentLow = in.getInt();
        final long holds = getVarLong(in);
        final long runs = getVarLong(in);
        if (firstFrame < 0 || runs < 0 || runs > PlayerInputs.MAX_FRAMES) {
            throw new IllegalArgumentException("Inputs out of range");
        }
        if (holds != 0) {
            final long frame = firstFrame - unzigzag(holds - 1);
            if (frame >= 0 && frame > peerHolds) {
                peerHolds = frame;
            }
        }
        final int[] counts = new int[(int) runs];
        final int[] buttons = new int[(int) runs];
        final int[] yaws = new int[(int) runs];
        final int[] pitches = new int[(int) runs];
        int yaw = 0;
        int pitch = 0;
        for (int i = 0; i < runs; ++i) {
            counts[i] = Byte.toUnsignedInt(in.get());
            buttons[i] = Byte.toUnsignedInt(in.get());
            yaw = (yaw + (int) unzigzag(getVarLong(in))) & 0xFFFF;
            pitch = (int) (pitch + unzigzag(getVarLong(in)));
            yaws[i] = yaw;
            pitches[i] = pitch;
        }
        return new PlayerInputs(firstFrame, counts, buttons, yaws, pitches, widenSent(sentLow));
    }

    /**
     * Widen the low bits of a send time against the newest one seen, so later times stay later
     * across the 32-bit wrap.
     *
     * @param low The low 32 bits.
     * @return The time.
     */
    private long widenSent(int low) {
        final long full = peerSentNanos == 0 ? low : peerSentNanos + (low - (int) peerSentNanos);
        if (peerSentNanos == 0 || full - peerSentNanos > 0) {
            peerSentNanos = full;
        }
        return full == 0 ? 1 : full;
    }

    /**
     * Pack a state against the newest one the client holds, if it is still here.
     *
     * @param state The state.
     * @return The bytes.
     */
    private byte[] encodeState(PlayerState state) {
        final long frame = state.getFrame();
        final long[] fields = fields(state);
        long[] base = NOTHING;
        long offset = 0;
        if (peerHolds >= 0 && frame > peerHolds && frame - peerHolds < HISTORY) {
            final int slot = (int) (peerHolds % HISTORY);
            if (sentFrames[slot] == peerHolds) {
                base = sentFields[slot];
                offset = frame - peerHolds;
            }
        }
        final ByteBuffer out = ByteBuffer.allocate(1 + (3 + FIELDS) * MAX_VARINT + Integer.BYTES);
        out.put(MessageCodec.STATE);
        putVarLong(out, frame);
        putVarLong(out, offset);
        long mask = 0;
        for (int i = 0; i < FIELDS; ++i) {
            if (fields[i] != base[i]) {
                mask |= 1L << i;
            }
        }
        putVarLong(out, mask);
        for (int i = 0; i < FIELDS; ++i) {
            if (fields[i] != base[i]) {
                putVarLong(out, zigzag(fields[i] - base[i]));
            }
        }
        out.putInt((int) state.getEchoNanos());
        putVarLong(out, Math.max(0, state.getHeldNanos()) / NANOS_PER_MICRO);
        if (frame >= 0) {
            final int slot = (int) (frame % HISTORY);
            sentFrames[slot] = frame;
            sentFields[slot] = fields;
        }
        return Arrays.copyOf(out.array(), out.position());
    }

    /**
     * Unpack a state.
     *
     * @param in The bytes after the type.
     * @param now The time, to widen the echo with.
     * @return The state, or empty if its baseline is gone.
     */
    private Optional<Request> decodeState(ByteBuffer in, long now) {
        final long frame = getVarLong(in);
        final long offset = getVarLong(in);
        final long mask = getVarLong(in);
        if (frame < 0
                || offset < 0
                || offset >= HISTORY
                || offset > frame
                || mask >>> FIELDS != 0) {
            throw new IllegalArgumentException("State out of range");
        }
        final long[] fields = new long[FIELDS];
        for (int i = 0; i < FIELDS; ++i) {
            if ((mask & 1L << i) != 0) {
                fields[i] = unzigzag(getVarLong(in));
            }
        }
        final int echoLow = in.getInt();
        final long heldMicros = getVarLong(in);
        if (heldMicros < 0 || heldMicros > Long.MAX_VALUE / NANOS_PER_MICRO) {
            throw new IllegalArgumentException("Hold time out of range");
        }
        if (offset != 0) {
            final long baseFrame = frame - offset;
            final int slot = (int) (baseFrame % HISTORY);
            if (receivedFrames[slot] != baseFrame) {
                return Optional.empty();
            }
            final long[] base = receivedFields[slot];
            for (int i = 0; i < FIELDS; ++i) {
                fields[i] += base[i];
            }
        }
        final int slot = (int) (frame % HISTORY);
        receivedFrames[slot] = frame;
        receivedFields[slot] = fields;
        if (frame > newestReceived) {
            newestReceived = frame;
        }
        final long echo = echoLow == 0 ? 0 : now - (((int) now - echoLow) & 0xFFFF_FFFFL);
        final PlayerState state =
                new PlayerState(
                        frame,
                        (int) fields[0],
                        fields[1],
                        fields[2],
                        fields[3],
                        fields[4],
                        fields[5],
                        fields[6],
                        (int) fields[7],
                        (int) fields[8],
                        fields[9] != 0,
                        (int) fields[10],
                        fields[11],
                        echo,
                        heldMicros * NANOS_PER_MICRO);
        return Optional.of(state);
    }

    /**
     * The fields of a state that are packed as changes.
     *
     * @param state The state.
     * @return Mode, position, velocity, yaw, pitch, on ground, buffered and missed.
     */
    private static long[] fields(PlayerState state) {
        return new long[] {
            state.getMode(),
            state.getX(),
            state.getY(),
            state.getZ(),
            state.getVx(),
            state.getVy(),
            state.getVz(),
            state.getYaw(),
            state.getPitch(),
            state.isOnGround() ? 1 : 0,
            state.getBuffered(),
            state.getMissed()
        };
    }

    /**
     * Zigzag code a signed number, so small ones either way are small.
     *
     * @param value The number.
     * @return The code.
     */
    static long zigzag(long value) {
        return value << 1 ^ value >> 63;
    }

    /**
     * Undo {@link #zigzag(long)}.
     *
     * @param code The code.
     * @return The number.
     */
    static long unzigzag(long code) {
        return code >>> 1 ^ -(code & 1);
    }

    /**
     * Write a number 7 bits a byte, low first, as unsigned.
     *
     * @param out Where to.
     * @param value The number.
     */
    static void putVarLong(ByteBuffer out, long value) {
        long rest = value;
        while ((rest & ~0x7FL) != 0) {
            out.put((byte) (rest & 0x7F | 0x80));
            rest >>>= 7;
        }
        out.put((byte) rest);
    }

    /**
     * Read a number written by {@link #putVarLong(ByteBuffer, long)}.
     *
     * @param in Where from.
     * @return The number.
     * @throws IllegalArgumentException If it runs too long.
     */
    static long getVarLong(ByteBuffer in) {
        long value = 0;
        for (int shift = 0; shift < Long.SIZE; shift += 7) {
            final byte b = in.get();
            value |= (long) (b & 0x7F) << shift;
            if (b >= 0) {
                return value;
            }
        }
        throw new IllegalArgumentException("Number too long");
    }
}
