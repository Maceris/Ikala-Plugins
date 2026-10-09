package com.ikalagaming.converter.inspector;

import com.ikalagaming.graphics.UI;
import com.ikalagaming.graphics.asset.AssetHeader;
import com.ikalagaming.graphics.asset.AssetMetadata;
import com.ikalagaming.graphics.asset.AssetValidator;
import com.ikalagaming.graphics.asset.SectionTag;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.flags.SelectableFlags;
import com.ikalagaming.graphics.gui.flags.TableFlags;
import com.ikalagaming.graphics.gui.flags.TreeNodeFlags;
import com.ikalagaming.graphics.gui.util.Color;
import com.ikalagaming.graphics.ui.Align;
import com.ikalagaming.graphics.ui.Anchors;
import com.ikalagaming.graphics.ui.Button;
import com.ikalagaming.graphics.ui.Column;
import com.ikalagaming.graphics.ui.Immediate;
import com.ikalagaming.graphics.ui.Insets;
import com.ikalagaming.graphics.ui.Label;
import com.ikalagaming.graphics.ui.Row;
import com.ikalagaming.graphics.ui.Scroll;
import com.ikalagaming.graphics.ui.Selectable;
import com.ikalagaming.graphics.ui.Sizing;
import com.ikalagaming.graphics.ui.Surface;
import com.ikalagaming.graphics.ui.TextInput;
import com.ikalagaming.graphics.ui.UiFrame;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * The asset inspector: browse for a {@code .ika} container and see its header, metadata, sections
 * and problems, with a hex preview of the selected section. Built on retained UI, with the detail
 * tables drawn in an {@link Immediate} area.
 */
@Slf4j
public class AssetInspectorWindow {
    /** The ID of the inspector surface. */
    public static final String SURFACE_ID = "converter/asset-inspector";

    /** How many bytes of a section the hex preview shows. */
    private static final int PREVIEW_BYTES = 512;

    /** Error text. */
    private static final int ERROR_COLOR = Color.rgba(1.0f, 0.35f, 0.35f, 1.0f);

    /** Warning text. */
    private static final int WARNING_COLOR = Color.rgba(1.0f, 0.85f, 0.3f, 1.0f);

    /** Offsets in the hex preview. */
    private static final int OFFSET_COLOR = Color.rgba(0.6f, 0.6f, 0.7f, 1.0f);

    /** Good news text. */
    private static final int OK_COLOR = Color.rgba(0.45f, 0.9f, 0.45f, 1.0f);

    /** What is being browsed and shown. */
    private final AssetInspector inspector;

    /** The UI the window is shown through. */
    private final UI ui;

    /** The path of the folder or file being looked at. */
    private final TextInput path;

    /** The folder's contents. */
    private final Column files;

    /** Switches between containers only and every file. */
    private final Button filter;

    /** The open file's name, size, version and exporter. */
    private final Label summary;

    /** The count of problems, or a reason nothing is open. */
    private final Label status;

    /**
     * Create the inspector, browsing the working directory.
     *
     * @param ui The UI to show the window through.
     */
    public AssetInspectorWindow(@NonNull UI ui) {
        this.ui = ui;
        inspector = new AssetInspector(Path.of(""));
        path = new TextInput("path", "").onSubmit(this::openPath);
        files = new Column("files").align(Align.STRETCH);
        filter = new Button("filter", "").onClick(this::toggleFilter);
        summary = new Label("summary", "");
        status = new Label("status", "");
        refresh();
    }

    /**
     * Build the inspector surface, ready to show.
     *
     * @return The surface.
     */
    public Surface build() {
        Row toolbar =
                new Row("toolbar")
                        .gap(8)
                        .add(
                                path,
                                new Button("up", "Up").onClick(this::up),
                                new Button("open", "Open").onClick(() -> openPath(path.text())),
                                filter,
                                new Button("samples", "Write samples").onClick(this::samples));

        Row drives = new Row("drives").gap(4);
        List<Path> roots = inspector.roots();
        if (roots.size() > 1) {
            for (int i = 0; i < roots.size(); ++i) {
                Path root = roots.get(i);
                drives.add(new Button("root" + i, root.toString()).onClick(() -> navigate(root)));
            }
        }

        Column browser =
                new Column("browser")
                        .width(Sizing.fixed(260))
                        .height(Sizing.grow())
                        .gap(4)
                        .align(Align.STRETCH)
                        .add(drives, new Scroll("list").border(true).content(files));

        Column details =
                new Column("details")
                        .width(Sizing.grow())
                        .height(Sizing.grow())
                        .gap(4)
                        .align(Align.STRETCH)
                        .add(summary, status, new Immediate("tables", this::drawDetails));

        Column content =
                new Column("content")
                        .padding(Insets.all(4))
                        .gap(8)
                        .align(Align.STRETCH)
                        .add(
                                toolbar,
                                new Row("body")
                                        .height(Sizing.grow())
                                        .gap(8)
                                        .align(Align.STRETCH)
                                        .add(browser, details));

        return ui.surface(SURFACE_ID)
                .anchors(Anchors.center())
                .width(Sizing.percent(0.7f))
                .height(Sizing.percent(0.8f))
                .movable()
                .content(content);
    }

