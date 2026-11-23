package com.ikalagaming.factory.networking.request.clientbound;

import com.ikalagaming.factory.chat.ChatText;
import com.ikalagaming.factory.networking.base.Request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * A line of chat for the player to see: something someone said, a command's answer, or a system
 * message, as colored spans.
 *
 * <p>Fields have setters and there is a no-argument constructor, so remote connections can
 * serialize it. A local connection passes the object itself, so nothing may change it after it is
 * sent.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage implements Request {

    /** Each span's text. */
    private List<String> texts;

    /** Each span's color, as 0xRRGGBB or -1 for the default. */
    private List<Integer> colors;

    /**
     * A message with a line.
     *
     * @param text The line.
     * @return The message.
     */
    public static ChatMessage of(@NonNull ChatText text) {
        final List<String> texts = new ArrayList<>();
        final List<Integer> colors = new ArrayList<>();
        for (ChatText.Span span : text.spans()) {
            texts.add(span.text());
            colors.add(span.color());
        }
        return new ChatMessage(texts, colors);
    }

    /**
     * The line.
     *
     * @return The line; spans missing a color get the default.
     */
    public ChatText text() {
        final List<ChatText.Span> spans = new ArrayList<>();
        if (texts != null) {
            for (int i = 0; i < texts.size(); ++i) {
                final Integer color = colors != null && i < colors.size() ? colors.get(i) : null;
                spans.add(
                        new ChatText.Span(
                                texts.get(i) == null ? "" : texts.get(i),
                                color == null ? ChatText.DEFAULT : color));
            }
        }
        return new ChatText(spans);
    }

    @Override
    public void handleUsing(@NonNull ClientBoundRequestHandler handler) {
        handler.handle(this);
    }
}
