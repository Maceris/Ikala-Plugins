package com.ikalagaming.factory.events.registry;

import com.ikalagaming.event.CancelableEvent;

/**
 * We are in the process of loading tags. They will actually be loaded with an order of {@link
 * com.ikalagaming.event.Order#LATEST LATEST}, unless the event is canceled before then.
 */
public class LoadingTags extends CancelableEvent {}
