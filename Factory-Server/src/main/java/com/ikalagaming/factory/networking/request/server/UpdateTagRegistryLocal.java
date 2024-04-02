package com.ikalagaming.factory.networking.request.server;

import lombok.NonNull;

import java.util.List;

/** Used to directly pass a list. */
public class UpdateTagRegistryLocal extends UpdateTagRegistry {

    private final List<String> tags;

    public UpdateTagRegistryLocal(@NonNull UpdateType updateType, @NonNull List<String> tags) {
        super(updateType);
        this.tags = tags;
    }

    @Override
    public List<String> getTags() {
        return tags;
    }
}
