/*-
 * #%L
 * This file is part of libtiled-java.
 * %%
 * Copyright (C) 2004 - 2020 Thorbjørn Lindeijer <thorbjorn@lindeijer.nl>
 * Copyright (C) 2004 - 2020 Adam Turk <aturk@biggeruniverse.com>
 * Copyright (C) 2016 - 2020 Mike Thomas <mikepthomas@outlook.com>
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package org.mapeditor.core;

import java.awt.Image;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Sprite class.
 *
 * @version 1.4.2
 */
public class Sprite {

	public class KeyFrame {

		public static final int MASK_ANIMATION = 0x0000000F;

		public static final int KEY_LOOP = 0x01;
		public static final int KEY_STOP = 0x02;
		public static final int KEY_AUTO = 0x04;
		public static final int KEY_REVERSE = 0x08;

		public static final int KEY_NAME_LENGTH_MAX = 32;

		private String name = null;
		private int id = -1;
		private int flags = KeyFrame.KEY_LOOP;
		private float frameRate = 1.0f; // one fps
		private Tile[] frames;

		public KeyFrame() {
			this.flags = KeyFrame.KEY_LOOP;
		}

		public KeyFrame(String name) {
			this();
			this.name = name;
		}

		public KeyFrame(String name, Tile[] tile) {
			this(name);
			this.frames = tile;
		}

		public boolean equalsIgnoreCase(String n) {
			return this.name != null && this.name.equalsIgnoreCase(n);
		}

		public int getFlags() {
			return this.flags;
		}

		public Tile getFrame(int f) {
			if (f > 0 && f < this.frames.length) {
				return this.frames[f];
			}
			return null;
		}

		public float getFrameRate() {
			return this.frameRate;
		}

		public int getId() {
			return this.id;
		}

		public int getLastFrame() {
			return this.frames.length - 1;
		}

		public String getName() {
			return this.name;
		}

		public int getTotalFrames() {
			return this.frames.length;
		}

		public boolean isFrameLast(int frame) {
			return this.frames.length - 1 == frame;
		}

		public void setFlags(int f) {
			this.flags = f;
		}

		public void setFrameRate(float r) {
			this.frameRate = r;
		}

		public void setId(int id) {
			this.id = id;
		}

		public void setName(String name) {
			this.name = name;
		}

		@Override
		public String toString() {
			return "(" + this.name + ")" + this.id + ": @ " + this.frameRate;
		}
	}

	private List<KeyFrame> keys;
	private int borderWidth = 0;
	private int fpl = 0;

	private int totalKeys = -1;
	private float currentFrame = 0;
	private Rectangle frameSize;

	private boolean bPlaying = true;

	private KeyFrame currentKey = null;

	/**
	 * Constructor for Sprite.
	 */
	public Sprite() {
		this.frameSize = new Rectangle();
		this.keys = new ArrayList<>();
	}

	/**
	 * Constructor for Sprite.
	 *
	 * @param image a {@link java.awt.Image} object.
	 * @param fpl a int.
	 * @param border a int.
	 * @param totalFrames a int.
	 */
	public Sprite(Image image, int fpl, int border, int totalFrames) {
		Tile[] frames = null;
		this.fpl = fpl;
		this.borderWidth = border;

		// TODO: break up the image into tiles
		// given this information, extrapolate the rest...
		this.frameSize.width =
			image.getWidth(null) / (fpl + this.borderWidth * fpl);
		this.frameSize.height =
			(int) (image.getHeight(null) / (Math.ceil(totalFrames / fpl)
				+ Math.ceil(totalFrames / fpl) * this.borderWidth));
		this.createKey("", frames, KeyFrame.KEY_LOOP);
	}

	/**
	 * Constructor for Sprite.
	 *
	 * @param frames an array of {@link org.mapeditor.core.Tile} objects.
	 */
	public Sprite(Tile[] frames) {
		this.setFrames(frames);
	}

	/**
	 * addKey.
	 *
	 * @param k a {@link org.mapeditor.core.Sprite.KeyFrame} object.
	 */
	public void addKey(KeyFrame k) {
		this.keys.add(k);
	}

