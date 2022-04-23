package com.ikalagaming.window;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * A window on the screen. These can nest and are aligned, sized, and positioned
 * relative to the parent (another window, or the whole canvas for top level
 * windows).
 *
 * @author Ches Burks
 *
 */
@Slf4j
public class Window {

	/**
	 * The style to use if a window does not have a style specifically set.
	 *
	 * @param defaultStyle The new style to use as default.
	 * @return The current default window style.
	 */
	@Getter
	@Setter
	private static WindowStyle defaultStyle = new WindowStyle();

	/**
	 * The style to use in drawing the window. If it is null, the default style
	 * is used. If not null, it overrides the default.
	 *
	 * @param style The new style to use.
	 */
	@Setter
	private WindowStyle style;

	/**
	 * How far from the alignment edge the window should be placed. The x and y
	 * values are floats representing the percentage of the parent's respective
	 * width or height to be shifted by. These should be between 0.0f and 1.0f
	 * if you want to be inside the parent window.
	 */
	protected Point localDisplace;

	/**
	 * The displacement from the parent as measured from the northwest corner of
	 * this window. This is used for calculating real screen coordinates, and
	 * recalculated whenever the window is changed or dirtied.
	 */
	private Point localDisplaceNW;

	/**
	 * The real screen displacement of the window, not local. If drawn on a
	 * canvas, this would be the displacement to properly place the window on
	 * the canvas using the canvas dimensions.
	 */
	private Point realDisplacement;

	/**
	 * The true screen width of the window. This is a percentage of the canvas
	 * this is drawn on. Should be positive and <= 1.0f, where 1 is the entire
	 * canvas. X represents width, and Y represents height.
	 */
	private Point realScale;

	/**
	 * Where in the parent should this window snap to, or have displacement
	 * measured from.
	 */
	protected Alignment align;

	/**
	 * The percent of the parent window's width to take up. Should be positive
	 * and <= 1.0f, where 1 is the entire window. X represents width, and Y
	 * represents height.
	 */
	protected Point scale;

	/**
	 * The parent window, or null if there is no parent window. A null parent
	 * means this window is a root.
	 */
	protected Window parent;

	/**
	 * True if the window has been changed and needs to be recalculated.
	 */
	protected boolean dirty;

	/**
	 * Whether or not it should be drawn on the screen. If a window is
	 * invisible, it and any children will not be drawn.
	 *
	 * @param visible If we should render this window or not.
	 */
	@Setter
	protected boolean visible;

	/**
	 * A list of sub-windows this window contains.
	 */
	private List<Window> children;

	/**
	 * True if this window absorbs touches and stops them from reaching lower
	 * windows, false if touch events can reach lower windows. Defaults to true.
	 *
	 * <p>
	 * This value only affects whether or not this window blocks touches. This
	 * means windows are allowed to absorb touches and do nothing, or not absorb
	 * touches and perform an action while allowing lower windows to be
	 * interacted with by the same touch event.
	 * </p>
	 *
	 * @param consumeTouches true if touch events should stop when they touch
	 *            this window, false if they can pass through and potentially
	 *            affect lower windows.
	 * @return true if touch events should stop when they touch this window,
	 *         false if they can pass through and potentially affect lower
	 *         windows.
	 */
	@Getter
	@Setter
	private boolean consumeTouches;

	/**
	 * Creates a default window and initializes internal variables.
	 */
	public Window() {
		this.visible = true;
		this.localDisplace = new Point();
		this.localDisplaceNW = new Point();
		this.realDisplacement = new Point();
		this.realScale = new Point();
		this.align = Alignment.NORTH_WEST;
		this.scale = new Point(0.0f, 0.0f);
		this.children = new ArrayList<>();
		this.consumeTouches = true;
		this.dirty();
	}

	/**
	 * Constructs a new Window with displacement, alignment, and size as given.
	 * These are Points with x and y values given as floats representing decimal
	 * percentage of the parent window's size.
	 *
	 * @param displacement The percent of the parent to displace away from
	 *            whatever edge it is aligned to.
	 * @param alignment What part of the parent should the displacement be
	 *            measured from.
	 * @param widthAndHeight The width and height of this window as a percent of
	 *            the parents respective width and height.
	 */
	public Window(@NonNull final Point displacement,
		@NonNull final Alignment alignment,
		@NonNull final Point widthAndHeight) {
		this();
		this.localDisplace.set(displacement);
		this.align = alignment;
		this.scale.set(widthAndHeight);
	}

