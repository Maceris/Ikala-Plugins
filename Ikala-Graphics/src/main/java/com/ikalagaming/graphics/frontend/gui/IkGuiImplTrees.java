package com.ikalagaming.graphics.frontend.gui;

import com.ikalagaming.graphics.frontend.gui.data.*;
import com.ikalagaming.graphics.frontend.gui.enums.Axis;
import com.ikalagaming.graphics.frontend.gui.enums.ColorType;
import com.ikalagaming.graphics.frontend.gui.enums.Condition;
import com.ikalagaming.graphics.frontend.gui.enums.Direction;
import com.ikalagaming.graphics.frontend.gui.enums.MouseButton;
import com.ikalagaming.graphics.frontend.gui.flags.ButtonFlags;
import com.ikalagaming.graphics.frontend.gui.flags.ItemFlags;
import com.ikalagaming.graphics.frontend.gui.flags.ItemStatusFlags;
import com.ikalagaming.graphics.frontend.gui.flags.MultiSelectFlags;
import com.ikalagaming.graphics.frontend.gui.flags.NavRenderCursorFlags;
import com.ikalagaming.graphics.frontend.gui.flags.NextItemFlags;
import com.ikalagaming.graphics.frontend.gui.flags.TreeNodeFlags;
import com.ikalagaming.graphics.frontend.gui.util.Hash;
import com.ikalagaming.graphics.frontend.gui.util.RectFloat;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector2f;

@Slf4j
class IkGuiImplTrees {
    static Context context;

    /**
     * A tree node with a frame, which doesn't indent or push on the ID stack when open.
     *
     * @param label The label, which is also used for the ID.
     * @param visible If not null, a close button is displayed and this is set to false when it is
     *     clicked. The header is not displayed if this is false.
     * @param treeNodeFlags Flags for the tree node.
     * @return True if the header is open.
     * @see TreeNodeFlags
     */
    public static boolean collapsingHeader(String label, IkBoolean visible, int treeNodeFlags) {
        final Window window = IkGuiInternal.getCurrentWindow();
        if (window.skipItems) {
            return false;
        }
        if (visible != null && !visible.get()) {
            return false;
        }

        final int id = window.getID(label);
        treeNodeFlags |= TreeNodeFlags.COLLAPSING_HEADER;
        if (visible != null) {
            treeNodeFlags |=
                    TreeNodeFlags.ALLOW_OVERLAP
                            | TreeNodeFlags.INTERNAL_CLIP_LABEL_FOR_TRAILING_BUTTON;
        }
        final boolean isOpen = treeNodeBehavior(id, treeNodeFlags, label);

        if (visible != null) {
            // Create a small overlapping close button
            final LastItemData lastItemBackup = new LastItemData();
            lastItemBackup.set(context.lastItemData);
            final float buttonSize = IkGuiInternal.getFontSize();
            final RectFloat rect = context.lastItemData.rect;
            final float framePaddingX = context.style.variable.framePadding.x;
            final float buttonX =
                    Math.max(rect.getLeft(), rect.getRight() - framePaddingX - buttonSize);
            final float buttonY = rect.getTop() + context.style.variable.framePadding.y;
            final int closeButtonID = Hash.getID("#CLOSE", id);
            if (IkGuiImplWindows.closeButton(closeButtonID, buttonX, buttonY)) {
                visible.set(false);
            }
            context.lastItemData.set(lastItemBackup);
        }

        return isOpen;
    }

    /**
     * A tree node with an explicit ID and display text.
     *
     * @param id The ID of the tree node.
     * @param text The text to display.
     * @return True if the node is open, in which case treePop() needs to be called.
     */
    public static boolean treeNode(int id, String text) {
        return treeNodeEx(id, TreeNodeFlags.NONE, text);
    }

    /**
     * A tree node.
     *
     * @param label The label, which is also used for the ID.
     * @return True if the node is open, in which case treePop() needs to be called.
     */
    public static boolean treeNode(String label) {
        return treeNodeEx(label, TreeNodeFlags.NONE);
    }

    /**
     * A tree node with an ID string and separate display text.
     *
     * @param stringID The string used for the ID.
     * @param text The text to display.
     * @param treeNodeFlags Flags for the tree node.
     * @return True if the node is open, in which case treePop() needs to be called (unless {@link
     *     TreeNodeFlags#NO_TREE_PUSH_ON_OPEN} is set).
     */
    public static boolean treeNodeEx(String stringID, int treeNodeFlags, String text) {
        final Window window = IkGuiInternal.getCurrentWindow();
        if (window.skipItems) {
            return false;
        }
        return treeNodeBehavior(window.getID(stringID), treeNodeFlags, text);
    }

