package com.ikalagaming.graphics.benchmark;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * The sections the gate benchmark draws: every section within a radius of the camera's, as a
 * sphere, and what each holds. Pure Java, so the load it makes can be checked without a GPU.
 */
public final class GateScene {

    /**
     * One section with something to draw.
     *
     * @param x The section's x, in sections.
     * @param y The section's y, in sections.
     * @param z The section's z, in sections.
     * @param placements What it holds.
     */
    public record Section(int x, int y, int z, SyntheticTerrain.@NonNull Placements placements) {}

    /**
     * How heavy the scene is, to set beside the timings.
     *
     * @param sectionsInSphere Sections within the radius.
     * @param contentSections Sections with something to draw.
     * @param columns Columns of sections with something to draw.
     * @param placements Placements across every section, after buried cells are left out.
     * @param triangles Triangles once baked, or 0 if not known yet.
     */
    public record LoadStats(
            int sectionsInSphere,
            int contentSections,
            int columns,
            long placements,
            long triangles) {

        /**
         * Sections with content per column that has any.
         *
         * @return The average.
         */
        public double sectionsPerColumn() {
            return columns == 0 ? 0 : (double) contentSections / columns;
        }

        /**
         * Triangles per section with content.
         *
         * @return The average.
         */
        public double trianglesPerSection() {
            return contentSections == 0 ? 0 : (double) triangles / contentSections;
        }

        /**
         * The same load with the baked triangle count filled in.
         *
         * @param baked The triangles once baked.
         * @return The completed statistics.
         */
        public LoadStats withTriangles(long baked) {
            return new LoadStats(sectionsInSphere, contentSections, columns, placements, baked);
        }
    }

    /**
     * The scene's sections, and its load before baking.
     *
     * @param sections The sections with something to draw.
     * @param load The load, with no triangle count yet.
     * @param cameraSectionY The y of the section the camera is in.
     */
    public record Layout(
            @NonNull List<Section> sections, @NonNull LoadStats load, int cameraSectionY) {}

    /**
     * Lay out every section within a radius of the camera, which looks around from above the origin
     * column, and work out what each holds, using every core.
     *
     * @param terrain The world.
     * @param radius The view distance, in sections.
     * @return The layout.
     */
    public static Layout build(@NonNull SyntheticTerrain terrain, int radius) {
        final int cameraSectionY =
                Math.floorDiv((int) Math.floor(terrain.cameraY()), SyntheticTerrain.SECTION);
        final List<int[]> inSphere = new ArrayList<>();
        for (int x = -radius; x <= radius; ++x) {
            for (int y = -radius; y <= radius; ++y) {
                for (int z = -radius; z <= radius; ++z) {
                    if (x * x + y * y + z * z <= radius * radius) {
                        inSphere.add(new int[] {x, cameraSectionY + y, z});
                    }
                }
            }
        }
        final Section[] built = new Section[inSphere.size()];
        IntStream.range(0, inSphere.size())
                .parallel()
                .forEach(
                        i -> {
                            final int[] at = inSphere.get(i);
                            final SyntheticTerrain.Placements placements =
                                    terrain.placements(at[0], at[1], at[2]);
                            if (placements.count() > 0) {
                                built[i] = new Section(at[0], at[1], at[2], placements);
                            }
                        });
        final List<Section> sections = new ArrayList<>();
        final Set<Long> columns = new HashSet<>();
        long placements = 0;
        for (Section section : built) {
            if (section != null) {
                sections.add(section);
                columns.add(((long) section.x() << 32) ^ (section.z() & 0xFFFFFFFFL));
                placements += section.placements().count();
            }
        }
        return new Layout(
                sections,
                new LoadStats(inSphere.size(), sections.size(), columns.size(), placements, 0),
                cameraSectionY);
    }

    /** Static helpers only. */
    private GateScene() {}
}
