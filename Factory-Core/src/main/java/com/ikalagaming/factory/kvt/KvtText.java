package com.ikalagaming.factory.kvt;

import lombok.NonNull;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.BufferedTokenStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * A KVT file's text with where each value is in it, for editing a file in place: a value replaced
 * where it is, a key added to an object or taken out of it, so the rest of the text (its layout,
 * comments and the way numbers are written) stays as it was. Values are found by path: an object's
 * key after a period, a list's index in brackets, like {@code args[1].arg}; the file's own object
 * is the empty path.
 */
public final class KvtText {

    /**
     * Where something is in the text.
     *
     * @param start Its first character's index.
     * @param end The index after its last character.
     */
    public record Span(int start, int end) {}

    /**
     * Where an object's entry is.
     *
     * @param entry From its key to the end of its value.
     * @param value Its value.
     */
    private record Entry(Span entry, Span value) {}

    /**
     * Where an object is, and its entries in order.
     *
     * @param span From its opening brace to after its closing one.
     * @param entries Its entries' keys, in order.
     */
    private record ObjectSpan(Span span, List<String> entries) {}

    /** A change to the text. */
    private record Edit(int start, int end, String text) {}

    /** The text. */
    private final String text;

    /** Each value's span, by path. */
    private final Map<String, Span> values = new HashMap<>();

    /** Each object's entries, by the object's path. */
    private final Map<String, ObjectSpan> objects = new HashMap<>();

    /** Each entry's span, by the entry's value's path. */
    private final Map<String, Entry> entries = new HashMap<>();

    /** The edits so far, applied by {@link #result()}. */
    private final List<Edit> edits = new ArrayList<>();

    /**
     * Index a file's text.
     *
     * @param text The text.
     */
    private KvtText(String text) {
        this.text = text;
    }

    /**
     * Parse a file's text, finding where each value is.
     *
     * @param text The text.
     * @return The indexed text, or empty if it isn't valid KVT.
     */
    public static Optional<KvtText> parse(@NonNull String text) {
        final boolean[] failed = {false};
        final BaseErrorListener listener =
                new BaseErrorListener() {
                    @Override
                    public void syntaxError(
                            Recognizer<?, ?> recognizer,
                            Object offendingSymbol,
                            int line,
                            int position,
                            String message,
                            RecognitionException e) {
                        failed[0] = true;
                    }
                };
        final KVTLexer lexer = new KVTLexer(CharStreams.fromString(text));
        lexer.removeErrorListeners();
        lexer.addErrorListener(listener);
        final KVTParser parser = new KVTParser(new BufferedTokenStream(lexer));
        parser.removeErrorListeners();
        parser.addErrorListener(listener);
        final KVTParser.CompilationUnitContext unit = parser.compilationUnit();
        if (failed[0] || unit.node() == null) {
            return Optional.empty();
        }
        final KvtText indexed = new KvtText(text);
        indexed.indexValue("", unit.node());
        return Optional.of(indexed);
    }

    /**
     * Index a value and everything in it.
     *
     * @param path Its path.
     * @param context Its parse tree: a node, an array or a literal.
     */
    private void indexValue(String path, ParserRuleContext context) {
        values.put(path, span(context));
        if (context instanceof KVTParser.ValueContext value) {
            if (value.node() != null) {
                indexValue(path, value.node());
            } else if (value.array() != null) {
                indexValue(path, value.array());
            }
            return;
        }
        if (context instanceof KVTParser.NodeContext node) {
            final List<String> keys = new ArrayList<>();
            if (node.entryList() != null) {
                for (KVTParser.EntryContext entry : node.entryList().entry()) {
                    final String key = unquote(entry.key().getText());
                    keys.add(key);
                    final String child = path.isEmpty() ? key : path + "." + key;
                    entries.put(child, new Entry(span(entry), span(entry.value())));
                    indexValue(child, entry.value());
                }
            }
            objects.put(path, new ObjectSpan(span(node), keys));
        } else if (context instanceof KVTParser.ArrayContext array
                && array.arrayElements() != null) {
            final List<KVTParser.ValueContext> elements = array.arrayElements().value();
            for (int i = 0; i < elements.size(); ++i) {
                indexValue(path + "[" + i + "]", elements.get(i));
            }
        }
    }

