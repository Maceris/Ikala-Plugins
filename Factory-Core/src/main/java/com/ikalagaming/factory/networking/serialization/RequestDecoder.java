package com.ikalagaming.factory.networking.serialization;

import com.ikalagaming.factory.FactoryStrings;
import com.ikalagaming.factory.kvt.Node;
import com.ikalagaming.factory.kvt.TreeBinarySerialization;
import com.ikalagaming.factory.networking.RequestRegistry;
import com.ikalagaming.factory.networking.base.Request;
import com.ikalagaming.factory.networking.base.RequestDirection;
import com.ikalagaming.util.SafeResourceLoader;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

/**
 * Decodes requests that were sent over the network. Each message is one whole frame, from the frame
 * decoder {@link RequestCodec} puts before this: a 4 byte request ID, then the request as a binary
 * KVT tree. A frame that can't be decoded closes the connection, since the other end is either
 * broken or hostile, and nothing after it can be trusted to line up.
 */
@Slf4j
@RequiredArgsConstructor
public class RequestDecoder extends MessageToMessageDecoder<ByteBuf> {
    /** The direction this decoder is handling encoding for. */
    private final RequestDirection direction;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf frame, List<Object> out) {
        Optional<Request> request = RequestDecoder.decode(frame, direction);
        if (request.isEmpty()) {
            log.warn(
                    SafeResourceLoader.getStringFormatted(
                            "REQUEST_CLOSING",
                            FactoryStrings.bundle(),
                            String.valueOf(ctx.channel().remoteAddress())));
            ctx.close();
            return;
        }
        out.add(request.get());
    }

    /**
     * Decode one whole frame.
     *
     * @param frame The frame, which this reads all of.
     * @param direction The direction the request travels.
     * @return The request, or empty if the frame isn't a valid request (which is logged).
     */
    public static Optional<Request> decode(
            @NonNull ByteBuf frame, @NonNull RequestDirection direction) {
        if (frame.readableBytes() < Integer.BYTES) {
            log.warn(
                    SafeResourceLoader.getStringFormatted(
                            "REQUEST_TOO_SHORT",
                            FactoryStrings.bundle(),
                            Integer.toString(frame.readableBytes())));
            return Optional.empty();
        }
        final int id = frame.readInt();
        Class<? extends Request> type = RequestRegistry.getType(direction, id);
        if (type == null) {
            log.warn(
                    SafeResourceLoader.getStringFormatted(
                            "UNKNOWN_REQUEST_ID", FactoryStrings.bundle(), Integer.toString(id)));
            return Optional.empty();
        }
        try (InputStream input = new ByteBufInputStream(frame)) {
            Optional<Node> contents = TreeBinarySerialization.read(input);
            // Leftover bytes mean the sizes inside don't match the frame
            if (contents.isPresent() && !frame.isReadable()) {
                Optional<? extends Request> request =
                        TreeRequestSerialization.toObject(contents.get(), type);
                if (request.isPresent()) {
                    return Optional.of(request.get());
                }
            }
        } catch (IOException | RuntimeException e) {
            log.debug("Decoding request {} failed", id, e);
        }
        log.warn(
                SafeResourceLoader.getStringFormatted(
                        "ERROR_DECODING_REQUEST", FactoryStrings.bundle(), Integer.toString(id)));
        return Optional.empty();
    }
}
