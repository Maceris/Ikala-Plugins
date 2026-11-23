package com.ikalagaming.factory.server;

import com.ikalagaming.factory.chat.ChatText;
import com.ikalagaming.permissions.Subject;

import lombok.NonNull;

/** Who runs a command: a player in chat, or the server console. */
public interface CommandSender {

    /**
     * The name to show for them.
     *
     * @return The name.
     */
    String name();

    /**
     * Who they are for permissions.
     *
     * @return The subject.
     */
    Subject subject();

    /**
     * Tell them something.
     *
     * @param text The line.
     */
    void reply(@NonNull ChatText text);

    /**
     * Their player, if they are one.
     *
     * @return The player, or null for the console.
     */
    ServerPlayer player();
}
