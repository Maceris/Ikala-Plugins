package com.ikalagaming.graphics.frontend.gui;

import com.ikalagaming.graphics.frontend.gui.data.*;
import com.ikalagaming.graphics.frontend.gui.enums.Axis;
import com.ikalagaming.graphics.frontend.gui.flags.ConfigFlags;
import com.ikalagaming.graphics.frontend.gui.flags.DebugLogFlags;
import com.ikalagaming.graphics.frontend.gui.flags.DockNodeFlags;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Docking settings, saved to and loaded from the .ini file. Because dock node settings are always
 * mirrored by dock nodes, we can fully rewrite the settings from the nodes when saving.
 *
 * <p>Example of the format:
 *
 * <pre>
 * [Docking][Data]
 * DockSpace     ID=0x00000001 Window=0x12345678 Pos=0,0 Size=1280,720 Split=X
 *   DockNode    ID=0x00000002 Parent=0x00000001 SizeRef=300,720 Selected=0x87654321
 *   DockNode    ID=0x00000003 Parent=0x00000001 SizeRef=978,720 CentralNode=1
 * </pre>
 */
@Slf4j
class IkGuiImplDockSettings {
    /** The type name for docking settings in the .ini file. */
    static final String DOCKING_TYPE_NAME = "Docking";

    /** The only entry name used by the docking settings. */
    private static final String DATA_ENTRY_NAME = "Data";

    static Context context;

    /** Register the settings handler for docking. */
    static void dockSettingsAddSettingsHandler() {
        final SettingsHandler handler = new SettingsHandler(DOCKING_TYPE_NAME);
        handler.clearAllFunction = IkGuiImplDockSettings::dockSettingsHandlerClearAll;
        // Also clear on read
        handler.readInitFunction = IkGuiImplDockSettings::dockSettingsHandlerClearAll;
        handler.readOpenFunction = IkGuiImplDockSettings::dockSettingsHandlerReadOpen;
        handler.readLineFunction = IkGuiImplDockSettings::dockSettingsHandlerReadLine;
        handler.applyAllFunction = IkGuiImplDockSettings::dockSettingsHandlerApplyAll;
        handler.writeAllFunction = IkGuiImplDockSettings::dockSettingsHandlerWriteAll;
        IkGuiImplConfig.addSettingsHandler(handler);
    }

    /**
     * Rename references to a node in windows and window settings, used when nodes are merged or
     * moved.
     *
     * @param oldNodeID The old node ID.
     * @param newNodeID The new node ID.
     */
    static void dockSettingsRenameNodeReferences(int oldNodeID, int newNodeID) {
        IkGuiImplDebugTools.debugLog(
                DebugLogFlags.EVENT_DOCKING,
                "[docking] DockSettingsRenameNodeReferences: from 0x%08X -> to 0x%08X",
                oldNodeID,
                newNodeID);
        log.trace(
                "dockSettingsRenameNodeReferences: from 0x{} -> to 0x{}",
                Integer.toHexString(oldNodeID),
                Integer.toHexString(newNodeID));
        for (Window window : context.windowDisplayOrder) {
            if (window.dockID == oldNodeID && window.dockNode == null) {
                window.dockID = newNodeID;
            }
        }
        for (WindowSettings settings : context.settingsWindows) {
            if (settings.dockID == oldNodeID) {
                settings.dockID = newNodeID;
            }
        }
    }

    /**
     * Remove references stored in window settings to the given nodes.
     *
     * @param nodeIDs The node IDs.
     */
    static void dockSettingsRemoveNodeReferences(int @NonNull [] nodeIDs) {
        for (WindowSettings settings : context.settingsWindows) {
            for (int nodeID : nodeIDs) {
                if (settings.dockID == nodeID) {
                    settings.dockID = 0;
                    settings.dockOrder = -1;
                    break;
                }
            }
        }
    }

    /**
     * Find the settings for a node.
     *
     * @param id The node ID.
     * @return The settings, or null if not found.
     */
    static DockNodeSettings dockSettingsFindNodeSettings(int id) {
        for (DockNodeSettings settings : context.dockContext.nodeSettings) {
            if (settings.id == id) {
                return settings;
            }
        }
        return null;
    }

    /**
     * Clear the settings data.
     *
     * @param ctx The context.
     * @param handler The handler.
     */
    private static void dockSettingsHandlerClearAll(
            @NonNull Context ctx, @NonNull SettingsHandler handler) {
        ctx.dockContext.nodeSettings.clear();
        IkGuiImplDocking.dockContextClearNodes(0, true);
    }

