package com.ikalagaming.converter.workbench;

import com.ikalagaming.converter.texgraph.GraphParameter;
import com.ikalagaming.converter.texgraph.SharedParameters;
import com.ikalagaming.converter.texgraph.TextureGraph;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.data.IkString;
import com.ikalagaming.graphics.gui.flags.InputTextFlags;
import com.ikalagaming.graphics.ui.UiFrame;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * The graph panel's parameter section: the shared parameter files a texture graph reads, and every
 * parameter it can read, listed by group and order with a widget for its kind. The graph's own
 * parameters can be added, changed and removed; shared ones are changed here and saved to their
 * files, which bakes the graphs that read them again. Render thread only.
 */
@Slf4j
public class ParameterPanel {
    /** The popup editing a parameter's settings. */
    private static final String EDIT_POPUP = "Parameter settings";

    /** The texture graph. */
    private final TextureGraph texture;

    /** Where the graph's relative file names start, or null while unsaved. */
    private final Supplier<Path> folder;

    /** Told when the graph's parameters changed, so it is unsaved and evaluates again. */
    private final Runnable changed;

    /** Told when shared values changed, which don't change the graph, so it evaluates again. */
    private final Runnable reevaluate;

    /** Told when a shared file was saved, with the file, to bake the graphs reading it. */
    private final Consumer<Path> sharedSaved;

    /** Reports what happened. */
    private final Consumer<String> report;

    /** Changed shared values not saved yet, by file, then name. */
    private final Map<String, Map<String, GraphParameter>> sharedEdits = new LinkedHashMap<>();

    /** Text fields, by key, kept while typed in. */
    private final Map<String, IkString> texts = new HashMap<>();

    /** The new parameter's name. */
    private final IkString newName = new IkString(64);

    /** The new parameter's kind. */
    private GraphParameter.Kind newKind = GraphParameter.Kind.SCALAR;

    /** The parameter whose settings the popup edits, or null. */
    private @Nullable String editing;

    /** Whether to open the settings popup this frame. */
    private boolean openEdit;

    /** Scratch for sliders and fields. */
    private final float[] number = new float[1];

    /** Scratch for color pickers. */
    private final float[] color = new float[4];

    /** Scratch for whole numbers. */
    private final int[] whole = new int[1];

    /** Rolls seeds. */
    private final Random random = new Random();

    /**
     * Set up the section.
     *
     * @param texture The texture graph.
     * @param folder Where its relative file names start, or null while unsaved.
     * @param changed Told when the graph's parameters changed.
     * @param reevaluate Told when shared values changed, to evaluate the graph again.
     * @param sharedSaved Told when a shared file was saved.
     * @param report Reports what happened.
     */
    public ParameterPanel(
            @NonNull TextureGraph texture,
            @NonNull Supplier<Path> folder,
            @NonNull Runnable changed,
            @NonNull Runnable reevaluate,
            @NonNull Consumer<Path> sharedSaved,
            @NonNull Consumer<String> report) {
        this.texture = texture;
        this.folder = folder;
        this.changed = changed;
        this.reevaluate = reevaluate;
        this.sharedSaved = sharedSaved;
        this.report = report;
    }

    /** Forget unsaved shared values and fields, as when another graph opens. */
    public void clear() {
        sharedEdits.clear();
        texture.setSharedOverrides(Map.of());
        texts.clear();
        editing = null;
    }

    /**
     * A parameter as listed: where it comes from and whether it is the graph's own.
     *
     * @param parameter The parameter, with any unsaved shared value.
     * @param file The shared file it comes from, or null for the graph's own.
     */
    private record Listed(GraphParameter parameter, @Nullable String file) {}

