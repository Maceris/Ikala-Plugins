package com.ikalagaming.factory.networking.serialization;

import com.ikalagaming.factory.FactoryPlugin;
import com.ikalagaming.factory.kvt.KVT;
import com.ikalagaming.factory.kvt.TreeBinarySerialization;
import com.ikalagaming.factory.networking.RequestRegistry;
import com.ikalagaming.factory.networking.base.Request;
import com.ikalagaming.factory.networking.base.RequestDirection;
import com.ikalagaming.util.SafeResourceLoader;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.util.List;

/**
 * Decodes requests that were sent over the network.
 */
@Slf4j
@RequiredArgsConstructor
public class RequestDecoder extends ByteToMessageDecoder {
    /** The direction this decoder is handling encoding for. */
    private final RequestDirection direction;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out)
            throws Exception {
        int id = in.readInt();

        Class<? extends Request> type = RequestRegistry.getType(direction, id);

        if (type == null) {
            log.error(
                    SafeResourceLoader.getStringFormatted(
                            "UNKNOWN_REQUEST_ID",
                            FactoryPlugin.getResourceBundle(),
                            Integer.toString(id)));
            return;
        }

        try (InputStream input = new ByteBufInputStream(in)) {
            KVT contents =
                    TreeBinarySerialization.read(input).orElseThrow(IllegalArgumentException::new);

            var result = RequestKVTMapper.fromKVT(contents, type);
            out.add(result);
        } catch (IllegalArgumentException ignored) {
            log.error(
                    SafeResourceLoader.getStringFormatted(
                            "ERROR_DECODING_REQUEST",
                            FactoryPlugin.getResourceBundle(),
                            Integer.toString(id)));
        }
    }
}
