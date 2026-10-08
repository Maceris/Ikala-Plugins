package com.ikalagaming.graphics.frontend.gui.data;

/** Storage for the options of the metrics/debugger window and its inspectors. */
public class MetricsConfig {
    /** Show the debug log window. */
    public final IkBoolean showDebugLog = new IkBoolean(false);

    /** Show the ID stack tool window. */
    public final IkBoolean showIDStackTool = new IkBoolean(false);

    /** Draw a rectangle of each window, selected by {@link #showWindowsRectsType}. */
    public final IkBoolean showWindowsRects = new IkBoolean(false);

    /** Draw the begin order of each window over the window. */
    public final IkBoolean showWindowsBeginOrder = new IkBoolean(false);

    /** Draw a rectangle of each table, selected by {@link #showTablesRectsType}. */
    public final IkBoolean showTablesRects = new IkBoolean(false);

    /** Draw the quad of a draw command when hovering it in the inspector. */
    public final IkBoolean showDrawCmdMesh = new IkBoolean(true);

    /** Draw the bounding boxes of a draw command when hovering it in the inspector. */
    public final IkBoolean showDrawCmdBoundingBoxes = new IkBoolean(true);

    /** Show the text encoding viewer in the tools section. */
    public final IkBoolean showTextEncodingViewer = new IkBoolean(false);

    /** Show dock node info over the hovered dock node while holding Ctrl. */
    public final IkBoolean showDockingNodes = new IkBoolean(false);

    /** Which window rectangle to display, from the list in the metrics window. */
    public final IkInt showWindowsRectsType = new IkInt(-1);

    /** Which table rectangle to display, from the list in the metrics window. */
    public final IkInt showTablesRectsType = new IkInt(-1);

    /** The index of the monitor to highlight in the viewport thumbnails, or -1 for none. */
    public int highlightMonitorIndex = -1;

    /** The ID of the viewport to highlight in the viewport thumbnails, or 0 for none. */
    public int highlightViewportID = 0;

    /** How many months old settings entries need to be to be highlighted or discarded. */
    public final IkInt settingsDiscardMonths = new IkInt(6);

    /** Highlight settings entries older than {@link #settingsDiscardMonths}. */
    public final IkBoolean settingsHighlightOldEntries = new IkBoolean(false);

    /** Only list root nodes in the docking section. */
    public final IkBoolean dockingRootNodesOnly = new IkBoolean(true);

    /** The text being inspected by the text encoding viewer. */
    public final IkString textEncodingBuffer = new IkString(64);
}
