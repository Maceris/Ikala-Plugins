package com.ikalagaming.factory.networking.serialization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.factory.auth.Crypto;
import com.ikalagaming.factory.networking.RequestRegistry;
import com.ikalagaming.factory.networking.SimpleRequest;
import com.ikalagaming.factory.networking.base.RequestDirection;
import com.ikalagaming.factory.networking.request.clientbound.UpdateTagRegistry;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.EncoderException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

/** Tests sending requests through the codec, including frames a hostile peer could send. */
class TestRequestCodec {

    @BeforeAll
    static void registerRequests() {
        RequestRegistry.registerDefaults();
    }

    /**
     * A channel with the codec, receiving and sending client bound requests.
     *
     * @return The channel.
     */
    private static EmbeddedChannel channel() {
        EmbeddedChannel channel = new EmbeddedChannel();
        RequestCodec.addTo(
                channel.pipeline(), RequestDirection.CLIENT_BOUND, RequestDirection.CLIENT_BOUND);
        return channel;
    }

    private static UpdateTagRegistry request() {
        UpdateTagRegistry request = new UpdateTagRegistry();
        request.setUpdateType(UpdateTagRegistry.UpdateType.ADD);
        request.setTags(List.of("factory:ore", "factory:metal"));
        return request;
    }

    /**
     * The bytes a request is sent as, with its length in front.
     *
     * @return The bytes.
     */
    private static byte[] wire() {
        EmbeddedChannel sender = channel();
        assertTrue(sender.writeOutbound(request()));
        ByteBuf sent = Unpooled.buffer();
        for (Object part; (part = sender.readOutbound()) != null; ) {
            ByteBuf buffer = (ByteBuf) part;
            sent.writeBytes(buffer);
            buffer.release();
        }
        byte[] bytes = new byte[sent.readableBytes()];
        sent.readBytes(bytes);
        return bytes;
    }

    @Test
    void requestsRoundTrip() {
        EmbeddedChannel receiver = channel();
        receiver.writeInbound(Unpooled.wrappedBuffer(wire()));
        assertEquals(request(), receiver.readInbound());
        assertTrue(receiver.isOpen());
    }

    @Test
    void framesSplitAcrossReadsAreJoined() {
        byte[] bytes = wire();
        EmbeddedChannel receiver = channel();
        for (byte b : bytes) {
            receiver.writeInbound(Unpooled.wrappedBuffer(new byte[] {b}));
        }
        assertEquals(request(), receiver.readInbound());
        assertNull(receiver.readInbound());
    }

    @Test
    void twoFramesInOneReadAreSplit() {
        byte[] one = wire();
        byte[] two = Arrays.copyOf(one, one.length * 2);
        System.arraycopy(one, 0, two, one.length, one.length);
        EmbeddedChannel receiver = channel();
        receiver.writeInbound(Unpooled.wrappedBuffer(two));
        assertNotNull(receiver.readInbound());
        assertNotNull(receiver.readInbound());
    }

    @Test
    void oversizedFramesCloseTheConnection() {
        for (int length : new int[] {RequestCodec.MAX_FRAME_BYTES + 1, Integer.MAX_VALUE, -1}) {
            EmbeddedChannel receiver = channel();
            receiver.writeInbound(Unpooled.buffer().writeInt(length).writeInt(0));
            assertFalse(receiver.isOpen(), "length " + length);
            assertNull(receiver.readInbound());
        }
    }

    /**
     * Send one frame with the given contents.
     *
     * @param contents What goes after the length.
     * @return The channel, after reading it.
     */
    private static EmbeddedChannel receive(ByteBuf contents) {
        EmbeddedChannel receiver = channel();
        ByteBuf frame = Unpooled.buffer().writeInt(contents.readableBytes()).writeBytes(contents);
        receiver.writeInbound(frame);
        return receiver;
    }

    @Test
    void badFramesCloseTheConnection() {
        byte[] good = wire();
        // Without the length field: the request ID, then the tree
        ByteBuf contents = Unpooled.wrappedBuffer(good, 4, good.length - 4);

        // Unknown request ID
        ByteBuf unknown = contents.copy();
        unknown.setInt(0, 12_345);
        // Too short for an ID
        ByteBuf tiny = Unpooled.buffer().writeShort(1);
        // Garbage instead of a tree
        ByteBuf garbage = Unpooled.buffer().writeInt(0).writeLong(Long.MAX_VALUE).writeInt(-1);
        // Bytes left over after the tree
        ByteBuf extra = contents.copy().writeByte(0);
        // A tree cut short
        ByteBuf cut = contents.copy(0, contents.readableBytes() - 3);

        for (ByteBuf bad : List.of(unknown, tiny, garbage, extra, cut)) {
            EmbeddedChannel receiver = receive(bad);
            assertNull(receiver.readInbound());
            assertFalse(receiver.isOpen());
        }
    }

    @Test
    void unregisteredRequestsAreNotSent() {
        EmbeddedChannel sender = channel();
        assertThrows(EncoderException.class, () -> sender.writeOutbound(new SimpleRequest()));
        assertNull(sender.readOutbound(), "No empty frame goes out");
    }

    @Test
    void challengesAreUnpredictableAndUnique() {
        byte[] first = Crypto.getChallenge();
        byte[] second = Crypto.getChallenge();
        assertEquals(16, first.length);
        assertFalse(Arrays.equals(first, second));
        assertFalse(
                Arrays.equals(Arrays.copyOf(first, 12), Arrays.copyOf(second, 12)),
                "The random part differs, not just the counter");
    }
}
