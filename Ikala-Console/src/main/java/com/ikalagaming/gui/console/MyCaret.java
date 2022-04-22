package com.ikalagaming.gui.console;

import java.awt.Graphics;
import java.awt.Rectangle;

import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;
import javax.swing.text.JTextComponent;

/**
 * A custom caret that looks better than the default one. This code is from
 * http://www.java2s.com/Code/Java/Swing-JFC/Fanciercustomcaretclass.htm
 *
 * @author Ches Burks
 *
 */
class MyCaret extends DefaultCaret {

	private static final long serialVersionUID = -389070432822516041L;

	@Override
	protected synchronized void damage(Rectangle r) {
		if (r == null) {
			return;
		}

		// give values to x,y,width,height (inherited from java.awt.Rectangle)
		this.x = r.x;
		this.y = r.y;
		this.height = r.height;
		// A value for width was probably set by paint(), which we leave alone.
		// But the first call to damage() precedes the first call to paint(), so
		// in this case we must be prepared to set a valid width, or else
		// paint()
		// will receive a bogus clip area and caret will not get drawn properly.
		if (this.width <= 0) {
			this.width = this.getComponent().getWidth();
		}

		this.repaint(); // calls getComponent().repaint(x, y, width, height)
	}

	@Override
	public void paint(Graphics g) {
		JTextComponent comp = this.getComponent();
		if (comp == null) {
			return;
		}

		int dot = this.getDot();
		Rectangle r = null;
		char dotChar;
		try {
			r = comp.modelToView(dot);
			if (r == null) {
				return;
			}
			dotChar = comp.getText(dot, 1).charAt(0);
		}
		catch (BadLocationException e) {
			return;
		}

		if ((this.x != r.x) || (this.y != r.y)) {
			// paint() has been called directly, without a previous call to
			// damage(), so do some cleanup. (This happens, for example, when
			// the
			// text component is resized.)
			this.repaint(); // erase previous location of caret
			this.x = r.x; // Update dimensions (width gets set later in this
			// method)
			this.y = r.y;
			this.height = r.height;
		}

		g.setColor(comp.getCaretColor());
		g.setXORMode(comp.getBackground()); // do this to draw in XOR mode

		dotChar = ' ';

		this.width = g.getFontMetrics().charWidth(dotChar);
		if (this.isVisible()) {
			g.fillRect(r.x, r.y, this.width, r.height);
		}
	}
}
