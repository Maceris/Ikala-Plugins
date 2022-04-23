package com.ikalagaming.gui.console;

import java.util.LinkedList;

/**
 * A list of previously typed commands.
 *
 * @author Ches Burks
 *
 */
class CommandHistory {
	private final int defaultMaxElements = 20;
	private int maxElements;
	private LinkedList<String> commands;
	private int curPos;

	/**
	 * Constructs a command history with the default size.
	 *
	 * @see #CommandHistory(int)
	 */
	public CommandHistory() {
		this.maxElements = this.defaultMaxElements;
		this.commands = new LinkedList<>();
	}

	/**
	 * Constructs a new command history with a maxiumum size as provided.
	 *
	 * @see #CommandHistory()
	 * @param maxLines the max history size
	 */
	public CommandHistory(int maxLines) {
		this.maxElements = maxLines;
	}

	/**
	 * Adds an item to the end of the list. Items at the beginning of the list
	 * are removed if they are past the max history size.
	 *
	 * @param command the item to add
	 */
	public void addItem(String command) {
		// disallow duplicate commands
		if (this.commands.contains(command)) {
			this.commands.remove(command);
		}
		this.commands.addLast(command);
		while (this.commands.size() > this.maxElements) {
			this.commands.removeFirst();
		}
		// reset the current position to the last item added

		this.curPos = this.commands.size();
	}

	/**
	 * Returns the maximum number of items that will be stored in the history.
	 *
	 * @return the max history size
	 */
	public int getMaxLines() {
		return this.maxElements;
	}

	/**
	 * Returns the next value in the history, if it exists. This moves the
	 * current position forward one if it does have a next value. If no object
	 * exists, returns an empty string.
	 *
	 * @return the next command in the history
	 */
	public String getNext() {
		if (this.commands.size() <= 0) {
			return "";
		}
		else if (this.curPos == -1) {
			++this.curPos;
			return this.commands.get(this.curPos);
		}
		else if (this.curPos >= this.commands.size() - 1) {
			return "";
		}
		else {
			++this.curPos;
			return this.commands.get(this.curPos);
		}
	}

	/**
	 * Returns the previous value in the history, if it exists. This moves the
	 * current position backwards one if it does have a previous value. If no
	 * object exists, returns an empty string.
	 *
	 * @return the previous command in the history
	 */
	public String getPrevious() {
		if (this.commands.size() <= 0) {
			return "";
		}
		else if (this.curPos <= 0) {
			return "";
		}
		else if (this.curPos <= this.commands.size()) {
			--this.curPos;
			return this.commands.get(this.curPos);
		}
		else {
			return "";
		}
	}

	/**
	 * Returns true if there is a string after the current position in the
	 * history. If the history is empty or we are at the last entry, returns
	 * false.
	 *
	 * @return true if there is a string after the current one
	 */
	public boolean hasNext() {
		if (this.commands.size() <= 0) {
			return false;
		}
		else if (this.curPos == -1) {
			return true;
		}
		else if (this.curPos >= this.commands.size() - 1) {
			return false;
		}
		else {
			return true;
		}
	}

	/**
	 * Returns true if there is a string before the current position in the
	 * history. If the history is empty or we are at the first entry, returns
	 * false.
	 *
	 * @return true if there is a string before the current one
	 */
	public boolean hasPrevious() {
		if (this.commands.size() <= 0) {
			return false;
		}
		else if (this.curPos <= 0) {
			return false;
		}
		else if (this.curPos <= this.commands.size()) {
			return true;
		}
		else {
			return false;
		}
	}

	/**
	 * Sets the maximum number of items that will be stored in the history.
	 *
	 * @param maxLines the max history size
	 */
	public void setMaxLines(int maxLines) {
		this.maxElements = maxLines;
	}
}
