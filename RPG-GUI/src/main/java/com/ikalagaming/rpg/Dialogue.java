package com.ikalagaming.rpg;

import com.ikalagaming.scripting.ScriptManager;

import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import lombok.Getter;
import lombok.NonNull;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Information required to draw a dialogue window.
 *
 * @author Ches Burks
 *
 */
public class Dialogue {

	/**
	 * The type of text. to render.
	 *
	 * @author Ches Burks
	 *
	 */
	public enum TextType {
		/**
		 * A chat bubble on the left side.
		 */
		CHAT_LEFT,
		/**
		 * A chat bubble on the right side.
		 */
		CHAT_RIGHT,
		/**
		 * Plain text, like context, scene description, etc.
		 */
		TEXT,
		/**
		 * Centered text used to describe things like actions between dialogue
		 * lines.
		 */
		CENTER,
		/**
		 * A horizontal rule dividing text.
		 */
		DIVIDER
	}

	/**
	 * A line of text in the dialogue window.
	 *
	 * @author Ches Burks
	 * @param type The type of text.
	 * @param text The associated text.
	 *
	 */
	private static record ChatLine(@NonNull TextType type,
		@NonNull String text) {}

	/**
	 * Lines of text to show in the window. We only ever have one set up at a
	 * time.
	 */
	private static List<ChatLine> lines =
		Collections.synchronizedList(new LinkedList<>());

	/**
	 * Options that are shown to the user, to respond to the chat contents.
	 */
	private static List<String> options =
		Collections.synchronizedList(new LinkedList<>());

	/**
	 * Whether the window is open.
	 */
	static ImBoolean windowOpen = new ImBoolean(false);

	/**
	 * The last selection that was made.
	 *
	 * @return The last selection.
	 */
	@Getter
	private static int lastDialogueSelection;

	/**
	 * Add a line of centered text, used to describe things like actions between
	 * dialogue lines.
	 *
	 * @param text The text to show.
	 */
	public static void centerText(@NonNull String text) {
		Dialogue.lines.add(new ChatLine(TextType.CENTER, text));
	}

	/**
	 * Clear out all of the lines and options, to prepare for a new window.
	 */
	public static void clearDialogue() {
		synchronized (Dialogue.lines) {
			Dialogue.lines.clear();
		}
		synchronized (Dialogue.options) {
			Dialogue.options.clear();
		}
	}

	/**
	 * Add a horizontal divider.
	 */
	public static void divider() {
		Dialogue.lines.add(new ChatLine(TextType.DIVIDER, ""));
	}

	/**
	 * Hide the dialogue window.
	 */
	public static void hideDialogue() {
		Dialogue.windowOpen.set(false);
	}

	/**
	 * Add a chat bubble on the left side.
	 *
	 * @param text The text to show.
	 */
	public static void leftChat(@NonNull String text) {
		Dialogue.lines.add(new ChatLine(TextType.CHAT_LEFT, text));
	}

	/**
	 * Add a new user-selectable option to the window.
	 *
	 * @param text The text to show.
	 */
	public static void option(@NonNull String text) {
		Dialogue.options.add(text);
	}

	/**
	 * Draw the window using ImGui.
	 */
	public static void renderWindow() {
		ImGui.setNextWindowPos(470, 30, ImGuiCond.Once);
		ImGui.setNextWindowSize(600, 500, ImGuiCond.Once);
		ImGui.begin("Dialogue", Dialogue.windowOpen,
			ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.NoResize);
		ImVec2 textSize = new ImVec2();
		float width;
		ImGui.beginChild("DialogueText", 0,
			ImGui.getContentRegionMaxY() - ImGui.getTextLineHeightWithSpacing()
				* (Dialogue.options.size() + 1));
		ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, 10f);
		ImGui.pushStyleVar(ImGuiStyleVar.DisabledAlpha, 1f);
		ImGui.beginDisabled();
		synchronized (Dialogue.lines) {
			for (ChatLine line : Dialogue.lines) {
				switch (line.type) {
					case CENTER:
						width = ImGui.getContentRegionMaxX();
						ImGui.calcTextSize(textSize, line.text);
						ImGui.setCursorPosX((width - textSize.x) * 0.5f);
						ImGui.text(line.text);
						break;
					case CHAT_LEFT:
						ImGui.pushStyleColor(ImGuiCol.Button, 1f, 0.2f, 0.2f,
							1f);

						ImGui.button(line.text);
						ImGui.popStyleColor();
						break;
					case CHAT_RIGHT:
						width = ImGui.getContentRegionMaxX();
						ImGui.calcTextSize(textSize, line.text);
						ImGui.setCursorPosX(
							Math.max(width - (textSize.x + 8), 0));
						ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.2f, 1f,
							1f);
						ImGui.button(line.text);
						ImGui.popStyleColor();
						break;
					case DIVIDER:
						ImGui.separator();
						break;
					case TEXT:
					default:
						ImGui.textWrapped(line.text);
						break;
				}
			}
		}
		ImGui.endDisabled();
		ImGui.popStyleVar();
		ImGui.popStyleVar();
		ImGui.endChild();

		ImGui.separator();
		synchronized (Dialogue.options) {
			for (int i = 0; i < Dialogue.options.size(); ++i) {
				if (ImGui.selectable(String.format("option %d - %s", i + 1,
					Dialogue.options.get(i)))) {
					Dialogue.lastDialogueSelection = i;
					ScriptManager.resume("Dialogue");
				}
			}
		}

		ImGui.end();
	}

	/**
	 * Add a chat bubble on the right side.
	 *
	 * @param text The text to show.
	 */
	public static void rightChat(@NonNull String text) {
		Dialogue.lines.add(new ChatLine(TextType.CHAT_RIGHT, text));
	}

	/**
	 * Show the dialogue window.
	 */
	public static void showDialogue() {
		Dialogue.windowOpen.set(true);
	}

	/**
	 * Add a line of regular text, like context, scene description, etc.
	 *
	 * @param text The text to show.
	 */
	public static void text(@NonNull String text) {
		Dialogue.lines.add(new ChatLine(TextType.TEXT, text));
	}
}
