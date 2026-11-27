package com.ikalagaming.converter.workbench;

import com.ikalagaming.converter.inspector.ContainerFile;
import com.ikalagaming.converter.inspector.HexDump;
import com.ikalagaming.graphics.asset.AssetHeader;
import com.ikalagaming.graphics.asset.AssetMetadata;
import com.ikalagaming.graphics.asset.AssetValidator;
import com.ikalagaming.graphics.asset.SectionTag;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.flags.SelectableFlags;
import com.ikalagaming.graphics.gui.flags.TableFlags;
import com.ikalagaming.graphics.gui.flags.TreeNodeFlags;
import com.ikalagaming.graphics.gui.util.Color;
import com.ikalagaming.graphics.ui.UiFrame;
import com.ikalagaming.graphics.ui.style.ActiveTheme;
import com.ikalagaming.graphics.ui.style.Theme;
import com.ikalagaming.graphics.ui.style.Token;

import lombok.Getter;
import lombok.NonNull;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Shows a {@code .ika} container: its header and metadata, its section table with checksums, and
 * its problems. Picking a section shows its bytes in the properties panel. Render thread only.
 */
public class ContainerViewer implements Viewer {
    /** How many bytes of a section the hex view shows. */
    static final int PREVIEW_BYTES = 512;

    /** Error text, if the theme has no {@code converter.error} token. */
    private static final int ERROR_COLOR = Color.rgba(1.0f, 0.35f, 0.35f, 1.0f);

    /** Warning text, if the theme has no {@code converter.warning} token. */
    private static final int WARNING_COLOR = Color.rgba(1.0f, 0.85f, 0.3f, 1.0f);

    /** Offsets in the hex view, if the theme has no {@code converter.offset} token. */
    private static final int OFFSET_COLOR = Color.rgba(0.6f, 0.6f, 0.7f, 1.0f);

    /** Good news text, if the theme has no {@code converter.ok} token. */
    private static final int OK_COLOR = Color.rgba(0.45f, 0.9f, 0.45f, 1.0f);

    /** The columns of the section table. */
    private static final String[] SECTION_COLUMNS = {
        "#", "Tag", "Version", "Flags", "Offset", "Length", "Checksum", "Known"
    };

    /**
     * What was read.
     *
     * @return The container.
     */
    @Getter private final ContainerFile container;

    /**
     * The selected section, an index into the inspection's entries.
     *
     * @return The index, or -1 for none.
     */
    @Getter private int selected = -1;

    /** Error text this frame, from the theme. */
    private int errorColor = ERROR_COLOR;

    /** Warning text this frame, from the theme. */
    private int warningColor = WARNING_COLOR;

    /** Hex view offsets this frame, from the theme. */
    private int offsetColor = OFFSET_COLOR;

    /** Good news text this frame, from the theme. */
    private int okColor = OK_COLOR;

    /**
     * Show a container.
     *
     * @param container What was read.
     */
    public ContainerViewer(@NonNull ContainerFile container) {
        this.container = container;
    }

    /**
     * The converter's own theme tokens for the viewer's colors, to add with {@code UI.addStyles}.
     * They point at the default theme's tokens, so a game theme that changes those changes the
     * viewer too.
     *
     * @return The theme extension.
     */
    public static Theme styles() {
        return Theme.builder("converter")
                .token("converter.error", new Token("color.error-text"))
                .token("converter.warning", new Token("color.warning"))
                .token("converter.ok", new Token("color.ok"))
                .token("converter.offset", "#9999B3FF")
                .build();
    }

    @Override
    public String kind() {
        return "Container";
    }

    @Override
    public @Nullable Path file() {
        return container.path();
    }

    @Override
    public void close() {
        // Holds nothing but memory
    }

    /**
     * Select a section to show the bytes of.
     *
     * @param index The entry index, or -1 for none; out of range selects none.
     */
    public void select(int index) {
        final int count = container.inspection().entries().size();
        selected = index >= 0 && index < count ? index : -1;
    }

    /**
     * The selected entry.
     *
     * @return The entry, or null if none is selected.
     */
    public @Nullable AssetValidator.Entry selectedEntry() {
        return selected < 0 ? null : container.inspection().entries().get(selected);
    }

    /**
     * Read this frame's colors from the theme.
     *
     * @param frame The current frame.
     */
    private void readColors(UiFrame frame) {
        final ActiveTheme theme = frame.theme();
        errorColor = theme.color("converter.error", ERROR_COLOR);
        warningColor = theme.color("converter.warning", WARNING_COLOR);
        offsetColor = theme.color("converter.offset", OFFSET_COLOR);
        okColor = theme.color("converter.ok", OK_COLOR);
    }

    @Override
    public void drawContent(@NonNull UiFrame frame) {
        readColors(frame);
        final AssetValidator.Inspection inspection = container.inspection();
        IkGui.textUnformatted(container.summary());
        final String status = container.status();
        IkGui.textColored(
                !inspection.isReadable()
                        ? errorColor
                        : inspection.count(AssetValidator.Severity.WARNING) > 0
                                ? warningColor
                                : okColor,
                status);
        if (IkGui.collapsingHeader("Header and metadata", TreeNodeFlags.DEFAULT_OPEN)) {
            drawMetadata(inspection);
        }
        final String sectionsTitle = "Sections (" + inspection.entries().size() + ")###sections";
        if (IkGui.collapsingHeader(sectionsTitle, TreeNodeFlags.DEFAULT_OPEN)) {
            drawSections(inspection, frame);
        }
        final String problemsTitle = "Problems (" + inspection.problems().size() + ")###problems";
        if (IkGui.collapsingHeader(problemsTitle, TreeNodeFlags.DEFAULT_OPEN)) {
            if (inspection.problems().isEmpty()) {
                IkGui.textColored(okColor, "None");
            }
            for (AssetValidator.Problem problem : inspection.problems()) {
                IkGui.textColored(
                        problem.severity() == AssetValidator.Severity.ERROR
                                ? errorColor
                                : warningColor,
                        problem.toString());
            }
        }
    }