    /**
     * Recreate nodes based on the settings data.
     *
     * @param ctx The context.
     * @param handler The handler.
     */
    private static void dockSettingsHandlerApplyAll(
            @NonNull Context ctx, @NonNull SettingsHandler handler) {
        // Prune settings at boot time only
        if (ctx.windowByID.isEmpty()) {
            IkGuiImplDocking.dockContextPruneUnusedSettingsNodes();
        }
        IkGuiImplDocking.dockContextBuildNodesFromSettings(ctx.dockContext.nodeSettings);
        IkGuiImplDocking.dockContextBuildAddWindowsToNodes(0);
    }

    /**
     * Start reading an entry. Docking only uses the "[Docking][Data]" entry.
     *
     * @param ctx The context.
     * @param handler The handler.
     * @param name The entry name.
     * @return A non-null marker for the data entry, or null to skip the entry.
     */
    private static Object dockSettingsHandlerReadOpen(
            @NonNull Context ctx, @NonNull SettingsHandler handler, @NonNull String name) {
        if (!DATA_ENTRY_NAME.equals(name)) {
            return null;
        }
        return DATA_ENTRY_NAME;
    }

    /**
     * Parse a hex value written as 0xABCD.
     *
     * @param value The text.
     * @return The value, or null if it is not valid.
     */
    private static Integer parseHex(@NonNull String value) {
        if (!value.startsWith("0x") && !value.startsWith("0X")) {
            return null;
        }
        try {
            return Integer.parseUnsignedInt(value.substring(2), 16);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Parse a pair of integers written as x,y.
     *
     * @param value The text.
     * @return The values, or null if they are not valid.
     */
    private static int[] parsePair(@NonNull String value) {
        final String[] parts = value.split(",");
        if (parts.length != 2) {
            return null;
        }
        try {
            return new int[] {Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim())};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Parse a flag value written as 0 or 1.
     *
     * @param value The text.
     * @return True if the flag is set.
     */
    private static boolean parseFlag(@NonNull String value) {
        try {
            return Integer.parseInt(value.trim()) != 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Read a line of the docking settings, e.g. " DockNode ID=0x00000001 Pos=383,193 Size=201,322
     * Split=Y".
     *
     * @param ctx The context.
     * @param handler The handler.
     * @param entry The entry from readOpen().
     * @param line The line.
     */
    private static void dockSettingsHandlerReadLine(
            @NonNull Context ctx,
            @NonNull SettingsHandler handler,
            @NonNull Object entry,
            @NonNull String line) {
        final DockNodeSettings node = new DockNodeSettings();
        final String[] tokens = line.trim().split("\\s+");
        if (tokens.length == 0) {
            return;
        }
        if ("DockSpace".equals(tokens[0])) {
            node.flags |= DockNodeFlags.INTERNAL_DOCK_SPACE;
        } else if (!"DockNode".equals(tokens[0])) {
            return;
        }

        final Map<String, String> values = new HashMap<>();
        for (int i = 1; i < tokens.length; ++i) {
            final int equals = tokens[i].indexOf('=');
            if (equals <= 0) {
                continue;
            }
            values.put(tokens[i].substring(0, equals), tokens[i].substring(equals + 1));
        }

        final Integer id = values.containsKey("ID") ? parseHex(values.get("ID")) : null;
        if (id == null) {
            return;
        }
        node.id = id;
        if (values.containsKey("Parent")) {
            final Integer parent = parseHex(values.get("Parent"));
            if (parent == null || parent == 0) {
                return;
            }
            node.parentNodeID = parent;
        }
        if (values.containsKey("Window")) {
            final Integer parentWindow = parseHex(values.get("Window"));
            if (parentWindow == null || parentWindow == 0) {
                return;
            }
            node.parentWindowID = parentWindow;
        }
        if (node.parentNodeID == 0) {
            final int[] position = values.containsKey("Pos") ? parsePair(values.get("Pos")) : null;
            final int[] size = values.containsKey("Size") ? parsePair(values.get("Size")) : null;
            if (position == null || size == null) {
                return;
            }
            node.positionX = position[0];
            node.positionY = position[1];
            node.sizeX = size[0];
            node.sizeY = size[1];
        } else if (values.containsKey("SizeRef")) {
            final int[] sizeRef = parsePair(values.get("SizeRef"));
            if (sizeRef != null) {
                node.sizeRefX = sizeRef[0];
                node.sizeRefY = sizeRef[1];
            }
        }
        if (values.containsKey("Split")) {
            final String split = values.get("Split");
            if (split.startsWith("X")) {
                node.splitAxis = Axis.X;
            } else if (split.startsWith("Y")) {
                node.splitAxis = Axis.Y;
            }
        }
        if (values.containsKey("NoResize") && parseFlag(values.get("NoResize"))) {
            node.flags |= DockNodeFlags.NO_RESIZE;
        }
        if (values.containsKey("CentralNode") && parseFlag(values.get("CentralNode"))) {
            node.flags |= DockNodeFlags.INTERNAL_CENTRAL_NODE;
        }
        if (values.containsKey("NoTabBar") && parseFlag(values.get("NoTabBar"))) {
            node.flags |= DockNodeFlags.INTERNAL_NO_TAB_BAR;
        }
        if (values.containsKey("HiddenTabBar") && parseFlag(values.get("HiddenTabBar"))) {
            node.flags |= DockNodeFlags.INTERNAL_HIDDEN_TAB_BAR;
        }
        if (values.containsKey("NoWindowMenuButton")
                && parseFlag(values.get("NoWindowMenuButton"))) {
            node.flags |= DockNodeFlags.INTERNAL_NO_WINDOW_MENU_BUTTON;
        }
        if (values.containsKey("NoCloseButton") && parseFlag(values.get("NoCloseButton"))) {
            node.flags |= DockNodeFlags.INTERNAL_NO_CLOSE_BUTTON;
        }
        if (values.containsKey("Selected")) {
            final Integer selected = parseHex(values.get("Selected"));
            if (selected != null) {
                node.selectedTabID = selected;
            }
        }
        if (node.parentNodeID != 0) {
            final DockNodeSettings parentSettings = dockSettingsFindNodeSettings(node.parentNodeID);
            if (parentSettings != null) {
                node.depth = parentSettings.depth + 1;
            }
        }
        ctx.dockContext.nodeSettings.add(node);
    }

    /**
     * Recursively store the settings of a node hierarchy.
     *
     * @param dc The dock context.
     * @param node The node.
     * @param depth The depth of the node.
     */
    private static void dockSettingsHandlerDockNodeToSettings(
            @NonNull DockContext dc, @NonNull DockNode node, int depth) {
        final DockNodeSettings nodeSettings = new DockNodeSettings();
        nodeSettings.id = node.id;
        nodeSettings.parentNodeID = node.parentNode != null ? node.parentNode.id : 0;
        nodeSettings.parentWindowID =
                (node.isDockSpace()
                                && node.hostWindow != null
                                && node.hostWindow.parentWindow != null)
                        ? node.hostWindow.parentWindow.id
                        : 0;
        nodeSettings.selectedTabID = node.selectedTabID;
        nodeSettings.splitAxis = node.isSplitNode() ? node.splitAxis : Axis.NONE;
        nodeSettings.depth = depth;
        nodeSettings.flags = node.localFlags & DockNodeFlags.SAVED_FLAGS_MASK;
        nodeSettings.positionX = (int) node.position.x;
        nodeSettings.positionY = (int) node.position.y;
        nodeSettings.sizeX = (int) node.size.x;
        nodeSettings.sizeY = (int) node.size.y;
        nodeSettings.sizeRefX = (int) node.sizeRef.x;
        nodeSettings.sizeRefY = (int) node.sizeRef.y;
        dc.nodeSettings.add(nodeSettings);
        if (node.childNodes[0] != null) {
            dockSettingsHandlerDockNodeToSettings(dc, node.childNodes[0], depth + 1);
        }
        if (node.childNodes[1] != null) {
            dockSettingsHandlerDockNodeToSettings(dc, node.childNodes[1], depth + 1);
        }
    }

    /**
     * Write all the docking settings.
     *
     * @param ctx The context.
     * @param handler The handler.
     * @param out The output buffer.
     */
    private static void dockSettingsHandlerWriteAll(
            @NonNull Context ctx, @NonNull SettingsHandler handler, @NonNull StringBuilder out) {
        final DockContext dc = ctx.dockContext;
        if ((ctx.io.configFlags & ConfigFlags.DOCKING_ENABLE) == 0) {
            return;
        }

        // Gather the settings data. Unlike window settings, because nodes are always built we can
        // do a full rewrite of the node settings.
        dc.nodeSettings.clear();
        for (DockNode node : new ArrayList<>(dc.nodes.values())) {
            if (node.isRootNode()) {
                dockSettingsHandlerDockNodeToSettings(dc, node, 0);
            }
        }

        int maxDepth = 0;
        for (DockNodeSettings nodeSettings : dc.nodeSettings) {
            maxDepth = Math.max(nodeSettings.depth, maxDepth);
        }

        // Write to the text buffer
        out.append('[').append(handler.typeName).append("][").append(DATA_ENTRY_NAME).append("]\n");
        for (DockNodeSettings nodeSettings : dc.nodeSettings) {
            final int lineStart = out.length();
            // Text align nodes to make the .ini file easier to read
            out.append(" ".repeat(nodeSettings.depth * 2));
            out.append(
                    (nodeSettings.flags & DockNodeFlags.INTERNAL_DOCK_SPACE) != 0
                            ? "DockSpace"
                            : "DockNode ");
            out.append(" ".repeat((maxDepth - nodeSettings.depth) * 2));
            out.append(String.format(Locale.ROOT, " ID=0x%08X", nodeSettings.id));
            if (nodeSettings.parentNodeID != 0) {
                out.append(
                        String.format(
                                Locale.ROOT,
                                " Parent=0x%08X SizeRef=%d,%d",
                                nodeSettings.parentNodeID,
                                nodeSettings.sizeRefX,
                                nodeSettings.sizeRefY));
            } else {
                if (nodeSettings.parentWindowID != 0) {
                    out.append(
                            String.format(
                                    Locale.ROOT, " Window=0x%08X", nodeSettings.parentWindowID));
                }
                out.append(
                        String.format(
                                Locale.ROOT,
                                " Pos=%d,%d Size=%d,%d",
                                nodeSettings.positionX,
                                nodeSettings.positionY,
                                nodeSettings.sizeX,
                                nodeSettings.sizeY));
            }
            if (nodeSettings.splitAxis != Axis.NONE) {
                out.append(" Split=").append(nodeSettings.splitAxis == Axis.X ? 'X' : 'Y');
            }
            if ((nodeSettings.flags & DockNodeFlags.NO_RESIZE) != 0) {
                out.append(" NoResize=1");
            }
            if ((nodeSettings.flags & DockNodeFlags.INTERNAL_CENTRAL_NODE) != 0) {
                out.append(" CentralNode=1");
            }
            if ((nodeSettings.flags & DockNodeFlags.INTERNAL_NO_TAB_BAR) != 0) {
                out.append(" NoTabBar=1");
            }
            if ((nodeSettings.flags & DockNodeFlags.INTERNAL_HIDDEN_TAB_BAR) != 0) {
                out.append(" HiddenTabBar=1");
            }
            if ((nodeSettings.flags & DockNodeFlags.INTERNAL_NO_WINDOW_MENU_BUTTON) != 0) {
                out.append(" NoWindowMenuButton=1");
            }
            if ((nodeSettings.flags & DockNodeFlags.INTERNAL_NO_CLOSE_BUTTON) != 0) {
                out.append(" NoCloseButton=1");
            }
            if (nodeSettings.selectedTabID != 0) {
                out.append(
                        String.format(Locale.ROOT, " Selected=0x%08X", nodeSettings.selectedTabID));
            }

            // [DEBUG] Include comments in the .ini file to ease debugging (this makes saving
            // slower!)
            if (ctx.io.configDebugIniSettings) {
                final DockNode node = IkGuiImplDocking.dockContextFindNodeByID(nodeSettings.id);
                if (node != null) {
                    // Align everything
                    out.append(" ".repeat(Math.max(2, (lineStart + 92) - out.length())));
                    if (node.isDockSpace()
                            && node.hostWindow != null
                            && node.hostWindow.parentWindow != null) {
                        out.append(" ; in '")
                                .append(node.hostWindow.parentWindow.name)
                                .append('\'');
                    }
                    // Iterate settings so we can give info about windows that didn't exist during
                    // the session
                    int containsWindow = 0;
                    for (WindowSettings settings : ctx.settingsWindows) {
                        if (settings.dockID == nodeSettings.id) {
                            if (containsWindow++ == 0) {
                                out.append(" ; contains ");
                            }
                            out.append('\'').append(settings.name).append("' ");
                        }
                    }
                }
            }

            out.append('\n');
        }
        out.append('\n');
    }

    /** Private constructor so this is not instantiated. */
    private IkGuiImplDockSettings() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