    /**
     * Browse a folder.
     *
     * @param folder The folder.
     */
    private void navigate(Path folder) {
        inspector.navigate(folder);
        refresh();
    }

    /** Browse the parent folder. */
    private void up() {
        inspector.up();
        refresh();
    }

    /**
     * Browse a folder or open a file, from a typed path.
     *
     * @param text The path.
     */
    private void openPath(String text) {
        inspector.openPath(text);
        refresh();
    }

    /**
     * Open a file.
     *
     * @param file The file.
     */
    private void open(Path file) {
        inspector.open(file);
        refresh();
    }

    /** Switch between listing containers and every file. */
    private void toggleFilter() {
        inspector.setShowAllFiles(!inspector.isShowAllFiles());
        refresh();
    }

    /** Write the samples and browse their folder. */
    private void samples() {
        Path folder = Path.of(AssetSamples.FOLDER);
        try {
            AssetSamples.write(folder);
            navigate(folder);
        } catch (IOException e) {
            log.warn("Could not write the asset samples", e);
            inspector.navigate(folder);
            refresh();
        }
    }

    /** Update the retained nodes from the inspector, after anything changes. */
    private void refresh() {
        Path file = inspector.getFile();
        path.text((file != null ? file : inspector.getDirectory()).toString());
        filter.text(inspector.isShowAllFiles() ? "Show .ika only" : "Show all files");

        files.clear();
        if (inspector.getDirectory().getParent() != null) {
            files.add(new Selectable("parent", "../").onClick(this::up));
        }
        int index = 0;
        for (AssetInspector.DirectoryEntry entry : inspector.getEntries()) {
            String id = "entry" + index++;
            if (entry.directory()) {
                files.add(
                        new Selectable(id, entry.name() + "/")
                                .onClick(() -> navigate(entry.path())));
            } else {
                files.add(
                        new Selectable(id, entry.name())
                                .selected(entry.path().equals(file))
                                .onClick(() -> open(entry.path())));
            }
        }

        AssetValidator.Inspection inspection = inspector.getInspection();
        if (file == null || inspection == null) {
            summary.text("No file open");
        } else {
            summary.text(
                    String.format(
                            "%s, %,d bytes%s",
                            file.getFileName(), inspector.getFileSize(), describe(inspection)));
        }
        if (inspector.getError() != null) {
            status.text(inspector.getError());
        } else if (inspection == null) {
            status.text("Pick a .ika file, or write the samples to have something to look at");
        } else if (inspection.isReadable()) {
            long warnings = inspection.count(AssetValidator.Severity.WARNING);
            status.text(warnings == 0 ? "OK" : "Readable, " + plural(warnings, "warning"));
        } else {
            status.text(
                    "Not readable: "
                            + plural(inspection.count(AssetValidator.Severity.ERROR), "error")
                            + ", "
                            + plural(inspection.count(AssetValidator.Severity.WARNING), "warning"));
        }
    }

    /**
     * A count and a noun, plural unless the count is one.
     *
     * @param count The count.
     * @param noun The singular noun.
     * @return For example "1 error" or "2 errors".
     */
    private static String plural(long count, String noun) {
        return count + " " + noun + (count == 1 ? "" : "s");
    }

    /**
     * The version and exporter of an inspected file, for the summary.
     *
     * @param inspection The inspection.
     * @return Text to add after the file name.
     */
    private static String describe(AssetValidator.Inspection inspection) {
        StringBuilder text = new StringBuilder();
        AssetHeader header = inspection.header();
        if (header != null) {
            text.append(
                    String.format(", format %d.%d", header.formatMajor(), header.formatMinor()));
        }
        AssetMetadata metadata = inspection.metadata();
        if (metadata != null && metadata.exporter() != null) {
            text.append(", from ").append(metadata.exporter());
            if (metadata.exporterVersion() != null) {
                text.append(' ').append(metadata.exporterVersion());
            }
        }
        return text.toString();
    }