    /**
     * Draw the section.
     *
     * @param frame The current frame.
     */
    public void draw(@NonNull UiFrame frame) {
        IkGui.separator();
        IkGui.textUnformatted("Parameters");
        textField(
                "shared",
                "Shared files",
                String.join(", ", texture.getShared()),
                text ->
                        frame.fire(
                                () -> {
                                    final List<String> files = new ArrayList<>();
                                    for (String part : text.split(",")) {
                                        if (!part.isBlank()) {
                                            files.add(part.trim());
                                        }
                                    }
                                    texture.setShared(files);
                                    changed.run();
                                }));
        final List<Listed> listed = listed();
        String group = null;
        for (Listed item : listed) {
            final GraphParameter parameter = item.parameter();
            if (!parameter.group().equals(group)) {
                group = parameter.group();
                if (!group.isEmpty()) {
                    IkGui.textDisabled(group);
                }
            }
            drawValue(frame, item);
        }
        if (!sharedEdits.isEmpty()) {
            if (IkGui.button("Save shared files")) {
                frame.fire(this::saveShared);
            }
            IkGui.sameLine();
            if (IkGui.button("Forget shared changes")) {
                frame.fire(
                        () -> {
                            sharedEdits.clear();
                            texture.setSharedOverrides(Map.of());
                            reevaluate.run();
                        });
            }
        }
        drawAdd(frame);
        drawEditPopup(frame);
    }

    /**
     * Every parameter the graph reads, by group, order and name: shared ones with unsaved values,
     * and its own, which win over shared ones of the same name.
     *
     * @return The parameters.
     */
    private List<Listed> listed() {
        final Map<String, Listed> all = new LinkedHashMap<>();
        final Path base = folder.get();
        for (String file : texture.getShared()) {
            if (base == null) {
                continue;
            }
            try {
                final SharedParameters.Read read = SharedParameters.read(base.resolve(file));
                for (GraphParameter parameter : read.parameters().values()) {
                    final GraphParameter edited =
                            sharedEdits.getOrDefault(file, Map.of()).get(parameter.name());
                    all.put(
                            parameter.name(),
                            new Listed(edited == null ? parameter : edited, file));
                }
            } catch (IOException | RuntimeException e) {
                log.debug("Can't read the shared parameters {}", file, e);
            }
        }
        for (GraphParameter parameter : texture.getParameters()) {
            all.put(parameter.name(), new Listed(parameter, null));
        }
        final List<Listed> out = new ArrayList<>(all.values());
        out.sort(
                Comparator.comparing((Listed l) -> l.parameter().group())
                        .thenComparingInt(l -> l.parameter().order())
                        .thenComparing(l -> l.parameter().name()));
        return out;
    }

    /**
     * Shared parameters with their unsaved values, for evaluating the graph as it will be.
     *
     * @return The changed parameters, by name.
     */
    public Map<String, GraphParameter> unsavedShared() {
        final Map<String, GraphParameter> out = new LinkedHashMap<>();
        for (Map<String, GraphParameter> file : sharedEdits.values()) {
            out.putAll(file);
        }
        return out;
    }

    /**
     * Draw one parameter's value with its kind's widget, and its settings button.
     *
     * @param frame The current frame.
     * @param item The parameter.
     */
    private void drawValue(UiFrame frame, Listed item) {
        final GraphParameter parameter = item.parameter();
        final String label = parameter.name() + (item.file() == null ? "" : " (shared)");
        final String id = "##" + parameter.name();
        IkGui.setNextItemWidth(160);
        switch (parameter.kind()) {
            case SCALAR -> {
                number[0] = parse(parameter.value());
                final boolean moved =
                        parameter.max() > parameter.min()
                                ? IkGui.sliderFloat(
                                        label + id, number, parameter.min(), parameter.max())
                                : IkGui.dragFloat(label + id, number, 0.01f);
                if (moved) {
                    float value = number[0];
                    if (parameter.step() > 0) {
                        value =
                                parameter.min()
                                        + Math.round((value - parameter.min()) / parameter.step())
                                                * parameter.step();
                    }
                    set(frame, item, trim(value));
                }
            }
            case COLOR -> {
                final float[] linear = safeColor(parameter.value());
                for (int c = 0; c < 3; ++c) {
                    color[c] = Srgbs.encode(linear[c]);
                }
                color[3] = linear[3];
                if (IkGui.colorEdit4(label + id, color)) {
                    set(frame, item, hex(color));
                }
            }
            case SWITCH -> {
                if (IkGui.checkbox(label + id, "true".equals(parameter.value()))) {
                    set(frame, item, "true".equals(parameter.value()) ? "false" : "true");
                }
            }
            case CHOICE -> {
                if (IkGui.beginCombo(label + id, parameter.value())) {
                    for (String choice : parameter.choices()) {
                        if (IkGui.selectable(choice, choice.equals(parameter.value()))) {
                            set(frame, item, choice);
                        }
                    }
                    IkGui.endCombo();
                }
            }
            case SEED -> {
                textField(
                        "value/" + parameter.name(),
                        label,
                        parameter.value(),
                        text -> set(frame, item, text.trim()));
                IkGui.sameLine();
                if (IkGui.smallButton("Roll" + id)) {
                    set(frame, item, Long.toString(random.nextLong(1, 1L << 40)));
                }
            }
            case TEXTURE ->
                    textField(
                            "value/" + parameter.name(),
                            label,
                            parameter.value(),
                            text -> set(frame, item, text.trim()));
        }
        if (item.file() == null) {
            IkGui.sameLine();
            if (IkGui.smallButton("..." + id)) {
                editing = parameter.name();
                openEdit = true;
            }
        }
    }

