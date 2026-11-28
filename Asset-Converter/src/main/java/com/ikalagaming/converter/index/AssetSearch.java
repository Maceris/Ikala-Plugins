package com.ikalagaming.converter.index;

import lombok.NonNull;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Finding indexed files: by type, namespace and folder, and by a fuzzy match of their name or ID,
 * where the query's letters must appear in order, and matches that run together, start words or
 * come early rank first.
 */
public final class AssetSearch {
    /** What a matched letter scores. */
    private static final int LETTER = 10;

    /** What a letter right after the last matched one adds. */
    private static final int RUN = 8;

    /** What a letter starting a word adds. */
    private static final int WORD_START = 6;

    /** Characters that end a word. */
    private static final String SEPARATORS = "/\\_-.: ";

    /** Not made. */
    private AssetSearch() {}

    /**
     * What to find.
     *
     * @param text The fuzzy text, empty for everything.
     * @param type The type, or null for any.
     * @param namespace The namespace, or null for any; empty for files without one.
     * @param folder The folder the files are in, at any depth, or null for every root.
     */
    public record Query(
            @NonNull String text,
            @Nullable String type,
            @Nullable String namespace,
            @Nullable Path folder) {
        /** Everything. */
        public static final Query ALL = new Query("", null, null, null);
    }

    /**
     * How well a text matches a query: its letters in order, case aside.
     *
     * @param query The query.
     * @param text The text.
     * @return The score, higher for better, or -1 if it doesn't match; 0 for an empty query.
     */
    public static int score(@NonNull String query, @NonNull String text) {
        final String q = query.toLowerCase(Locale.ROOT).replace(" ", "");
        if (q.isEmpty()) {
            return 0;
        }
        final String t = text.toLowerCase(Locale.ROOT);
        int score = 0;
        int at = 0;
        int last = -2;
        int first = -1;
        for (int i = 0; i < q.length(); ++i) {
            final int found = t.indexOf(q.charAt(i), at);
            if (found < 0) {
                return -1;
            }
            if (first < 0) {
                first = found;
            }
            score += LETTER;
            if (found == last + 1) {
                score += RUN;
            }
            if (found == 0 || SEPARATORS.indexOf(t.charAt(found - 1)) >= 0) {
                score += WORD_START;
            }
            last = found;
            at = found + 1;
        }
        // Earlier and tighter matches first
        return score * 4 - first - (last - first);
    }

    /**
     * The namespace of a file: its ID's, or empty without one.
     *
     * @param entry The file's entry.
     * @return The namespace, or empty.
     */
    public static String namespaceOf(AssetIndex.@NonNull Entry entry) {
        return entry.id() == null ? "" : entry.id().substring(0, entry.id().indexOf(':'));
    }

    /**
     * Find files.
     *
     * @param snapshot The index.
     * @param query What to find.
     * @param only The files to find among, as a collection holds, or null for every file.
     * @return The files, best match first, or by type then name without fuzzy text.
     */
    public static List<AssetIndex.Entry> find(
            AssetIndex.@NonNull Snapshot snapshot, @NonNull Query query, @Nullable Set<Path> only) {
        record Found(AssetIndex.Entry entry, int score) {}
        final List<Found> found = new ArrayList<>();
        for (AssetIndex.Entry entry : snapshot.entries()) {
            if (only != null && !only.contains(entry.file())) {
                continue;
            }
            if (query.type() != null && !query.type().equals(entry.type())) {
                continue;
            }
            if (query.namespace() != null && !query.namespace().equals(namespaceOf(entry))) {
                continue;
            }
            if (query.folder() != null && !entry.file().startsWith(query.folder())) {
                continue;
            }
            int score = score(query.text(), entry.file().getFileName().toString());
            if (entry.id() != null) {
                score = Math.max(score, score(query.text(), entry.id()));
            }
            if (score >= 0) {
                found.add(new Found(entry, score));
            }
        }
        final Comparator<Found> byName =
                Comparator.comparing(f -> f.entry().file().getFileName().toString());
        found.sort(
                query.text().isBlank()
                        ? Comparator.<Found, String>comparing(f -> f.entry().type())
                                .thenComparing(byName)
                        : Comparator.comparingInt(Found::score).reversed().thenComparing(byName));
        return found.stream().map(Found::entry).toList();
    }
}
