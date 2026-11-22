package com.ikalagaming.ecs;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Systems in the order they run. The order is part of what makes a step repeatable, so it is fixed
 * by the order systems are added, never by anything like hashing.
 */
public final class Schedule {

    /**
     * A system with a name.
     *
     * @param name The name, for logs and profiling.
     * @param system The system.
     */
    private record Entry(String name, WorldSystem system) {}

    /** The systems, in order. */
    private final List<Entry> entries = new ArrayList<>();

    /**
     * Add a system after the ones already added.
     *
     * @param name The system's name, unique in this schedule.
     * @param system The system.
     * @return This schedule.
     * @throws IllegalArgumentException If a system already has that name.
     */
    public Schedule add(@NonNull String name, @NonNull WorldSystem system) {
        for (Entry entry : entries) {
            if (entry.name().equals(name)) {
                throw new IllegalArgumentException("A system is already named " + name);
            }
        }
        entries.add(new Entry(name, system));
        return this;
    }

    /**
     * The systems' names, in order.
     *
     * @return The names.
     */
    public List<String> names() {
        final List<String> out = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            out.add(entry.name());
        }
        return out;
    }

    /**
     * Run every system once, in order.
     *
     * @param world The world.
     */
    public void run(@NonNull World world) {
        for (Entry entry : entries) {
            entry.system().run(world);
        }
    }
}