    /**
     * Change a parameter's value, if it can hold it.
     *
     * @param frame The current frame.
     * @param item The parameter.
     * @param value The new value.
     */
    private void set(UiFrame frame, Listed item, String value) {
        final GraphParameter parameter = item.parameter();
        final String problem = parameter.check(value);
        if (problem != null) {
            report.accept(parameter.name() + ": " + problem);
            return;
        }
        frame.fire(
                () -> {
                    if (item.file() == null) {
                        texture.putParameter(parameter.withValue(value));
                    } else {
                        sharedEdits
                                .computeIfAbsent(item.file(), f -> new LinkedHashMap<>())
                                .put(parameter.name(), parameter.withValue(value));
                        texture.setSharedOverrides(unsavedShared());
                        reevaluate.run();
                        return;
                    }
                    changed.run();
                });
    }

    /** Write the shared files with unsaved values, then bake the graphs reading them. */
    private void saveShared() {
        final Path base = folder.get();
        if (base == null) {
            return;
        }
        for (Map.Entry<String, Map<String, GraphParameter>> edits : sharedEdits.entrySet()) {
            final Path file = base.resolve(edits.getKey());
            try {
                final Map<String, GraphParameter> parameters =
                        new LinkedHashMap<>(SharedParameters.read(file).parameters());
                parameters.putAll(edits.getValue());
                SharedParameters.save(file, parameters.values());
                sharedSaved.accept(file.toAbsolutePath().normalize());
            } catch (IOException | RuntimeException e) {
                report.accept("Can't save " + edits.getKey() + ": " + e.getMessage());
                return;
            }
        }
        sharedEdits.clear();
        texture.setSharedOverrides(Map.of());
        reevaluate.run();
    }

    /**
     * Draw the row adding a parameter: its name, its kind, and an Add button.
     *
     * @param frame The current frame.
     */
    private void drawAdd(UiFrame frame) {
        IkGui.setNextItemWidth(120);
        IkGui.inputText("##new-parameter", newName);
        IkGui.sameLine();
        IkGui.setNextItemWidth(90);
        if (IkGui.beginCombo("##new-kind", newKind.id)) {
            for (GraphParameter.Kind kind : GraphParameter.Kind.values()) {
                if (IkGui.selectable(kind.id, kind == newKind)) {
                    newKind = kind;
                }
            }
            IkGui.endCombo();
        }
        IkGui.sameLine();
        if (IkGui.button("Add parameter")) {
            final String name = newName.get().trim();
            final GraphParameter.Kind kind = newKind;
            frame.fire(() -> add(name, kind));
        }
    }

    /**
     * Add a parameter to the graph.
     *
     * @param name Its name.
     * @param kind Its kind.
     * @return Null if added, else why not.
     */
    public @Nullable String add(@NonNull String name, GraphParameter.@NonNull Kind kind) {
        if (texture.parameter(name) != null) {
            report.accept("There is already a parameter " + name);
            return "taken";
        }
        try {
            texture.putParameter(GraphParameter.of(name, kind));
        } catch (IllegalArgumentException e) {
            report.accept(e.getMessage());
            return e.getMessage();
        }
        newName.set("", true);
        changed.run();
        return null;
    }