    /**
     * A tree node with an explicit ID and display text.
     *
     * @param id The ID of the tree node.
     * @param treeNodeFlags Flags for the tree node.
     * @param text The text to display.
     * @return True if the node is open, in which case treePop() needs to be called (unless {@link
     *     TreeNodeFlags#NO_TREE_PUSH_ON_OPEN} is set).
     */
    public static boolean treeNodeEx(int id, int treeNodeFlags, String text) {
        final Window window = IkGuiInternal.getCurrentWindow();
        if (window.skipItems) {
            return false;
        }
        return treeNodeBehavior(id, treeNodeFlags, text);
    }

    /**
     * A tree node with flags.
     *
     * @param label The label, which is also used for the ID.
     * @param treeNodeFlags Flags for the tree node.
     * @return True if the node is open, in which case treePop() needs to be called (unless {@link
     *     TreeNodeFlags#NO_TREE_PUSH_ON_OPEN} is set).
     */
    public static boolean treeNodeEx(String label, int treeNodeFlags) {
        final Window window = IkGuiInternal.getCurrentWindow();
        if (window.skipItems) {
            return false;
        }
        return treeNodeBehavior(window.getID(label), treeNodeFlags, label);
    }

    /** Unindent and pop the ID that was pushed by a tree node or treePush(). */
    public static void treePop() {
        final Window window = IkGuiInternal.getCurrentWindow();
        if (window.treeDepth <= 0) {
            IkGuiImplDebugTools.reportError(log, "Calling treePop() too many times!");
            return;
        }
        IkGuiImplLayout.unindent(0.0f);
        window.treeDepth--;
        final int treeDepthMask = treeDepthMask(window.treeDepth);
        if ((window.treeHasStackDataDepthMask & treeDepthMask) != 0
                && !context.treeNodeStack.isEmpty()) {
            final TreeNodeStackData data = context.treeNodeStack.pop();
            // Handle the left arrow to move to the parent tree node
            if ((data.treeNodeFlags & TreeNodeFlags.NAV_LEFT_JUMPS_TO_PARENT) != 0
                    && context.navIDAlive
                    && context.navMoveDirection == Direction.LEFT
                    && context.navFocusedWindow == window
                    && IkGuiImplNav.navMoveRequestButNoResultYet()) {
                IkGuiImplNav.navMoveRequestResolveWithPastTreeNode(
                        context.navMoveResultLocal, data);
            }
            // Draw the vertical line of the hierarchy
            if (data.drawLinesX1 != Float.MAX_VALUE
                    && window.cursorPosition.y >= window.rectCurrentClip.getTop()) {
                treeNodeDrawLineToTreePop(window, data);
            }
            window.treeHasStackDataDepthMask &= ~treeDepthMask;
            window.treeRecordsClippedNodesY2Mask &= ~treeDepthMask;
        }
        IkGuiImplUtils.popID();
    }

    /**
     * The bit for a tree depth in the tree depth masks.
     *
     * @param depth The tree depth.
     * @return The mask bit, or 0 if the depth is too deep to track.
     */
    private static int treeDepthMask(int depth) {
        return depth >= 0 && depth < TREE_NODE_MAX_DEPTH ? 1 << depth : 0;
    }

    /** The deepest tree depth that we track stack data for, limited by the bits in an int. */
    private static final int TREE_NODE_MAX_DEPTH = 32;

    /**
     * Store data about the last item (a tree node) so it can be used when the tree is popped. Must
     * be called before the tree is pushed.
     *
     * @param window The current window.
     * @param treeNodeFlags The tree node flags.
     * @param x1 The left of the arrow of the tree node.
     */
    private static void treeNodeStoreStackData(
            @NonNull Window window, int treeNodeFlags, float x1) {
        final TreeNodeStackData data = new TreeNodeStackData();
        data.id = context.lastItemData.id;
        data.treeNodeFlags = treeNodeFlags;
        data.itemFlags = context.lastItemData.itemFlags;
        data.navRect.set(context.lastItemData.navRect);

        final boolean drawLines =
                (treeNodeFlags
                                & (TreeNodeFlags.DRAW_LINES_FULL
                                        | TreeNodeFlags.DRAW_LINES_TO_NODES))
                        != 0;
        // The vertical line goes through the middle of the arrow
        data.drawLinesX1 =
                drawLines
                        ? x1
                                + IkGuiInternal.getFontSize() * 0.5f
                                + context.style.variable.framePadding.x
                        : Float.MAX_VALUE;
        data.drawLinesTableColumn =
                drawLines && context.currentTable != null ? context.currentTable.currentColumn : -1;
        data.drawLinesToNodeY2 = -Float.MAX_VALUE;
        context.treeNodeStack.push(data);
        window.treeHasStackDataDepthMask |= treeDepthMask(window.treeDepth);
        if ((treeNodeFlags & TreeNodeFlags.DRAW_LINES_TO_NODES) != 0) {
            window.treeRecordsClippedNodesY2Mask |= treeDepthMask(window.treeDepth);
        }
    }

