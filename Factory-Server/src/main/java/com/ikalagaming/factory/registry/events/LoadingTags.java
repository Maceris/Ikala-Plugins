package com.ikalagaming.factory.registry.events;

import com.ikalagaming.event.Event;

/**
 * We are in the process of loading tags. They will actually be loaded with an order of {@link
 * com.ikalagaming.event.Order#EARLY EARLY}.
 */
public class LoadingTags extends Event {}
