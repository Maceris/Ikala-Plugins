package com.ikalagaming.factory.networking.request.serverbound;

import com.ikalagaming.factory.networking.base.Request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * A player says something in chat, or runs a command when the text starts with {@code /}.
 *
 * <p>Fields have setters and there is a no-argument constructor, so remote connections can
 * serialize it. A local connection passes the object itself, so nothing may change it after it is
 * sent.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatSend implements Request {

    /** The most characters a line can have. */
    public static final int MAX_LENGTH = 256;

    /** What they typed. */
    private String text;

    @Override
    public void handleUsing(@NonNull ServerBoundRequestHandler handler) {
        handler.handle(this);
    }
}
