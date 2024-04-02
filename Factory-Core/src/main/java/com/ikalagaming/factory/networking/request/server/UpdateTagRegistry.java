package com.ikalagaming.factory.networking.request.server;

import com.ikalagaming.factory.networking.base.Request;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * Update the tag registry with new information.
 */
@RequiredArgsConstructor
public abstract class UpdateTagRegistry extends Request {
    /**
     * What we need to do with the provided tags.
     */
    public enum UpdateType {
        /**
         * Replace all the tags with what is provided.
         */
        REPLACE,
        /**
         * Add the provided tags to the list.
         */
        ADD,
        /**
         * Remove the provided tags from the list.
         */
        REMOVE
    }

    /**
     * Determines what we need to do with the provided tags.
     */
    @Getter
    protected final UpdateType updateType;

    /**
     * Returns the list of tags that we need to update the registry with.
     *
     * @return The tags.
     */
    public abstract List<String> getTags();
}
