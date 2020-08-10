package com.ikalagaming.gui.console;

import com.ikalagaming.event.EventManager;
import com.ikalagaming.event.Listener;
import com.ikalagaming.gui.console.events.ConsoleCommandEntered;
import com.ikalagaming.localization.Localization;
import com.ikalagaming.logging.Logging;
import com.ikalagaming.plugins.Plugin;
import com.ikalagaming.plugins.PluginManager;
import com.ikalagaming.util.SafeResourceLoader;

import java.awt.Color;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import javax.swing.ActionMap;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.WindowConstants;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;

/**
 * A simple console.
 *
 * @author Ches Burks
 *
 */
public class Console extends WindowAdapter implements Plugin, ClipboardOwner {

	private class ConsoleKeyListener extends KeyAdapter {

		private Console parent;

		public ConsoleKeyListener(Console parentConsole) {
			this.parent = parentConsole;
		}

		private void handleArrow(int keyCode) {
			if (Console.ARROW_LEFT.contains(keyCode)) {
				this.parent.moveLeft();
			}
			else if (Console.ARROW_RIGHT.contains(keyCode)) {
				this.parent.moveRight();
			}
			else if (Console.ARROW_UP.contains(keyCode)) {
				if (this.parent.history.hasPrevious()) {
					this.parent
						.setCurrentText(this.parent.history.getPrevious());
				}
			}
			else if (Console.ARROW_DOWN.contains(keyCode)) {
				if (this.parent.history.hasNext()) {
					this.parent.setCurrentText(this.parent.history.getNext());
				}
			}
		}

		@Override
		public void keyPressed(KeyEvent event) {
			int keyCode = event.getKeyCode();

			if (Console.ARROWS.contains(keyCode)) {
				this.handleArrow(keyCode);
			}
			else if (keyCode == KeyEvent.VK_ENTER) {
				this.parent.runLine();
			}
			else if (keyCode == KeyEvent.VK_BACK_SPACE) {
				this.parent.delChar();
			}
			else if (keyCode == KeyEvent.VK_V && event.isControlDown()) {
				this.parent.setCurrentText(this.parent.getClipboardContents());
			}
			else if (keyCode == KeyEvent.VK_C && event.isControlDown()) {
				if (this.parent.textArea.getSelectedText() != null) {
					this.parent.setClipboardContents(
						this.parent.textArea.getSelectedText());
				}
			}
			else if (Console.LETTERS.contains(keyCode)) {
				this.parent.addChar(event.getKeyChar());
			}
		}

		@Override
		public void keyReleased(KeyEvent event) {}
	}

	private static final Integer[] LETTER_VALUES = new Integer[] {KeyEvent.VK_0,
		KeyEvent.VK_0, KeyEvent.VK_1, KeyEvent.VK_2, KeyEvent.VK_3,
		KeyEvent.VK_4, KeyEvent.VK_5, KeyEvent.VK_6, KeyEvent.VK_7,
		KeyEvent.VK_8, KeyEvent.VK_9, KeyEvent.VK_A, KeyEvent.VK_B,
		KeyEvent.VK_C, KeyEvent.VK_D, KeyEvent.VK_E, KeyEvent.VK_F,
		KeyEvent.VK_G, KeyEvent.VK_H, KeyEvent.VK_I, KeyEvent.VK_J,
		KeyEvent.VK_K, KeyEvent.VK_L, KeyEvent.VK_M, KeyEvent.VK_N,
		KeyEvent.VK_O, KeyEvent.VK_P, KeyEvent.VK_Q, KeyEvent.VK_R,
		KeyEvent.VK_S, KeyEvent.VK_T, KeyEvent.VK_U, KeyEvent.VK_V,
		KeyEvent.VK_W, KeyEvent.VK_X, KeyEvent.VK_Y, KeyEvent.VK_Z,
		KeyEvent.VK_NUMPAD0, KeyEvent.VK_NUMPAD1, KeyEvent.VK_NUMPAD2,
		KeyEvent.VK_NUMPAD3, KeyEvent.VK_NUMPAD4, KeyEvent.VK_NUMPAD5,
		KeyEvent.VK_NUMPAD6, KeyEvent.VK_NUMPAD7, KeyEvent.VK_NUMPAD8,
		KeyEvent.VK_NUMPAD9, KeyEvent.VK_AMPERSAND, KeyEvent.VK_ASTERISK,
		KeyEvent.VK_AT, KeyEvent.VK_BACK_SLASH, KeyEvent.VK_BRACELEFT,
		KeyEvent.VK_BRACERIGHT, KeyEvent.VK_CLOSE_BRACKET, KeyEvent.VK_COLON,
		KeyEvent.VK_COMMA, KeyEvent.VK_DOLLAR, KeyEvent.VK_EQUALS,
		KeyEvent.VK_EXCLAMATION_MARK, KeyEvent.VK_GREATER,
		KeyEvent.VK_LEFT_PARENTHESIS, KeyEvent.VK_LESS, KeyEvent.VK_MINUS,
		KeyEvent.VK_NUMBER_SIGN, KeyEvent.VK_OPEN_BRACKET, KeyEvent.VK_PERIOD,
		KeyEvent.VK_PLUS, KeyEvent.VK_QUOTE, KeyEvent.VK_QUOTEDBL,
		KeyEvent.VK_RIGHT_PARENTHESIS, KeyEvent.VK_SEMICOLON, KeyEvent.VK_SLASH,
		KeyEvent.VK_SPACE};

