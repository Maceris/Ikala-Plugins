package com.ikalagaming.graphics.gui;

import static com.ikalagaming.graphics.gui.IkGuiDemo.helpMarker;

import com.ikalagaming.graphics.TextureHandle;
import com.ikalagaming.graphics.gui.callback.GuiInputTextCallback;
import com.ikalagaming.graphics.gui.data.*;
import com.ikalagaming.graphics.gui.enums.*;
import com.ikalagaming.graphics.gui.flags.*;
import com.ikalagaming.graphics.gui.util.Color;
import com.ikalagaming.graphics.gui.util.KeyChord;
import com.ikalagaming.graphics.gui.util.MathUtil;

import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * The example apps of the demo, which are accessible from the "Examples" menu of the demo window.
 * This is a port of the example apps in Dear ImGui's imgui_demo.cpp.
 */
final class IkGuiDemoExamples {
    // Examples apps (accessible from the "Examples" menu)
    static final IkBoolean showMainMenuBar = new IkBoolean(false);
    static final IkBoolean showAppAssetsBrowser = new IkBoolean(false);
    static final IkBoolean showAppConsole = new IkBoolean(false);
    static final IkBoolean showAppCustomRendering = new IkBoolean(false);
    static final IkBoolean showAppDocuments = new IkBoolean(false);
    static final IkBoolean showAppDockSpace = new IkBoolean(false);
    static final IkBoolean showAppImageViewer = new IkBoolean(false);
    static final IkBoolean showAppLog = new IkBoolean(false);
    static final IkBoolean showAppPropertyEditor = new IkBoolean(false);
    static final IkBoolean showAppLayout = new IkBoolean(false);
    static final IkBoolean showAppSimpleOverlay = new IkBoolean(false);
    static final IkBoolean showAppAutoResize = new IkBoolean(false);
    static final IkBoolean showAppConstrainedResize = new IkBoolean(false);
    static final IkBoolean showAppFullscreen = new IkBoolean(false);
    static final IkBoolean showAppLongText = new IkBoolean(false);
    static final IkBoolean showAppWindowTitles = new IkBoolean(false);

    /** Show the example apps that are open. Called by showDemoWindow() before the demo window. */
    static void showExampleApps() {
        if (showMainMenuBar.get()) {
            showExampleAppMainMenuBar();
        }
        // Important: Process the Docking app first, as explicit dockSpace() nodes needs to be
        // submitted early
        if (showAppDockSpace.get()) {
            showExampleAppDockSpace(showAppDockSpace);
        }
        // ...process the Document app next, as it may also use a dockSpace()
        if (showAppDocuments.get()) {
            showExampleAppDocuments(showAppDocuments);
        }
        if (showAppAssetsBrowser.get()) {
            showExampleAppAssetsBrowser(showAppAssetsBrowser);
        }
        if (showAppConsole.get()) {
            showExampleAppConsole(showAppConsole);
        }
        if (showAppCustomRendering.get()) {
            showExampleAppCustomRendering(showAppCustomRendering);
        }
        if (showAppImageViewer.get()) {
            showExampleAppImageViewer(showAppImageViewer);
        }
        if (showAppLog.get()) {
            showExampleAppLog(showAppLog);
        }
        if (showAppLayout.get()) {
            showExampleAppLayout(showAppLayout);
        }
        if (showAppPropertyEditor.get()) {
            showExampleAppPropertyEditor(showAppPropertyEditor);
        }
        if (showAppSimpleOverlay.get()) {
            showExampleAppSimpleOverlay(showAppSimpleOverlay);
        }
        if (showAppAutoResize.get()) {
            showExampleAppAutoResize(showAppAutoResize);
        }
        if (showAppConstrainedResize.get()) {
            showExampleAppConstrainedResize(showAppConstrainedResize);
        }
        if (showAppFullscreen.get()) {
            showExampleAppFullscreen(showAppFullscreen);
        }
        if (showAppLongText.get()) {
            showExampleAppLongText(showAppLongText);
        }
        if (showAppWindowTitles.get()) {
            showExampleAppWindowTitles();
        }
    }

    /** The contents of the "Examples" menu of the demo window. */
    static void showExamplesMenu() {
        IkGui.menuItem("Main menu bar", null, showMainMenuBar);

        IkGui.separatorText("Mini apps");
        IkGui.menuItem("Assets Browser", null, showAppAssetsBrowser);
        IkGui.menuItem("Console", null, showAppConsole);
        IkGui.menuItem("Custom rendering", null, showAppCustomRendering);
        IkGui.menuItem("Documents", null, showAppDocuments);
        IkGui.menuItem("Dockspace", null, showAppDockSpace);
        IkGui.menuItem("Image Viewer", null, showAppImageViewer);
        IkGui.menuItem("Log", null, showAppLog);
        IkGui.menuItem("Property editor", null, showAppPropertyEditor);
        IkGui.menuItem("Simple layout", null, showAppLayout);
        IkGui.menuItem("Simple overlay", null, showAppSimpleOverlay);

        IkGui.separatorText("Concepts");
        IkGui.menuItem("Auto-resizing window", null, showAppAutoResize);
        IkGui.menuItem("Constrained-resizing window", null, showAppConstrainedResize);
        IkGui.menuItem("Fullscreen window", null, showAppFullscreen);
        IkGui.menuItem("Long text display", null, showAppLongText);
        IkGui.menuItem("Manipulating window titles", null, showAppWindowTitles);
    }

