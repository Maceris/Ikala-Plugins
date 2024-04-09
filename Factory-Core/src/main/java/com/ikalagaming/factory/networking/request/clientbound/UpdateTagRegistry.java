package com.ikalagaming.factory.networking.request.clientbound;

import com.ikalagaming.factory.FactoryPlugin;
import com.ikalagaming.factory.kvt.KVT;
import com.ikalagaming.factory.kvt.Node;
import com.ikalagaming.factory.networking.base.Connection;
import com.ikalagaming.factory.networking.base.Request;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/** Update the tag registry with new information. */
@Slf4j
@Getter
@AllArgsConstructor
public class UpdateTagRegistry implements Request {

    /**
     * Convert the object to KVT data.
     *
     * @param object The object we want to parse.
     * @return The corresponding KVT data.
     */
    public static KVT toKVT(@NonNull UpdateTagRegistry object) {
        KVT result = new Node();
        result.addString("updateType", object.getUpdateType().name());
        result.addStringArray("tags", object.getTags());
        return result;
    }

    /**
     * Parse from KVT data.
     *
     * @param kvt The data we want to parse from.
     * @return The parsed object.
     */
    public static UpdateTagRegistry fromKVT(@NonNull KVT kvt) {
        var updateTypeName = kvt.getString("updateType");
        if (updateTypeName == null) {
            var message =
                    SafeResourceLoader.getStringFormatted(
                            "ERROR_PARSING_FIELD", FactoryPlugin.getResourceBundle(), "updateType");
            log.error(message);
            throw new IllegalArgumentException(message);
        }
        var updateType = UpdateType.valueOf(updateTypeName);

        var tags = kvt.getStringArray("tags");
        if (tags == null) {
            var message =
                    SafeResourceLoader.getStringFormatted(
                            "ERROR_PARSING_FIELD", FactoryPlugin.getResourceBundle(), "tags");
            log.error(message);
            throw new IllegalArgumentException(message);
        }

        return new UpdateTagRegistry(updateType, tags);
    }

    /** What we need to do with the provided tags. */
    public enum UpdateType {
        /** Replace all the tags with what is provided. */
        REPLACE,
        /** Add the provided tags to the list. */
        ADD,
        /** Remove the provided tags from the list. */
        REMOVE
    }

    /** Determines what we need to do with the provided tags. */
    @NonNull private final UpdateType updateType;

    /** The list of tags that we need to update the registry with. */
    @NonNull private final List<String> tags;

    @Override
    public void handleUsing(@NonNull ClientBoundConnection handler) {
        handler.handle(this);
    }

    @Override
    public void sendUsing(@NonNull Connection connection) {
        connection.dispatch(this);
    }
}