	/**
	 * All characters that are permitted to be typed into the console.
	 */
	static final HashSet<Integer> LETTERS =
		new HashSet<>(Arrays.asList(Console.LETTER_VALUES));
	/**
	 * Keys that represent the left arrow on the keyboard.
	 */
	static final HashSet<Integer> ARROW_LEFT =
		new HashSet<>(Arrays.asList(KeyEvent.VK_LEFT, KeyEvent.VK_KP_LEFT));
	/**
	 * Keys that represent the right arrow on the keyboard.
	 */
	static final HashSet<Integer> ARROW_RIGHT =
		new HashSet<>(Arrays.asList(KeyEvent.VK_RIGHT, KeyEvent.VK_KP_RIGHT));
	/**
	 * Keys that represent the up arrow on the keyboard.
	 */
	static final HashSet<Integer> ARROW_UP =
		new HashSet<>(Arrays.asList(KeyEvent.VK_UP, KeyEvent.VK_KP_UP));

	/**
	 * Keys that represent the down arrow on the keyboard.
	 */
	static final HashSet<Integer> ARROW_DOWN =
		new HashSet<>(Arrays.asList(KeyEvent.VK_DOWN, KeyEvent.VK_KP_DOWN));

	/**
	 * Up, down, left, and right arrows.
	 */
	static final HashSet<Integer> ARROWS =
		new HashSet<>(Arrays.asList(KeyEvent.VK_LEFT, KeyEvent.VK_KP_LEFT,
			KeyEvent.VK_RIGHT, KeyEvent.VK_KP_RIGHT, KeyEvent.VK_UP,
			KeyEvent.VK_KP_UP, KeyEvent.VK_DOWN, KeyEvent.VK_KP_DOWN));

	/**
	 * The default width of the console. The console will start out with this
	 * dimension.
	 */
	static final int DEFAULT_WIDTH = 680;
	/**
	 * The default height of the console. The console will start out with this
	 * dimension.
	 */
	static final int DEFAULT_HEIGHT = 350;
	/**
	 * The default number of lines allowed in the console. Lines that go past
	 * this limit are removed from the console.
	 */
	static final int DEFAULT_LINE_COUNT = 150;
	/**
	 * The name to use for the console, should be whatever is in plugin.yml
	 */
	static final String PLUGIN_NAME = "Ikala-Console";

	private ResourceBundle resourceBundle;
	private ConsoleListener listener;
	private PMEventListener listen2;
	private String windowTitle;
	private int width = Console.DEFAULT_WIDTH;
	private int height = Console.DEFAULT_HEIGHT;
	private int maxLineCount = Console.DEFAULT_LINE_COUNT;
	private Color background = new Color(2, 3, 2);

