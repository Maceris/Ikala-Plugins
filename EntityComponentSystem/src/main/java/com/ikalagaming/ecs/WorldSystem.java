package com.ikalagaming.ecs;

/**
 * One step of work on a world, such as moving everything that has a velocity. Systems run in the
 * order a {@link Schedule} lists them.
 *
 * <p>A system that changes simulated components should read only the world, and whatever inputs the
 * world holds as resources, so that running it again from a restored snapshot gives the same
 * result.
 */
@FunctionalInterface
public interface WorldSystem {

    /**
     * Do this system's work.
     *
     * @param world The world.
     */
    void run(World world);
}
