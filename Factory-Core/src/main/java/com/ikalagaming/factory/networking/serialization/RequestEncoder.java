package com.ikalagaming.factory.networking.serialization;

import com.ikalagaming.factory.FactoryStrings;
import com.ikalagaming.factory.kvt.Node;
import com.ikalagaming.factory.kvt.TreeBinarySerialization;
import com.ikalagaming.factory.networking.RequestRegistry;
import com.ikalagaming.factory.networking.base.Request;
import com.ikalagaming.factory.networking.base.RequestDirection;
import com.ikalagaming.util.SafeResourceLoader;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.EncoderException;
import io.netty.handler.codec.MessageToByteEncoder;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Encodes requests for sending them over the network: a 4 byte request ID, then the request as a
 * binary KVT tree. The frame prepender {@link RequestCodec} puts after this adds the length. A
 * request that can't be encoded fails the write with an {@link EncoderException} instead of sending
 * an empty frame.
 */
@RequiredArgsConstructor
public class RequestEncoder extends MessageToByteEncoder<Request> {
    /** The direction this encoder is handling encoding for. */
    private final RequestDirection direction;

    @Override
    protected void encode(ChannelHandlerContext ctx, Request msg, ByteBuf out) {
        RequestEncoder.encode(msg, direction, out);
    }

    /**
     * Encode one request.
     *
     * @param request The request.
     * @param direction The direction it travels.
     * @param out Where to write it.
     * @throws EncoderException If it isn't registered, can't be serialized, or is too big for a
     *     frame.
     */
    public static void encode(
            @NonNull Request request, @NonNull RequestDirection direction, @NonNull ByteBuf out) {
        final int id = RequestRegistry.getID(direction, request.getClass());
        if (id == -1) {
            throw new EncoderException(
                    SafeResourceLoader.getStringFormatted(
                            "UNSUPPORTED_REQUEST_TYPE",
                            FactoryStrings.bundle(),
                            request.getClass().getSimpleName()));
        }
        Node tree =
                TreeRequestSerialization.fromObject(request)
                        .orElseThrow(() -> RequestEncoder.failed(id));
        final int start = out.writerIndex();
        out.writeInt(id);
        try (OutputStream output = new ByteBufOutputStream(out)) {
            if (!TreeBinarySerialization.write(tree, output)) {
                throw RequestEncoder.failed(id);
            }
        } catch (IOException e) {
            throw new EncoderException(e);
        }
        final int size = out.writerIndex() - start;
        if (size > RequestCodec.MAX_FRAME_BYTES) {
            out.writerIndex(start);
            throw new EncoderException(
                    SafeResourceLoader.getStringFormatted(
                            "REQUEST_TOO_BIG",
                            FactoryStrings.bundle(),
                            Integer.toString(id),
                            Integer.toString(size),
                            Integer.toString(RequestCodec.MAX_FRAME_BYTES)));
        }
    }

    /**
     * The exception for a request that couldn't be serialized.
     *
     * @param id The request ID.
     * @return The exception.
     */
    private static EncoderException failed(int id) {
        return new EncoderException(
                SafeResourceLoader.getStringFormatted(
                        "ERROR_ENCODING_REQUEST", FactoryStrings.bundle(), Integer.toString(id)));
    }
}
