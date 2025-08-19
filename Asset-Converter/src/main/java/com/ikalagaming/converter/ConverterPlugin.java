package com.ikalagaming.converter;

import com.ikalagaming.event.Listener;
import com.ikalagaming.plugins.Plugin;

import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Slf4j
public class ConverterPlugin extends Plugin {
    public static final String PLUGIN_NAME = "Asset-Converter";

    private Set<Listener> listeners;

    @Override
    public Set<Listener> getListeners() {
        if (null == this.listeners) {
            this.listeners = Collections.synchronizedSet(new HashSet<>());
        }
        return this.listeners;
    }

    @Override
    public String getName() {
        return ConverterPlugin.PLUGIN_NAME;
    }

}