    /**
     * Draw the horizontal line from the parent tree node to a child node. Only called for visible
     * child nodes.
     *
     * @param window The current window.
     * @param targetX The x position to draw the line to, where the arrow of the child starts.
     * @param targetY The y position of the line, the middle of the child.
     */
    private static void treeNodeDrawLineToChildNode(
            @NonNull Window window, float targetX, float targetY) {
        if ((window.treeHasStackDataDepthMask & treeDepthMask(window.treeDepth - 1)) == 0
                || context.treeNodeStack.isEmpty()) {
            return;
        }
        final StyleVariables style = context.style.variable;
        final TreeNodeStackData parent = context.treeNodeStack.peek();
        float x1 = IkGuiInternal.truncate(parent.drawLinesX1);
        final float x2 = IkGuiInternal.truncate(targetX - style.itemInnerSpacing.x);
        final float y = IkGuiInternal.truncate(targetY);
        final float rounding =
                style.treeLinesRounding > 0.0f ? Math.min(x2 - x1, style.treeLinesRounding) : 0.0f;
        parent.drawLinesToNodeY2 = Math.max(parent.drawLinesToNodeY2, y - rounding);
        if (x1 >= x2) {
            return;
        }
        final int color = IkGuiImplUtils.getColorWithGlobalAlpha(ColorType.TREE_LINES);
        if (rounding > 0.0f) {
            // A quarter circle from the vertical line down to the horizontal line
            x1 += 0.5f + rounding;
            window.drawList.addArc(
                    x1,
                    y - rounding,
                    rounding,
                    (float) (Math.PI * 0.5),
                    (float) Math.PI,
                    color,
                    style.treeLinesSize);
            if (x1 < x2) {
                window.drawList.addLineH(x1, x2, y, color, style.treeLinesSize);
            }
        } else {
            window.drawList.addLineH(x1, x2, y, color, style.treeLinesSize);
        }
    }

    /**
     * Draw the vertical line of the hierarchy, when popping a tree node.
     *
     * @param window The current window.
     * @param data The data stored for the tree node.
     */
    private static void treeNodeDrawLineToTreePop(
            @NonNull Window window, @NonNull TreeNodeStackData data) {
        final StyleVariables style = context.style.variable;
        final float y1 = Math.max(data.navRect.getBottom(), window.rectCurrentClip.getTop());
        float y2 = data.drawLinesToNodeY2;
        if ((data.treeNodeFlags & TreeNodeFlags.DRAW_LINES_FULL) != 0) {
            float y2Full = window.cursorPosition.y;
            if (context.currentTable != null) {
                y2Full = Math.max(context.currentTable.rowPosY2, y2Full);
            }
            y2Full =
                    IkGuiInternal.truncate(
                            y2Full - style.itemSpacing.y - IkGuiInternal.getFontSize() * 0.5f);
            // Stop at the last child node instead if it's close to the end anyway
            if (y2 + (style.itemSpacing.y + style.treeLinesRounding) < y2Full) {
                y2 = y2Full;
            }
        }
        y2 = Math.min(y2, window.rectCurrentClip.getBottom());
        if (y2 <= y1) {
            return;
        }
        final float x = IkGuiInternal.truncate(data.drawLinesX1);
        if (data.drawLinesTableColumn != -1) {
            IkGuiImplTables.tablePushColumnChannel(data.drawLinesTableColumn);
        }
        window.drawList.addLineV(
                x,
                y1,
                y2,
                IkGuiImplUtils.getColorWithGlobalAlpha(ColorType.TREE_LINES),
                style.treeLinesSize);
        if (data.drawLinesTableColumn != -1) {
            IkGuiImplTables.tablePopColumnChannel();
        }
    }

    /** Indent and push an ID, as if a tree node was opened. */
    public static void treePush() {
        treePush("#TreePush");
    }

    /**
     * Indent and push an ID, as if a tree node was opened.
     *
     * @param stringID The ID to push.
     */
    public static void treePush(@NonNull String stringID) {
        final Window window = IkGuiInternal.getCurrentWindow();
        IkGuiImplLayout.indent(0.0f);
        window.treeDepth++;
        IkGuiImplUtils.pushID(stringID);
    }

    /**
     * Indent and push an ID, as if a tree node was opened.
     *
     * @param id The ID to push.
     */
    public static void treePush(int id) {
        final Window window = IkGuiInternal.getCurrentWindow();
        IkGuiImplLayout.indent(0.0f);
        window.treeDepth++;
        IkGuiImplUtils.pushID(id);
    }

    /**
     * Indent and push an exact ID on the stack, without combining it with the current ID stack.
     *
     * @param id The ID to push.
     */
    static void treePushOverrideID(int id) {
        final Window window = IkGuiInternal.getCurrentWindow();
        IkGuiImplLayout.indent(0.0f);
        window.treeDepth++;
        window.idStack.push(id);
    }

