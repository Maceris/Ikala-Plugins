package com.ikalagaming.factory.networking.serialization;

import com.ikalagaming.factory.FactoryStrings;
import com.ikalagaming.factory.networking.base.RequestDirection;
import com.ikalagaming.util.SafeResourceLoader;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Sets up a channel to send and receive requests. On the wire, each request is one frame: a 4 byte
 * big-endian length, then that many bytes holding a 4 byte request ID and the request as a binary
 * KVT tree. Frames above {@link #MAX_FRAME_BYTES} are refused before any of them is buffered, and a
 * frame that can't be decoded closes the connection.
 *
 * @author Ches Burks
 */
@Slf4j
public final class RequestCodec {
    /**
     * The largest frame, not counting its length field. Each connection may buffer up to this much
     * while a frame arrives, so it bounds the memory a peer can make us hold. Data bigger than this,
     * like world chunks, should be split across requests.
     */
    public static final int MAX_FRAME_BYTES = 4 << 20;

    /** The size of the length field in front of each frame. */
    static final int LENGTH_BYTES = 4;

    /** Pipeline name of the frame decoder. */
    public static final String FRAME_DECODER = "requestFrames";

    /** Pipeline name of the length prepender. */
    public static final String FRAME_ENCODER = "requestLengths";

    /** Pipeline name of the request decoder. */
    public static final String REQUEST_DECODER = "requestDecoder";

    /** Pipeline name of the request encoder. */
    public static final String REQUEST_ENCODER = "requestEncoder";

    /** Pipeline name of the handler that closes the connection on bad frames. */
    public static final String DECODE_ERRORS = "requestDecodeErrors";

    /**
     * Closes the connection when a frame is malformed or too long. Later frames can't be trusted
     * to line up after one, and a peer sending them is broken or hostile.
     */
    static final class DecodeErrors extends ChannelInboundHandlerAdapter {
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            if (cause instanceof DecoderException) {
                log.warn(
                        SafeResourceLoader.getStringFormatted(
                                "REQUEST_BAD_FRAME",
                                FactoryStrings.bundle(),
                                String.valueOf(ctx.channel().remoteAddress()),
                                String.valueOf(cause.getMessage())));
                ctx.close();
                return;
            }
            ctx.fireExceptionCaught(cause);
        }
    }

    /**
     * Add the request codec to the end of a pipeline. Handlers that process requests go after it.
     *
     * @param pipeline The channel's pipeline.
     * @param incoming The direction of requests this end receives, like {@link
     *     RequestDirection#SERVER_BOUND} on a server.
     * @param outgoing The direction of requests this end sends.
     */
    public static void addTo(
            @NonNull ChannelPipeline pipeline,
            @NonNull RequestDirection incoming,
            @NonNull RequestDirection outgoing) {
        pipeline.addLast(
                FRAME_DECODER,
                new LengthFieldBasedFrameDecoder(
                        MAX_FRAME_BYTES + LENGTH_BYTES, 0, LENGTH_BYTES, 0, LENGTH_BYTES));
        pipeline.addLast(FRAME_ENCODER, new LengthFieldPrepender(LENGTH_BYTES));
        pipeline.addLast(REQUEST_DECODER, new RequestDecoder(incoming));
        pipeline.addLast(REQUEST_ENCODER, new RequestEncoder(outgoing));
        pipeline.addLast(DECODE_ERRORS, new DecodeErrors());
    }

    /** Static helpers only. */
    private RequestCodec() {}
}