    /**
     * Draw the popup editing a parameter's settings: its kind, group, order, range and choices, and
     * removing it.
     *
     * @param frame The current frame.
     */
    private void drawEditPopup(UiFrame frame) {
        if (openEdit) {
            IkGui.openPopup(EDIT_POPUP);
            openEdit = false;
        }
        if (!IkGui.beginPopup(EDIT_POPUP)) {
            return;
        }
        final GraphParameter parameter = editing == null ? null : texture.parameter(editing);
        if (parameter == null) {
            IkGui.closeCurrentPopup();
            IkGui.endPopup();
            return;
        }
        IkGui.textUnformatted(parameter.name());
        IkGui.setNextItemWidth(120);
        if (IkGui.beginCombo("Type", parameter.kind().id)) {
            for (GraphParameter.Kind kind : GraphParameter.Kind.values()) {
                if (IkGui.selectable(kind.id, kind == parameter.kind())) {
                    frame.fire(
                            () ->
                                    replace(
                                            new GraphParameter(
                                                    parameter.name(),
                                                    kind,
                                                    kind.initial,
                                                    parameter.min(),
                                                    parameter.max(),
                                                    parameter.step(),
                                                    parameter.choices(),
                                                    parameter.group(),
                                                    parameter.order())));
                }
            }
            IkGui.endCombo();
        }
        textField(
                "group/" + parameter.name(),
                "Group",
                parameter.group(),
                text ->
                        frame.fire(
                                () ->
                                        replace(
                                                settings(
                                                        parameter,
                                                        null,
                                                        null,
                                                        null,
                                                        text.trim(),
                                                        null,
                                                        null))));
        whole[0] = parameter.order();
        IkGui.setNextItemWidth(120);
        if (IkGui.inputInt("Order", whole)) {
            final int order = whole[0];
            frame.fire(() -> replace(settings(parameter, null, null, null, null, order, null)));
        }
        if (parameter.kind() == GraphParameter.Kind.SCALAR) {
            rangeField(frame, parameter, "Min", parameter.min(), 0);
            rangeField(frame, parameter, "Max", parameter.max(), 1);
            rangeField(frame, parameter, "Step", parameter.step(), 2);
        }
        if (parameter.kind() == GraphParameter.Kind.CHOICE) {
            textField(
                    "choices/" + parameter.name(),
                    "Choices",
                    String.join(", ", parameter.choices()),
                    text ->
                            frame.fire(
                                    () -> {
                                        final List<String> choices = new ArrayList<>();
                                        for (String part : text.split(",")) {
                                            if (!part.isBlank()) {
                                                choices.add(part.trim());
                                            }
                                        }
                                        final GraphParameter withChoices =
                                                settings(
                                                        parameter, null, null, null, null, null,
                                                        choices);
                                        replace(
                                                choices.contains(withChoices.value())
                                                                || choices.isEmpty()
                                                        ? withChoices
                                                        : withChoices.withValue(
                                                                choices.getFirst()));
                                    }));
        }
        if (IkGui.button("Remove")) {
            frame.fire(
                    () -> {
                        texture.removeParameter(parameter.name());
                        changed.run();
                    });
            IkGui.closeCurrentPopup();
        }
        IkGui.endPopup();
    }

    /**
     * Draw one of a scalar's range fields.
     *
     * @param frame The current frame.
     * @param parameter The parameter.
     * @param label The field's label.
     * @param value Its value.
     * @param which 0 for min, 1 for max, 2 for step.
     */
    private void rangeField(
            UiFrame frame, GraphParameter parameter, String label, float value, int which) {
        number[0] = value;
        IkGui.setNextItemWidth(120);
        if (IkGui.inputFloat(label, number)) {
            final float set = number[0];
            frame.fire(
                    () ->
                            replace(
                                    settings(
                                            parameter,
                                            which == 0 ? set : null,
                                            which == 1 ? set : null,
                                            which == 2 ? Math.max(0, set) : null,
                                            null,
                                            null,
                                            null)));
        }
    }