	private Color foreground = new Color(2, 200, 2);
	private JFrame frame;

	/**
	 * The main area of the console where text is shown.
	 */
	JTextArea textArea;
	private int posInString = 0;// where the cursor is in the string
	private char inputIndicator = '>';
	private String currentLine = "";
	private int currentIndicatorLine = 0;
	private final int maxHistory = 30;
	private ReentrantLock editLock = new ReentrantLock();

	/**
	 * The previous commands typed in the console.
	 */
	CommandHistory history;
	private EventManager eventManager;
	private HashSet<Listener> listeners;

	/**
	 * Constructs a console that uses the default EventManager for sending and
	 * receiving events. It is not visible or set up and must be loaded with the
	 * plugin manager before it can be used.
	 *
	 */
	public Console() {
		this(EventManager.getInstance());
	}

	/**
	 * Constructs a console that uses the given EventManager for sending and
	 * receiving events. It is not visible or set up and must be loaded with the
	 * plugin manager before it can be used.
	 *
	 * @param evtManager the event manager to use with the console
	 */
	public Console(EventManager evtManager) {
		this.eventManager = evtManager;
		this.listener = new ConsoleListener(this);
		this.listen2 = new PMEventListener(this);
	}

	/**
	 * Adds a char to the end of the current string and console line
	 *
	 * @param c the char to add
	 */
	void addChar(char c) {
		this.editLock.lock();
		try {
			this.textArea.insert("" + c,
				this.getSafeLineStartOffset(this.currentIndicatorLine)
					+ (this.posInString) + 1);
			// how many lines the current line takes up
			this.currentLine = this.currentLine.substring(0, this.posInString)
				+ c + this.currentLine.substring(this.posInString);
		}
		finally {
			this.editLock.unlock();
		}
		this.moveRight();
	}

	/**
	 * Appends the input indicator char to the console
	 */
	private void appendIndicatorChar() {
		this.editLock.lock();
		try {
			this.textArea.append("" + this.inputIndicator);
			++this.posInString;
		}
		finally {
			this.editLock.unlock();
		}
		this.moveRight();
	}

	/**
	 * Adds a String to the bottom of the console. Removes the top lines
	 * if/while they exceed the maximum line count.
	 *
	 * @param message The message to append
	 */
	public synchronized void appendMessage(String message) {
		int p = this.posInString;
		this.clearCurrentText();
		this.removeIndicatorChar();
		// the double spacing and removal of space is there to have an
		// extra gap just before the input line and any previous lines for
		// visibility
		if (this.textArea.getText().endsWith(System.lineSeparator())) {
			this.textArea.replaceRange("", this.textArea.getText().length() - 1,
				this.textArea.getText().length());
			// removes the last newline if it exists
		}
		this.textArea.append(message);
		this.textArea.append(System.lineSeparator());// extra space to be
		// removed
		this.textArea.append(System.lineSeparator());
		this.updateInputLine();
		this.textArea.append(this.currentLine);
		this.posInString = p;
		this.validatePositions();
		this.updateCaretPosition();
		while (this.textArea.getLineCount() > this.maxLineCount) {
			this.removeTopLine();
		}
	}

	/**
	 * Clears the console.
	 */
	public void clear() {
		while (this.textArea.getLineCount() > 0) {
			this.removeTopLine();
		}
		this.appendIndicatorChar();
	}

	/**
	 * Clears out the text on the current line(s). Everything after the
	 * indicator char until the end of the current string (end of the console)
	 * will be removed.
	 */
	private void clearCurrentText() {
		this.editLock.lock();
		try {
			int start;
			// fetch the index of the last line of text
			start = this.getSafeLineStartOffset(this.currentIndicatorLine);
			// add one to account for the input indicator char
			++start;
			this.textArea.replaceRange("", start,
				start + this.currentLine.length());
			this.posInString = 0;
			this.currentLine = "";
		}
		finally {
			this.editLock.unlock();
		}
		this.validatePositions();
		this.updateCaretPosition();

	}