    /** Explain that docking is disabled, with a button to enable it. */
    private static void showDockingDisabledMessage() {
        final IkIO io = IkGui.getIO();
        IkGui.text("ERROR: Docking is not enabled! See Demo > Configuration.");
        IkGui.text("Set io.configFlags |= ConfigFlags.DOCKING_ENABLE in your code, or ");
        IkGui.sameLine(0.0f, 0.0f);
        if (IkGui.smallButton("click here")) {
            io.configFlags |= ConfigFlags.DOCKING_ENABLE;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Example App: Main Menu Bar
    // ---------------------------------------------------------------------------------------------

    /**
     * Demonstrate creating a "main" fullscreen menu bar and populating it. Note the difference
     * between beginMainMenuBar() and beginMenuBar(): beginMenuBar() is for any window (with
     * WindowFlags.MENU_BAR), beginMainMenuBar() is a helper to create a menu bar at the top of the
     * screen.
     */
    private static void showExampleAppMainMenuBar() {
        if (IkGui.beginMainMenuBar()) {
            if (IkGui.beginMenu("File")) {
                IkGuiDemo.showExampleMenuFile();
                IkGui.endMenu();
            }
            if (IkGui.beginMenu("Edit")) {
                IkGui.menuItem("Undo", "Ctrl+Z");
                IkGui.menuItem("Redo", "Ctrl+Y", false, false); // Disabled item
                IkGui.separator();
                IkGui.menuItem("Cut", "Ctrl+X");
                IkGui.menuItem("Copy", "Ctrl+C");
                IkGui.menuItem("Paste", "Ctrl+V");
                IkGui.endMenu();
            }
            IkGui.endMainMenuBar();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Example App: Debug Console
    // ---------------------------------------------------------------------------------------------

    /**
     * Demonstrate creating a simple console window, with scrolling, filtering, completion and
     * history. For the console example, we are using a more Java like approach of declaring a class
     * to hold both data and functions.
     */
    static class ExampleAppConsole {
        final IkString inputBuffer = new IkString(256);
        final List<String> items = new ArrayList<>();
        final List<String> commands = new ArrayList<>();
        final List<String> history = new ArrayList<>();

        /** -1: new line, 0..history.size()-1 browsing history. */
        int historyPos;

        final TextFilter filter = new TextFilter();
        final IkBoolean autoScroll = new IkBoolean(true);
        boolean scrollToBottom;

        ExampleAppConsole() {
            clearLog();
            historyPos = -1;

            // "CLASSIFY" is here to provide the test case where "C"+[tab] completes to "CL" and
            // display multiple matches.
            commands.add("HELP");
            commands.add("HISTORY");
            commands.add("CLEAR");
            commands.add("CLASSIFY");
            scrollToBottom = false;
            addLog("Welcome to IkGui!");
        }

        void clearLog() {
            items.clear();
        }

        void addLog(String format, Object... args) {
            items.add(String.format(format, args));
        }

        void draw(String title, IkBoolean open) {
            IkGui.setNextWindowSize(520, 600, Condition.FIRST_USE_EVER);
            if (!IkGui.begin(title, open)) {
                IkGui.end();
                return;
            }

            // As a specific feature guaranteed by the library, after calling begin() the last
            // item represents the title bar. So e.g. isItemHovered() will return true when
            // hovering the title bar. Here we create a context menu only available from the title
            // bar.
            if (IkGui.beginPopupContextItem()) {
                if (IkGui.menuItem("Close Console")) {
                    open.set(false);
                }
                IkGui.endPopup();
            }

            IkGui.textWrapped(
                    "This example implements a console with basic coloring, completion (TAB key)"
                            + " and history (Up/Down keys). A more elaborate implementation may"
                            + " want to store entries along with extra data such as timestamp,"
                            + " emitter, etc.");
            IkGui.textWrapped("Enter 'HELP' for help.");

            if (IkGui.smallButton("Add Debug Text")) {
                addLog("%d some text", items.size());
                addLog("some more text");
                addLog("display very important message here!");
            }
            IkGui.sameLine();
            if (IkGui.smallButton("Add Debug Error")) {
                addLog("[error] something went wrong");
            }
            IkGui.sameLine();
            if (IkGui.smallButton("Clear")) {
                clearLog();
            }
            IkGui.sameLine();
            final boolean copyToClipboard = IkGui.smallButton("Copy");

            IkGui.separator();

            // Options menu
            if (IkGui.beginPopup("Options")) {
                IkGui.checkbox("Auto-scroll", autoScroll);
                IkGui.endPopup();
            }

            // Options, Filter
            IkGui.setNextItemShortcut(KeyChord.of(KeyModFlags.CTRL, Key.O), InputFlags.TOOLTIP);
            if (IkGui.button("Options")) {
                IkGui.openPopup("Options");
            }
            IkGui.sameLine();

            IkGui.setNextItemShortcut(KeyChord.of(KeyModFlags.CTRL, Key.F), InputFlags.TOOLTIP);
            IkGui.setNextItemWidth(-Float.MIN_VALUE);
            filter.drawWithHint("##Filter", "Filter (incl -excl)");
            IkGui.separator();

            // Reserve enough left-over height for 1 separator + 1 input text
            final StyleVariables style = IkGui.getStyle().variable;
            final float footerHeightToReserve =
                    style.separatorSize + style.itemSpacing.y + IkGui.getFrameHeightWithSpacing();
            if (IkGui.beginChild(
                    "ScrollingRegion",
                    0,
                    -footerHeightToReserve,
                    ChildFlags.NAV_FLATTENED,
                    WindowFlags.HORIZONTAL_SCROLLBAR)) {
                if (IkGui.beginPopupContextWindow()) {
                    if (IkGui.selectable("Clear")) {
                        clearLog();
                    }
                    IkGui.endPopup();
                }

                // Display every line as a separate entry so we can change their color or add
                // custom widgets. If you have thousands of entries this approach may be too
                // inefficient and may require user-side clipping to only process visible items.
                // The clipper will automatically measure the height of your first item and then
                // "seek" to display only items in the visible area. You cannot use the clipper
                // as-is if a filter is active because it breaks the 'cheap random-access'
                // property. We would need random-access on the post-filtered list.
                IkGui.pushStyleVarFloat2(StyleVariable.ITEM_SPACING, 4, 1); // Tighten spacing
                if (copyToClipboard) {
                    IkGui.logToClipboard();
                }
                for (String item : items) {
                    if (!filter.passFilter(item)) {
                        continue;
                    }

                    // Normally you would store more information in your item than just a string.
                    // (e.g. make items a list of records, store color/type etc.)
                    int color = 0;
                    boolean hasColor = false;
                    if (item.contains("[error]")) {
                        color = Color.rgba(1.0f, 0.4f, 0.4f, 1.0f);
                        hasColor = true;
                    } else if (item.startsWith("# ")) {
                        color = Color.rgba(1.0f, 0.8f, 0.6f, 1.0f);
                        hasColor = true;
                    }
                    if (hasColor) {
                        IkGui.pushStyleColor(ColorType.TEXT, color);
                    }
                    IkGui.textUnformatted(item);
                    if (hasColor) {
                        IkGui.popStyleColor();
                    }
                }
                if (copyToClipboard) {
                    IkGui.logFinish();
                }

                // Keep up at the bottom of the scroll region if we were already at the bottom at
                // the beginning of the frame. Using a scrollbar or mouse-wheel will take away from
                // the bottom edge.
                if (scrollToBottom
                        || (autoScroll.get() && IkGui.getScrollY() >= IkGui.getScrollMaxY())) {
                    IkGui.setScrollHereY(1.0f);
                }
                scrollToBottom = false;

                IkGui.popStyleVar();
            }
            IkGui.endChild();
            IkGui.separator();

            // Command-line
            boolean reclaimFocus = false;
            final int inputTextFlags =
                    InputTextFlags.ENTER_RETURNS_TRUE
                            | InputTextFlags.ESCAPE_CLEARS_ALL
                            | InputTextFlags.CALLBACK_COMPLETION
                            | InputTextFlags.CALLBACK_HISTORY;
            if (IkGui.inputText("Input", inputBuffer, inputTextFlags, textEditCallback)) {
                final String s = inputBuffer.get().stripTrailing();
                if (!s.isEmpty()) {
                    execCommand(s);
                }
                inputBuffer.set("");
                reclaimFocus = true;
            }

            // Auto-focus on window apparition
            IkGui.setItemDefaultFocus();
            if (reclaimFocus) {
                IkGui.setKeyboardFocusHere(-1); // Auto focus previous widget
            }

            IkGui.end();
        }

        void execCommand(String commandLine) {
            addLog("# %s\n", commandLine);

            // Insert into history. First find match and delete it so it can be pushed to the
            // back. This isn't trying to be smart or optimal.
            historyPos = -1;
            for (int i = history.size() - 1; i >= 0; i--) {
                if (history.get(i).equalsIgnoreCase(commandLine)) {
                    history.remove(i);
                    break;
                }
            }
            history.add(commandLine);

            // Process command
            if ("CLEAR".equalsIgnoreCase(commandLine)) {
                clearLog();
            } else if ("HELP".equalsIgnoreCase(commandLine)) {
                addLog("Commands:");
                for (String command : commands) {
                    addLog("- %s", command);
                }
            } else if ("HISTORY".equalsIgnoreCase(commandLine)) {
                final int first = history.size() - 10;
                for (int i = Math.max(first, 0); i < history.size(); i++) {
                    addLog("%3d: %s\n", i, history.get(i));
                }
            } else {
                addLog("Unknown command: '%s'\n", commandLine);
            }

            // On command input, we scroll to bottom even if autoScroll is false
            scrollToBottom = true;
        }

        /** Completion with Tab, and history with Up/Down. */
        final GuiInputTextCallback textEditCallback =
                new GuiInputTextCallback() {
                    @Override
                    public void accept(GuiInputTextCallbackData data) {
                        if (data.getEventFlag() == InputTextFlags.CALLBACK_COMPLETION) {
                            complete(data);
                        } else if (data.getEventFlag() == InputTextFlags.CALLBACK_HISTORY) {
                            browseHistory(data);
                        }
                    }
                };

        /** Example of text completion. */
        private void complete(GuiInputTextCallbackData data) {
            // Locate beginning of current word
            final String buffer = data.getBuf();
            final int wordEnd = data.getCursorPos();
            int wordStart = wordEnd;
            while (wordStart > 0) {
                final char c = buffer.charAt(wordStart - 1);
                if (c == ' ' || c == '\t' || c == ',' || c == ';') {
                    break;
                }
                wordStart--;
            }
            final String word = buffer.substring(wordStart, wordEnd);

            // Build a list of candidates
            final List<String> candidates = new ArrayList<>();
            for (String command : commands) {
                if (command.regionMatches(true, 0, word, 0, word.length())) {
                    candidates.add(command);
                }
            }

            if (candidates.isEmpty()) {
                // No match
                addLog("No match for \"%s\"!\n", word);
            } else if (candidates.size() == 1) {
                // Single match. Delete the beginning of the word and replace it entirely so we've
                // got nice casing.
                data.deleteChars(wordStart, wordEnd - wordStart);
                data.insertChars(data.getCursorPos(), candidates.getFirst());
                data.insertChars(data.getCursorPos(), " ");
            } else {
                // Multiple matches. Complete as much as we can.. So inputting "C"+Tab will
                // complete to "CL" then display "CLEAR" and "CLASSIFY" as matches.
                int matchLength = wordEnd - wordStart;
                while (true) {
                    int c = 0;
                    boolean allCandidatesMatch = true;
                    for (int i = 0; i < candidates.size() && allCandidatesMatch; i++) {
                        final String candidate = candidates.get(i);
                        final int next =
                                matchLength < candidate.length()
                                        ? Character.toUpperCase(candidate.charAt(matchLength))
                                        : 0;
                        if (i == 0) {
                            c = next;
                        } else if (c == 0 || c != next) {
                            allCandidatesMatch = false;
                        }
                    }
                    if (!allCandidatesMatch) {
                        break;
                    }
                    matchLength++;
                }

                if (matchLength > 0) {
                    data.deleteChars(wordStart, wordEnd - wordStart);
                    data.insertChars(
                            data.getCursorPos(), candidates.getFirst().substring(0, matchLength));
                }

                // List matches
                addLog("Possible matches:\n");
                for (String candidate : candidates) {
                    addLog("- %s\n", candidate);
                }
            }
        }

        /** Example of history. */
        private void browseHistory(GuiInputTextCallbackData data) {
            final int prevHistoryPos = historyPos;
            if (data.getEventKey() == Key.ARROW_UP) {
                if (historyPos == -1) {
                    historyPos = history.size() - 1;
                } else if (historyPos > 0) {
                    historyPos--;
                }
            } else if (data.getEventKey() == Key.ARROW_DOWN) {
                if (historyPos != -1 && ++historyPos >= history.size()) {
                    historyPos = -1;
                }
            }

            // A better implementation would preserve the data on the current input line along
            // with cursor position.
            if (prevHistoryPos != historyPos) {
                final String historyString = historyPos >= 0 ? history.get(historyPos) : "";
                data.deleteChars(0, data.getBufTextLen());
                data.insertChars(0, historyString);
            }
        }
    }

    static final ExampleAppConsole console = new ExampleAppConsole();

    private static void showExampleAppConsole(IkBoolean open) {
        console.draw("Example: Console", open);
    }

    // ---------------------------------------------------------------------------------------------
    // Example App: Image Viewer
    // ---------------------------------------------------------------------------------------------

    private static final IkGuiDemo.ExampleImageViewerData imageViewer =
            new IkGuiDemo.ExampleImageViewerData();

    private static void showExampleAppImageViewer(IkBoolean open) {
        // We don't have access to other textures in this demo!
        final FontAtlas atlas = IkGui.getIO().fonts;
        final TextureHandle texture = atlas.texture;
        final int textureWidth = FontAtlas.FONT_ATLAS_IMAGE_WIDTH;
        final int textureHeight = FontAtlas.FONT_ATLAS_IMAGE_HEIGHT;
        if (IkGui.begin("Example: Image Viewer", open)) {
            if (texture == null) {
                IkGui.textDisabled(
                        "(The rendering backend hasn't set the font atlas texture yet.)");
            } else {
                IkGuiDemo.exampleImageViewerDrawOptions(imageViewer);
                final Vector2f canvasSize = IkGui.getContentRegionAvailable();
                // Our atlas texture is much larger than upstream's, so the initial size is based
                // on a fraction of it
                final float minWidth = IkGui.isWindowAppearing() ? textureWidth * 0.25f : 1.0f;
                final float minHeight = IkGui.isWindowAppearing() ? textureHeight * 0.25f : 1.0f;
                IkGuiDemo.exampleImageViewerDrawCanvas(
                        imageViewer,
                        Math.max(canvasSize.x, minWidth),
                        Math.max(canvasSize.y, minHeight),
                        texture,
                        textureWidth,
                        textureHeight);
            }
        }
        IkGui.end();
    }

    // ---------------------------------------------------------------------------------------------
    // Example App: Debug Log
    // ---------------------------------------------------------------------------------------------

    /**
     * A simple log window with basic filtering.
     *
     * <p>Usage:
     *
     * <pre>{@code
     * ExampleAppLog myLog = new ExampleAppLog();
     * myLog.addLog("Hello %d world\n", 123);
     * myLog.draw("title", null);
     * }</pre>
     */
    static class ExampleAppLog {
        final StringBuilder buffer = new StringBuilder();
        final TextFilter filter = new TextFilter();

        /** Index to lines offset. We maintain this with addLog() calls. */
        final List<Integer> lineOffsets = new ArrayList<>();

        /** Keep scrolling if already at the bottom. */
        final IkBoolean autoScroll = new IkBoolean(true);

        ExampleAppLog() {
            clear();
        }

        void clear() {
            buffer.setLength(0);
            lineOffsets.clear();
            lineOffsets.add(0);
        }

        void addLog(String format, Object... args) {
            int oldSize = buffer.length();
            buffer.append(String.format(format, args));
            for (final int newSize = buffer.length(); oldSize < newSize; oldSize++) {
                if (buffer.charAt(oldSize) == '\n') {
                    lineOffsets.add(oldSize + 1);
                }
            }
        }

        /**
         * The text of a line, without the line break.
         *
         * @param lineNumber The line number.
         * @return The text of the line.
         */
        private String line(int lineNumber) {
            final int lineStart = lineOffsets.get(lineNumber);
            final int lineEnd =
                    lineNumber + 1 < lineOffsets.size()
                            ? lineOffsets.get(lineNumber + 1) - 1
                            : buffer.length();
            return buffer.substring(lineStart, lineEnd);
        }

        void draw(String title, IkBoolean open) {
            if (!IkGui.begin(title, open)) {
                IkGui.end();
                return;
            }

            // Options menu
            if (IkGui.beginPopup("Options")) {
                IkGui.checkbox("Auto-scroll", autoScroll);
                IkGui.endPopup();
            }

            // Main window
            if (IkGui.button("Options")) {
                IkGui.openPopup("Options");
            }
            IkGui.sameLine();
            final boolean clear = IkGui.button("Clear");
            IkGui.sameLine();
            final boolean copy = IkGui.button("Copy");
            IkGui.sameLine();
            IkGui.setNextItemWidth(-Float.MIN_VALUE);
            filter.drawWithHint("##Filter", "Filter (incl -excl)");

            IkGui.separator();

            if (IkGui.beginChild(
                    "scrolling", 0, 0, ChildFlags.NONE, WindowFlags.HORIZONTAL_SCROLLBAR)) {
                if (clear) {
                    clear();
                }
                if (copy) {
                    IkGui.logToClipboard();
                }

                IkGui.pushStyleVarFloat2(StyleVariable.ITEM_SPACING, 0, 0);
                if (filter.isActive()) {
                    // In this example we don't use the clipper when the filter is enabled. This
                    // is because we don't have random access to the result of our filter. A real
                    // application processing logs with ten of thousands of entries may want to
                    // store the result of search/filter.. especially if the filtering function is
                    // not trivial (e.g. reg-exp).
                    for (int lineNumber = 0; lineNumber < lineOffsets.size(); lineNumber++) {
                        final String text = line(lineNumber);
                        if (filter.passFilter(text)) {
                            IkGui.textUnformatted(text);
                        }
                    }
                } else {
                    // The simplest and easy way to display the entire buffer is
                    // textUnformatted(buffer.toString()), and it'll just work. Here we instead
                    // demonstrate using the clipper to only process lines that are within the
                    // visible area. If you have tens of thousands of items and their processing
                    // cost is non-negligible, coarse clipping them on your side is recommended.
                    // Using ListClipper requires A) random access into your data and B) items all
                    // being the same height, both of which we can handle since we have an array
                    // pointing to the beginning of each line of text.
                    final ListClipper clipper = new ListClipper();
                    clipper.begin(lineOffsets.size());
                    while (clipper.step()) {
                        for (int lineNumber = clipper.displayStart;
                                lineNumber < clipper.displayEnd;
                                lineNumber++) {
                            IkGui.textUnformatted(line(lineNumber));
                        }
                    }
                    clipper.end();
                }
                IkGui.popStyleVar();

                // Keep up at the bottom of the scroll region if we were already at the bottom at
                // the beginning of the frame. Using a scrollbar or mouse-wheel will take away from
                // the bottom edge.
                if (autoScroll.get() && IkGui.getScrollY() >= IkGui.getScrollMaxY()) {
                    IkGui.setScrollHereY(1.0f);
                }
            }
            IkGui.endChild();
            IkGui.end();
        }
    }

    private static final ExampleAppLog log = new ExampleAppLog();
    private static int logCounter = 0;

    /** Demonstrate creating a simple log window with basic filtering. */
    private static void showExampleAppLog(IkBoolean open) {
        // For the demo: add a debug button before the normal log window contents. We take
        // advantage of a rarely used feature: multiple calls to begin()/end() are appending to the
        // same window. Most of the contents of the window will be added by the log.draw() call.
        IkGui.setNextWindowSize(500, 400, Condition.FIRST_USE_EVER);
        IkGui.begin("Example: Log", open);
        if (IkGui.smallButton("[Debug] Add 5 entries")) {
            final String[] categories = {"info", "warn", "error"};
            final String[] words = {
                "Bumfuzzled",
                "Cattywampus",
                "Snickersnee",
                "Abibliophobia",
                "Absquatulate",
                "Nincompoop",
                "Pauciloquent"
            };
            for (int n = 0; n < 5; n++) {
                final String category = categories[logCounter % categories.length];
                final String word = words[logCounter % words.length];
                log.addLog(
                        "[%05d] [%s] Hello, current time is %.1f, here's a word: '%s'\n",
                        IkGui.getFrameCount(), category, IkGui.getTime() / 1000.0, word);
                logCounter++;
            }
        }
        IkGui.end();

        // Actually call in the regular log helper (which will begin() into the same window as we
        // just did)
        log.draw("Example: Log", open);
    }

    // ---------------------------------------------------------------------------------------------
    // Example App: Simple Layout
    // ---------------------------------------------------------------------------------------------

    private static int layoutSelected = 0;

    /** Demonstrate creating a window with multiple child windows. */
    private static void showExampleAppLayout(IkBoolean open) {
        IkGui.setNextWindowSize(500, 440, Condition.FIRST_USE_EVER);
        if (IkGui.begin("Example: Simple layout", open, WindowFlags.MENU_BAR)) {
            if (IkGui.beginMenuBar()) {
                if (IkGui.beginMenu("File")) {
                    if (IkGui.menuItem("Close", "Ctrl+W")) {
                        open.set(false);
                    }
                    IkGui.endMenu();
                }
                IkGui.endMenuBar();
            }

            // Left
            {
                IkGui.beginChild("left pane", 150, 0, ChildFlags.BORDERS | ChildFlags.RESIZE_X);
                for (int i = 0; i < 100; i++) {
                    if (IkGui.selectable(
                            "MyObject " + i, layoutSelected == i, SelectableFlags.SELECT_ON_NAV)) {
                        layoutSelected = i;
                    }
                }
                IkGui.endChild();
            }
            IkGui.sameLine();

            // Right
            {
                IkGui.beginGroup();
                // Leave room for 1 line below us
                IkGui.beginChild("item view", 0, -IkGui.getFrameHeightWithSpacing());
                IkGui.text("MyObject: " + layoutSelected);
                IkGui.separator();
                if (IkGui.beginTabBar("##Tabs", TabBarFlags.NONE)) {
                    if (IkGui.beginTabItem("Description")) {
                        IkGui.textWrapped(
                                "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do"
                                        + " eiusmod tempor incididunt ut labore et dolore magna"
                                        + " aliqua. ");
                        IkGui.endTabItem();
                    }
                    if (IkGui.beginTabItem("Details")) {
                        IkGui.text("ID: 0123456789");
                        IkGui.endTabItem();
                    }
                    IkGui.endTabBar();
                }
                IkGui.endChild();
                IkGui.button("Revert");
                IkGui.sameLine();
                IkGui.button("Save");
                IkGui.endGroup();
            }
        }
        IkGui.end();
    }

    // ---------------------------------------------------------------------------------------------
    // Example App: Property Editor
    // ---------------------------------------------------------------------------------------------
    // Some of the interactions are a bit lack-luster:
    // - We would want pressing validating or leaving the filter to somehow restore focus.
    // - We may want more advanced filtering (child nodes) and clipper support: both will need
    //   extra work.
    // - We would want to customize some keyboard interactions to easily keyboard navigate between
    //   the tree and the properties.

    /** The kind of a member of the example tree nodes. */
    private enum MemberType {
        STRING,
        BOOL,
        INT,
        FLOAT
    }

    /**
     * A simple representation of struct metadata/serialization data (this is a minimal version of
     * what a typical advanced application may provide).
     *
     * @param name The member name.
     * @param type The member type.
     * @param count The member count, 1 when scalar.
     */
    private record ExampleMemberInfo(String name, MemberType type, int count) {}

    /** Metadata description of the ExampleTreeNode members. */
    private static final ExampleMemberInfo[] EXAMPLE_TREE_NODE_MEMBER_INFOS = {
        new ExampleMemberInfo("MyName", MemberType.STRING, 1),
        new ExampleMemberInfo("MyBool", MemberType.BOOL, 1),
        new ExampleMemberInfo("MyInt", MemberType.INT, 1),
        new ExampleMemberInfo("MyVec2", MemberType.FLOAT, 2),
    };

    static class ExampleAppPropertyEditor {
        final TextFilter filter = new TextFilter();
        IkGuiDemo.ExampleTreeNode selectedNode = null;
        final IkBoolean useClipper = new IkBoolean(false);

        void draw(IkGuiDemo.ExampleTreeNode rootNode) {
            // Left side: draw tree
            // - Currently using a table to benefit from the ROW_BG feature
            // - Our tree node are all of equal height, facilitating the use of a clipper.
            if (IkGui.beginChild(
                    "##tree",
                    300,
                    0,
                    ChildFlags.RESIZE_X | ChildFlags.BORDERS | ChildFlags.NAV_FLATTENED)) {
                IkGui.pushItemFlag(ItemFlags.NO_NAV_DEFAULT_FOCUS, true);
                IkGui.checkbox("Use Clipper", useClipper);
                IkGui.sameLine();
                IkGui.text("(" + rootNode.children.size() + " root nodes)");
                IkGui.setNextItemWidth(-Float.MIN_VALUE);
                IkGui.setNextItemShortcut(KeyChord.of(KeyModFlags.CTRL, Key.F), InputFlags.TOOLTIP);
                if (IkGui.inputTextWithHint(
                        "##Filter",
                        "incl -excl",
                        filter.inputBuffer,
                        InputTextFlags.ESCAPE_CLEARS_ALL)) {
                    filter.build();
                }
                IkGui.popItemFlag();

                if (IkGui.beginTable("##list", 1, TableFlags.ROW_BACKGROUND)) {
                    if (useClipper.get()) {
                        drawClippedTree(rootNode);
                    } else {
                        drawTree(rootNode);
                    }
                    IkGui.endTable();
                }
            }
            IkGui.endChild();

            // Right side: draw properties
            IkGui.sameLine();

            IkGui.beginGroup(); // Lock X position
            final IkGuiDemo.ExampleTreeNode node = selectedNode;
            if (node != null) {
                IkGui.text(node.name.get());
                IkGui.textDisabled(String.format("UID: 0x%08X", node.uid));
                IkGui.separator();
                if (IkGui.beginTable(
                        "##properties", 2, TableFlags.RESIZABLE | TableFlags.SCROLL_Y)) {
                    // Push object ID after we entered the table, so table is shared for all
                    // objects
                    IkGui.pushID(node.uid);
                    IkGui.tableSetupColumn("", TableColumnFlags.WIDTH_FIXED);
                    // Default twice larger
                    IkGui.tableSetupColumn("", TableColumnFlags.WIDTH_STRETCH, 2.0f);
                    if (node.hasData) {
                        // In a typical application, the structure description would be derived
                        // from a data-driven system. We try to mimic this with our
                        // ExampleMemberInfo records. Limits and some details are hard-coded to
                        // simplify the demo.
                        for (ExampleMemberInfo fieldDesc : EXAMPLE_TREE_NODE_MEMBER_INFOS) {
                            IkGui.tableNextRow();
                            IkGui.pushID(fieldDesc.name());
                            IkGui.tableNextColumn();
                            IkGui.alignTextToFramePadding();
                            IkGui.textUnformatted(fieldDesc.name());
                            IkGui.tableNextColumn();
                            drawMemberEditor(node, fieldDesc);
                            IkGui.popID();
                        }
                    }
                    IkGui.popID();
                    IkGui.endTable();
                }
            }
            IkGui.endGroup();
        }

        private static void drawMemberEditor(
                IkGuiDemo.ExampleTreeNode node, ExampleMemberInfo fieldDesc) {
            switch (fieldDesc.type()) {
                case BOOL -> IkGui.checkbox("##Editor", node.dataMyBool);
                case INT -> {
                    IkGui.setNextItemWidth(-Float.MIN_VALUE);
                    IkGui.dragInt(
                            "##Editor", node.dataMyInt, 1.0f, Integer.MIN_VALUE, Integer.MAX_VALUE);
                }
                case FLOAT -> {
                    IkGui.setNextItemWidth(-Float.MIN_VALUE);
                    IkGui.sliderFloat2("##Editor", node.dataMyVec2, 0.0f, 1.0f);
                }
                case STRING -> IkGui.inputText("##Editor", node.name);
            }
        }

        /**
         * Custom search filter, applied on root nodes only. This does a case-insensitive search,
         * which is pretty heavy. In a real large-scale app you would likely store a filtered list
         * which in turns would be trivial to linearize.
         */
        private boolean isNodePassingFilter(IkGuiDemo.ExampleTreeNode node) {
            return node.parent.parent != null || filter.passFilter(node.name.get());
        }

        /**
         * Basic version, recursive. This is how you would generally draw a tree. Simple but going
         * to be noticeably costly if you have a large amount of nodes as drawTreeNode() is called
         * for all of them. Unlike arrays or grids which are very easy to clip, trees are currently
         * more difficult to clip.
         */
        private void drawTree(IkGuiDemo.ExampleTreeNode node) {
            for (IkGuiDemo.ExampleTreeNode child : node.children) {
                if (isNodePassingFilter(child) && drawTreeNode(child)) {
                    drawTree(child);
                    IkGui.treePop();
                }
            }
        }

        /**
         * More advanced version. Use an alternative clipping technique: fast-forwarding through
         * non-visible chunks.
         *
         * <ol>
         *   <li>Use the clipper with an indeterminate count (Integer.MAX_VALUE): we need to call
         *       seekCursorForItem() at the end once we know the count.
         *   <li>Use setNextItemStorageID() to specify the ID used for open/close storage, making it
         *       easy to call treeNodeGetOpen() on any arbitrary node.
         *   <li>Linearize the tree during traversal: our tree data structure makes it easy to
         *       access siblings and parents.
         * </ol>
         *
         * Unlike clipping for a regular array or grid which may be done using random access limited
         * to visible areas, this technique requires traversing most accessible nodes.
         */
        private void drawClippedTree(IkGuiDemo.ExampleTreeNode rootNode) {
            IkGuiDemo.ExampleTreeNode node = rootNode.children.getFirst(); // First node
            final ListClipper clipper = new ListClipper();
            clipper.begin(Integer.MAX_VALUE);
            while (clipper.step()) {
                while (clipper.userIndex < clipper.displayEnd && node != null) {
                    node = drawClippedTreeNodeAndAdvanceToNext(clipper, node);
                }
            }

            // Keep going to count nodes and submit final count so we have a reliable scrollbar.
            while (node != null) {
                node = drawClippedTreeNodeAndAdvanceToNext(clipper, node);
            }
            clipper.seekCursorForItem(clipper.userIndex);
        }

        private IkGuiDemo.ExampleTreeNode drawClippedTreeNodeAndAdvanceToNext(
                ListClipper clipper, IkGuiDemo.ExampleTreeNode node) {
            if (isNodePassingFilter(node)) {
                // Draw node if within visible range
                final boolean isOpen;
                if (clipper.userIndex >= clipper.displayStart
                        && clipper.userIndex < clipper.displayEnd) {
                    isOpen = drawTreeNode(node);
                } else {
                    isOpen = !node.children.isEmpty() && IkGuiInternal.treeNodeGetOpen(node.uid);
                    if (isOpen) {
                        IkGui.treePush(node.name.get());
                    }
                }
                clipper.userIndex++;

                // Next node: recurse into children
                if (isOpen) {
                    return node.children.getFirst();
                }
            }

            // Next node: next sibling, otherwise move back to parent
            IkGuiDemo.ExampleTreeNode current = node;
            while (current != null) {
                if (current.indexInParent + 1 < current.parent.children.size()) {
                    return current.parent.children.get(current.indexInParent + 1);
                }
                current = current.parent;
                if (current.parent == null) {
                    break;
                }
                IkGui.treePop();
            }
            return null;
        }

        /** To support nodes with the same name, we use the node UID as the item ID. */
        private boolean drawTreeNode(IkGuiDemo.ExampleTreeNode node) {
            IkGui.tableNextRow();
            IkGui.tableNextColumn();
            int treeFlags = TreeNodeFlags.NONE;
            // Standard opening mode as we are likely to want to add selection afterwards
            treeFlags |= TreeNodeFlags.OPEN_ON_ARROW | TreeNodeFlags.OPEN_ON_DOUBLE_CLICK;
            treeFlags |= TreeNodeFlags.NAV_LEFT_JUMPS_TO_PARENT; // Left arrow support
            treeFlags |= TreeNodeFlags.SPAN_FULL_WIDTH; // Span full width for easier mouse reach
            treeFlags |= TreeNodeFlags.DRAW_LINES_TO_NODES; // Always draw hierarchy outlines
            if (node == selectedNode) {
                treeFlags |= TreeNodeFlags.SELECTED; // Draw selection highlight
            }
            if (node.children.isEmpty()) {
                // Use NO_TREE_PUSH_ON_OPEN + set isOpen to false to avoid unnecessarily push/pop
                // on leaves
                treeFlags |=
                        TreeNodeFlags.LEAF
                                | TreeNodeFlags.BULLET
                                | TreeNodeFlags.NO_TREE_PUSH_ON_OPEN;
            }
            if (!node.dataMyBool.get()) {
                IkGui.pushStyleColor(ColorType.TEXT, IkGui.getColor(ColorType.TEXT_DISABLED));
            }
            IkGui.setNextItemStorageID(node.uid); // Use the node UID as storage id
            boolean isOpen = IkGui.treeNodeEx(node.uid, treeFlags, node.name.get());
            if (node.children.isEmpty()) {
                isOpen = false;
            }
            if (!node.dataMyBool.get()) {
                IkGui.popStyleColor();
            }
            if (IkGui.isItemFocused()) {
                selectedNode = node;
            }
            return isOpen;
        }
    }

    private static final ExampleAppPropertyEditor propertyEditor = new ExampleAppPropertyEditor();

    /** Demonstrate creating a simple property editor. */
    private static void showExampleAppPropertyEditor(IkBoolean open) {
        IkGui.setNextWindowSize(430, 450, Condition.FIRST_USE_EVER);
        if (!IkGui.begin("Example: Property editor", open)) {
            IkGui.end();
            return;
        }
        propertyEditor.draw(IkGuiDemo.getDemoTree());
        IkGui.end();
    }

    // ---------------------------------------------------------------------------------------------
    // Example App: Long Text
    // ---------------------------------------------------------------------------------------------

    private static final IkInt longTextTestType = new IkInt(0);
    private static final StringBuilder longTextLog = new StringBuilder();
    private static int longTextLines = 0;

    /** Demonstrate/test rendering huge amount of text, and the incidence of clipping. */
    private static void showExampleAppLongText(IkBoolean open) {
        IkGui.setNextWindowSize(520, 600, Condition.FIRST_USE_EVER);
        if (!IkGui.begin("Example: Long text display", open)) {
            IkGui.end();
            return;
        }

        IkGui.text("Printing unusually long amount of text.");
        IkGui.combo(
                "Test type",
                longTextTestType,
                new String[] {
                    "Single call to textUnformatted()",
                    "Multiple calls to text(), clipped",
                    "Multiple calls to text(), not clipped (slow)"
                });
        IkGui.text(
                String.format(
                        "Buffer contents: %d lines, %d characters",
                        longTextLines, longTextLog.length()));
        if (IkGui.button("Clear")) {
            longTextLog.setLength(0);
            longTextLines = 0;
        }
        IkGui.sameLine();
        if (IkGui.button("Add 1000 lines")) {
            for (int i = 0; i < 1000; i++) {
                longTextLog
                        .append(longTextLines + i)
                        .append(" The quick brown fox jumps over the lazy dog\n");
            }
            longTextLines += 1000;
        }
        IkGui.beginChild("Log");
        switch (longTextTestType.get()) {
            case 0 ->
                    IkGui.textUnformatted(longTextLog.toString()); // Single call with a big buffer
            case 1 -> {
                // Multiple calls to text(), manually coarsely clipped - demonstrate how to use the
                // ListClipper helper.
                IkGui.pushStyleVarFloat2(StyleVariable.ITEM_SPACING, 0, 0);
                final ListClipper clipper = new ListClipper();
                clipper.begin(longTextLines);
                while (clipper.step()) {
                    for (int i = clipper.displayStart; i < clipper.displayEnd; i++) {
                        IkGui.text(i + " The quick brown fox jumps over the lazy dog");
                    }
                }
                IkGui.popStyleVar();
            }
            default -> {
                // Multiple calls to text(), not clipped (slow)
                IkGui.pushStyleVarFloat2(StyleVariable.ITEM_SPACING, 0, 0);
                for (int i = 0; i < longTextLines; i++) {
                    IkGui.text(i + " The quick brown fox jumps over the lazy dog");
                }
                IkGui.popStyleVar();
            }
        }
        IkGui.endChild();
        IkGui.end();
    }

    // ---------------------------------------------------------------------------------------------
    // Example App: Auto Resize
    // ---------------------------------------------------------------------------------------------

    private static final int[] autoResizeLines = {10};

    /** Demonstrate creating a window which gets auto-resized according to its content. */
    private static void showExampleAppAutoResize(IkBoolean open) {
        if (!IkGui.begin("Example: Auto-resizing window", open, WindowFlags.ALWAYS_AUTO_RESIZE)) {
            IkGui.end();
            return;
        }

        IkGui.textUnformatted(
                "Window will resize every-frame to the size of its content.\n"
                        + "Note that you probably don't want to query the window size to\n"
                        + "output your content because that would create a feedback loop.");
        IkGui.sliderInt("Number of lines", autoResizeLines, 1, 20);
        for (int i = 0; i < autoResizeLines[0]; i++) {
            // Pad with space to extend size horizontally
            IkGui.text(" ".repeat(i * 4) + "This is line " + i);
        }
        IkGui.end();
    }

    // ---------------------------------------------------------------------------------------------
    // Example App: Constrained Resize
    // ---------------------------------------------------------------------------------------------

    private static final IkBoolean constrainedAutoResize = new IkBoolean(false);
    private static final IkBoolean constrainedWindowPadding = new IkBoolean(true);
    private static final IkInt constrainedType = new IkInt(6); // Aspect Ratio
    private static final int[] constrainedDisplayLines = {10};

    /**
     * Demonstrate creating a window with custom resize constraints. Note that size constraints
     * currently don't work on a docked window.
     */
    private static void showExampleAppConstrainedResize(IkBoolean open) {
        // Helper functions to demonstrate programmatic constraints. These don't take account of
        // decoration size (e.g. title bar), and none of them work consistently when resizing from
        // borders.
        final float aspectRatio = 16.0f / 9.0f;
        final float fixedStep = 100.0f;
        final Consumer<SizeCallbackData> aspectRatioConstraint =
                data -> data.desiredSize.y = (int) (data.desiredSize.x / aspectRatio);
        final Consumer<SizeCallbackData> squareConstraint =
                data -> {
                    final float size = Math.max(data.desiredSize.x, data.desiredSize.y);
                    data.desiredSize.set(size, size);
                };
        final Consumer<SizeCallbackData> stepConstraint =
                data ->
                        data.desiredSize.set(
                                (int) (data.desiredSize.x / fixedStep + 0.5f) * fixedStep,
                                (int) (data.desiredSize.y / fixedStep + 0.5f) * fixedStep);

        final String[] testDescriptions = {
            "Between 100x100 and 500x500",
            "At least 100x100",
            "Resize vertical + lock current width",
            "Resize horizontal + lock current height",
            "Width Between 400 and 500",
            "Height at least 400",
            "Custom: Aspect Ratio 16:9",
            "Custom: Always Square",
            "Custom: Fixed Steps (100)",
        };

        // Submit constraint
        final float max = Float.MAX_VALUE;
        switch (constrainedType.get()) {
            case 0 -> IkGui.setNextWindowSizeConstraints(100, 100, 500, 500);
            case 1 -> IkGui.setNextWindowSizeConstraints(100, 100, max, max);
            case 2 -> IkGui.setNextWindowSizeConstraints(-1, 0, -1, max);
            case 3 -> IkGui.setNextWindowSizeConstraints(0, -1, max, -1);
            case 4 -> IkGui.setNextWindowSizeConstraints(400, -1, 500, -1);
            case 5 -> IkGui.setNextWindowSizeConstraints(-1, 400, -1, max);
            case 6 -> IkGui.setNextWindowSizeConstraints(0, 0, max, max, aspectRatioConstraint);
            case 7 -> IkGui.setNextWindowSizeConstraints(0, 0, max, max, squareConstraint);
            case 8 -> IkGui.setNextWindowSizeConstraints(0, 0, max, max, stepConstraint);
            default -> {}
        }

        // Submit window
        if (!constrainedWindowPadding.get()) {
            IkGui.pushStyleVarFloat2(StyleVariable.WINDOW_PADDING, 0.0f, 0.0f);
        }
        final int windowFlags =
                constrainedAutoResize.get() ? WindowFlags.ALWAYS_AUTO_RESIZE : WindowFlags.NONE;
        final boolean windowOpen = IkGui.begin("Example: Constrained Resize", open, windowFlags);
        if (!constrainedWindowPadding.get()) {
            IkGui.popStyleVar();
        }
        if (windowOpen) {
            if (IkGui.getIO().keyShift) {
                // Display a dummy viewport (in your real app you would likely use imageButton() to
                // display a texture)
                final Vector2f availableSize = IkGui.getContentRegionAvailable();
                final Vector2f position = IkGui.getCursorScreenPos();
                IkGui.colorButton(
                        "viewport",
                        new float[] {0.5f, 0.2f, 0.5f, 1.0f},
                        ColorEditFlags.NO_TOOLTIP | ColorEditFlags.NO_DRAG_DROP,
                        availableSize.x,
                        availableSize.y);
                IkGui.setCursorScreenPos(position.x + 10, position.y + 10);
                IkGui.text(String.format("%.2f x %.2f", availableSize.x, availableSize.y));
            } else {
                IkGui.text("(Hold Shift to display a dummy viewport)");
                if (IkGui.isWindowDocked()) {
                    IkGui.text("Warning: Sizing Constraints won't work if the window is docked!");
                }
                if (IkGui.button("Set 200x200")) {
                    IkGui.setWindowSize(200, 200);
                }
                IkGui.sameLine();
                if (IkGui.button("Set 500x500")) {
                    IkGui.setWindowSize(500, 500);
                }
                IkGui.sameLine();
                if (IkGui.button("Set 800x200")) {
                    IkGui.setWindowSize(800, 200);
                }
                IkGui.setNextItemWidth(IkGui.getFontSize() * 20);
                IkGui.combo("Constraint", constrainedType, testDescriptions);
                IkGui.setNextItemWidth(IkGui.getFontSize() * 20);
                IkGui.dragInt("Lines", constrainedDisplayLines, 0.2f, 1, 100);
                IkGui.checkbox("Auto-resize", constrainedAutoResize);
                IkGui.checkbox("Window padding", constrainedWindowPadding);
                for (int i = 0; i < constrainedDisplayLines[0]; i++) {
                    IkGui.text(
                            " ".repeat(i * 4)
                                    + "Hello, sailor! Making this line long enough for the"
                                    + " example.");
                }
            }
        }
        IkGui.end();
    }

    // ---------------------------------------------------------------------------------------------
    // Example App: Simple overlay
    // ---------------------------------------------------------------------------------------------

    private static int overlayLocation = 0;

    /**
     * Demonstrate creating a simple static window with no decoration, and a context-menu to choose
     * which corner of the screen to use.
     */
    private static void showExampleAppSimpleOverlay(IkBoolean open) {
        final IkIO io = IkGui.getIO();
        int windowFlags =
                WindowFlags.NO_DECORATION
                        | WindowFlags.NO_DOCKING
                        | WindowFlags.ALWAYS_AUTO_RESIZE
                        | WindowFlags.NO_SAVED_SETTINGS
                        | WindowFlags.NO_FOCUS_ON_APPEARING
                        | WindowFlags.NO_NAV;
        if (overlayLocation >= 0) {
            final float pad = 10.0f;
            final Viewport viewport = IkGui.getMainViewport();
            // Use the work area to avoid menu-bar/task-bar, if any!
            final Vector2f workPos = viewport.workPosition;
            final Vector2f workSize = viewport.workSize;
            final float windowX =
                    (overlayLocation & 1) != 0 ? workPos.x + workSize.x - pad : workPos.x + pad;
            final float windowY =
                    (overlayLocation & 2) != 0 ? workPos.y + workSize.y - pad : workPos.y + pad;
            final float pivotX = (overlayLocation & 1) != 0 ? 1.0f : 0.0f;
            final float pivotY = (overlayLocation & 2) != 0 ? 1.0f : 0.0f;
            IkGui.setNextWindowPos(windowX, windowY, Condition.ALWAYS, pivotX, pivotY);
            IkGui.setNextWindowViewport(viewport.id);
            windowFlags |= WindowFlags.NO_MOVE;
        } else if (overlayLocation == -2) {
            // Center window
            final Vector2f center = IkGui.getMainViewport().getCenter(new Vector2f());
            IkGui.setNextWindowPos(center.x, center.y, Condition.ALWAYS, 0.5f, 0.5f);
            windowFlags |= WindowFlags.NO_MOVE;
        }
        IkGui.setNextWindowBgAlpha(0.35f); // Transparent background
        if (IkGui.begin("Example: Simple overlay", open, windowFlags)) {
            IkGui.text("Simple overlay\n(right-click to change position)");
            IkGui.separator();
            if (IkGui.isMousePosValid()) {
                IkGui.text(
                        String.format(
                                "Mouse Position: (%.1f,%.1f)",
                                io.mousePosition.x, io.mousePosition.y));
            } else {
                IkGui.text("Mouse Position: <invalid>");
            }
            if (IkGui.beginPopupContextWindow()) {
                if (IkGui.menuItem("Custom", null, overlayLocation == -1)) {
                    overlayLocation = -1;
                }
                if (IkGui.menuItem("Center", null, overlayLocation == -2)) {
                    overlayLocation = -2;
                }
                if (IkGui.menuItem("Top-left", null, overlayLocation == 0)) {
                    overlayLocation = 0;
                }
                if (IkGui.menuItem("Top-right", null, overlayLocation == 1)) {
                    overlayLocation = 1;
                }
                if (IkGui.menuItem("Bottom-left", null, overlayLocation == 2)) {
                    overlayLocation = 2;
                }
                if (IkGui.menuItem("Bottom-right", null, overlayLocation == 3)) {
                    overlayLocation = 3;
                }
                if (open != null && IkGui.menuItem("Close")) {
                    open.set(false);
                }
                IkGui.endPopup();
            }
        }
        IkGui.end();
    }

    // ---------------------------------------------------------------------------------------------
    // Example App: Fullscreen window
    // ---------------------------------------------------------------------------------------------

    private static final IkBoolean fullscreenUseWorkArea = new IkBoolean(true);
    private static final IkInt fullscreenFlags =
            new IkInt(
                    WindowFlags.NO_DECORATION
                            | WindowFlags.NO_MOVE
                            | WindowFlags.NO_SAVED_SETTINGS);

    /** Demonstrate creating a window covering the entire screen/viewport. */
    private static void showExampleAppFullscreen(IkBoolean open) {
        // We demonstrate using the full viewport area or the work area (without menu-bars,
        // task-bars etc.) Based on your use case you may want one or the other.
        final Viewport viewport = IkGui.getMainViewport();
        final boolean useWorkArea = fullscreenUseWorkArea.get();
        final Vector2f position = useWorkArea ? viewport.workPosition : viewport.position;
        final Vector2f size = useWorkArea ? viewport.workSize : viewport.size;
        IkGui.setNextWindowPos(position.x, position.y);
        IkGui.setNextWindowSize(size.x, size.y);

        if (IkGui.begin("Example: Fullscreen window", open, fullscreenFlags.get())) {
            IkGui.checkbox("Use work area instead of main area", fullscreenUseWorkArea);
            IkGui.sameLine();
            helpMarker(
                    "Main Area = entire viewport,\n"
                            + "Work Area = entire viewport minus sections used by the main menu"
                            + " bars, task bars etc.\n\n"
                            + "Enable the main-menu bar in Examples menu to see the difference.");

            IkGui.checkboxFlags("NO_BACKGROUND", fullscreenFlags, WindowFlags.NO_BACKGROUND);
            IkGui.checkboxFlags("NO_DECORATION", fullscreenFlags, WindowFlags.NO_DECORATION);
            IkGui.indent();
            IkGui.checkboxFlags("NO_TITLE_BAR", fullscreenFlags, WindowFlags.NO_TITLE_BAR);
            IkGui.checkboxFlags("NO_COLLAPSE", fullscreenFlags, WindowFlags.NO_COLLAPSE);
            IkGui.checkboxFlags("NO_SCROLLBAR", fullscreenFlags, WindowFlags.NO_SCROLLBAR);
            IkGui.unindent();

            if (open != null && IkGui.button("Close this window")) {
                open.set(false);
            }
        }
        IkGui.end();
    }

    // ---------------------------------------------------------------------------------------------
    // Example App: Manipulating Window Titles
    // ---------------------------------------------------------------------------------------------

    /**
     * Demonstrate the use of "##" and "###" in identifiers to manipulate ID generation. This
     * applies to all regular items as well.
     */
    private static void showExampleAppWindowTitles() {
        final Viewport viewport = IkGui.getMainViewport();
        final Vector2f basePos = viewport.position;

        // By default, windows are uniquely identified by their title. You can use the "##" and
        // "###" markers to manipulate the display/ID.

        // Using "##" to display same title but have unique identifier.
        IkGui.setNextWindowPos(basePos.x + 100, basePos.y + 100, Condition.FIRST_USE_EVER);
        IkGui.begin("Same title as another window##1");
        IkGui.text(
                "This is window 1.\nMy title is the same as window 2, but my identifier is"
                        + " unique.");
        IkGui.end();

        IkGui.setNextWindowPos(basePos.x + 100, basePos.y + 200, Condition.FIRST_USE_EVER);
        IkGui.begin("Same title as another window##2");
        IkGui.text(
                "This is window 2.\nMy title is the same as window 1, but my identifier is"
                        + " unique.");
        IkGui.end();

        // Using "###" to display a changing title but keep a static identifier "AnimatedTitle".
        // The time is in milliseconds.
        final String spinner = "|/-\\";
        final String title =
                String.format(
                        "Animated title %c %d###AnimatedTitle",
                        spinner.charAt((int) (IkGui.getTime() / 250) & 3), IkGui.getFrameCount());
        IkGui.setNextWindowPos(basePos.x + 100, basePos.y + 300, Condition.FIRST_USE_EVER);
        IkGui.begin(title);
        IkGui.text("This window has a changing title.");
        IkGui.end();
    }

    // ---------------------------------------------------------------------------------------------
    // Example App: Custom Rendering using the DrawList API
    // ---------------------------------------------------------------------------------------------

    /** Add a |_| looking shape. */
    private static void pathConcaveShape(DrawList drawList, float x, float y, float sz) {
        final float[][] posNorms = {
            {0.0f, 0.0f}, {0.3f, 0.0f}, {0.3f, 0.7f}, {0.7f, 0.7f},
            {0.7f, 0.0f}, {1.0f, 0.0f}, {1.0f, 1.0f}, {0.0f, 1.0f}
        };
        for (float[] p : posNorms) {
            drawList.pathLineTo(x + 0.5f + (int) (sz * p[0]), y + 0.5f + (int) (sz * p[1]));
        }
    }

    /**
     * Add an ellipse to the path. The SDF draw list has no ellipse primitive, so we build it from
     * line segments.
     */
    private static void pathEllipse(
            DrawList drawList,
            float centerX,
            float centerY,
            float radiusX,
            float radiusY,
            float rotation,
            int segments) {
        final int count = segments > 0 ? segments : 32;
        final float cos = (float) Math.cos(rotation);
        final float sin = (float) Math.sin(rotation);
        for (int i = 0; i < count; i++) {
            final double angle = 2.0 * Math.PI * i / count;
            final float x = (float) (Math.cos(angle) * radiusX);
            final float y = (float) (Math.sin(angle) * radiusY);
            drawList.pathLineTo(centerX + x * cos - y * sin, centerY + x * sin + y * cos);
        }
    }

    private static final float[] customSize = {42.0f};
    private static final float[] customThickness = {3.0f};
    private static final int[] customNgonSides = {6};
    private static final IkBoolean customCircleSegmentsOverride = new IkBoolean(false);
    private static final int[] customCircleSegmentsOverrideValue = {12};
    private static final IkBoolean customCurveSegmentsOverride = new IkBoolean(false);
    private static final int[] customCurveSegmentsOverrideValue = {8};
    private static final float[] customColor = {1.0f, 1.0f, 0.4f, 1.0f};

    private static final List<Vector2f> canvasPoints = new ArrayList<>();
    private static final Vector2f canvasScrolling = new Vector2f(0.0f, 0.0f);
    private static final IkBoolean canvasEnableGrid = new IkBoolean(true);
    private static final IkBoolean canvasEnableContextMenu = new IkBoolean(true);
    private static boolean canvasAddingLine = false;

    private static final IkBoolean customDrawBackground = new IkBoolean(true);
    private static final IkBoolean customDrawForeground = new IkBoolean(true);

    /** Demonstrate using the low-level DrawList to draw custom shapes. */
    private static void showExampleAppCustomRendering(IkBoolean open) {
        if (!IkGui.begin("Example: Custom rendering", open)) {
            IkGui.end();
            return;
        }

        if (IkGui.beginTabBar("##TabBar")) {
            if (IkGui.beginTabItem("Primitives")) {
                showCustomRenderingPrimitives();
                IkGui.endTabItem();
            }

            if (IkGui.beginTabItem("Canvas")) {
                showCustomRenderingCanvas();
                IkGui.endTabItem();
            }

            if (IkGui.beginTabItem("BG/FG draw lists")) {
                IkGui.checkbox("Draw in Background draw list", customDrawBackground);
                IkGui.sameLine();
                helpMarker("The Background draw list will be rendered below every IkGui windows.");
                IkGui.checkbox("Draw in Foreground draw list", customDrawForeground);
                IkGui.sameLine();
                helpMarker("The Foreground draw list will be rendered over every IkGui windows.");
                final Vector2f windowPos = IkGui.getWindowPos();
                final Vector2f windowSize = IkGui.getWindowSize();
                final float centerX = windowPos.x + windowSize.x * 0.5f;
                final float centerY = windowPos.y + windowSize.y * 0.5f;
                if (customDrawBackground.get()) {
                    IkGui.getBackgroundDrawList()
                            .addCircle(
                                    centerX,
                                    centerY,
                                    windowSize.x * 0.6f,
                                    Color.rgba(255, 0, 0, 200),
                                    10 + 4);
                }
                if (customDrawForeground.get()) {
                    IkGui.getForegroundDrawList()
                            .addCircle(
                                    centerX,
                                    centerY,
                                    windowSize.y * 0.6f,
                                    Color.rgba(0, 255, 0, 200),
                                    10);
                }
                IkGui.endTabItem();
            }

            // Demonstrate out-of-order rendering via channels splitting. We use functions in
            // DrawList as each draw list contains a convenience splitter.
            if (IkGui.beginTabItem("Draw Channels")) {
                showCustomRenderingChannels();
                IkGui.endTabItem();
            }

            IkGui.endTabBar();
        }

        IkGui.end();
    }

    private static void showCustomRenderingPrimitives() {
        IkGui.pushItemWidth(-IkGui.getFontSize() * 15);
        IkGui.pushItemFlag(ItemFlags.LIVE_EDIT_ON_INPUT, true);
        final DrawList drawList = IkGui.getWindowDrawList();

        // Draw gradients. Calling IkGui.getColorU32() multiplies the given colors by the current
        // style alpha, but you may pass the colors directly as well.
        IkGui.text("Gradients");
        final float gradientWidth = IkGui.calcItemWidth();
        final float gradientHeight = IkGui.getFrameHeight();
        {
            final Vector2f p0 = IkGui.getCursorScreenPos();
            final int colorA = IkGui.getColorU32(Color.rgba(0, 0, 0, 255));
            final int colorB = IkGui.getColorU32(Color.rgba(255, 255, 255, 255));
            drawList.addRectFilledMultiColor(
                    p0.x,
                    p0.y,
                    p0.x + gradientWidth,
                    p0.y + gradientHeight,
                    colorA,
                    colorB,
                    colorB,
                    colorA);
            IkGui.invisibleButton("##gradient1", gradientWidth, gradientHeight);
        }
        {
            final Vector2f p0 = IkGui.getCursorScreenPos();
            final int colorA = IkGui.getColorU32(Color.rgba(0, 255, 0, 255));
            final int colorB = IkGui.getColorU32(Color.rgba(255, 0, 0, 255));
            drawList.addRectFilledMultiColor(
                    p0.x,
                    p0.y,
                    p0.x + gradientWidth,
                    p0.y + gradientHeight,
                    colorA,
                    colorB,
                    colorB,
                    colorA);
            IkGui.invisibleButton("##gradient2", gradientWidth, gradientHeight);
        }

        // Draw a bunch of primitives
        IkGui.text("All primitives");
        IkGui.dragFloat("Size", customSize, 0.2f, 2.0f, 100.0f, "%.0f");
        IkGui.dragFloat("Thickness", customThickness, 0.05f, 1.0f, 8.0f, "%.02f");
        IkGui.sliderInt("N-gon sides", customNgonSides, 3, 12);
        IkGui.checkbox("##circlesegmentoverride", customCircleSegmentsOverride);
        IkGui.sameLine(0.0f, IkGui.getStyle().variable.itemInnerSpacing.x);
        if (IkGui.sliderInt("Circle segments override", customCircleSegmentsOverrideValue, 3, 40)) {
            customCircleSegmentsOverride.set(true);
        }
        IkGui.checkbox("##curvessegmentoverride", customCurveSegmentsOverride);
        IkGui.sameLine(0.0f, IkGui.getStyle().variable.itemInnerSpacing.x);
        if (IkGui.sliderInt("Curves segments override", customCurveSegmentsOverrideValue, 3, 40)) {
            customCurveSegmentsOverride.set(true);
        }
        IkGui.colorEdit4("Color", customColor);

        final Vector2f p = IkGui.getCursorScreenPos();
        final int color =
                Color.rgba(customColor[0], customColor[1], customColor[2], customColor[3]);
        final float sz = customSize[0];
        final float thickness = customThickness[0];
        final int ngonSides = customNgonSides[0];
        final float spacing = 10.0f;
        final int cornersTopLeftBottomRight =
                DrawFlags.ROUND_CORNERS_TOP_LEFT | DrawFlags.ROUND_CORNERS_BOTTOM_RIGHT;
        final float rounding = sz / 5.0f;
        // The SDF draw list draws exact circles and curves, so segment counts only apply when
        // overridden: circles are then drawn as n-gons, and ellipses are always line segments
        final int circleSegments =
                customCircleSegmentsOverride.get() ? customCircleSegmentsOverrideValue[0] : 0;
        final int curveSegments =
                customCurveSegmentsOverride.get() ? customCurveSegmentsOverrideValue[0] : 0;
        // Control points for curves
        final float[] cp3 = {0.0f, sz * 0.6f, sz * 0.5f, -sz * 0.4f, sz, sz};
        final float[] cp4 = {
            0.0f, 0.0f, sz * 1.3f, sz * 0.3f, sz - sz * 1.3f, sz - sz * 0.3f, sz, sz
        };

        float x = p.x + 4.0f;
        float y = p.y + 4.0f;
        for (int n = 0; n < 2; n++) {
            // First line uses a thickness of 1.0f, second line uses the configurable thickness
            final float th = n == 0 ? 1.0f : thickness;
            drawList.addNgon(x + sz * 0.5f, y + sz * 0.5f, sz * 0.5f, color, ngonSides, th);
            x += sz + spacing; // N-gon
            if (circleSegments > 0) {
                drawList.addNgon(
                        x + sz * 0.5f, y + sz * 0.5f, sz * 0.5f, color, circleSegments, th);
            } else {
                drawList.addCircle(x + sz * 0.5f, y + sz * 0.5f, sz * 0.5f, color, th);
            }
            x += sz + spacing; // Circle
            pathEllipse(
                    drawList,
                    x + sz * 0.5f,
                    y + sz * 0.5f,
                    sz * 0.5f,
                    sz * 0.3f,
                    -0.3f,
                    circleSegments);
            drawList.pathStroke(color, true, th);
            x += sz + spacing; // Ellipse
            drawList.addRect(x, y, x + sz, y + sz, color, 0.0f, DrawFlags.ROUND_CORNERS_ALL, th);
            x += sz + spacing; // Square
            drawList.addRect(
                    x, y, x + sz, y + sz, color, rounding, DrawFlags.ROUND_CORNERS_ALL, th);
            x += sz + spacing; // Square with all rounded corners
            drawList.addRect(x, y, x + sz, y + sz, color, rounding, cornersTopLeftBottomRight, th);
            x += sz + spacing; // Square with two rounded corners
            drawList.addTriangle(
                    x + sz * 0.5f, y, x + sz, y + sz - 0.5f, x, y + sz - 0.5f, color, th);
            x += sz + spacing; // Triangle
            pathConcaveShape(drawList, x, y, sz);
            drawList.pathStroke(color, true, th);
            x += sz + spacing; // Concave Shape
            // Horizontal line (note: drawing a filled rectangle will be faster!)
            drawList.addLineH(x, x + sz, y, color, th);
            x += sz + spacing;
            // Vertical line (note: drawing a filled rectangle will be faster!)
            drawList.addLineV(x, y, y + sz, color, th);
            x += spacing;
            drawList.addLine(x, y, x + sz, y + sz, color, th);
            x += sz + spacing; // Diagonal line

            // Path
            drawList.pathArcTo(
                    x + sz * 0.5f, y + sz * 0.5f, sz * 0.5f, 3.141_592f, 3.141_592f * -0.5f);
            drawList.pathStroke(color, false, th);
            x += sz + spacing;

            // Quadratic Bezier Curve (3 control points)
            drawList.addBezierQuadratic(
                    x + cp3[0],
                    y + cp3[1],
                    x + cp3[2],
                    y + cp3[3],
                    x + cp3[4],
                    y + cp3[5],
                    color,
                    th,
                    curveSegments);
            x += sz + spacing;

            // Cubic Bezier Curve (4 control points)
            drawList.addBezierCubic(
                    x + cp4[0],
                    y + cp4[1],
                    x + cp4[2],
                    y + cp4[3],
                    x + cp4[4],
                    y + cp4[5],
                    x + cp4[6],
                    y + cp4[7],
                    color,
                    th,
                    curveSegments);

            x = p.x + 4;
            y += sz + spacing;
        }

        // Filled shapes
        drawList.addNgonFilled(x + sz * 0.5f, y + sz * 0.5f, sz * 0.5f, color, ngonSides);
        x += sz + spacing; // N-gon
        if (circleSegments > 0) {
            drawList.addNgonFilled(x + sz * 0.5f, y + sz * 0.5f, sz * 0.5f, color, circleSegments);
        } else {
            drawList.addCircleFilled(x + sz * 0.5f, y + sz * 0.5f, sz * 0.5f, color);
        }
        x += sz + spacing; // Circle
        pathEllipse(
                drawList,
                x + sz * 0.5f,
                y + sz * 0.5f,
                sz * 0.5f,
                sz * 0.3f,
                -0.3f,
                circleSegments);
        drawList.pathFillConvex(color);
        x += sz + spacing; // Ellipse
        drawList.addRectFilled(x, y, x + sz, y + sz, color);
        x += sz + spacing; // Square
        drawList.addRectFilled(x, y, x + sz, y + sz, color, 10.0f);
        x += sz + spacing; // Square with all rounded corners
        drawList.addRectFilled(x, y, x + sz, y + sz, color, 10.0f, cornersTopLeftBottomRight);
        x += sz + spacing; // Square with two rounded corners
        drawList.addTriangleFilled(
                x + sz * 0.5f, y, x + sz, y + sz - 0.5f, x, y + sz - 0.5f, color);
        x += sz + spacing; // Triangle
        pathConcaveShape(drawList, x, y, sz);
        drawList.pathFillConcave(color);
        x += sz + spacing; // Concave shape
        // Horizontal line (faster than addLine, but only handle integer thickness)
        drawList.addRectFilled(x, y, x + sz, y + thickness, color);
        x += sz + spacing;
        // Vertical line (faster than addLine, but only handle integer thickness)
        drawList.addRectFilled(x, y, x + thickness, y + sz, color);
        x += spacing * 2.0f;
        drawList.addRectFilled(x, y, x + 1, y + 1, color);
        x += sz; // Pixel (faster than addLine)

        // Path
        drawList.pathArcTo(x + sz * 0.5f, y + sz * 0.5f, sz * 0.5f, 3.141_592f * -0.5f, 3.141_592f);
        drawList.pathFillConvex(color);
        x += sz + spacing;

        // Quadratic Bezier Curve (3 control points)
        drawList.pathLineTo(x + cp3[0], y + cp3[1]);
        drawList.pathBezierQuadraticCurveTo(
                x + cp3[2], y + cp3[3], x + cp3[4], y + cp3[5], curveSegments);
        drawList.pathFillConvex(color);
        x += sz + spacing;

        drawList.addRectFilledMultiColor(
                x,
                y,
                x + sz,
                y + sz,
                Color.rgba(0, 0, 0, 255),
                Color.rgba(255, 0, 0, 255),
                Color.rgba(255, 255, 0, 255),
                Color.rgba(0, 255, 0, 255));

        IkGui.dummy((sz + spacing) * 13.2f, (sz + spacing) * 3.0f);
        IkGui.popItemFlag();
        IkGui.popItemWidth();
    }

    private static void showCustomRenderingCanvas() {
        IkGui.checkbox("Enable grid", canvasEnableGrid);
        IkGui.checkbox("Enable context menu", canvasEnableContextMenu);
        IkGui.text(
                "Mouse Left: drag to add lines,\nMouse Right: drag to scroll, click for context menu.");

        // Typically you would use a beginChild()/endChild() pair to benefit from a clipping region
        // + own scrolling. Here we demonstrate that this can be replaced by simple offsetting +
        // custom drawing + pushClipRect()/popClipRect() calls.

        // Using invisibleButton() as a convenience 1) it will advance the layout cursor and 2)
        // allows us to use isItemHovered()/isItemActive()
        final Vector2f canvasP0 =
                IkGui.getCursorScreenPos(); // DrawList API uses screen coordinates!
        final Vector2f canvasSize =
                IkGui.getContentRegionAvailable(); // Resize canvas to what's available
        canvasSize.x = Math.max(canvasSize.x, 50.0f);
        canvasSize.y = Math.max(canvasSize.y, 50.0f);
        final float canvasP1X = canvasP0.x + canvasSize.x;
        final float canvasP1Y = canvasP0.y + canvasSize.y;

        // Draw border and background color
        final IkIO io = IkGui.getIO();
        final DrawList drawList = IkGui.getWindowDrawList();
        drawList.addRectFilled(
                canvasP0.x, canvasP0.y, canvasP1X, canvasP1Y, Color.rgba(50, 50, 50, 255));
        drawList.addRect(
                canvasP0.x, canvasP0.y, canvasP1X, canvasP1Y, Color.rgba(255, 255, 255, 255));

        // This will catch our interactions
        IkGui.invisibleButton(
                "canvas",
                canvasSize.x,
                canvasSize.y,
                ButtonFlags.MOUSE_BUTTON_LEFT | ButtonFlags.MOUSE_BUTTON_RIGHT);
        final boolean isHovered = IkGui.isItemHovered(); // Hovered
        final boolean isActive = IkGui.isItemActive(); // Held
        // Lock scrolled origin
        final float originX = canvasP0.x + canvasScrolling.x;
        final float originY = canvasP0.y + canvasScrolling.y;
        final float mouseInCanvasX = io.mousePosition.x - originX;
        final float mouseInCanvasY = io.mousePosition.y - originY;

        // Add first and second point
        if (isHovered && !canvasAddingLine && IkGui.isMouseClicked(MouseButton.LEFT)) {
            canvasPoints.add(new Vector2f(mouseInCanvasX, mouseInCanvasY));
            canvasPoints.add(new Vector2f(mouseInCanvasX, mouseInCanvasY));
            canvasAddingLine = true;
        }
        if (canvasAddingLine) {
            canvasPoints.getLast().set(mouseInCanvasX, mouseInCanvasY);
            if (!IkGui.isMouseDown(MouseButton.LEFT)) {
                canvasAddingLine = false;
            }
        }

        // Pan (we use a zero mouse threshold when there's no context menu). You may decide to make
        // that threshold dynamic based on whether the mouse is hovering something etc.
        final float mouseThresholdForPan = canvasEnableContextMenu.get() ? -1.0f : 0.0f;
        if (isActive && IkGui.isMouseDragging(MouseButton.RIGHT, mouseThresholdForPan)) {
            canvasScrolling.x += io.mouseDelta.x;
            canvasScrolling.y += io.mouseDelta.y;
        }

        // Context menu (under default mouse threshold)
        final Vector2f dragDelta = IkGui.getMouseDragDelta(MouseButton.RIGHT);
        if (canvasEnableContextMenu.get() && dragDelta.x == 0.0f && dragDelta.y == 0.0f) {
            IkGui.openPopupOnItemClick("context", PopupFlags.MOUSE_BUTTON_RIGHT);
        }
        if (IkGui.beginPopup("context")) {
            if (canvasAddingLine) {
                canvasPoints.removeLast();
                canvasPoints.removeLast();
            }
            canvasAddingLine = false;
            if (IkGui.menuItem("Remove one", null, false, !canvasPoints.isEmpty())) {
                canvasPoints.removeLast();
                canvasPoints.removeLast();
            }
            if (IkGui.menuItem("Remove all", null, false, !canvasPoints.isEmpty())) {
                canvasPoints.clear();
            }
            IkGui.endPopup();
        }

        // Draw grid + all lines in the canvas
        drawList.pushClipRect(canvasP0.x, canvasP0.y, canvasP1X, canvasP1Y, true);
        if (canvasEnableGrid.get()) {
            final float gridStep = 64.0f;
            for (float x = canvasScrolling.x % gridStep; x < canvasSize.x; x += gridStep) {
                drawList.addLineV(
                        canvasP0.x + x, canvasP0.y, canvasP1Y, Color.rgba(200, 200, 200, 40), 1.0f);
            }
            for (float y = canvasScrolling.y % gridStep; y < canvasSize.y; y += gridStep) {
                drawList.addLineH(
                        canvasP0.x, canvasP1X, canvasP0.y + y, Color.rgba(200, 200, 200, 40), 1.0f);
            }
        }
        for (int n = 0; n + 1 < canvasPoints.size(); n += 2) {
            final Vector2f a = canvasPoints.get(n);
            final Vector2f b = canvasPoints.get(n + 1);
            drawList.addLine(
                    originX + a.x,
                    originY + a.y,
                    originX + b.x,
                    originY + b.y,
                    Color.rgba(255, 255, 0, 255),
                    2.0f);
        }
        drawList.popClipRect();
    }

    private static void showCustomRenderingChannels() {
        final DrawList drawList = IkGui.getWindowDrawList();
        {
            IkGui.text("Blue shape is drawn first: appears in back");
            IkGui.text("Red shape is drawn after: appears in front");
            final Vector2f p0 = IkGui.getCursorScreenPos();
            drawList.addRectFilled(
                    p0.x, p0.y, p0.x + 50, p0.y + 50, Color.rgba(0, 0, 255, 255)); // Blue
            drawList.addRectFilled(
                    p0.x + 25, p0.y + 25, p0.x + 75, p0.y + 75, Color.rgba(255, 0, 0, 255)); // Red
            IkGui.dummy(75, 75);
        }
        IkGui.separator();
        {
            IkGui.text("Blue shape is drawn first, into channel 1: appears in front");
            IkGui.text("Red shape is drawn after, into channel 0: appears in back");
            final Vector2f p1 = IkGui.getCursorScreenPos();

            // Create 2 channels and draw a Blue shape THEN a Red shape. You can create any number
            // of channels. Tables API use 1 channel per column in order to better batch draw
            // calls.
            drawList.channelsSplit(2);
            drawList.channelsSetCurrent(1);
            drawList.addRectFilled(
                    p1.x, p1.y, p1.x + 50, p1.y + 50, Color.rgba(0, 0, 255, 255)); // Blue
            drawList.channelsSetCurrent(0);
            drawList.addRectFilled(
                    p1.x + 25, p1.y + 25, p1.x + 75, p1.y + 75, Color.rgba(255, 0, 0, 255)); // Red

            // Flatten/reorder channels. Red shape is in channel 0 and it appears below the Blue
            // shape in channel 1. This works by copying draw indices only (vertices are not
            // copied).
            drawList.channelsMerge();
            IkGui.dummy(75, 75);
            IkGui.text("After reordering, contents of channel 0 appears below channel 1.");
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Example App: Docking, DockSpace
    // ---------------------------------------------------------------------------------------------

    /** 0 for the basic demo, 1 for the advanced demo. */
    private static final IkInt dockspaceDemoMode = new IkInt(0);

    private static boolean dockspaceDemoModeChanged = false;
    private static final IkBoolean dockspaceFullscreen = new IkBoolean(true);

    /**
     * Keep the window padding to help understand that dockSpace() is a widget inside the window.
     */
    private static final IkBoolean dockspaceKeepWindowPadding = new IkBoolean(false);

    private static final IkInt dockspaceFlags = new IkInt(DockNodeFlags.NONE);

    /**
     * This is a demo for advanced usage of dockSpace(). Most regular applications wanting to allow
     * docking windows on the edge of the screen can simply call dockSpaceOverViewport() after
     * newFrame(). The reasons we don't use that here are that we allow the host window to be
     * floating/movable instead of filling the viewport, we allow the host window to have padding,
     * and we expose a variety of other flags.
     *
     * @param open The open state of the example.
     */
    private static void showExampleAppDockSpaceAdvanced(IkBoolean open) {
        int flags = dockspaceFlags.get();

        // We are using the NO_DOCKING flag to make the parent window not dockable into, because it
        // would be confusing to have two docking targets within each other
        int windowFlags = WindowFlags.NO_DOCKING;
        if (dockspaceFullscreen.get()) {
            // Fullscreen dockspace: practically the same as calling dockSpaceOverViewport()
            final Viewport viewport = IkGui.getMainViewport();
            IkGui.setNextWindowPos(viewport.workPosition.x, viewport.workPosition.y);
            IkGui.setNextWindowSize(viewport.workSize.x, viewport.workSize.y);
            IkGui.setNextWindowViewport(viewport.id);
            IkGui.pushStyleVarFloat(StyleVariable.WINDOW_ROUNDING, 0.0f);
            IkGui.pushStyleVarFloat(StyleVariable.WINDOW_BORDER_SIZE, 0.0f);
            windowFlags |=
                    WindowFlags.NO_TITLE_BAR
                            | WindowFlags.NO_COLLAPSE
                            | WindowFlags.NO_RESIZE
                            | WindowFlags.NO_MOVE;
            windowFlags |= WindowFlags.NO_BRING_TO_FRONT_ON_FOCUS | WindowFlags.NO_NAV_FOCUS;
            windowFlags |= WindowFlags.NO_BACKGROUND;
        } else {
            // Floating dockspace
            flags &= ~DockNodeFlags.PASSTHROUGH_CENTRAL_NODE;
        }

        // Important: note that we proceed even if begin() returns false (the window is
        // collapsed). This is because we want to keep our dockspace active. If a dockspace is
        // inactive, all active windows docked into it will lose their parent and become undocked.
        // We cannot preserve the docking relationship between an active window and an inactive
        // docking, otherwise any change of dockspace/settings would lead to windows being stuck in
        // limbo and never being visible.
        if (!dockspaceKeepWindowPadding.get()) {
            IkGui.pushStyleVarFloat2(StyleVariable.WINDOW_PADDING, 0.0f, 0.0f);
        }
        IkGui.begin("Window with a DockSpace", open, windowFlags);
        if (!dockspaceKeepWindowPadding.get()) {
            IkGui.popStyleVar();
        }
        if (dockspaceFullscreen.get()) {
            IkGui.popStyleVar(2);
        }

        // Submit the dockspace widget inside our window. Note that the ID here is different from
        // the one used by dockSpaceOverViewport(), so the docking state won't get transferred
        // between the basic and advanced demos.
        final int dockspaceID = IkGui.getID("MyDockSpace");
        IkGui.dockSpace(dockspaceID, 0.0f, 0.0f, flags);

        IkGui.end();
    }

    /**
     * The basic version, which you can use in many apps: dockSpaceOverViewport(), optionally with
     * DockNodeFlags.PASSTHROUGH_CENTRAL_NODE to make the central node transparent.
     *
     * @param flags The dock node flags.
     */
    private static void showExampleAppDockSpaceBasic(int flags) {
        IkGui.dockSpaceOverViewport(0, null, flags);
    }

    private static void showExampleAppDockSpace(IkBoolean open) {
        if (dockspaceDemoMode.get() == 0) {
            showExampleAppDockSpaceBasic(dockspaceFlags.get());
        } else {
            showExampleAppDockSpaceAdvanced(open);
        }

        // Refocus our window to minimize perceived loss of focus when changing mode (caused by
        // the fact that each uses a different window, which would not happen in a real app)
        if (dockspaceDemoModeChanged) {
            IkGui.setNextWindowFocus();
        }
        IkGui.begin("Examples: Dockspace", open, WindowFlags.MENU_BAR);
        dockspaceDemoModeChanged = false;
        dockspaceDemoModeChanged |= IkGui.radioButton("Basic demo mode", dockspaceDemoMode, 0);
        dockspaceDemoModeChanged |= IkGui.radioButton("Advanced demo mode", dockspaceDemoMode, 1);

        IkGui.separatorText("Options");

        if ((IkGui.getIO().configFlags & ConfigFlags.DOCKING_ENABLE) == 0) {
            showDockingDisabledMessage();
        } else if (dockspaceDemoMode.get() == 0) {
            // Allowed flags
            dockspaceFlags.set(dockspaceFlags.get() & DockNodeFlags.PASSTHROUGH_CENTRAL_NODE);
            IkGui.checkboxFlags(
                    "Flag: PassthroughCentralNode",
                    dockspaceFlags,
                    DockNodeFlags.PASSTHROUGH_CENTRAL_NODE);
        } else {
            IkGui.checkbox("Fullscreen", dockspaceFullscreen);
            IkGui.checkbox("Keep Window Padding", dockspaceKeepWindowPadding);
            IkGui.sameLine();
            helpMarker(
                    "This is mostly exposed to facilitate understanding that a dockSpace() is"
                            + " inside a window.");
            IkGui.beginDisabled(!dockspaceFullscreen.get());
            IkGui.checkboxFlags(
                    "Flag: PassthroughCentralNode",
                    dockspaceFlags,
                    DockNodeFlags.PASSTHROUGH_CENTRAL_NODE);
            IkGui.endDisabled();
            IkGui.checkboxFlags(
                    "Flag: NoDockingOverCentralNode",
                    dockspaceFlags,
                    DockNodeFlags.NO_DOCKING_OVER_CENTRAL_NODE);
            IkGui.checkboxFlags(
                    "Flag: NoDockingSplit", dockspaceFlags, DockNodeFlags.NO_DOCKING_SPLIT);
            IkGui.checkboxFlags("Flag: NoUndocking", dockspaceFlags, DockNodeFlags.NO_UNDOCKING);
            IkGui.checkboxFlags("Flag: NoResize", dockspaceFlags, DockNodeFlags.NO_RESIZE);
            IkGui.checkboxFlags(
                    "Flag: AutoHideTabBar", dockspaceFlags, DockNodeFlags.AUTO_HIDE_TAB_BAR);
        }

        // Show demo options and help
        if (IkGui.beginMenuBar()) {
            if (IkGui.beginMenu("Help")) {
                IkGui.textUnformatted(
                        "This demonstrates the use of IkGui.dockSpace() which allows you to"
                                + " manually\ncreate a docking node within another window.\n"
                                + "The \"Basic\" version uses the IkGui.dockSpaceOverViewport()"
                                + " helper. Most applications can probably use this.");
                IkGui.separator();
                IkGui.textUnformatted(
                        "When docking is enabled, you can ALWAYS dock MOST window into another!"
                                + " Try it now!\n"
                                + "- Drag from window title bar or their tab to dock/undock.\n"
                                + "- Drag from window menu button (upper-left button) to undock"
                                + " an entire node (all windows).\n"
                                + "- Hold SHIFT to disable docking (if io.configDockingWithShift"
                                + " == false, default)\n"
                                + "- Hold SHIFT to enable docking (if io.configDockingWithShift =="
                                + " true)");
                IkGui.separator();
                IkGui.textUnformatted("More details:");
                IkGui.bullet();
                IkGui.sameLine();
                IkGui.textLinkOpenURL(
                        "Docking Wiki page", "https://github.com/ocornut/imgui/wiki/Docking");
                IkGui.bulletText("Read comments in showExampleAppDockSpace()");
                IkGui.endMenu();
            }
            IkGui.endMenuBar();
        }

        IkGui.end();
    }

    // ---------------------------------------------------------------------------------------------
    // Example App: Documents Handling
    // ---------------------------------------------------------------------------------------------

    /** Simplified structure to mimic a document model. */
    static class MyDocument {
        /** The document title. */
        final IkString name;

        /** Unique ID, which is necessary as we can change the title. */
        final int uid;

        /**
         * Set when open (we keep a list of all available documents to simplify demo code!). Used
         * with the tab or window close button.
         */
        final IkBoolean open;

        /** Copy of open from the last update. */
        boolean openPrevious;

        /** Set when the document has been modified. */
        boolean dirty;

        /** An arbitrary variable associated with the document. */
        final float[] color;

        MyDocument(int uid, String name, boolean open, float[] color) {
            this.uid = uid;
            this.name = new IkString(name, 32);
            this.open = new IkBoolean(open);
            openPrevious = open;
            dirty = false;
            this.color = color;
        }

        void doOpen() {
            open.set(true);
        }

        void doForceClose() {
            open.set(false);
            dirty = false;
        }

        void doSave() {
            dirty = false;
        }
    }

    static class ExampleAppDocuments {
        final List<MyDocument> documents = new ArrayList<>();
        final List<MyDocument> closeQueue = new ArrayList<>();
        MyDocument renamingDoc = null;
        boolean renamingStarted = false;

        ExampleAppDocuments() {
            documents.add(new MyDocument(0, "Lettuce", true, new float[] {0.4f, 0.8f, 0.4f, 1.0f}));
            documents.add(
                    new MyDocument(1, "Eggplant", true, new float[] {0.8f, 0.5f, 1.0f, 1.0f}));
            documents.add(new MyDocument(2, "Carrot", true, new float[] {1.0f, 0.8f, 0.5f, 1.0f}));
            documents.add(new MyDocument(3, "Tomato", false, new float[] {1.0f, 0.3f, 0.4f, 1.0f}));
            documents.add(
                    new MyDocument(
                            4, "A Rather Long Title", false, new float[] {0.4f, 0.8f, 0.8f, 1.0f}));
            documents.add(
                    new MyDocument(
                            5, "Some Document", false, new float[] {0.8f, 0.8f, 1.0f, 1.0f}));
        }

        /**
         * As we allow changing the document name, we append a never-changing document ID so tabs
         * are stable.
         */
        String getTabName(MyDocument doc) {
            return doc.name.get() + "###doc" + doc.uid;
        }

        /** Display placeholder contents for the document. */
        void displayDocContents(MyDocument doc) {
            IkGui.pushID(doc.uid);
            IkGui.text("Document \"" + doc.name.get() + "\"");
            IkGui.pushStyleColor(
                    ColorType.TEXT,
                    Color.rgba(doc.color[0], doc.color[1], doc.color[2], doc.color[3]));
            IkGui.textWrapped(
                    "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor"
                            + " incididunt ut labore et dolore magna aliqua.");
            IkGui.popStyleColor();

            IkGui.setNextItemShortcut(KeyChord.of(KeyModFlags.CTRL, Key.R), InputFlags.TOOLTIP);
            if (IkGui.button("Rename..")) {
                renamingDoc = doc;
                renamingStarted = true;
            }
            IkGui.sameLine();

            IkGui.setNextItemShortcut(KeyChord.of(KeyModFlags.CTRL, Key.M), InputFlags.TOOLTIP);
            if (IkGui.button("Modify")) {
                doc.dirty = true;
            }

            IkGui.sameLine();
            IkGui.setNextItemShortcut(KeyChord.of(KeyModFlags.CTRL, Key.S), InputFlags.TOOLTIP);
            if (IkGui.button("Save")) {
                doc.doSave();
            }

            IkGui.sameLine();
            IkGui.setNextItemShortcut(KeyChord.of(KeyModFlags.CTRL, Key.W), InputFlags.TOOLTIP);
            if (IkGui.button("Close")) {
                closeQueue.add(doc);
            }
            // Useful to test drag and drop and hold-dragged-to-open-tab behavior.
            IkGui.colorEdit3("color", doc.color);
            IkGui.popID();
        }

        /** Display the context menu for the document. */
        void displayDocContextMenu(MyDocument doc) {
            if (!IkGui.beginPopupContextItem()) {
                return;
            }

            if (IkGui.menuItem("Save " + doc.name.get(), "Ctrl+S", false, doc.open.get())) {
                doc.doSave();
            }
            if (IkGui.menuItem("Rename...", "Ctrl+R", false, doc.open.get())) {
                renamingDoc = doc;
            }
            if (IkGui.menuItem("Close", "Ctrl+W", false, doc.open.get())) {
                closeQueue.add(doc);
            }
            IkGui.endPopup();
        }

        /**
         * [Optional] Notify the system of tabs/windows closure that happened outside the regular
         * tab interface. If a tab has been closed programmatically (aka closed from another source
         * such as the checkbox in the demo, as opposed to clicking on the regular tab closing
         * button) and stops being submitted, it will take a frame for the tab bar to notice its
         * absence. During this frame there will be a gap in the tab bar, and if the tab that has
         * disappeared was the selected one, the tab bar will report no selected tab during the
         * frame. This will effectively give the impression of a flicker for one frame. We call
         * setTabItemClosed() to manually notify the tab bar or docking system of removed tabs to
         * avoid this glitch. Note that this is completely optional, and only affects tab bars with
         * the TabBarFlags.REORDERABLE flag.
         */
        void notifyOfDocumentsClosedElsewhere() {
            for (MyDocument doc : documents) {
                if (!doc.open.get() && doc.openPrevious) {
                    IkGui.setTabItemClosed(doc.name.get());
                }
                doc.openPrevious = doc.open.get();
            }
        }
    }

    static final ExampleAppDocuments documentsApp = new ExampleAppDocuments();

    /** Create documents as local tabs into a local tab bar. */
    private static final int DOCUMENTS_TARGET_TAB = 1;

    /** Create documents as regular windows, and create an embedded dockspace. */
    private static final int DOCUMENTS_TARGET_DOCKSPACE_AND_WINDOW = 2;

    private static final IkInt documentsTarget = new IkInt(DOCUMENTS_TARGET_TAB);
    private static final IkBoolean documentsReorderable = new IkBoolean(true);
    private static final int documentsFittingFlags = TabBarFlags.FITTING_POLICY_DEFAULT;

    static void showExampleAppDocuments(IkBoolean open) {
        final ExampleAppDocuments app = documentsApp;

        // When the target is DOCKSPACE_AND_WINDOW there is the possibility that one of our child
        // document windows (e.g. "Eggplant") that we emit gets docked into the same spot as the
        // parent window ("Example: Documents"). This would create a problematic feedback loop
        // because selecting the "Eggplant" tab would make the "Example: Documents" tab not
        // visible, which in turn would stop submitting the "Eggplant" window. We avoid this
        // problem by submitting our documents window even if our parent window is not currently
        // visible. Another solution may be to make the "Example: Documents" window use the
        // WindowFlags.NO_DOCKING flag.

        final boolean windowContentsVisible =
                IkGui.begin("Example: Documents", open, WindowFlags.MENU_BAR);
        if (!windowContentsVisible
                && documentsTarget.get() != DOCUMENTS_TARGET_DOCKSPACE_AND_WINDOW) {
            IkGui.end();
            return;
        }

        // Menu
        if (IkGui.beginMenuBar()) {
            if (IkGui.beginMenu("File")) {
                int openCount = 0;
                for (MyDocument doc : app.documents) {
                    openCount += doc.open.get() ? 1 : 0;
                }

                if (IkGui.beginMenu("Open", openCount < app.documents.size())) {
                    for (MyDocument doc : app.documents) {
                        if (!doc.open.get() && IkGui.menuItem(doc.name.get())) {
                            doc.doOpen();
                        }
                    }
                    IkGui.endMenu();
                }
                if (IkGui.menuItem("Close All Documents", null, false, openCount > 0)) {
                    app.closeQueue.addAll(app.documents);
                }
                if (IkGui.menuItem("Exit") && open != null) {
                    open.set(false);
                }
                IkGui.endMenu();
            }
            IkGui.endMenuBar();
        }

        // [Debug] List documents with one checkbox for each
        for (int docN = 0; docN < app.documents.size(); docN++) {
            final MyDocument doc = app.documents.get(docN);
            if (docN > 0) {
                IkGui.sameLine();
            }
            IkGui.pushID(doc.uid);
            if (IkGui.checkbox(doc.name.get(), doc.open) && !doc.open.get()) {
                doc.doForceClose();
            }
            IkGui.popID();
        }
        IkGui.pushItemWidth(IkGui.getFontSize() * 12);
        IkGui.combo(
                "Output",
                documentsTarget,
                new String[] {"None", "TabBar+Tabs", "DockSpace+Window"});
        IkGui.popItemWidth();
        boolean redockAll = false;
        if (documentsTarget.get() == DOCUMENTS_TARGET_TAB) {
            IkGui.sameLine();
            IkGui.checkbox("Reorderable Tabs", documentsReorderable);
        }
        if (documentsTarget.get() == DOCUMENTS_TARGET_DOCKSPACE_AND_WINDOW) {
            IkGui.sameLine();
            redockAll = IkGui.button("Redock all");
        }

        IkGui.separator();

        // About the WindowFlags.UNSAVED_DOCUMENT / TabItemFlags.UNSAVED_DOCUMENT flags. They have
        // multiple effects:
        // - Display a dot next to the title.
        // - Tab is selected when clicking the X close button.
        // - Closure is not assumed (will wait for user to stop submitting the tab). Otherwise
        //   closure is assumed when pressing the X, so if you keep submitting the tab may reappear
        //   at end of tab bar. We need to assume closure by default otherwise waiting for "lack of
        //   submission" on the next frame would leave an empty hole for one-frame, both in the
        //   tab-bar and in tab-contents when closing a tab/window. The rarely used
        //   setTabItemClosed() function is a way to notify of programmatic closure to avoid the
        //   one-frame hole.

        // Tabs
        if (documentsTarget.get() == DOCUMENTS_TARGET_TAB) {
            int tabBarFlags =
                    documentsFittingFlags
                            | (documentsReorderable.get() ? TabBarFlags.REORDERABLE : 0);
            tabBarFlags |= TabBarFlags.DRAW_SELECTED_OVERLINE;
            if (IkGui.beginTabBar("##tabs", tabBarFlags)) {
                if (documentsReorderable.get()) {
                    app.notifyOfDocumentsClosedElsewhere();
                }

                // Submit Tabs
                for (MyDocument doc : app.documents) {
                    if (!doc.open.get()) {
                        continue;
                    }

                    // As we allow changing the document name, we append a never-changing document
                    // id so tabs are stable
                    final int tabFlags =
                            doc.dirty ? TabItemFlags.UNSAVED_DOCUMENT : TabItemFlags.NONE;
                    final boolean visible =
                            IkGui.beginTabItem(app.getTabName(doc), doc.open, tabFlags);

                    // Cancel attempt to close when unsaved add to save queue so we can display a
                    // popup.
                    if (!doc.open.get() && doc.dirty) {
                        doc.open.set(true);
                        app.closeQueue.add(doc);
                    }

                    app.displayDocContextMenu(doc);
                    if (visible) {
                        app.displayDocContents(doc);
                        IkGui.endTabItem();
                    }
                }

                IkGui.endTabBar();
            }
        } else if (documentsTarget.get() == DOCUMENTS_TARGET_DOCKSPACE_AND_WINDOW) {
            if ((IkGui.getIO().configFlags & ConfigFlags.DOCKING_ENABLE) != 0) {
                app.notifyOfDocumentsClosedElsewhere();

                // Create a DockSpace node where any window can be docked
                final int dockspaceID = IkGui.getID("MyDockSpace");
                IkGui.dockSpace(dockspaceID);

                // Create Windows
                for (MyDocument doc : app.documents) {
                    if (!doc.open.get()) {
                        continue;
                    }

                    IkGui.setNextWindowDockID(
                            dockspaceID, redockAll ? Condition.ALWAYS : Condition.FIRST_USE_EVER);
                    final int windowFlags =
                            doc.dirty ? WindowFlags.UNSAVED_DOCUMENT : WindowFlags.NONE;
                    final boolean visible = IkGui.begin(doc.name.get(), doc.open, windowFlags);

                    // Cancel attempt to close when unsaved add to save queue so we can display a
                    // popup.
                    if (!doc.open.get() && doc.dirty) {
                        doc.open.set(true);
                        app.closeQueue.add(doc);
                    }

                    app.displayDocContextMenu(doc);
                    if (visible) {
                        app.displayDocContents(doc);
                    }

                    IkGui.end();
                }
            } else {
                showDockingDisabledMessage();
            }
        }

        // Early out other contents
        if (!windowContentsVisible) {
            IkGui.end();
            return;
        }

        // Display renaming UI
        if (app.renamingDoc != null) {
            if (app.renamingStarted) {
                IkGui.openPopup("Rename");
            }
            if (IkGui.beginPopup("Rename")) {
                IkGui.setNextItemWidth(IkGui.getFontSize() * 30);
                if (IkGui.inputText(
                        "###Name", app.renamingDoc.name, InputTextFlags.ENTER_RETURNS_TRUE)) {
                    IkGui.closeCurrentPopup();
                    app.renamingDoc = null;
                }
                if (app.renamingStarted) {
                    IkGui.setKeyboardFocusHere(-1);
                }
                IkGui.endPopup();
            } else {
                app.renamingDoc = null;
            }
            app.renamingStarted = false;
        }

        // Display closing confirmation UI
        if (!app.closeQueue.isEmpty()) {
            int closeQueueUnsavedDocuments = 0;
            for (MyDocument doc : app.closeQueue) {
                if (doc.dirty) {
                    closeQueueUnsavedDocuments++;
                }
            }

            if (closeQueueUnsavedDocuments == 0) {
                // Close documents when all are unsaved
                for (MyDocument doc : app.closeQueue) {
                    doc.doForceClose();
                }
                app.closeQueue.clear();
            } else {
                if (!IkGui.isPopupOpen("Save?")) {
                    IkGui.openPopup("Save?");
                }
                if (IkGui.beginPopupModal("Save?", null, WindowFlags.ALWAYS_AUTO_RESIZE)) {
                    IkGui.text("Save change to the following items?");
                    final float itemHeight = IkGui.getTextLineHeightWithSpacing();
                    if (IkGui.beginChild(
                            IkGui.getID("frame"),
                            -Float.MIN_VALUE,
                            6.25f * itemHeight,
                            ChildFlags.FRAME_STYLE)) {
                        for (MyDocument doc : app.closeQueue) {
                            if (doc.dirty) {
                                IkGui.text(doc.name.get());
                            }
                        }
                    }
                    IkGui.endChild();

                    final float buttonWidth = IkGui.getFontSize() * 7.0f;
                    if (IkGui.button("Yes", buttonWidth, 0.0f)) {
                        for (MyDocument doc : app.closeQueue) {
                            if (doc.dirty) {
                                doc.doSave();
                            }
                            doc.doForceClose();
                        }
                        app.closeQueue.clear();
                        IkGui.closeCurrentPopup();
                    }
                    IkGui.sameLine();
                    if (IkGui.button("No", buttonWidth, 0.0f)) {
                        for (MyDocument doc : app.closeQueue) {
                            doc.doForceClose();
                        }
                        app.closeQueue.clear();
                        IkGui.closeCurrentPopup();
                    }
                    IkGui.sameLine();
                    if (IkGui.button("Cancel", buttonWidth, 0.0f)) {
                        app.closeQueue.clear();
                        IkGui.closeCurrentPopup();
                    }
                    IkGui.endPopup();
                }
            }
        }

        IkGui.end();
    }

    // ---------------------------------------------------------------------------------------------
    // Example App: Assets Browser
    // ---------------------------------------------------------------------------------------------

    /**
     * An asset in the assets browser.
     *
     * @param id The unique ID.
     * @param type The type, which shows as a colored overlay.
     */
    record ExampleAsset(int id, int type) {
        /**
         * Sort assets by the table sort specs, by index (column 0) or type (column 1).
         *
         * @param sortSpecs The sort specs.
         * @param items The assets to sort.
         */
        static void sortWithSortSpecs(TableSortSpecs sortSpecs, List<ExampleAsset> items) {
            Comparator<ExampleAsset> comparator = null;
            for (int n = 0; n < sortSpecs.specsCount; n++) {
                final TableColumnSortSpecs sortSpec = sortSpecs.specs[n];
                Comparator<ExampleAsset> column =
                        sortSpec.columnIndex == 0
                                ? Comparator.comparingInt(ExampleAsset::id)
                                : Comparator.comparingInt(ExampleAsset::type);
                if (sortSpec.sortDirection == SortDirection.DESCENDING) {
                    column = column.reversed();
                }
                comparator = comparator == null ? column : comparator.thenComparing(column);
            }
            // Always have a way to differentiate items
            final Comparator<ExampleAsset> byID = Comparator.comparingInt(ExampleAsset::id);
            items.sort(comparator == null ? byID : comparator.thenComparing(byID));
        }
    }

    static class ExampleAssetsBrowser {
        // Options
        final IkBoolean showTypeOverlay = new IkBoolean(true);
        final IkBoolean allowSorting = new IkBoolean(true);

        /** Will set MultiSelectFlags.BOX_SELECT_2D. */
        final IkBoolean allowBoxSelect = new IkBoolean(true);

        /** Will set MultiSelectFlags.SELECT_ON_CLICK_ALWAYS. */
        final IkBoolean allowBoxSelectInsideSelection = new IkBoolean(false);

        /** Will set MultiSelectFlags.SELECT_ON_CLICK_RELEASE. */
        final IkBoolean allowDragUnselected = new IkBoolean(false);

        final float[] iconSize = {0};
        final int[] iconSpacing = {10};

        /**
         * Increase hit-spacing if you want to make it possible to clear or box-select from gaps.
         * Some spacing is required to be able to amend with Shift+box-select.
         */
        final int[] iconHitSpacing = {4};

        final IkBoolean stretchSpacing = new IkBoolean(true);

        /**
         * Debug: submit twice the number of items per line (overflow horizontally to exercise
         * scrolling on X + box-select).
         */
        final IkBoolean useScrollX = new IkBoolean(false);

        // State
        /** Our items. */
        final List<ExampleAsset> items = new ArrayList<>();

        /** Our selection, with helper functions to handle deletion. */
        final IkGuiDemo.ExampleSelectionWithDeletion selection =
                new IkGuiDemo.ExampleSelectionWithDeletion();

        /** Unique identifier when creating new items. */
        int nextItemID = 0;

        /** Deferred deletion request. */
        boolean requestDelete = false;

        /** Deferred sort request. */
        boolean requestSort = false;

        /** Mouse wheel accumulator to handle smooth wheels better. */
        float zoomWheelAccum = 0.0f;

        // Calculated sizes for layout, output of updateLayoutSizes(). Could be locals but our code
        // is simpler this way.
        float layoutItemSize;

        /** layoutItemSize + layoutItemSpacing. */
        float layoutItemStep;

        float layoutItemSpacing = 0.0f;
        float layoutSelectableSpacing = 0.0f;
        float layoutOuterPadding = 0.0f;
        int layoutColumnCount = 0;
        int layoutLineCount = 0;

        ExampleAssetsBrowser() {
            addItems(10_000);
            // Use custom selection adapter: store the ID in the selection (recommended)
            selection.adapterIndexToStorageID = index -> items.get(index).id();
        }

        void addItems(int count) {
            if (items.isEmpty()) {
                nextItemID = 0;
            }
            for (int n = 0; n < count; n++, nextItemID++) {
                final int type = (nextItemID % 20) < 15 ? 0 : (nextItemID % 20) < 18 ? 1 : 2;
                items.add(new ExampleAsset(nextItemID, type));
            }
            requestSort = true;
        }

        void clearItems() {
            items.clear();
            selection.clear();
        }

        /**
         * The layout logic, which would be written in the main code after beginChild() and
         * outputting to local variables. We extracted it into a function so we can call it easily
         * from multiple places.
         *
         * @param availableWidth The available width.
         */
        void updateLayoutSizes(float availableWidth) {
            // Layout: when not stretching: allow extending into right-most spacing.
            layoutItemSpacing = iconSpacing[0];
            if (!stretchSpacing.get()) {
                availableWidth += (float) Math.floor(layoutItemSpacing * 0.5f);
            }

            // Layout: calculate number of icon per line and number of lines
            layoutItemSize = (float) Math.floor(iconSize[0]);
            layoutColumnCount =
                    Math.max((int) (availableWidth / (layoutItemSize + layoutItemSpacing)), 1);

            // Layout: when stretching: allocate remaining space to more spacing. Round before
            // division, so item spacing may be non-integer.
            if (stretchSpacing.get() && layoutColumnCount > 1) {
                layoutItemSpacing =
                        (float) Math.floor(availableWidth - layoutItemSize * layoutColumnCount)
                                / layoutColumnCount;
            }

            if (useScrollX.get()) {
                layoutColumnCount *= 2;
            }
            layoutLineCount = (items.size() + layoutColumnCount - 1) / layoutColumnCount;

            layoutItemStep = layoutItemSize + layoutItemSpacing;
            layoutSelectableSpacing =
                    Math.max((float) Math.floor(layoutItemSpacing) - iconHitSpacing[0], 0.0f);
            layoutOuterPadding = (float) Math.floor(layoutItemSpacing * 0.5f);
        }

        void draw(String title, IkBoolean open) {
            if (iconSize[0] <= 0.0f) {
                iconSize[0] = IkGui.calcTextSize("99999").x;
            }

            IkGui.setNextWindowSize(iconSize[0] * 25, iconSize[0] * 15, Condition.FIRST_USE_EVER);
            if (!IkGui.begin(title, open, WindowFlags.MENU_BAR)) {
                IkGui.end();
                return;
            }

            drawMenuBar(open);

            // Show a table with ONLY one header row to showcase the idea/possibility of using this
            // to provide a sorting UI
            if (allowSorting.get()) {
                IkGui.pushStyleVarFloat2(StyleVariable.ITEM_SPACING, 0, 0);
                final int tableFlagsForSortSpecs =
                        TableFlags.SORTABLE
                                | TableFlags.SORT_MULTI
                                | TableFlags.SIZING_FIXED_FIT
                                | TableFlags.BORDERS;
                if (IkGui.beginTable(
                        "for_sort_specs_only",
                        2,
                        tableFlagsForSortSpecs,
                        0.0f,
                        IkGui.getFrameHeight())) {
                    IkGui.tableSetupColumn("Index");
                    IkGui.tableSetupColumn("Type");
                    IkGui.tableHeadersRow();
                    final TableSortSpecs sortSpecs = IkGui.tableGetSortSpecs();
                    if (sortSpecs != null && (sortSpecs.specsDirty || requestSort)) {
                        ExampleAsset.sortWithSortSpecs(sortSpecs, items);
                        sortSpecs.specsDirty = requestSort = false;
                    }
                    IkGui.endTable();
                }
                IkGui.popStyleVar();
            }

            final IkIO io = IkGui.getIO();
            IkGui.setNextWindowContentSize(
                    0.0f,
                    layoutOuterPadding + layoutLineCount * (layoutItemSize + layoutItemSpacing));
            if (IkGui.beginChild(
                    "Assets",
                    0.0f,
                    -IkGui.getTextLineHeightWithSpacing(),
                    ChildFlags.BORDERS,
                    WindowFlags.NO_MOVE | WindowFlags.HORIZONTAL_SCROLLBAR)) {
                drawAssets(io);
            }
            IkGui.endChild();

            IkGui.text(String.format("Selected: %d/%d items", selection.getSize(), items.size()));
            IkGui.end();
        }

        private void drawMenuBar(IkBoolean open) {
            if (!IkGui.beginMenuBar()) {
                return;
            }
            if (IkGui.beginMenu("File")) {
                if (IkGui.menuItem("Add 10000 items")) {
                    addItems(10_000);
                }
                if (IkGui.menuItem("Clear items")) {
                    clearItems();
                }
                IkGui.separator();
                if (IkGui.menuItem("Close", null, false, open != null)) {
                    open.set(false);
                }
                IkGui.endMenu();
            }
            if (IkGui.beginMenu("Edit")) {
                if (IkGui.menuItem("Delete", "Del", false, selection.getSize() > 0)) {
                    requestDelete = true;
                }
                IkGui.endMenu();
            }
            if (IkGui.beginMenu("Options")) {
                IkGui.pushItemWidth(IkGui.getFontSize() * 10);

                IkGui.separatorText("Contents");
                IkGui.checkbox("Show Type Overlay", showTypeOverlay);
                IkGui.checkbox("Allow Sorting", allowSorting);

                IkGui.separatorText("Selection Behavior");
                IkGui.checkbox("Allow box-selection", allowBoxSelect);
                if (IkGui.checkbox(
                                "Allow box-selection from selected items",
                                allowBoxSelectInsideSelection)
                        && allowBoxSelectInsideSelection.get()) {
                    allowDragUnselected.set(false);
                }
                if (IkGui.checkbox("Allow dragging unselected item", allowDragUnselected)
                        && allowDragUnselected.get()) {
                    allowBoxSelectInsideSelection.set(false);
                }

                IkGui.separatorText("Layout");
                IkGui.sliderFloat("Icon Size", iconSize, 16.0f, 128.0f, "%.0f");
                IkGui.sameLine();
                helpMarker("Use Ctrl+Wheel to zoom");
                IkGui.sliderInt("Icon Spacing", iconSpacing, 0, 32);
                IkGui.sliderInt("Icon Hit Spacing", iconHitSpacing, 0, 32);
                IkGui.checkbox("Stretch Spacing", stretchSpacing);
                IkGui.checkbox("Use ScrollX", useScrollX);
                IkGui.popItemWidth();
                IkGui.endMenu();
            }
            IkGui.endMenuBar();
        }

        private void drawAssets(IkIO io) {
            final DrawList drawList = IkGui.getWindowDrawList();

            final float availableWidth = IkGui.getContentRegionAvailableX();
            updateLayoutSizes(availableWidth);

            // Calculate and store start position.
            final Vector2f startPos = IkGui.getCursorScreenPos();
            startPos.add(layoutOuterPadding, layoutOuterPadding);
            IkGui.setCursorScreenPos(startPos.x, startPos.y);

            // Multi-select
            int multiSelectFlags =
                    MultiSelectFlags.CLEAR_ON_ESCAPE | MultiSelectFlags.CLEAR_ON_CLICK_VOID;

            // - Enable box-select (in 2D mode, so that changing box-select rectangle X1/X2
            //   boundaries will affect clipped items)
            if (allowBoxSelect.get()) {
                multiSelectFlags |= MultiSelectFlags.BOX_SELECT_2D;
            }

            // - Selection mode
            if (allowDragUnselected.get()) {
                // Rarely used: Allows dragging an unselected item without selecting it
                multiSelectFlags |= MultiSelectFlags.SELECT_ON_CLICK_RELEASE;
            } else if (allowBoxSelectInsideSelection.get()) {
                // Rarely used: Prevents drag and drop from being used on multiple-selection, but
                // allows e.g. box-select to always reselect even when clicking inside an existing
                // selection.
                multiSelectFlags |= MultiSelectFlags.SELECT_ON_CLICK_ALWAYS;
            }

            // - Enable keyboard wrapping on X axis
            multiSelectFlags |= MultiSelectFlags.NAV_WRAP_X;

            MultiSelectIO multiSelectIO =
                    IkGui.beginMultiSelect(multiSelectFlags, selection.getSize(), items.size());
            selection.applyRequests(multiSelectIO);

            final boolean wantDelete =
                    (IkGui.shortcut(KeyChord.of(Key.DELETE), InputFlags.REPEAT)
                                    && selection.getSize() > 0)
                            || requestDelete;
            final int itemCurrentIndexToFocus =
                    wantDelete ? selection.applyDeletionPreLoop(multiSelectIO, items.size()) : -1;
            requestDelete = false;

            // Push layoutSelectableSpacing (which is layoutItemSpacing minus hit-spacing, if we
            // decide to have hit gaps between items). Altering the item spacing may seem
            // unnecessary as we position every item using setCursorScreenPos()... But it is
            // necessary for two reasons:
            // - Selectables uses it by default to visually fill the space between two items.
            // - The vertical spacing would be measured by the clipper to calculate line height if
            //   we didn't provide it explicitly (here we do).
            IkGui.pushStyleVarFloat2(
                    StyleVariable.ITEM_SPACING, layoutSelectableSpacing, layoutSelectableSpacing);

            // Rendering parameters
            final int[] iconTypeOverlayColors = {
                0, Color.rgba(200, 70, 70, 255), Color.rgba(70, 170, 70, 255)
            };
            final int iconBackgroundColor = IkGui.getColorU32(Color.rgba(35, 35, 35, 220));
            final float iconTypeOverlaySize = 4.0f;
            final boolean displayLabel = layoutItemSize >= IkGui.calcTextSize("999").x;

            final int columnCount = layoutColumnCount;
            final ListClipper clipper = new ListClipper();
            clipper.begin(layoutLineCount, layoutItemStep);
            if (itemCurrentIndexToFocus != -1) {
                // Ensure focused item line is not clipped.
                clipper.includeItemByIndex(itemCurrentIndexToFocus / columnCount);
            }
            if (multiSelectIO.rangeSourceItem != -1) {
                // Ensure the range source item line is not clipped.
                clipper.includeItemByIndex((int) multiSelectIO.rangeSourceItem / columnCount);
            }
            while (clipper.step()) {
                for (int lineIndex = clipper.displayStart;
                        lineIndex < clipper.displayEnd;
                        lineIndex++) {
                    final int itemMinIndexForCurrentLine = lineIndex * columnCount;
                    final int itemMaxIndexForCurrentLine =
                            Math.min((lineIndex + 1) * columnCount, items.size());
                    for (int itemIndex = itemMinIndexForCurrentLine;
                            itemIndex < itemMaxIndexForCurrentLine;
                            ++itemIndex) {
                        drawAsset(
                                drawList,
                                startPos,
                                itemIndex,
                                lineIndex,
                                itemCurrentIndexToFocus,
                                iconTypeOverlayColors,
                                iconBackgroundColor,
                                iconTypeOverlaySize,
                                displayLabel);
                    }
                }
            }
            clipper.end();
            if (items.isEmpty()) {
                IkGui.dummy(0, 0);
            }
            IkGui.popStyleVar(); // ITEM_SPACING

            // Context menu
            if (IkGui.beginPopupContextWindow()) {
                IkGui.text(String.format("Selection: %d items", selection.getSize()));
                IkGui.separator();
                if (IkGui.menuItem("Delete", "Del", false, selection.getSize() > 0)) {
                    requestDelete = true;
                }
                IkGui.endPopup();
            }

            multiSelectIO = IkGui.endMultiSelect();
            selection.applyRequests(multiSelectIO);
            if (wantDelete) {
                selection.applyDeletionPostLoop(multiSelectIO, items, itemCurrentIndexToFocus);
            }

            // Zooming with Ctrl+Wheel
            if (IkGui.isWindowAppearing()) {
                zoomWheelAccum = 0.0f;
            }
            if (IkGui.isWindowHovered()
                    && io.mouseWheel != 0.0f
                    && IkGui.isKeyDown(Key.MOD_CTRL)
                    && !IkGui.isAnyItemActive()) {
                zoomWheelAccum += io.mouseWheel;
                if (Math.abs(zoomWheelAccum) >= 1.0f) {
                    // Calculate hovered item index from mouse location
                    final float hoveredItemNX =
                            (io.mousePosition.x - startPos.x + layoutItemSpacing * 0.5f)
                                    / layoutItemStep;
                    final float hoveredItemNY =
                            (io.mousePosition.y - startPos.y + layoutItemSpacing * 0.5f)
                                    / layoutItemStep;
                    final int hoveredItemIndex =
                            ((int) hoveredItemNY * layoutColumnCount) + (int) hoveredItemNX;

                    // Zoom
                    iconSize[0] *= (float) Math.pow(1.1f, (int) zoomWheelAccum);
                    iconSize[0] = MathUtil.clamp(iconSize[0], 16.0f, 128.0f);
                    zoomWheelAccum -= (int) zoomWheelAccum;
                    updateLayoutSizes(availableWidth);

                    // Manipulate scroll to that we will land at the same Y location of currently
                    // hovered item.
                    // - Calculate next frame position of item under mouse
                    // - Set new scroll position to be used in next beginChild() call.
                    float hoveredItemRelativePosY =
                            ((float) (hoveredItemIndex / layoutColumnCount) + hoveredItemNY % 1.0f)
                                    * layoutItemStep;
                    hoveredItemRelativePosY += IkGui.getStyle().variable.windowPadding.y;
                    final float mouseLocalY = io.mousePosition.y - IkGui.getWindowPos().y;
                    IkGui.setScrollY(hoveredItemRelativePosY - mouseLocalY);
                }
            }
        }

        private void drawAsset(
                DrawList drawList,
                Vector2f startPos,
                int itemIndex,
                int lineIndex,
                int itemCurrentIndexToFocus,
                int[] iconTypeOverlayColors,
                int iconBackgroundColor,
                float iconTypeOverlaySize,
                boolean displayLabel) {
            final ExampleAsset itemData = items.get(itemIndex);
            IkGui.pushID(itemData.id());

            // Position item
            final float posX = startPos.x + (itemIndex % layoutColumnCount) * layoutItemStep;
            final float posY = startPos.y + lineIndex * layoutItemStep;
            IkGui.setCursorScreenPos(posX, posY);

            IkGui.setNextItemSelectionUserData(itemIndex);
            boolean itemIsSelected = selection.contains(itemData.id());
            final boolean itemIsVisible = IkGui.isRectVisible(layoutItemSize, layoutItemSize);
            IkGui.selectable(
                    "", itemIsSelected, SelectableFlags.NONE, layoutItemSize, layoutItemSize);

            // Update our selection state immediately (without waiting for endMultiSelect()
            // requests) because we use this to alter the color of our text/icon.
            if (IkGui.isItemToggledSelection()) {
                itemIsSelected = !itemIsSelected;
            }

            // Focus (for after deletion)
            if (itemCurrentIndexToFocus == itemIndex) {
                IkGui.setKeyboardFocusHere(-1);
            }

            // Drag and drop
            if (IkGui.beginDragDropSource()) {
                // Create payload with full selection OR single unselected item. (the latter is
                // only possible when using MultiSelectFlags.SELECT_ON_CLICK_RELEASE)
                if (IkGui.getDragDropPayloadInfo() == null) {
                    final int[] payloadItems =
                            itemIsSelected
                                    ? selection.getSelectedItems()
                                    : new int[] {itemData.id()};
                    IkGui.setDragDropPayload("ASSETS_BROWSER_ITEMS", payloadItems);
                }

                // Display payload content in tooltip, by extracting it from the payload data (we
                // could read from selection, but it is more correct and reusable to read from
                // payload)
                final Payload payload = IkGui.getDragDropPayloadInfo();
                final int payloadCount = ((int[]) payload.data).length;
                IkGui.text(payloadCount + " assets");

                IkGui.endDragDropSource();
            }

            // Render icon (a real app would likely display an image/thumbnail here). Because we
            // use MultiSelectFlags.BOX_SELECT_2D, clipping vertical may occasionally be larger, so
            // we coarse-clip our rendering as well.
            if (itemIsVisible) {
                final float boxMinX = posX - 1;
                final float boxMinY = posY - 1;
                final float boxMaxX = boxMinX + layoutItemSize + 2;
                final float boxMaxY = boxMinY + layoutItemSize + 2;
                drawList.addRectFilled(boxMinX, boxMinY, boxMaxX, boxMaxY, iconBackgroundColor);
                if (showTypeOverlay.get() && itemData.type() != 0) {
                    final int typeColor =
                            iconTypeOverlayColors[itemData.type() % iconTypeOverlayColors.length];
                    drawList.addRectFilled(
                            boxMaxX - 2 - iconTypeOverlaySize,
                            boxMinY + 2,
                            boxMaxX - 2,
                            boxMinY + 2 + iconTypeOverlaySize,
                            typeColor);
                }
                if (displayLabel) {
                    final int labelColor =
                            IkGui.getColor(
                                    itemIsSelected ? ColorType.TEXT : ColorType.TEXT_DISABLED);
                    drawList.addText(
                            IkGui.getFontSize(),
                            boxMinX,
                            boxMaxY - IkGui.getFontSize(),
                            labelColor,
                            String.valueOf(itemData.id()));
                }
            }

            IkGui.popID();
        }
    }

    private static final ExampleAssetsBrowser assetsBrowser = new ExampleAssetsBrowser();

    static void showExampleAppAssetsBrowser(IkBoolean open) {
        assetsBrowser.draw("Example: Assets Browser", open);
    }

    /** Private constructor so this is not instantiated. */
    private IkGuiDemoExamples() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