    /**
     * Set the ID used to store the open state of the next tree node, instead of its item ID. This
     * makes it easy to query or change the open state from anywhere with treeNodeGetOpen() and
     * treeNodeSetOpen(), since the ID doesn't depend on the ID stack.
     *
     * @param storageID The ID to store the open state under.
     */
    static void setNextItemStorageID(int storageID) {
        context.nextItemData.fieldFlags |= NextItemFlags.HAS_STORAGE_ID;
        context.nextItemData.storageID = storageID;
    }

    /**
     * Check if a tree node is open, from the current window's storage.
     *
     * @param storageID The ID the open state is stored under.
     * @return True if the node is open.
     */
    static boolean treeNodeGetOpen(int storageID) {
        return context.windowCurrent.currentStateStorage.getBool(storageID, false);
    }

    /**
     * Open or close a tree node, in the current window's storage.
     *
     * @param storageID The ID the open state is stored under.
     * @param open Whether the node should be open.
     */
    static void treeNodeSetOpen(int storageID, boolean open) {
        context.windowCurrent.currentStateStorage.setBool(storageID, open);
    }

    /**
     * Figure out if a tree node should be open, based on storage and any setNextItemOpen() call.
     *
     * @param storageID The ID used to store the open state.
     * @param treeNodeFlags The tree node flags.
     * @return True if the node should be open.
     */
    static boolean treeNodeUpdateNextOpen(int storageID, int treeNodeFlags) {
        if ((treeNodeFlags & TreeNodeFlags.LEAF) != 0) {
            return true;
        }
        boolean isOpen = treeNodeReadNextOpen(storageID, treeNodeFlags);

        // When logging is enabled, we automatically expand tree nodes (but not collapsing
        // headers). If we are above the max depth we still allow manually opened nodes to be
        // logged.
        if (context.logEnabled
                && (treeNodeFlags & TreeNodeFlags.NO_AUTO_OPEN_ON_LOG) == 0
                && context.windowCurrent.treeDepth - context.logDepthRef
                        < context.logDepthToExpand) {
            isOpen = true;
        }
        return isOpen;
    }

    /**
     * Read the open state of a tree node from storage, applying any setNextItemOpen() call.
     *
     * @param storageID The ID used to store the open state.
     * @param treeNodeFlags The tree node flags.
     * @return True if the node is open.
     */
    private static boolean treeNodeReadNextOpen(int storageID, int treeNodeFlags) {

        final Storage storage = context.windowCurrent.currentStateStorage;
        final NextItemData nextItemData = context.nextItemData;
        if ((nextItemData.fieldFlags & NextItemFlags.HAS_OPEN) != 0) {
            final boolean openValue = nextItemData.openValue;
            if (nextItemData.openCondition == Condition.ALWAYS
                    || nextItemData.openCondition == Condition.NONE) {
                storage.setBool(storageID, openValue);
                return openValue;
            }
            // We treat ONCE and FIRST_USE_EVER the same, if we haven't stored anything yet
            if (!storage.hasInt(storageID)
                    || (nextItemData.openCondition == Condition.APPEARING
                            && context.windowCurrent.appearing)) {
                storage.setBool(storageID, openValue);
                return openValue;
            }
            return storage.getBool(storageID, false);
        }
        return storage.getBool(storageID, (treeNodeFlags & TreeNodeFlags.DEFAULT_OPEN) != 0);
    }