	/**
	 * Removes a char from the end of the current string and console line
	 */
	void delChar() {
		if (this.posInString <= 0) {
			return;
		}
		int pos = this.getSafeLineStartOffset(this.currentIndicatorLine)
			+ this.posInString;
		this.textArea.replaceRange("", pos, pos + 1);

		this.currentLine = this.currentLine.substring(0, this.posInString - 1)
			+ this.currentLine.substring(this.posInString);
		this.moveLeft();
	}

	/**
	 * Get the String from the clipboard.
	 *
	 * @return any text found on the Clipboard. If one is not found, returns an
	 *         empty String.
	 */
	String getClipboardContents() {
		String result = "";
		Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
		Transferable contents = clipboard.getContents(this);
		if (contents == null) {
			return "";
		}
		boolean hasTransferableText =
			contents.isDataFlavorSupported(DataFlavor.stringFlavor);
		if (hasTransferableText) {
			try {
				result =
					(String) contents.getTransferData(DataFlavor.stringFlavor);
			}
			catch (UnsupportedFlavorException | IOException ex) {
				String msg = SafeResourceLoader
					.getString("invalid_clipboard", this.getResourceBundle())
					.concat(ex.getLocalizedMessage());
				Logging.warning(Console.PLUGIN_NAME, msg);
			}
		}
		return result;
	}

	/**
	 * Returns the window height. This is the height of the frame the console is
	 * in.
	 *
	 * @return the height of the frame
	 */
	public int getHeight() {
		return this.height;
	}

	@Override
	public Set<Listener> getListeners() {
		if (this.listeners == null) {
			this.listeners = new HashSet<>();
			this.listeners.add(this.listener);
			this.listeners.add(this.listen2);
		}
		return this.listeners;
	}

	/**
	 * Returns the maximum number of lines that are stored in the window.
	 *
	 * @return the max number of lines
	 */
	public int getMaxLineCount() {
		return this.maxLineCount;
	}

	/**
	 * Returns the resource bundle for this console
	 *
	 * @return the resource bundle
	 */
	public ResourceBundle getResourceBundle() {
		return this.resourceBundle;
	}

	/**
	 * Returns the lineStartOffset of the given line and handles errors.
	 *
	 * @param line the line to find
	 * @return the offset of the start of the line
	 */
	private int getSafeLineStartOffset(int line) {
		int theLine = line;
		if (this.textArea == null) {
			return 0;
		}
		if (theLine >= this.textArea.getLineCount()) {
			if (this.textArea.getLineCount() >= 1) {
				theLine = this.textArea.getLineCount() - 1;
			}
			else {
				theLine = 0;
			}
		}
		try {
			return theLine <= 0 ? 0 : this.textArea.getLineStartOffset(theLine);
		}
		catch (BadLocationException e) {
			String msg = SafeResourceLoader.getString("error_bad_location",
				this.getResourceBundle());
			Logging.warning(Console.PLUGIN_NAME, msg);
		}
		return 0;
	}

	/**
	 * Returns window width. This is the width of the frame the console is in.
	 *
	 * @return the width of the frame
	 */
	public int getWidth() {
		return this.width;
	}

	/**
	 * Returns the window title.
	 *
	 * @return the String that is used as the title
	 */
	public String getWindowTitle() {
		return this.windowTitle;
	}