    @Override
    public void drawProperties(@NonNull UiFrame frame) {
        readColors(frame);
        final AssetValidator.Entry entry = selectedEntry();
        if (entry == null) {
            IkGui.textDisabled("Pick a section to see its bytes");
            return;
        }
        IkGui.separatorText(
                "Section " + entry.index() + " (" + SectionTag.toString(entry.tag()) + ")");
        if (entry.section() == null) {
            IkGui.textColored(errorColor, "The payload is outside the file");
        } else {
            drawHex(entry.section().data());
        }
    }

    /**
     * Draw a hex view as a table, one column per byte, so it lines up without a monospace font.
     *
     * @param data The section payload.
     */
    private void drawHex(ByteBuffer data) {
        final int columns = HexDump.BYTES_PER_LINE + 2;
        if (!IkGui.beginTable(
                "hex", columns, TableFlags.SIZING_FIXED_FIT | TableFlags.BORDERS_INNER_V)) {
            return;
        }
        for (HexDump.Line line : HexDump.lines(data, PREVIEW_BYTES)) {
            IkGui.tableNextRow();
            IkGui.tableNextColumn();
            IkGui.textColored(offsetColor, line.offsetText());
            for (int i = 0; i < HexDump.BYTES_PER_LINE; ++i) {
                IkGui.tableNextColumn();
                if (i < line.bytes().length) {
                    IkGui.textUnformatted(String.format("%02X", line.bytes()[i]));
                }
            }
            IkGui.tableNextColumn();
            IkGui.textUnformatted(line.text());
        }
        IkGui.endTable();
        if (data.remaining() > PREVIEW_BYTES) {
            IkGui.textUnformatted(
                    String.format("... %,d more bytes", data.remaining() - PREVIEW_BYTES));
        }
    }

    /**
     * Draw the header fields and metadata properties as a table.
     *
     * @param inspection The inspection.
     */
    private static void drawMetadata(AssetValidator.Inspection inspection) {
        if (!IkGui.beginTable("metadata", 2, TableFlags.BORDERS | TableFlags.ROW_BACKGROUND)) {
            return;
        }
        final AssetHeader header = inspection.header();
        if (header != null) {
            row("Format version", header.formatMajor() + "." + header.formatMinor());
            row("Header flags", Integer.toString(header.flags()));
            row("File length (header)", Long.toUnsignedString(header.fileLength()));
        } else {
            row("Header", "unreadable");
        }
        final AssetMetadata metadata = inspection.metadata();
        if (metadata != null) {
            for (Map.Entry<String, String> property : metadata.properties().entrySet()) {
                row(property.getKey(), property.getValue());
            }
        } else {
            row("META", "missing or unreadable");
        }
        IkGui.endTable();
    }

    /**
     * Draw one two-column table row.
     *
     * @param name The first column.
     * @param value The second column.
     */
    private static void row(String name, String value) {
        IkGui.tableNextRow();
        IkGui.tableNextColumn();
        IkGui.textUnformatted(name);
        IkGui.tableNextColumn();
        IkGui.textUnformatted(value);
    }

    /**
     * Draw the section table. Clicking a row selects it for the hex view.
     *
     * @param inspection The inspection.
     * @param frame The current frame, for firing actions.
     */
    private void drawSections(AssetValidator.Inspection inspection, UiFrame frame) {
        if (inspection.entries().isEmpty()) {
            IkGui.textUnformatted("The section table couldn't be read");
            return;
        }
        if (!IkGui.beginTable(
                "sections",
                SECTION_COLUMNS.length,
                TableFlags.BORDERS | TableFlags.ROW_BACKGROUND)) {
            return;
        }
        for (String column : SECTION_COLUMNS) {
            IkGui.tableSetupColumn(column);
        }
        IkGui.tableHeadersRow();
        for (AssetValidator.Entry entry : inspection.entries()) {
            IkGui.tableNextRow();
            IkGui.tableNextColumn();
            final int index = entry.index();
            if (IkGui.selectable(
                    index + "###row" + index,
                    index == selected,
                    SelectableFlags.SPAN_ALL_COLUMNS)) {
                frame.fire(() -> select(index));
            }
            IkGui.tableNextColumn();
            IkGui.textUnformatted(SectionTag.toString(entry.tag()));
            IkGui.tableNextColumn();
            IkGui.textUnformatted(Integer.toString(entry.version()));
            IkGui.tableNextColumn();
            IkGui.textUnformatted(entry.isRequired() ? "required" : "optional");
            IkGui.tableNextColumn();
            IkGui.textUnformatted(Long.toUnsignedString(entry.offset()));
            IkGui.tableNextColumn();
            IkGui.textUnformatted(Long.toUnsignedString(entry.length()));
            IkGui.tableNextColumn();
            if (entry.actualCrc() == null) {
                IkGui.textColored(errorColor, "out of bounds");
            } else if (entry.crcMatches()) {
                IkGui.textColored(okColor, String.format("%08X ok", entry.storedCrc()));
            } else {
                // The expected value is in the problems list, which has room for it
                IkGui.textColored(errorColor, String.format("%08X bad", entry.actualCrc()));
            }
            IkGui.tableNextColumn();
            if (entry.known()) {
                IkGui.textUnformatted("yes");
            } else if (entry.isRequired()) {
                IkGui.textColored(errorColor, "no");
            } else {
                IkGui.textColored(warningColor, "no, skipped");
            }
        }
        IkGui.endTable();
    }
}