    /**
     * A parameter with some settings changed.
     *
     * @param parameter The parameter.
     * @param min A new min, or null to keep it.
     * @param max A new max, or null.
     * @param step A new step, or null.
     * @param group A new group, or null.
     * @param order A new order, or null.
     * @param choices New choices, or null.
     * @return The changed parameter.
     */
    private static GraphParameter settings(
            GraphParameter parameter,
            @Nullable Float min,
            @Nullable Float max,
            @Nullable Float step,
            @Nullable String group,
            @Nullable Integer order,
            @Nullable List<String> choices) {
        return new GraphParameter(
                parameter.name(),
                parameter.kind(),
                parameter.value(),
                min == null ? parameter.min() : min,
                max == null ? parameter.max() : max,
                step == null ? parameter.step() : step,
                choices == null ? parameter.choices() : choices,
                group == null ? parameter.group() : group,
                order == null ? parameter.order() : order);
    }

    /**
     * Replace one of the graph's parameters.
     *
     * @param parameter The new parameter.
     */
    private void replace(GraphParameter parameter) {
        texture.putParameter(parameter);
        changed.run();
    }

    /**
     * A text field that keeps its text while typed in and reports it when left or entered.
     *
     * @param key Which field.
     * @param label Its label.
     * @param value The value now.
     * @param edited Gets the new text.
     */
    private void textField(String key, String label, String value, Consumer<String> edited) {
        final IkString text = texts.computeIfAbsent(key, k -> new IkString(256));
        if (!key.equals(active) && !text.get().equals(value)) {
            // Changed some other way while it wasn't being typed in
            text.set(value, true);
        }
        IkGui.setNextItemWidth(160);
        final boolean entered =
                IkGui.inputText(label + "##" + key, text, InputTextFlags.ENTER_RETURNS_TRUE);
        if (IkGui.isItemActive()) {
            active = key;
        } else if (key.equals(active)) {
            active = null;
        }
        if ((entered || IkGui.isItemDeactivatedAfterEdit()) && !text.get().equals(value)) {
            edited.accept(text.get());
        }
    }

    /** The field being typed in, or null. */
    private @Nullable String active;

    /**
     * A number from text, or 0.
     *
     * @param text The text.
     * @return The number.
     */
    private static float parse(String text) {
        try {
            return Float.parseFloat(text.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * A number as short text.
     *
     * @param value The number.
     * @return Up to four places, without trailing zeros.
     */
    private static String trim(float value) {
        String text = String.format(Locale.ROOT, "%.4f", value);
        text = text.replaceAll("0+$", "");
        return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
    }

    /**
     * A color parameter's value, or gray if it can't be read.
     *
     * @param text The value.
     * @return Linear red, green, blue and alpha.
     */
    private static float[] safeColor(String text) {
        try {
            return GraphParameter.color(text);
        } catch (RuntimeException e) {
            final float[] gray = new float[4];
            Arrays.fill(gray, 0.2f);
            gray[3] = 1;
            return gray;
        }
    }

    /**
     * An sRGB color as hex.
     *
     * @param rgba sRGB red, green and blue, and alpha, 0 to 1.
     * @return {@code #RRGGBB}, or {@code #RRGGBBAA} when not opaque.
     */
    private static String hex(float[] rgba) {
        final StringBuilder out = new StringBuilder("#");
        final int channels = rgba[3] >= 1 ? 3 : 4;
        for (int c = 0; c < channels; ++c) {
            out.append(
                    String.format(
                            Locale.ROOT, "%02x", Math.round(Math.clamp(rgba[c], 0, 1) * 255)));
        }
        return out.toString();
    }

    /** sRGB encoding for the color pickers, which show encoded values. */
    private static final class Srgbs {
        /** Not made. */
        private Srgbs() {}

        /**
         * Encode a linear value.
         *
         * @param linear The value.
         * @return The encoded value, 0 to 1.
         */
        static float encode(float linear) {
            return com.ikalagaming.converter.texgraph.Srgb.encode(linear) / 255f;
        }
    }
}