	private void init() {
		this.frame = new JFrame(this.windowTitle);
		this.frame.setSize(this.width, this.height);
		this.frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		this.frame.setBackground(this.background);
		this.frame.setForeground(this.foreground);

		this.textArea = new JTextArea();
		this.textArea.setFont(new Font("monospaced", Font.PLAIN, 12));
		this.textArea.setEditable(false);
		this.textArea.setLineWrap(true);
		this.textArea.setBackground(this.background);
		this.textArea.setForeground(this.foreground);
		this.textArea.setCaret(new MyCaret());
		MyCaret caret = (MyCaret) this.textArea.getCaret();
		caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
		caret.setBlinkRate(500);
		caret.setVisible(true);
		this.textArea.setCaretColor(this.foreground);

		// unbind caret bindings
		ActionMap am = this.textArea.getActionMap();
		am.get("caret-down").setEnabled(false);
		am.get("caret-up").setEnabled(false);
		am.get("selection-up").setEnabled(false);// shift pressed UP
		am.get("caret-next-word").setEnabled(false);// ctrl pressed RIGHT
		am.get("selection-previous-word").setEnabled(false);// shift ctrl
		// pressed LEFT
		am.get("selection-up").setEnabled(false);// shift pressed KP_UP
		am.get("caret-down").setEnabled(false);// pressed DOWN
		am.get("caret-previous-word").setEnabled(false);// ctrl pressed LEFT
		am.get("caret-end-line").setEnabled(false);// pressed END
		am.get("selection-page-up").setEnabled(false);// shift pressed PAGE_UP
		am.get("caret-up").setEnabled(false);// pressed KP_UP
		am.get("delete-next").setEnabled(false);// pressed DELETE
		am.get("caret-begin").setEnabled(false);// ctrl pressed HOME
		am.get("selection-backward").setEnabled(false);// shift pressed LEFT
		am.get("caret-end").setEnabled(false);// ctrl pressed END
		am.get("delete-previous").setEnabled(false);// pressed BACK_SPACE
		am.get("selection-next-word").setEnabled(false);// shift ctrl pressed
		// RIGHT
		am.get("caret-backward").setEnabled(false);// pressed LEFT
		am.get("caret-backward").setEnabled(false);// pressed KP_LEFT
		am.get("selection-forward").setEnabled(false);// shift pressed KP_RIGHT
		am.get("delete-previous").setEnabled(false);// ctrl pressed H
		am.get("unselect").setEnabled(false);// ctrl pressed BACK_SLASH
		am.get("insert-break").setEnabled(false);// pressed ENTER
		am.get("selection-begin-line").setEnabled(false);// shift pressed HOME
		am.get("caret-forward").setEnabled(false);// pressed RIGHT
		am.get("selection-page-left").setEnabled(false);// shift ctrl pressed
		// PAGE_UP
		am.get("selection-down").setEnabled(false);// shift pressed DOWN
		am.get("page-down").setEnabled(false);// pressed PAGE_DOWN
		am.get("delete-previous-word").setEnabled(false);// ctrl pressed
		// BACK_SPACE
		am.get("delete-next-word").setEnabled(false);// ctrl pressed DELETE
		am.get("selection-backward").setEnabled(false);// shift pressed KP_LEFT
		am.get("selection-page-right").setEnabled(false);// shift ctrl pressed
		// PAGE_DOWN
		am.get("caret-next-word").setEnabled(false);// ctrl pressed KP_RIGHT
		am.get("selection-end-line").setEnabled(false);// shift pressed END
		am.get("caret-previous-word").setEnabled(false);// ctrl pressed KP_LEFT
		am.get("caret-begin-line").setEnabled(false);// pressed HOME
		am.get("caret-down").setEnabled(false);// pressed KP_DOWN
		am.get("selection-forward").setEnabled(false);// shift pressed RIGHT
		am.get("selection-end").setEnabled(false);// shift ctrl pressed END
		am.get("selection-previous-word").setEnabled(false);// shift ctrl
		// pressed KP_LEFT
		am.get("selection-down").setEnabled(false);// shift pressed KP_DOWN
		am.get("insert-tab").setEnabled(false);// pressed TAB
		am.get("caret-up").setEnabled(false);// pressed UP
		am.get("selection-begin").setEnabled(false);// shift ctrl pressed HOME
		am.get("selection-page-down").setEnabled(false);// shift pressed
		// PAGE_DOWN
		am.get("delete-previous").setEnabled(false);// shift pressed BACK_SPACE
		am.get("caret-forward").setEnabled(false);// pressed KP_RIGHT
		am.get("selection-next-word").setEnabled(false);// shift ctrl pressed
		// KP_RIGHT
		am.get("page-up").setEnabled(false);// pressed PAGE_UP

		this.history = new CommandHistory();
		this.history.setMaxLines(this.maxHistory);

		this.textArea.addKeyListener(new ConsoleKeyListener(this));

		this.frame.getContentPane().add(new JScrollPane(this.textArea));

		this.frame.setVisible(true);
	}

