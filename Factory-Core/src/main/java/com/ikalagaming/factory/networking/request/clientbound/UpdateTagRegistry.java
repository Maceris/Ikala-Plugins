package com.ikalagaming.factory.networking.request.clientbound;

import com.ikalagaming.factory.networking.base.Connection;
import com.ikalagaming.factory.networking.base.Request;

import lombok.Data;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/** Update the tag registry with new information. */
@Slf4j
@Data
public class UpdateTagRegistry implements Request {

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
    private UpdateType updateType;

    /** The list of tags that we need to update the registry with. */
    private List<String> tags;

    @Override
    public void handleUsing(@NonNull ClientBoundConnection handler) {
        handler.handle(this);
    }

    @Override
    public void sendUsing(@NonNull Connection connection) {
        connection.dispatch(this);
    }
}
