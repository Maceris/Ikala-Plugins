package com.ikalagaming.rpg;

import com.ikalagaming.graphics.frontend.gui.IkGui;
import com.ikalagaming.graphics.frontend.gui.data.IkBoolean;
import com.ikalagaming.graphics.frontend.gui.enums.ColorType;
import com.ikalagaming.graphics.frontend.gui.enums.Condition;
import com.ikalagaming.graphics.frontend.gui.enums.StyleVariable;
import com.ikalagaming.graphics.frontend.gui.flags.WindowFlags;
import com.ikalagaming.scripting.ScriptManager;

import lombok.Getter;
import lombok.NonNull;
import org.joml.Vector2f;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Information required to draw a dialogue window. It is expected that only one script be
 * interacting with dialogue at a time, and that there is only ever one on screen at a time at most.
 *
 * @author Ches Burks
 */
public class Dialogue {

    /**
     * The type of text. to render.
     *
     * @author Ches Burks
     */
    public enum TextType {
        /** A chat bubble on the left side. */
        CHAT_LEFT,
        /** A chat bubble on the right side. */
        CHAT_RIGHT,
        /** Plain text, like context, scene description, etc. */
        TEXT,
        /** Centered text used to describe things like actions between dialogue lines. */
        CENTER,
        /** A horizontal rule dividing text. */
        DIVIDER
    }

    /**
     * A line of text in the dialogue window.
     *
     * @author Ches Burks
     * @param type The type of text.
     * @param text The associated text.
     */
    private record ChatLine(@NonNull TextType type, @NonNull String text) {}

    /** Lines of text to show in the window. We only ever have one set up at a time. */
    private static List<ChatLine> lines = Collections.synchronizedList(new LinkedList<>());

    /** Options that are shown to the user, to respond to the chat contents. */
    private static List<String> options = Collections.synchronizedList(new LinkedList<>());

    /** Whether the window is open. */
    static IkBoolean windowOpen = new IkBoolean(false);

    /**
     * The last selection that was made.
     *
     * @return The last selection.
     */
    @Getter private static int lastDialogueSelection;

    /**
     * Add a line of centered text, used to describe things like actions between dialogue lines.
     *
     * @param text The text to show.
     */
    public static void centerText(@NonNull String text) {
        lines.add(new ChatLine(TextType.CENTER, text));
    }

    /** Clear out all of the lines and options, to prepare for a new window. */
    public static void clearDialogue() {
        synchronized (lines) {
            lines.clear();
        }
        synchronized (options) {
            options.clear();
        }
    }

    /** Add a horizontal divider. */
    public static void divider() {
        lines.add(new ChatLine(TextType.DIVIDER, ""));
    }

    /** Hide the dialogue window. */
    public static void hideDialogue() {
        windowOpen.set(false);
    }

    /**
     * Add a chat bubble on the left side.
     *
     * @param text The text to show.
     */
    public static void leftChat(@NonNull String text) {
        lines.add(new ChatLine(TextType.CHAT_LEFT, text));
    }

    /**
     * Add a new user-selectable option to the window.
     *
     * @param text The text to show.
     * @see #getLastDialogueSelection()
     */
    public static void option(@NonNull String text) {
        options.add(text);
    }

    /** Draw the window using IkGui. */
    public static void renderWindow() {
        IkGui.setNextWindowPos(470, 30, Condition.ONCE);
        IkGui.setNextWindowSize(600, 500, Condition.ONCE);
        IkGui.begin("Dialogue", windowOpen, WindowFlags.NO_TITLE_BAR | WindowFlags.NO_RESIZE);
        Vector2f textSize = new Vector2f();
        float width;
        IkGui.beginChild(
                "DialogueText",
                0,
                IkGui.getContentRegionAvailableY()
                        - IkGui.getTextLineHeightWithSpacing() * (options.size() + 1));
        IkGui.pushStyleVarFloat(StyleVariable.FRAME_ROUNDING, 10f);
        IkGui.pushStyleVarFloat(StyleVariable.DISABLED_ALPHA, 1f);
        IkGui.beginDisabled();
        synchronized (lines) {
            for (ChatLine line : lines) {
                switch (line.type) {
                    case CENTER:
                        width = IkGui.getContentRegionAvailableX();
                        IkGui.calcTextSize(textSize, line.text);
                        IkGui.setCursorPosX((width - textSize.x) * 0.5f);
                        IkGui.text(line.text);
                        break;
                    case CHAT_LEFT:
                        IkGui.pushStyleColor(ColorType.BUTTON, 1f, 0.2f, 0.2f, 1f);

                        IkGui.button(line.text);
                        IkGui.popStyleColor();
                        break;
                    case CHAT_RIGHT:
                        width = IkGui.getContentRegionAvailableX();
                        IkGui.calcTextSize(textSize, line.text);
                        IkGui.setCursorPosX(Math.max(width - (textSize.x + 8), 0));
                        IkGui.pushStyleColor(ColorType.BUTTON, 0.2f, 0.2f, 1f, 1f);
                        IkGui.button(line.text);
                        IkGui.popStyleColor();
                        break;
                    case DIVIDER:
                        IkGui.separator();
                        break;
                    case TEXT:
                    default:
                        IkGui.textWrapped(line.text);
                        break;
                }
            }
        }
        IkGui.endDisabled();
        IkGui.popStyleVar();
        IkGui.popStyleVar();
        IkGui.endChild();

        IkGui.separator();
        synchronized (options) {
            for (int i = 0; i < options.size(); ++i) {
                if (IkGui.selectable(String.format("%s", options.get(i)))) {
                    lastDialogueSelection = i;
                    ScriptManager.resume("Dialogue");
                }
            }
        }

        IkGui.end();
    }

    /**
     * Add a chat bubble on the right side.
     *
     * @param text The text to show.
     */
    public static void rightChat(@NonNull String text) {
        lines.add(new ChatLine(TextType.CHAT_RIGHT, text));
    }

    /** Show the dialogue window. */
    public static void showDialogue() {
        windowOpen.set(true);
    }

    /**
     * Add a line of regular text, like context, scene description, etc.
     *
     * @param text The text to show.
     */
    public static void text(@NonNull String text) {
        lines.add(new ChatLine(TextType.TEXT, text));
    }
}
