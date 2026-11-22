/**
 * The deterministic simulation: anything predicted by clients and rolled back when the server
 * disagrees. Code here uses integer math only ({@link com.ikalagaming.factory.sim.Fixed}, {@link
 * com.ikalagaming.factory.sim.Angle}), never the wall clock, never unseeded randomness and never
 * hash order, so the same inputs always give exactly the same result. A test checks the source for
 * floating point and other things that break that. See the Command Frames design doc.
 */
package com.ikalagaming.factory.sim;