	/**
	 * Empty implementation of the ClipboardOwner interface.
	 */
	@Override
	public void lostOwnership(Clipboard aClipboard, Transferable aContents) {
		// do nothing
	}

	/**
	 * Moves the cursor left one position in the string if possible, and then
	 * updates the caret.
	 */
	void moveLeft() {
		if (this.posInString <= 0) {
			return;
		}
		this.editLock.lock();
		try {
			--this.posInString;
		}
		finally {
			this.editLock.unlock();
		}
		this.validatePositions();
		this.updateCaretPosition();
	}

	/**
	 * Moves the cursor right one position in the string if possible, and then
	 * updates the caret.
	 */
	void moveRight() {

		if (this.currentLine.length() <= 0) {
			this.validatePositions();
			this.updateCaretPosition();
			return;// do not do anything
		}
		if (this.posInString >= this.currentLine.length()) {
			this.validatePositions();
			this.updateCaretPosition();
			return;// do not do anything
		}
		if (this.posInString < 0) {
			this.editLock.lock();
			try {
				this.posInString = 0;
			}
			finally {
				this.editLock.unlock();
			}
			this.validatePositions();
			this.updateCaretPosition();
		}
		else if (this.posInString >= 0) {
			this.editLock.lock();
			try {
				++this.posInString;
			}
			finally {
				this.editLock.unlock();
			}
			this.validatePositions();
			this.updateCaretPosition();
		}

	}

	/**
	 * Moves the cursor to the next line, then shows the line indicator char.
	 */
	private void newLine() {
		this.posInString = 0;
		this.currentLine = "";
		this.textArea.append(System.lineSeparator());// this extra space is
		// removed
		this.textArea.append(System.lineSeparator());
		this.updateInputLine();
		while (this.textArea.getLineCount() > this.maxLineCount) {
			this.removeTopLine();
		}
		this.updateCaretPosition();
	}

	@Override
	public boolean onDisable() {
		this.frame.setVisible(false);
		this.frame.dispose();
		return true;
	}

	@Override
	public boolean onEnable() {
		this.init();
		this.appendIndicatorChar();
		this.appendMessage(SafeResourceLoader.getString("missed_logs",
			this.getResourceBundle()));
		return true;
	}

	@Override
	public boolean onLoad() {
		try {
			this.setResourceBundle(ResourceBundle.getBundle(
				"com.ikalagaming.gui.console.resources.Console",
				Localization.getLocale()));
		}
		catch (MissingResourceException missingResource) {
			// don't localize this since it would fail anyways
			Logging.warning(Console.PLUGIN_NAME,
				"Locale not found for Console in onLoad()");
		}
		this.windowTitle =
			SafeResourceLoader.getString("title", this.getResourceBundle());

		return true;
	}

	@Override
	public boolean onUnload() {
		if (this.frame != null) {
			this.frame.setVisible(false);
			this.frame.dispose();
			this.frame = null;
		}
		this.setResourceBundle(null);
		this.history = null;
		this.listener = null;
		if (this.listeners != null) {
			this.listeners.clear();
			this.listeners = null;
		}
		return true;
	}

	/**
	 * Replaces the input indicator char to the console
	 */
	private void removeIndicatorChar() {
		this.editLock.lock();
		try {
			int offset = this.getSafeLineStartOffset(this.currentIndicatorLine);
			this.textArea.replaceRange("", offset, offset + 1);
		}
		finally {
			this.editLock.unlock();
		}
	}

	/**
	 * Removes the top line of the input.
	 */
	private void removeTopLine() {
		int end;
		this.editLock.lock();
		try {
			end = this.textArea.getLineEndOffset(0);
			this.textArea.replaceRange("", 0, end);
		}
		catch (BadLocationException e) {
			String msg = SafeResourceLoader.getString("error_bad_location",
				this.getResourceBundle());
			Logging.warning(Console.PLUGIN_NAME, msg);
		}
		finally {
			this.editLock.lock();
		}
	}

