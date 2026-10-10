package com.ikalagaming.graphics;

/**
 * A handle to a baked section. Holding a handle does not keep the section alive: it is removed
 * explicitly through {@link Sections#remove(SectionHandle)}, or when the plugin that composed it is
 * unloaded. A handle whose section was removed is stale, and does nothing.
 *
 * @param id The section's id, never reused.
 * @see Sections
 */
public record SectionHandle(long id) {}