	/**
	 * Adds a child to the window, sets the child to have this as a parent, and
	 * then dirties this window (and thus children).
	 *
	 * @param item The child to add to the window.
	 */
	public void addChild(@NonNull Window item) {
		Window temp = item.parent;
		while (temp != null) {
			if (temp == this || temp == item) {
				// TODO localize
				Window.log.warn("Trying to add cyclic child, canceling");
				return;
			}
			temp = temp.parent;
		}

		this.children.add(item);
		if (item.parent != this) {
			item.setParent(this);
		}
		item.updateHeights();
		this.dirty();
	}

	/**
	 * Returns true if this window contains the given point, given a certain
	 * screen width and height.
	 *
	 * @param scrWidth The width of the screen/canvas the window is in.
	 * @param scrHeight The height of the screen/canvas the window is in.
	 * @param p The point to check for.
	 * @return true if the window contains the point, otherwise false.
	 */
	public boolean containsPoint(final int scrWidth, final int scrHeight,
		final Point p) {
		Rect actualRect = new Rect(this.getActualDisplaceX() * scrWidth,
			this.getActualDisplaceY() * scrHeight,
			(this.getActualDisplaceX() + this.getActualWidth()) * scrWidth,
			(this.getActualDisplaceY() + this.getActualHeight()) * scrHeight);
		return actualRect.contains(p);
	}

	/**
	 * Package level method that allows the manager to clean up references to
	 * this window.
	 */
	void delete() {
		this.parent = null;
		this.children.forEach(WindowManager::unregisterWin);
		this.children.forEach(Window::delete);
		this.children.clear();
	}

	/**
	 * Sets the dirty flag for this window and also calls dirty on all children.
	 * This signifies the window needs to be recalculated.
	 */
	public void dirty() {
		this.dirty = true;
		for (Window item : this.children) {
			item.dirty();
		}
	}

	/**
	 * Returns the actual displacement value of the window as it would be used
	 * on a canvas as a decimal percentage of the whole, from the left side of
	 * the window.
	 *
	 * @return The width, which should be >=0 and <=1.0 but is not guaranteed to
	 *         be.
	 */
	public float getActualDisplaceX() {
		if (this.dirty) {
			this.recalculate();
		}
		return this.realDisplacement.getX();
	}

	/**
	 * Returns the actual displacement value of the window as it would be used
	 * on a canvas as a decimal percentage of the whole, from the top of the
	 * window.
	 *
	 * @return The width, which should be >=0 and <=1.0 but is not guaranteed to
	 *         be.
	 */
	public float getActualDisplaceY() {
		if (this.dirty) {
			this.recalculate();
		}
		return this.realDisplacement.getY();
	}

	/**
	 * Returns the actual scale value of the window as it would be used on a
	 * canvas.
	 *
	 * @return The width, which should be >=0 and <=1.0 but is not guaranteed to
	 *         be.
	 */
	public float getActualHeight() {
		if (this.dirty) {
			this.recalculate();
		}
		return this.realScale.getY();
	}

	/**
	 * Returns the actual scale value of the window as it would be used on a
	 * canvas.
	 *
	 * @return The width, which should be >=0 and <=1.0 but is not guaranteed to
	 *         be.
	 */
	public float getActualWidth() {
		if (this.dirty) {
			this.recalculate();
		}
		return this.realScale.getX();
	}

	/**
	 * Returns a rectangle representing the actual size and location of this
	 * window should it be mapped onto the given rectangle representing the
	 * screen, using actual pixels.
	 *
	 * @param c The size of the screen to map values to.
	 * @return The rectangle representing where this would be located on the
	 *         screen.
	 */
	public Rect getBoundingRect(Rect c) {
		if (this.dirty) {
			this.recalculate();
		}
		Rect bounds = new Rect();
		float x = this.getActualDisplaceX();
		float y = this.getActualDisplaceY();
		float w = this.getActualWidth();
		float h = this.getActualHeight();
		bounds.set(x * c.getWidth(), y * c.getHeight(), (x + w) * c.getWidth(),
			(y + h) * c.getHeight());
		return bounds;
	}

	/**
	 * Returns the number of children belonging to this window.
	 *
	 * @return The size of the children list.
	 */
	public int getChildCount() {
		return this.children.size();
	}

	/**
	 * Returns the float value representing the decimal percentage of the parent
	 * window's height that this window takes up.
	 *
	 * @return The local height.
	 */
	public float getLocalHeight() {
		return this.scale.getY();
	}

	/**
	 * Returns the float value representing the decimal percentage of the parent
	 * window's width that this window takes up.
	 *
	 * @return The local width.
	 */
	public float getLocalWidth() {
		return this.scale.getX();
	}