    /**
     * Draw the detail tables with plain IkGui calls.
     *
     * @param frame The current frame, for firing actions.
     */
    private void drawDetails(UiFrame frame) {
        AssetValidator.Inspection inspection = inspector.getInspection();
        if (inspection == null) {
            return;
        }
        if (IkGui.collapsingHeader("Header and metadata", TreeNodeFlags.DEFAULT_OPEN)) {
            drawMetadata(inspection);
        }
        String sectionsTitle = "Sections (" + inspection.entries().size() + ")###sections";
        if (IkGui.collapsingHeader(sectionsTitle, TreeNodeFlags.DEFAULT_OPEN)) {
            drawSections(inspection, frame);
        }
        String problemsTitle = "Problems (" + inspection.problems().size() + ")###problems";
        if (IkGui.collapsingHeader(problemsTitle, TreeNodeFlags.DEFAULT_OPEN)) {
            if (inspection.problems().isEmpty()) {
                IkGui.textColored(OK_COLOR, "None");
            }
            for (AssetValidator.Problem problem : inspection.problems()) {
                IkGui.textColored(
                        problem.severity() == AssetValidator.Severity.ERROR
                                ? ERROR_COLOR
                                : WARNING_COLOR,
                        problem.toString());
            }
        }
        AssetValidator.Entry entry = inspector.selectedEntry();
        if (entry != null) {
            String previewTitle =
                    "Section " + entry.index() + " (" + SectionTag.toString(entry.tag()) + ")";
            if (IkGui.collapsingHeader(previewTitle + "###preview", TreeNodeFlags.DEFAULT_OPEN)) {
                if (entry.section() == null) {
                    IkGui.textColored(ERROR_COLOR, "The payload is outside the file");
                } else {
                    drawHex(entry.section().data());
                }
            }
        }
    }

    /**
     * Draw a hex preview as a table, one column per byte, so it lines up without a monospace font.
     *
     * @param data The section payload.
     */
    private static void drawHex(ByteBuffer data) {
        int columns = HexDump.BYTES_PER_LINE + 2;
        if (!IkGui.beginTable(
                "hex", columns, TableFlags.SIZING_FIXED_FIT | TableFlags.BORDERS_INNER_V)) {
            return;
        }
        for (HexDump.Line line : HexDump.lines(data, PREVIEW_BYTES)) {
            IkGui.tableNextRow();
            IkGui.tableNextColumn();
            IkGui.textColored(OFFSET_COLOR, line.offsetText());
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
        AssetHeader header = inspection.header();
        if (header != null) {
            row("Format version", header.formatMajor() + "." + header.formatMinor());
            row("Header flags", Integer.toString(header.flags()));
            row("File length (header)", Long.toUnsignedString(header.fileLength()));
        } else {
            row("Header", "unreadable");
        }
        AssetMetadata metadata = inspection.metadata();
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
     * Draw the section table. Clicking a row selects it for the preview.
     *
     * @param inspection The inspection.
     * @param frame The current frame, for firing actions.
     */
    private void drawSections(AssetValidator.Inspection inspection, UiFrame frame) {
        if (inspection.entries().isEmpty()) {
            IkGui.textUnformatted("The section table couldn't be read");
            return;
        }
        String[] columns = {
            "#", "Tag", "Version", "Flags", "Offset", "Length", "Checksum", "Known"
        };
        if (!IkGui.beginTable(
                "sections", columns.length, TableFlags.BORDERS | TableFlags.ROW_BACKGROUND)) {
            return;
        }
        for (String column : columns) {
            IkGui.tableSetupColumn(column);
        }
        IkGui.tableHeadersRow();
        for (AssetValidator.Entry entry : inspection.entries()) {
            IkGui.tableNextRow();
            IkGui.tableNextColumn();
            final int index = entry.index();
            if (IkGui.selectable(
                    index + "###row" + index,
                    index == inspector.getSelected(),
                    SelectableFlags.SPAN_ALL_COLUMNS)) {
                frame.fire(() -> inspector.select(index));
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
                IkGui.textColored(ERROR_COLOR, "out of bounds");
            } else if (entry.crcMatches()) {
                IkGui.textColored(OK_COLOR, String.format("%08X ok", entry.storedCrc()));
            } else {
                // The expected value is in the problems list, which has room for it
                IkGui.textColored(ERROR_COLOR, String.format("%08X bad", entry.actualCrc()));
            }
            IkGui.tableNextColumn();
            if (entry.known()) {
                IkGui.textUnformatted("yes");
            } else if (entry.isRequired()) {
                IkGui.textColored(ERROR_COLOR, "no");
            } else {
                IkGui.textColored(WARNING_COLOR, "no, skipped");
            }
        }
        IkGui.endTable();
    }
}