	/**
	 * createKey.
	 *
	 * @param name a {@link java.lang.String} object.
	 * @param frames an array of {@link org.mapeditor.core.Tile} objects.
	 * @param flags a int.
	 */
	public final void createKey(String name, Tile[] frames, int flags) {
		KeyFrame kf = new KeyFrame(name, frames);
		kf.setName(name);
		kf.setFlags(flags);
		this.addKey(kf);
	}

	/**
	 * Getter for the field <code>borderWidth</code>.
	 *
	 * @return a int.
	 */
	public int getBorderWidth() {
		return this.borderWidth;
	}

	/**
	 * Getter for the field <code>currentFrame</code>.
	 *
	 * @return a {@link org.mapeditor.core.Tile} object.
	 */
	public Tile getCurrentFrame() {
		return this.currentKey.getFrame((int) this.currentFrame);
	}

	/**
	 * getCurrentFrameRect.
	 *
	 * @return a {@link java.awt.Rectangle} object.
	 */
	public Rectangle getCurrentFrameRect() {
		int x = 0, y = 0;

		if (this.frameSize.height > 0 && this.frameSize.width > 0) {
			y = ((int) this.currentFrame / this.fpl)
				* (this.frameSize.height + this.borderWidth);
			x = ((int) this.currentFrame % this.fpl)
				* (this.frameSize.width + this.borderWidth);
		}

		return new Rectangle(x, y, this.frameSize.width, this.frameSize.height);
	}

	/**
	 * Getter for the field <code>currentKey</code>.
	 *
	 * @return a {@link org.mapeditor.core.Sprite.KeyFrame} object.
	 */
	public KeyFrame getCurrentKey() {
		return this.currentKey;
	}

	/**
	 * getFPL.
	 *
	 * @return a int.
	 */
	public int getFPL() {
		return this.fpl;
	}

	/**
	 * Getter for the field <code>frameSize</code>.
	 *
	 * @return a {@link java.awt.Rectangle} object.
	 */
	public Rectangle getFrameSize() {
		return this.frameSize;
	}

	/**
	 * getKey.
	 *
	 * @param i a int.
	 * @return a {@link org.mapeditor.core.Sprite.KeyFrame} object.
	 */
	public KeyFrame getKey(int i) {
		return this.keys.get(i);
	}

	/**
	 * getKey.
	 *
	 * @param keyName a {@link java.lang.String} object.
	 * @return a {@link org.mapeditor.core.Sprite.KeyFrame} object.
	 */
	public KeyFrame getKey(String keyName) {
		for (KeyFrame k : this.keys) {
			if (k != null && k.equalsIgnoreCase(keyName)) {
				return k;
			}
		}
		return null;
	}

	/**
	 * Getter for the field <code>keys</code>.
	 *
	 * @return a {@link java.util.Iterator} object.
	 * @throws java.lang.Exception if any.
	 */
	public Iterator<KeyFrame> getKeys() throws Exception {
		return this.keys.iterator();
	}

	/**
	 * getNextKey.
	 *
	 * @return a {@link org.mapeditor.core.Sprite.KeyFrame} object.
	 */
	public KeyFrame getNextKey() {
		Iterator<KeyFrame> itr = this.keys.iterator();
		while (itr.hasNext()) {
			KeyFrame k = itr.next();
			if (k == this.currentKey && itr.hasNext()) {
				return itr.next();
			}
		}

		return this.keys.get(0);
	}

	/**
	 * getPreviousKey.
	 *
	 * @return a {@link org.mapeditor.core.Sprite.KeyFrame} object.
	 */
	public KeyFrame getPreviousKey() {
		// TODO: this
		return null;
	}

	/**
	 * getTotalFrames.
	 *
	 * @return a int.
	 */
	public int getTotalFrames() {
		return this.keys.stream().map(KeyFrame::getTotalFrames).reduce(0,
			Integer::sum);
	}

	/**
	 * Getter for the field <code>totalKeys</code>.
	 *
	 * @return a int.
	 */
	public int getTotalKeys() {
		return this.keys.size();
	}

	/**
	 * iterateFrame.
	 */
	public void iterateFrame() {
		if (this.currentKey != null && this.bPlaying) {
			this.setCurrentFrame(
				this.currentFrame + this.currentKey.getFrameRate());
		}
	}

	/**
	 * Sets the current frame relative to the starting frame of the current key.
	 *
	 * @param c a int.
	 */
	public void keySetFrame(int c) {
		this.setCurrentFrame(c);
	}

