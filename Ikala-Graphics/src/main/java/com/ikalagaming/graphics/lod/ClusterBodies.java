package com.ikalagaming.graphics.lod;

import lombok.NonNull;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Pieces' clusters with their triangles, read from the cache together the first time any is asked
 * for, by whichever thread asks: for a check of a whole cut, which runs off the render thread.
 */
public final class ClusterBodies extends AbstractList<LodDag.Cluster> {

    /** The clusters. */
    private final List<LodStore.PieceCluster> pieces;

    /** Guards loading. */
    private final ReentrantLock lock = new ReentrantLock();

    /** The clusters with their triangles, once loaded. */
    private volatile List<LodDag.Cluster> loaded;

    /**
     * Wrap clusters whose triangles may be in the cache.
     *
     * @param pieces The clusters, which aren't copied.
     */
    public ClusterBodies(@NonNull List<LodStore.PieceCluster> pieces) {
        this.pieces = pieces;
    }

    @Override
    public LodDag.Cluster get(int index) {
        return loaded().get(index);
    }

    @Override
    public int size() {
        return pieces.size();
    }

    /**
     * The clusters with their triangles, loading them the first time.
     *
     * @return They.
     */
    private List<LodDag.Cluster> loaded() {
        List<LodDag.Cluster> out = loaded;
        if (out != null) {
            return out;
        }
        lock.lock();
        try {
            if (loaded == null) {
                final List<ClusterFile> places = new ArrayList<>();
                for (LodStore.PieceCluster piece : pieces) {
                    if (piece.paged()) {
                        places.add(piece.file());
                    }
                }
                final List<LodDag.Cluster> read = ClusterFile.readAll(places);
                final List<LodDag.Cluster> all = new ArrayList<>(pieces.size());
                int next = 0;
                for (LodStore.PieceCluster piece : pieces) {
                    all.add(piece.paged() ? read.get(next++) : piece.body());
                }
                loaded = all;
            }
            return loaded;
        } finally {
            lock.unlock();
        }
    }
}
