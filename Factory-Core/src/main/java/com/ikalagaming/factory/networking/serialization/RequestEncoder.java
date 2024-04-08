package com.ikalagaming.factory.networking.serialization;

import com.ikalagaming.factory.FactoryPlugin;
import com.ikalagaming.factory.kvt.KVT;
import com.ikalagaming.factory.kvt.Node;
import com.ikalagaming.factory.kvt.TreeBinarySerialization;
import com.ikalagaming.factory.networking.RequestRegistry;
import com.ikalagaming.factory.networking.base.Request;
import com.ikalagaming.factory.networking.base.RequestDirection;
import com.ikalagaming.util.SafeResourceLoader;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Encodes requests for sending them over the network.
 */
@Slf4j
@RequiredArgsConstructor
public class RequestEncoder extends MessageToByteEncoder<Request> {

    /** The direction this encoder is handling encoding for. */
    private final RequestDirection direction;

    @Override
    protected void encode(ChannelHandlerContext ctx, Request msg, ByteBuf out) throws IOException {
        int id = RequestRegistry.getID(direction, msg.getClass());

        if (id == -1) {
            log.error(
                    SafeResourceLoader.getStringFormatted(
                            "UNSUPPORTED_REQUEST_TYPE",
                            FactoryPlugin.getResourceBundle(),
                            msg.getClass().getSimpleName()));
            return;
        }

        try (OutputStream output = new ByteBufOutputStream(out)) {
            KVT tree = RequestKVTMapper.toKVT(msg);

            output.write(id);
            TreeBinarySerialization.write((Node) tree, output);
        } catch (IllegalArgumentException ignored) {
            log.error(
                    SafeResourceLoader.getStringFormatted(
                            "ERROR_ENCODING_REQUEST",
                            FactoryPlugin.getResourceBundle(),
                            Integer.toString(id)));
        }
    }
}