	/**
	 * keyStepBack.
	 *
	 * @param amt a int.
	 */
	public void keyStepBack(int amt) {
		this.setCurrentFrame(this.currentFrame - amt);
	}

	/**
	 * keyStepForward.
	 *
	 * @param amt a int.
	 */
	public void keyStepForward(int amt) {
		this.setCurrentFrame(this.currentFrame + amt);
	}

	/**
	 * play.
	 */
	public void play() {
		this.bPlaying = true;
	}

	/**
	 * removeKey.
	 *
	 * @param name a {@link java.lang.String} object.
	 */
	public void removeKey(String name) {
		this.keys.remove(this.getKey(name));
	}

	/**
	 * Setter for the field <code>borderWidth</code>.
	 *
	 * @param b a int.
	 */
	public void setBorderWidth(int b) {
		this.borderWidth = b;
	}

	/**
	 * Setter for the field <code>currentFrame</code>.
	 *
	 * @param c a float.
	 */
	public void setCurrentFrame(float c) {
		if (c < 0) {
			switch (this.currentKey.flags & KeyFrame.MASK_ANIMATION) {
				case KeyFrame.KEY_LOOP:
					this.currentFrame = this.currentKey.getLastFrame();
					break;
				case KeyFrame.KEY_AUTO:
					this.currentKey = this.getPreviousKey();
					this.currentFrame = this.currentKey.getLastFrame();
					break;
				case KeyFrame.KEY_REVERSE:
					this.currentKey
						.setFrameRate(-this.currentKey.getFrameRate());
					this.currentFrame = 0;
					break;
				case KeyFrame.KEY_STOP:
					this.bPlaying = false;
					this.currentFrame = 0;
					break;
			}
		}
		else if (c > this.currentKey.getLastFrame()) {
			switch (this.currentKey.flags & KeyFrame.MASK_ANIMATION) {
				case KeyFrame.KEY_LOOP:
					this.currentFrame = 0;
					break;
				case KeyFrame.KEY_AUTO:
					this.currentFrame = 0;
					this.currentKey = this.getNextKey();
					break;
				case KeyFrame.KEY_REVERSE:
					this.currentKey
						.setFrameRate(-this.currentKey.getFrameRate());
					this.currentFrame = this.currentKey.getLastFrame();
					break;
				case KeyFrame.KEY_STOP:
					this.bPlaying = false;
					this.currentFrame = this.currentKey.getLastFrame();
					break;
			}
		}
		else {
			this.currentFrame = c;
		}
	}

	/**
	 * Setter for the field <code>fpl</code>.
	 *
	 * @param f a int.
	 */
	public void setFpl(int f) {
		this.fpl = f;
	}

	/**
	 * setFrames.
	 *
	 * @param frames an array of {@link org.mapeditor.core.Tile} objects.
	 */
	public final void setFrames(Tile[] frames) {
		this.frameSize =
			new Rectangle(0, 0, frames[0].getWidth(), frames[0].getHeight());

		this.createKey("", frames, KeyFrame.KEY_LOOP);
	}

	/**
	 * Setter for the field <code>frameSize</code>.
	 *
	 * @param w a int.
	 * @param h a int.
	 */
	public void setFrameSize(int w, int h) {
		this.frameSize.width = w;
		this.frameSize.height = h;
	}

	/**
	 * setKeyFrameTo.
	 *
	 * @param name a {@link java.lang.String} object.
	 */
	public void setKeyFrameTo(String name) {
		for (KeyFrame k : this.keys) {
			if (k.equalsIgnoreCase(name)) {
				this.currentKey = k;
				break;
			}
		}
	}

	/**
	 * Setter for the field <code>totalKeys</code>.
	 *
	 * @param t a int.
	 */
	public void setTotalKeys(int t) {
		this.totalKeys = t;
	}

	/**
	 * stop.
	 */
	public void stop() {
		this.bPlaying = false;
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		return "Frame: (" + this.frameSize.width + "x" + this.frameSize.height
			+ ")\n" + "Border: " + this.borderWidth + "\n" + "FPL: " + this.fpl
			+ "\n" + "Total Frames: " + this.getTotalFrames() + "\n"
			+ "Total keys: " + this.totalKeys;
	}
}
