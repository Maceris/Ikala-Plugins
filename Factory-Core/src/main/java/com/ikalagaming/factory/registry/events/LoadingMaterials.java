package com.ikalagaming.factory.registry.events;

import com.ikalagaming.event.Event;

/**
 * We are in the process of loading materials. They will actually be loaded with an order of {@link
 * com.ikalagaming.event.Order#EARLY EARLY}.
 */
public class LoadingMaterials extends Event {}