	/**
	 * Returns the parents actual displacement, which is determined by recursing
	 * up the tree of windows. If the parent is null (that is, this is a root
	 * window), returns 0.0f so as to not change calculations (it measures from
	 * (0,0)).
	 *
	 * @return The parents actual displace percentage, or 0 if there is no
	 *         parent.
	 */
	private float getParentRealDisplaceX() {
		if (this.parent == null) {
			return 0.0f; // so calculations aren't affected
		}
		return this.parent.getActualDisplaceX();
	}

	/**
	 * Returns the parents actual displacement, which is determined by recursing
	 * up the tree of windows. If the parent is null (that is, this is a root
	 * window), returns 0.0f so as to not change calculations (it measures from
	 * (0,0)).
	 *
	 * @return The parents actual displace percentage, or 0 if there is no
	 *         parent.
	 */
	private float getParentRealDisplaceY() {
		if (this.parent == null) {
			return 0.0f; // so calculations aren't affected
		}
		return this.parent.getActualDisplaceY();
	}

	/**
	 * Returns the parents actual height, which is determined by recursing up
	 * the tree of windows. If the parent is null (that is, this is a root
	 * window), returns 1.0f so as to not change calculations.
	 *
	 * @return The parents actual height percentage, or 1 if there is no parent.
	 */
	private float getParentRealHeight() {
		if (this.parent == null) {
			return 1.0f;// so calculations aren't affected
		}
		return this.parent.getActualHeight();
	}

	/**
	 * Returns the parents actual width, which is determined by recursing up the
	 * tree of windows. If the parent is null (that is, this is a root window),
	 * returns 1.0f so as to not change calculations.
	 *
	 * @return The parents actual width percentage, or 1 if there is no parent.
	 */
	private float getParentRealWidth() {
		if (this.parent == null) {
			return 1.0f;// so calculations aren't affected
		}
		return this.parent.getActualWidth();
	}

	/**
	 * Returns an actual reference to the current window style. If one does not
	 * exist, the default style is returned instead.
	 *
	 * @return The current window style object, or if null, the default style.
	 */
	protected WindowStyle getStyle() {
		if (this.style == null) {
			return Window.defaultStyle;
		}
		return this.style;
	}

	/**
	 * Returns a clone of the current style. If there is no current style (it is
	 * null), then returns null. Modifying the returned object will not change
	 * the actual style of windows as that must be done via the
	 * {@link #setStyle(WindowStyle)} method.
	 *
	 * @return A copy of the current style or null if there is none.
	 */
	public WindowStyle getStyleClone() {
		WindowStyle clone;
		if (this.style == null) {
			clone = null;
		}
		else {
			clone = new WindowStyle(this.style.getStyle(),
				this.style.getBorder(), this.style.getColorScheme());
		}
		return clone;
	}

	/**
	 * Returns true if this window has the given child as one of its children.
	 *
	 * @param child The child to look for.
	 * @return true if this window is the child's direct parent, false
	 *         otherwise.
	 */
	public boolean hasChild(final Window child) {
		return this.children.contains(child);
	}

	/**
	 * Returns true if this window should be drawn on the screen.
	 *
	 * @return true if this window is visible.
	 */
	public boolean isVisible() {
		if (this.parent != null && !this.parent.isVisible()) {
			return false;
		}
		return this.visible;
	}

	/**
	 * Removes the parent and if there is a parent, removes this from the
	 * parent. Sets the local dimensions to the actual dimensions so that it
	 * will render in the same place it was before.
	 */
	public void orphanSelf() {
		this.localDisplace.set(this.getActualDisplaceX(),
			this.getActualDisplaceY());
		this.scale.set(this.getActualWidth(), this.getActualHeight());
		Window win = this.parent;
		this.parent = null;// so this does not recurse
		if (win != null) {
			win.removeChild(this);
		}
		WindowManager.setHeight(this, WindowManager.BASE_HEIGHT);
		for (Window child : this.children) {
			child.updateHeights();
		}
	}

	/**
	 * Recalculates all real displacements and scale, first recursing to the top
	 * dirty window and then trickling down until it has recalculated this and
	 * everything above it.
	 */
	protected void recalculate() {
		if (!this.dirty) {
			return;
		}

		if (this.parent != null) {
			this.parent.recalculate();
		}

		this.recalculateLocalDisplaceNW();

		this.realDisplacement
			.setX(this.localDisplaceNW.getX() * this.getParentRealWidth()
				+ this.getParentRealDisplaceX());
		this.realDisplacement
			.setY(this.localDisplaceNW.getY() * this.getParentRealHeight()
				+ this.getParentRealDisplaceY());
		this.realScale.setY(this.scale.getY() * this.getParentRealHeight());
		this.realScale.setX(this.scale.getX() * this.getParentRealWidth());

		this.dirty = false;
	}

