package com.ikalagaming.factory.save;

import lombok.NonNull;

import java.util.Locale;

/**
 * Who can make an account on a server, from {@code registration} in its {@value Saves#SERVER_FILE}.
 * See the Accounts and Identity design doc.
 */
public enum Registration {
    /** Anyone who connects. */
    OPEN,
    /** Anyone, but new accounts wait for an operator to accept them. */
    APPROVAL,
    /**
     * Only invited players. Join and invite codes aren't built yet, so for now this works like
     * {@link #APPROVAL}: a new player's request waits for an operator.
     */
    INVITE;

    /**
     * The policy's name in files.
     *
     * @return The name, in lower case.
     */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * The policy with a name.
     *
     * @param name The name.
     * @param fallback The policy if the name isn't one.
     * @return The policy.
     */
    public static Registration named(String name, @NonNull Registration fallback) {
        for (Registration policy : values()) {
            if (name != null && policy.id().equals(name.strip().toLowerCase(Locale.ROOT))) {
                return policy;
            }
        }
        return fallback;
    }

    /**
     * The policy a save gets when it doesn't say.
     *
     * @param dedicated Whether it runs as a dedicated server.
     * @return Invite for dedicated servers, approval for single player opened to others.
     */
    public static Registration defaultFor(boolean dedicated) {
        return dedicated ? INVITE : APPROVAL;
    }
}