    /**
     * Where a parse tree is in the text.
     *
     * @param context The tree.
     * @return Its span.
     */
    private static Span span(ParserRuleContext context) {
        return new Span(context.getStart().getStartIndex(), context.getStop().getStopIndex() + 1);
    }

    /**
     * A key without its quotes, if it is quoted.
     *
     * @param key The key as written.
     * @return The key.
     */
    private static String unquote(String key) {
        return key.length() >= 2 && key.startsWith("\"") && key.endsWith("\"")
                ? key.substring(1, key.length() - 1)
                : key;
    }

    /**
     * The text of a value as written.
     *
     * @param path The value's path.
     * @return The text, or empty if there is no such value.
     */
    public Optional<String> valueText(@NonNull String path) {
        final Span span = values.get(path);
        return span == null
                ? Optional.empty()
                : Optional.of(text.substring(span.start(), span.end()));
    }

    /**
     * Whether a value is in the text.
     *
     * @param path The value's path.
     * @return True if it is.
     */
    public boolean has(@NonNull String path) {
        return values.containsKey(path);
    }

    /**
     * Replace a value with other text.
     *
     * @param path The value's path.
     * @param replacement The new value's KVT text.
     * @throws IllegalArgumentException If there is no such value.
     */
    public void replace(@NonNull String path, @NonNull String replacement) {
        final Span span = values.get(path);
        if (span == null) {
            throw new IllegalArgumentException("No value at " + path);
        }
        edits.add(new Edit(span.start(), span.end(), replacement));
    }

    /**
     * Add a key to an object, after its last entry.
     *
     * @param objectPath The object's path.
     * @param key The key.
     * @param valueText Its value's KVT text.
     * @throws IllegalArgumentException If there is no such object.
     */
    public void add(@NonNull String objectPath, @NonNull String key, @NonNull String valueText) {
        final ObjectSpan object = objects.get(objectPath);
        if (object == null) {
            throw new IllegalArgumentException("No object at " + objectPath);
        }
        final String entryText = Node.formatKey(key) + ":" + valueText;
        if (object.entries().isEmpty()) {
            edits.add(new Edit(object.span().start() + 1, object.span().start() + 1, entryText));
            return;
        }
        final String last = object.entries().getLast();
        final Entry entry = entries.get(objectPath.isEmpty() ? last : objectPath + "." + last);
        edits.add(new Edit(entry.entry().end(), entry.entry().end(), ", " + entryText));
    }

    /**
     * Take a key out of its object, with the comma that separates it from the others.
     *
     * @param path The key's value's path.
     * @throws IllegalArgumentException If there is no such key.
     */
    public void remove(@NonNull String path) {
        final Entry entry = entries.get(path);
        if (entry == null) {
            throw new IllegalArgumentException("No key at " + path);
        }
        final int dot = path.lastIndexOf('.');
        final String parent = dot < 0 ? "" : path.substring(0, dot);
        final String key = dot < 0 ? path : path.substring(dot + 1);
        final List<String> keys = objects.get(parent).entries();
        final int index = keys.indexOf(key);
        if (keys.size() == 1) {
            edits.add(new Edit(entry.entry().start(), entry.entry().end(), ""));
        } else if (index > 0) {
            // From the end of the entry before, so its comma goes with this one
            final String before = keys.get(index - 1);
            final Entry previous = entries.get(parent.isEmpty() ? before : parent + "." + before);
            edits.add(new Edit(previous.entry().end(), entry.entry().end(), ""));
        } else {
            // The first: up to the start of the next
            final String after = keys.get(index + 1);
            final Entry next = entries.get(parent.isEmpty() ? after : parent + "." + after);
            edits.add(new Edit(entry.entry().start(), next.entry().start(), ""));
        }
    }

    /**
     * The text with the edits made, later ones in the text first so earlier offsets hold. Edits
     * mustn't overlap.
     *
     * @return The new text.
     */
    public String result() {
        final List<Edit> ordered = new ArrayList<>(edits);
        ordered.sort(Comparator.comparingInt(Edit::start).thenComparingInt(Edit::end).reversed());
        final StringBuilder out = new StringBuilder(text);
        @Nullable Edit previous = null;
        for (Edit edit : ordered) {
            if (previous != null && edit.end() > previous.start()) {
                throw new IllegalStateException("Overlapping edits at " + edit.start());
            }
            out.replace(edit.start(), edit.end(), edit.text());
            previous = edit;
        }
        return out.toString();
    }
}