    /**
     * The common behavior of tree nodes and collapsing headers.
     *
     * @param id The ID of the node.
     * @param treeNodeFlags Flags for the node.
     * @param label The text to display, anything after ## is hidden.
     * @return True if the node is open.
     */
    static boolean treeNodeBehavior(int id, int treeNodeFlags, String label) {
        final Window window = IkGuiInternal.getCurrentWindow();
        final StyleVariables style = context.style.variable;
        final float fontSize = IkGuiInternal.getFontSize();

        final boolean displayFrame = (treeNodeFlags & TreeNodeFlags.FRAMED) != 0;
        final float paddingX = style.framePadding.x;
        final float paddingY =
                (displayFrame || (treeNodeFlags & TreeNodeFlags.FRAME_PADDING) != 0)
                        ? style.framePadding.y
                        : Math.min(window.baseOffsetCurrentLine, style.framePadding.y);

        final String displayedLabel = Hash.getDisplayedText(label);
        final Vector2f labelSize = new Vector2f();
        IkGuiImplUtils.calcTextSize(labelSize, displayedLabel, false, -1.0f);

        // Collapsing arrow width + spacing
        final float textOffsetX = fontSize + (displayFrame ? paddingX * 3 : paddingX * 2);
        // Latch before itemSize changes it
        final float textOffsetY = Math.max(paddingY, window.baseOffsetCurrentLine);
        // Include the collapsing arrow
        final float textWidth = fontSize + labelSize.x + paddingX * 2;

        // We vertically grow up to the current line height, up to the typical widget height
        final float frameHeight =
                Math.max(
                        Math.min(window.lineSizeCurrent.y, fontSize + style.framePadding.y * 2),
                        labelSize.y + paddingY * 2);
        final boolean spanAllColumns =
                (treeNodeFlags & TreeNodeFlags.SPAN_ALL_COLUMNS) != 0
                        && context.currentTable != null;
        final boolean spanAllColumnsLabel =
                (treeNodeFlags & TreeNodeFlags.LABEL_SPAN_ALL_COLUMNS) != 0
                        && context.currentTable != null;
        final float frameMinX =
                spanAllColumns
                        ? window.rectParentWork.getLeft()
                        : (treeNodeFlags & TreeNodeFlags.SPAN_FULL_WIDTH) != 0
                                ? window.rectWork.getLeft()
                                : window.cursorPosition.x;
        final float frameMaxX =
                spanAllColumns
                        ? window.rectParentWork.getRight()
                        : (treeNodeFlags & TreeNodeFlags.SPAN_LABEL_WIDTH) != 0
                                ? window.cursorPosition.x + textWidth + paddingX
                                : window.rectWork.getRight();
        final RectFloat frameBB =
                new RectFloat(
                        frameMinX,
                        window.cursorPosition.y,
                        frameMaxX,
                        window.cursorPosition.y + frameHeight);
        if (displayFrame) {
            // Framed headers expand a little outside the default padding
            final float outerExtend = IkGuiInternal.truncate(window.padding.x * 0.5f);
            frameBB.setLeft(frameBB.getLeft() - outerExtend);
            frameBB.setRight(frameBB.getRight() + outerExtend);
        }

        final float textPosX = window.cursorPosition.x + textOffsetX;
        final float textPosY = window.cursorPosition.y + textOffsetY;
        IkGuiInternal.itemSize(textWidth, frameHeight, paddingY);

        // For regular tree nodes, we arbitrarily allow clicking past 2 worth of item spacing
        final RectFloat interactBB = new RectFloat();
        interactBB.set(frameBB);
        if ((treeNodeFlags
                        & (TreeNodeFlags.FRAMED
                                | TreeNodeFlags.SPAN_AVAIL_WIDTH
                                | TreeNodeFlags.SPAN_FULL_WIDTH
                                | TreeNodeFlags.SPAN_LABEL_WIDTH
                                | TreeNodeFlags.SPAN_ALL_COLUMNS))
                == 0) {
            interactBB.setRight(
                    frameBB.getLeft()
                            + textWidth
                            + (labelSize.x > 0.0f ? style.itemSpacing.x * 2.0f : 0.0f));
        }

        // Compute open state and set up storage
        // The open state is stored under the item ID, unless setNextItemStorageID() was called
        final int storageID =
                (context.nextItemData.fieldFlags & NextItemFlags.HAS_STORAGE_ID) != 0
                        ? context.nextItemData.storageID
                        : id;
        boolean isOpen = treeNodeUpdateNextOpen(storageID, treeNodeFlags);

        final int extraItemFlags =
                (treeNodeFlags & TreeNodeFlags.ALLOW_OVERLAP) != 0
                        ? ItemFlags.ALLOW_OVERLAP
                        : ItemFlags.NONE;
        final boolean itemAdd;
        if (spanAllColumns || spanAllColumnsLabel) {
            // Modify the clip rect for itemAdd(), faster than pushing the table background channel
            // for every tree node
            final RectFloat clip = window.rectCurrentClip;
            final float backupClipMinX = clip.getLeft();
            final float backupClipMaxX = clip.getRight();
            clip.setLeft(window.rectParentWork.getLeft());
            clip.setRight(window.rectParentWork.getRight());
            itemAdd = IkGuiInternal.itemAdd(interactBB, id, null, extraItemFlags);
            clip.setLeft(backupClipMinX);
            clip.setRight(backupClipMaxX);
        } else {
            itemAdd = IkGuiInternal.itemAdd(interactBB, id, null, extraItemFlags);
        }
        context.lastItemData.statusFlags |= ItemStatusFlags.HAS_DISPLAY_RECT;
        context.lastItemData.displayRect.set(frameBB);

        // Store data for the tree node to use in treePop(), when drawing tree lines, or when a left
        // navigation request is happening and NAV_LEFT_JUMPS_TO_PARENT is enabled. For the nav
        // request, we compare whether the nav ID became alive between treeNode() and treePop().
        if ((treeNodeFlags & TreeNodeFlags.INTERNAL_DRAW_LINES_MASK) == 0) {
            treeNodeFlags |= style.treeLinesFlags;
        }
        final boolean drawTreeLines =
                (treeNodeFlags
                                        & (TreeNodeFlags.DRAW_LINES_FULL
                                                | TreeNodeFlags.DRAW_LINES_TO_NODES))
                                != 0
                        && frameBB.getTop() < window.rectCurrentClip.getBottom()
                        && style.treeLinesSize > 0.0f;
        boolean storeTreeNodeStackData = false;
        if ((treeNodeFlags & TreeNodeFlags.NO_TREE_PUSH_ON_OPEN) == 0
                && window.treeDepth < TREE_NODE_MAX_DEPTH) {
            storeTreeNodeStackData =
                    drawTreeLines
                            || ((treeNodeFlags & TreeNodeFlags.NAV_LEFT_JUMPS_TO_PARENT) != 0
                                    && !context.navIDAlive
                                    && context.navMoveDirection == Direction.LEFT
                                    && context.navFocusedWindow == window
                                    && IkGuiImplNav.navMoveRequestButNoResultYet());
        }
        // The left of the arrow, where the tree lines line up
        final float arrowX = textPosX - textOffsetX;

        if (!itemAdd) {
            if ((treeNodeFlags & TreeNodeFlags.DRAW_LINES_TO_NODES) != 0) {
                // Extend the parent's line to clipped nodes, until we pass the bottom of the clip
                final int parentDepthMask = treeDepthMask(window.treeDepth - 1);
                if ((window.treeRecordsClippedNodesY2Mask & parentDepthMask) != 0
                        && !context.treeNodeStack.isEmpty()) {
                    final TreeNodeStackData parent = context.treeNodeStack.peek();
                    parent.drawLinesToNodeY2 =
                            Math.max(parent.drawLinesToNodeY2, window.cursorPosition.y);
                    if (frameBB.getTop() >= window.rectCurrentClip.getBottom()) {
                        window.treeRecordsClippedNodesY2Mask &= ~parentDepthMask;
                    }
                }
            }
            if (isOpen && storeTreeNodeStackData) {
                treeNodeStoreStackData(window, treeNodeFlags, arrowX);
            }
            if (isOpen && (treeNodeFlags & TreeNodeFlags.NO_TREE_PUSH_ON_OPEN) == 0) {
                treePushOverrideID(id);
            }
            IkGuiInternal.testEngineItemInfo(
                    context.lastItemData.id,
                    label,
                    context.lastItemData.statusFlags
                            | ((treeNodeFlags & TreeNodeFlags.LEAF) != 0
                                    ? 0
                                    : ItemStatusFlags.OPENABLE)
                            | (isOpen ? ItemStatusFlags.OPENED : 0));
            return isOpen;
        }

        if (spanAllColumns || spanAllColumnsLabel) {
            IkGuiImplTables.tablePushBackgroundChannel();
            context.lastItemData.statusFlags |= ItemStatusFlags.HAS_CLIP_RECT;
            context.lastItemData.clipRect.set(window.rectCurrentClip);
        }

        // We allow clicking on the arrow section with keyboard modifiers held, in order to easily
        // allow browsing a tree while preserving selection with code implementing multi-selection
        // patterns.
        final float arrowHitX1 = textPosX - textOffsetX - style.touchExtraPadding.x;
        final float arrowHitX2 =
                textPosX - textOffsetX + fontSize + paddingX * 2.0f + style.touchExtraPadding.x;
        final boolean isMouseXOverArrow =
                context.io.mousePosition.x >= arrowHitX1 && context.io.mousePosition.x < arrowHitX2;

        final boolean isLeaf = (treeNodeFlags & TreeNodeFlags.LEAF) != 0;
        int buttonFlags = ButtonFlags.NONE;
        if (!isLeaf) {
            // Holding a drag and drop payload over the node opens it
            buttonFlags |= ButtonFlags.INTERNAL_PRESSED_ON_DRAG_DROP_HOLD;
        }

        // We absolutely need to distinguish open vs select, so OPEN_ON_ARROW comes by default
        final int openOnMask = TreeNodeFlags.OPEN_ON_ARROW | TreeNodeFlags.OPEN_ON_DOUBLE_CLICK;
        final boolean isMultiSelect =
                (context.lastItemData.itemFlags & ItemFlags.INTERNAL_IS_MULTI_SELECT) != 0;
        if (isMultiSelect) {
            treeNodeFlags |=
                    (treeNodeFlags & openOnMask) == 0 ? openOnMask : TreeNodeFlags.OPEN_ON_ARROW;
        }

        // Open behaviors can be altered with the OPEN_ON_ARROW and OPEN_ON_DOUBLE_CLICK flags.
        // Some alterations have subtle effects (e.g. toggle on mouse up vs mouse down) due to
        // requirements for multi-selection and drag and drop support.
        // - Single-click on label = toggle on mouse up (default, without OPEN_ON_ARROW)
        // - Single-click on arrow = toggle on mouse down (with or without OPEN_ON_ARROW)
        // - Double-click on label = toggle on double click (with OPEN_ON_DOUBLE_CLICK)
        // - Double-click on arrow = toggle on double click (with OPEN_ON_DOUBLE_CLICK and without
        //   OPEN_ON_ARROW)
        // It is rather standard that arrow clicks react on mouse down rather than up. We use
        // PRESSED_ON_CLICK_RELEASE with OPEN_ON_DOUBLE_CLICK because we want the item to be
        // active on the initial mouse down, for drag and drop to work.
        if (isMouseXOverArrow) {
            buttonFlags |= ButtonFlags.INTERNAL_PRESSED_ON_CLICK;
        } else if ((treeNodeFlags & TreeNodeFlags.OPEN_ON_DOUBLE_CLICK) != 0) {
            buttonFlags |=
                    ButtonFlags.INTERNAL_PRESSED_ON_CLICK_RELEASE
                            | ButtonFlags.INTERNAL_PRESSED_ON_DOUBLE_CLICK;
        } else {
            buttonFlags |= ButtonFlags.INTERNAL_PRESSED_ON_CLICK_RELEASE;
        }

        boolean selected = (treeNodeFlags & TreeNodeFlags.SELECTED) != 0;
        final boolean wasSelected = selected;
        final IkBoolean selectedState = new IkBoolean(selected);

        // Multi-selection support (header)
        if (isMultiSelect) {
            // Handle multi-select and alter the button flags for it
            final IkInt multiSelectButtonFlags = new IkInt(buttonFlags);
            IkGuiImplMultiSelect.multiSelectItemHeader(id, selectedState, multiSelectButtonFlags);
            buttonFlags = multiSelectButtonFlags.get();
            selected = selectedState.get();
            if (isMouseXOverArrow) {
                buttonFlags =
                        (buttonFlags | ButtonFlags.INTERNAL_PRESSED_ON_CLICK)
                                & ~ButtonFlags.INTERNAL_PRESSED_ON_CLICK_RELEASE;
            }
        } else if (window != context.windowHovered || !isMouseXOverArrow) {
            buttonFlags |= ButtonFlags.INTERNAL_NO_KEY_MODS_ALLOWED;
        }

        final IkBoolean hovered = new IkBoolean();
        final IkBoolean held = new IkBoolean();
        boolean pressed = IkGuiInternal.buttonBehavior(interactBB, id, hovered, held, buttonFlags);
        boolean toggled = false;

        if (!isLeaf) {
            if (pressed && context.dragDropHoldJustPressedID == id) {
                // When using drag and drop "hold to open" we keep the node highlighted after
                // opening, but never close it again
                if (!isOpen) {
                    toggled = true;
                } else {
                    // Cancel the press so it doesn't trigger selection
                    pressed = false;
                }
            } else if (pressed) {
                // Single click, or activation through navigation
                toggled =
                        (treeNodeFlags & openOnMask) == 0
                                || (context.navActivateID == id && !isMultiSelect);
                if ((treeNodeFlags & TreeNodeFlags.OPEN_ON_ARROW) != 0) {
                    toggled |= isMouseXOverArrow && !context.navHighlightItemUnderNav;
                }
                if ((treeNodeFlags & TreeNodeFlags.OPEN_ON_DOUBLE_CLICK) != 0
                        && context.io.getMouseClickedLastCount(MouseButton.LEFT) == 2) {
                    toggled = true;
                }
            }

            // Navigate left to close, right to open
            if (context.navID == id
                    && ((context.navMoveDirection == Direction.LEFT && isOpen)
                            || (context.navMoveDirection == Direction.RIGHT && !isOpen))) {
                toggled = true;
                IkGuiImplNav.navClearPreferredPosForAxis(Axis.X);
                IkGuiImplNav.navMoveRequestCancel();
            }
            if (toggled) {
                isOpen = !isOpen;
                window.currentStateStorage.setBool(storageID, isOpen);
                context.lastItemData.statusFlags |= ItemStatusFlags.TOGGLED_OPEN;
            }
        }

        // Multi-selection support (footer)
        if (isMultiSelect) {
            final IkBoolean pressedState = new IkBoolean(pressed && !toggled);
            IkGuiImplMultiSelect.multiSelectItemFooter(
                    id, selectedState, pressedState, MultiSelectFlags.NONE);
            selected = selectedState.get();
            if (pressed) {
                IkGuiImplNav.setNavID(
                        id,
                        window.navLayerCurrent,
                        context.currentFocusScopeID,
                        IkGuiImplNav.windowRectAbsToRel(
                                window, interactBB, new RectFloat(0, 0, 0, 0)));
            }
        }

        if (selected != wasSelected) {
            context.lastItemData.statusFlags |= ItemStatusFlags.TOGGLED_SELECTION;
        }

        // Render
        final int textColor = IkGuiImplUtils.getColorWithGlobalAlpha(ColorType.TEXT);
        // Always show the nav rectangle in multi-select scopes
        final int navCursorFlags =
                NavRenderCursorFlags.COMPACT
                        | (isMultiSelect ? NavRenderCursorFlags.ALWAYS_DRAW : 0);
        if (displayFrame) {
            final int backgroundColor =
                    IkGuiImplUtils.getColorWithGlobalAlpha(
                            (held.get() && hovered.get())
                                    ? ColorType.HEADER_ACTIVE
                                    : hovered.get() ? ColorType.HEADER_HOVERED : ColorType.HEADER);
            IkGuiInternal.renderFrame(
                    frameBB.getLeft(),
                    frameBB.getTop(),
                    frameBB.getRight(),
                    frameBB.getBottom(),
                    backgroundColor,
                    true,
                    style.frameRounding);
            IkGuiImplNav.renderNavCursor(frameBB, id, navCursorFlags, -1.0f);
            if (spanAllColumns && !spanAllColumnsLabel) {
                IkGuiImplTables.tablePopBackgroundChannel();
            }

            float labelX = textPosX;
            if ((treeNodeFlags & TreeNodeFlags.BULLET) != 0) {
                IkGuiInternal.renderBullet(
                        window.drawList,
                        textPosX - textOffsetX * 0.60f,
                        textPosY + fontSize * 0.5f,
                        textColor);
            } else if (!isLeaf) {
                IkGuiInternal.renderArrow(
                        window.drawList,
                        textPosX - textOffsetX + paddingX,
                        textPosY,
                        textColor,
                        isOpen ? Direction.DOWN : Direction.RIGHT,
                        1.0f);
            } else {
                // Leaf without bullet, left-adjusted text
                labelX -= textOffsetX - paddingX;
            }

            float clipMaxX = frameBB.getRight();
            if ((treeNodeFlags & TreeNodeFlags.INTERNAL_CLIP_LABEL_FOR_TRAILING_BUTTON) != 0) {
                clipMaxX -= fontSize + style.framePadding.x;
            }
            if (context.logEnabled) {
                IkGuiImplLogging.logSetNextTextDecoration("###", "###");
            }
            IkGuiInternal.renderTextClipped(
                    labelX,
                    textPosY,
                    clipMaxX,
                    frameBB.getBottom(),
                    displayedLabel,
                    labelSize,
                    0.0f,
                    0.0f,
                    new RectFloat(labelX, frameBB.getTop(), clipMaxX, frameBB.getBottom()));
        } else {
            // Unframed typed for tree nodes
            if (hovered.get() || selected) {
                final int backgroundColor =
                        IkGuiImplUtils.getColorWithGlobalAlpha(
                                (held.get() && hovered.get())
                                        ? ColorType.HEADER_ACTIVE
                                        : hovered.get()
                                                ? ColorType.HEADER_HOVERED
                                                : ColorType.HEADER);
                IkGuiInternal.renderFrame(
                        frameBB.getLeft(),
                        frameBB.getTop(),
                        frameBB.getRight(),
                        frameBB.getBottom(),
                        backgroundColor,
                        false,
                        0.0f);
            }
            IkGuiImplNav.renderNavCursor(frameBB, id, navCursorFlags, 0.0f);
            if (spanAllColumns && !spanAllColumnsLabel) {
                IkGuiImplTables.tablePopBackgroundChannel();
            }
            if ((treeNodeFlags & TreeNodeFlags.BULLET) != 0) {
                IkGuiInternal.renderBullet(
                        window.drawList,
                        textPosX - textOffsetX * 0.5f,
                        textPosY + fontSize * 0.5f,
                        textColor);
            } else if (!isLeaf) {
                IkGuiInternal.renderArrow(
                        window.drawList,
                        textPosX - textOffsetX + paddingX,
                        textPosY + fontSize * 0.15f,
                        textColor,
                        isOpen ? Direction.DOWN : Direction.RIGHT,
                        0.70f);
            }
            if (context.logEnabled) {
                IkGuiImplLogging.logSetNextTextDecoration(">", null);
            }
            IkGuiInternal.renderText(textPosX, textPosY, displayedLabel, false);
        }
        if (drawTreeLines) {
            treeNodeDrawLineToChildNode(window, arrowX + paddingX, textPosY + fontSize * 0.5f);
        }
        if (spanAllColumnsLabel) {
            IkGuiImplTables.tablePopBackgroundChannel();
        }

        // Store after drawing the line from our parent, since that uses the top of the stack
        if (isOpen && storeTreeNodeStackData) {
            treeNodeStoreStackData(window, treeNodeFlags, arrowX);
        }
        if (isOpen && (treeNodeFlags & TreeNodeFlags.NO_TREE_PUSH_ON_OPEN) == 0) {
            treePushOverrideID(id);
        }

        IkGuiInternal.testEngineItemInfo(
                id,
                label,
                context.lastItemData.statusFlags
                        | (isLeaf ? 0 : ItemStatusFlags.OPENABLE)
                        | (isOpen ? ItemStatusFlags.OPENED : 0));
        return isOpen;
    }

    /** Private constructor so this is not instantiated. */
    private IkGuiImplTrees() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