	/**
	 * Calculates what the displacement would be as if from the northwest
	 * corner. This is used for screen displacement.
	 */
	private void recalculateLocalDisplaceNW() {
		float xDispl;
		float yDispl;

		switch (this.align) {
			case CENTER:
				xDispl = (1 - this.scale.getX()) / 2;
				yDispl = (1 - this.scale.getY()) / 2;
				break;
			case EAST:
				xDispl = 1 - (this.localDisplace.getX() + this.scale.getX());
				yDispl = (1 - this.scale.getY()) / 2;
				break;
			case NORTH:
				xDispl = (1 - this.scale.getX()) / 2;
				yDispl = this.localDisplace.getY();
				break;
			case NORTH_EAST:
				xDispl = 1 - (this.localDisplace.getX() + this.scale.getX());
				yDispl = this.localDisplace.getY();
				break;
			case SOUTH:
				xDispl = (1 - this.scale.getX()) / 2;
				yDispl = 1 - (this.localDisplace.getY() + this.scale.getY());
				break;
			case SOUTH_EAST:
				xDispl = 1 - (this.localDisplace.getX() + this.scale.getX());
				yDispl = 1 - (this.localDisplace.getY() + this.scale.getY());
				break;
			case SOUTH_WEST:
				xDispl = this.localDisplace.getX();
				yDispl = 1 - (this.localDisplace.getY() + this.scale.getY());
				break;
			case WEST:
				xDispl = this.localDisplace.getX();
				yDispl = (1 - this.scale.getY()) / 2;
				break;
			case NORTH_WEST:
			default:
				// just default to upper left corner
				xDispl = this.localDisplace.getX();
				yDispl = this.localDisplace.getY();
				break;
		}

		this.localDisplaceNW.set(xDispl, yDispl);
	}

	/**
	 * Removes the given child from this window. If the child recognizes this as
	 * its parent, it will {@link #orphanSelf() orphan itself} so that it's
	 * location and size remain fine.
	 *
	 * @param item the item to remove
	 */
	public void removeChild(Window item) {
		if (item == null) {
			return;
		}
		if (this.children.remove(item) && item.parent != null) {
			item.orphanSelf();
		}
	}

	/**
	 * Sets the alignment of the window to the given one. This determines from
	 * where the window is displaced in the parent. Flags the window as dirty.
	 *
	 * @param newAlignment The new alignment to use.
	 */
	public void setAlignment(final Alignment newAlignment) {
		this.align = newAlignment;
		this.dirty();
	}

	/**
	 * Sets the local displacement of a window, as a percentage of x and y.
	 * Depending on the alignment of the window in its parent, one or both of
	 * these values may not be used as it may be centered and thus ignore the
	 * value. Flags the window as dirty.
	 *
	 * @param displace The displacement to use.
	 */
	public void setDisplacement(final Point displace) {
		this.localDisplace.set(displace);
		this.dirty();
	}

	/**
	 * Sets the local height percentage and flags the window as dirty.
	 *
	 * @param height The new height of the window as a decimal percentage of the
	 *            parent.
	 */
	public void setLocalHeight(final float height) {
		this.scale.setY(height);
		this.dirty();
	}

	/**
	 * Sets the local width percentage and flags the window as dirty.
	 *
	 * @param width The new width of the window as a decimal percentage of the
	 *            parent.
	 */
	public void setLocalWidth(final float width) {
		this.scale.setX(width);
		this.dirty();
	}

	/**
	 * Sets the new parent of this window and adds this to the new parent's
	 * children.
	 *
	 * @param newParent The new parent of this window.
	 */
	public void setParent(final Window newParent) {
		ArrayDeque<Window> childQueue = new ArrayDeque<>(newParent.children);
		while (!childQueue.isEmpty()) {
			Window child = childQueue.removeFirst();
			if (child == this) {
				// TODO localize
				Window.log.warn("Trying to add cyclic child, canceling");
				return;
			}
			childQueue.addAll(child.children);
		}
		this.parent = newParent;
		if (!this.parent.hasChild(this)) {
			this.parent.addChild(this);
		}
		this.dirty();
	}

	/**
	 * Updates the heights of the window to 1 above the parent, recursively to
	 * all children. Should only be called on child objects.
	 */
	protected void updateHeights() {
		if (this.parent == null) {
			// TODO localize
			Window.log.warn("Trying to update height of child of null",
				new NullPointerException());
			return;
		}
		int parentHeight = WindowManager.getHeight(this.parent);
		if (parentHeight == WindowManager.ERROR_HEIGHT) {
			// TODO localize
			Window.log.warn("Adding a child to a window without a height",
				new RuntimeException());
			parentHeight = WindowManager.BASE_HEIGHT;
		}
		WindowManager.setHeight(this, parentHeight + 1);

		for (Window child : this.children) {
			child.updateHeights();
		}
	}

}
