package com.ikalagaming.factory.events.registry;

import com.ikalagaming.event.CancelableEvent;

/**
 * We are in the process of loading blocks. They will actually be loaded with an order of {@link
 * com.ikalagaming.event.Order#LATEST LATEST}, unless the event is canceled before then.
 */
public class LoadingBlocks extends CancelableEvent {}