	/**
	 * Attempts to execute the current line of input. If none exists, it does
	 * nothing.
	 */
	void runLine() {
		String line = this.currentLine;
		this.newLine();

		if (line.isEmpty()) {
			// don't do anything with an empty line
			return;
		}

		this.history.addItem(line);

		ConsoleCommandEntered cmd = new ConsoleCommandEntered(line);
		this.eventManager.fireEvent(cmd);
	}

	/**
	 * Copy a String to the clipboard, and make this class the owner of the
	 * Clipboard's contents.
	 *
	 * @param contents the new contents of the clipboard
	 */
	void setClipboardContents(String contents) {
		StringSelection stringSelection = new StringSelection(contents);
		Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
		clipboard.setContents(stringSelection, this);
	}

	/**
	 * Sets the current text. This assumes that the indicator char is already in
	 * place. This will clear out the current text if it is not already cleared.
	 *
	 * @param s the string to set the last line to
	 */
	void setCurrentText(String s) {
		if (!this.currentLine.isEmpty()) {
			this.clearCurrentText();
		}
		this.textArea.append(s);
		this.posInString = s.length();
		this.currentLine = s;
		this.validatePositions();
		this.updateCaretPosition();
	}

	/**
	 * Sets the frame height. This is the height of the frame the console is in.
	 * Negative values will result in a height of 0.
	 *
	 * @param newHeight The new height
	 */
	public void setHeight(int newHeight) {
		int h = newHeight;
		if (h < 0) {
			h = 0;
		}
		this.height = h;
		this.frame.setSize(this.frame.getWidth(), h);
	}

	/**
	 * Sets the maximum number of lines stored in the window.
	 *
	 * @param newMaxLines the maximum number of lines to store
	 */
	public void setMaxLineCount(int newMaxLines) {
		this.maxLineCount = newMaxLines;
	}

	private void setResourceBundle(ResourceBundle newBundle) {
		this.resourceBundle = newBundle;
	}

	/**
	 * Sets the frame width. This is the width of the frame the console is in.
	 * Negative values will result in a width of 0.
	 *
	 * @param newWidth The new width
	 */
	public void setWidth(int newWidth) {
		int w = newWidth;
		if (w < 0) {
			w = 0;
		}
		this.width = w;
		this.frame.setSize(w, this.frame.getHeight());
	}

	/**
	 * Sets the title of the window.
	 *
	 * @param newTitle the String to use as the title
	 */
	public void setWindowTitle(String newTitle) {
		this.windowTitle = newTitle;
		this.frame.setTitle(newTitle);
	}

	/**
	 * Moves the caret to the correct position.
	 */
	private void updateCaretPosition() {
		this.editLock.lock();
		try {
			int position =
				this.getSafeLineStartOffset(this.currentIndicatorLine)
					+ this.posInString + 1;
			if (position >= this.textArea.getText().length()) {
				position = this.textArea.getText().length();
			}

			this.textArea.setCaretPosition(position);
			if (!this.textArea.getCaret().isVisible()) {
				this.textArea.getCaret().setVisible(true);
			}
		}
		finally {
			this.editLock.unlock();
		}
	}

	/**
	 * Adds a new indicator character to the beginning of the last line.
	 */
	private void updateInputLine() {
		this.currentIndicatorLine = this.textArea.getLineCount() - 1;
		this.appendIndicatorChar();
		this.validatePositions();
		this.updateCaretPosition();
	}

	/**
	 * Checks that the cursor and string positions are valid, and fixes them if
	 * they are not.
	 */
	private void validatePositions() {
		this.editLock.lock();
		try {
			if (this.posInString < 0) {
				this.posInString = 0;
			}
			if (this.posInString > this.currentLine.length()) {
				this.posInString = this.currentLine.length();
			}
		}
		finally {
			this.editLock.unlock();
		}
	}

	@Override
	public void windowClosing(WindowEvent e) {
		String cmdUnload = SafeResourceLoader.getString("COMMAND_UNLOAD",
			PluginManager.getInstance().getResourceBundle());
		ConsoleCommandEntered cmdEvent = new ConsoleCommandEntered(cmdUnload);
		this.eventManager.fireEvent(cmdEvent);
		super.windowClosing(e);
	}

}
