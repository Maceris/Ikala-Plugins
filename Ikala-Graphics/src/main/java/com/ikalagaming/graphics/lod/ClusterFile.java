package com.ikalagaming.graphics.lod;

import com.ikalagaming.graphics.asset.AssetFile;

import lombok.NonNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Where a cluster's triangles lie in a cached cell file, for a cluster the store keeps only the
 * info of. The file is pinned in the cache while the store lasts, and never changes, so reading it
 * is safe from any thread.
 *
 * @param file The cell file.
 * @param start Where the cluster's record starts, in bytes.
 * @param end Where it ends.
 */
public record ClusterFile(@NonNull Path file, long start, long end) {

    /**
     * Read the cluster.
     *
     * @return It, with its triangles.
     */
    public LodDag.Cluster read() {
        return LodCellFile.readCluster(file, start, end);
    }

    /**
     * Read many clusters, each file's once over the span its clusters take.
     *
     * @param places Where each cluster is.
     * @return The clusters, in the same order.
     */
    public static List<LodDag.Cluster> readAll(@NonNull List<ClusterFile> places) {
        final LodDag.Cluster[] out = new LodDag.Cluster[places.size()];
        final Map<Path, List<Integer>> byFile = new HashMap<>();
        for (int i = 0; i < places.size(); ++i) {
            byFile.computeIfAbsent(places.get(i).file(), k -> new ArrayList<>()).add(i);
        }
        for (Map.Entry<Path, List<Integer>> entry : byFile.entrySet()) {
            long from = Long.MAX_VALUE;
            long to = Long.MIN_VALUE;
            for (int i : entry.getValue()) {
                from = Math.min(from, places.get(i).start());
                to = Math.max(to, places.get(i).end());
            }
            final ByteBuffer span =
                    AssetFile.read(entry.getKey(), from, Math.toIntExact(to - from));
            for (int i : entry.getValue()) {
                out[i] =
                        LodCellFile.decodeCluster(
                                span.duplicate()
                                        .order(ByteOrder.LITTLE_ENDIAN)
                                        .position(Math.toIntExact(places.get(i).start() - from)));
            }
        }
        return Arrays.asList(out);
    }
}
