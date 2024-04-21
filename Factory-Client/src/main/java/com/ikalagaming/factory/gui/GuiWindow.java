package com.ikalagaming.factory.gui;

import com.ikalagaming.factory.gui.component.util.Alignment;
import com.ikalagaming.factory.gui.component.util.Rect;
import com.ikalagaming.factory.gui.component.util.Style;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.scene.Scene;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;

/** A component that we can draw on the screen, can handle input, and can be enabled/disabled. */
@Setter
@Getter
public class GuiWindow {

    /** Whether the window should show. */
    protected boolean visible;

    /** The style to use if a window does not have a style specifically set. */
    @Getter @Setter
    private static Style defaultStyle =
            new Style(Style.Shape.SQUARE, Style.ColorScheme.BLUE, true, true);

    /**
     * The style to use in drawing the window. If it is null, the default style is used. If not
     * null, it overrides the default.
     */
    @Setter private Style style;

    /**
     * How far from the alignment edge the window should be placed. The x and y values are floats
     * representing the percentage of the parent's respective width or height to be shifted by.
     * These should be between 0.0f and 1.0f, otherwise it will be outside the parent.
     */
    protected Vector2f localDisplace;

    /** The global screen displacement of the window. */
    private final Vector2f globalDisplacement;

    /**
     * The true screen width of the window. This is a percentage of the canvas this is drawn on.
     * Should be positive and <= 1.0f, where 1 is the entire canvas. X represents width, and Y
     * represents height.
     */
    private final Vector2f globalScale;

    /** Where in the parent should this window snap to, or have displacement measured from. */
    protected Alignment align;

    /**
     * The percent of the parent window's width to take up. Should be positive and <= 1.0f, where 1
     * is the entire window. X represents width, and Y represents height.
     */
    protected Vector2f scale;

    /**
     * The parent window, or null if there is no parent window. A null parent means this window is a
     * root.
     */
    protected GuiWindow parent;

    /** True if the window has been changed and needs to be recalculated. */
    protected boolean dirty;

    /** A list of sub-windows this window contains */
    protected final List<GuiWindow> children;

    /** The height of the window, or z depth. Higher numbers are drawn on top. */
    @Getter private int height;

    /**
     * True if this window absorbs touches and stops them from reaching lower windows, false if
     * input can reach lower windows. Defaults to true.
     *
     * <p>This value only affects whether this window blocks inputs. This means windows are allowed
     * to absorb inputs and do nothing, or not absorb inputs and perform an action while allowing
     * lower windows to be interacted with by the same event.
     *
     * @return True if input events should stop when they hit this window, false if they can pass
     *     through and potentially affect lower windows.
     */
    @Getter @Setter private boolean consumeInputs;

    /** Creates a default window and initializes internal variables. */
    public GuiWindow() {
        this(new Vector2f(0, 0), Alignment.NORTH_WEST, new Vector2f(0, 0));
    }

    /**
     * Constructs a new window with displacement, alignment, and size as given. These are points
     * with x and y values given as floats representing decimal percentage of the parent window's
     * size.
     *
     * @param displacement The percent of the parent to displace away from whatever edge it is
     *     aligned to.
     * @param alignment What part of the parent should the displacement be measured from.
     * @param scale The width and height of this window as a percent of the parents respective width
     *     and height.
     */
    public GuiWindow(
            final @NonNull Vector2f displacement,
            final @NonNull Alignment alignment,
            final @NonNull Vector2f scale) {
        visible = true;
        localDisplace = new Vector2f(displacement.x, displacement.y);
        globalDisplacement = new Vector2f();
        globalScale = new Vector2f();
        align = alignment;
        this.scale = new Vector2f(scale.x, scale.y);
        children = new ArrayList<>();
        consumeInputs = true;
        dirty();
    }

    /**
     * Adds a child to the window, sets the child to have this as a parent, and then dirties this
     * window (and thus children).
     *
     * @param item The child to add to the window.
     */
    public void addChild(@NonNull GuiWindow item) {
        children.add(item);
        if (item.parent != this) {
            item.setParent(this);
        }

        item.updateHeights();
        dirty();
    }

    /**
     * Returns true if this window contains the given point, given a certain screen width and
     * height.
     *
     * @param screenWidth The width of the screen/canvas the window is in.
     * @param screenHeight The height of the screen/canvas the window is in.
     * @param p The point to check for.
     * @return True if the window contains the point, otherwise false.
     */
    public boolean containsPoint(final int screenWidth, final int screenHeight, final Vector2f p) {
        var actualRect =
                new Rect(
                        getActualDisplaceX() * screenWidth,
                        getActualDisplaceY() * screenHeight,
                        (getActualDisplaceX() + getActualWidth()) * screenWidth,
                        (getActualDisplaceY() + getActualHeight()) * screenHeight);
        return actualRect.contains(p);
    }

    /**
     * Sets the dirty flag for this window and also calls dirty on all children. This signifies the
     * window needs to be recalculated.
     */
    public void dirty() {
        dirty = true;
        children.forEach(GuiWindow::dirty);
    }

    /** Draws the window on the given canvas. Also draws all children (on top) recursively. */
    public void draw() {
        if (dirty) {
            recalculate();
        }
        if (!isVisible()) {
            return;
        }
        // TODO(ches) draw
        children.forEach(GuiWindow::draw);
    }

    /**
     * Returns the actual displacement value of the window as it would be used on a canvas as a
     * decimal percentage of the whole, from the left side of the window.
     *
     * @return The width, which should be >=0 and <=1.0 but is not guaranteed to be
     */
    public float getActualDisplaceX() {
        if (dirty) {
            recalculate();
        }
        return globalDisplacement.x;
    }

    /**
     * Returns the actual displacement value of the window as it would be used on a canvas as a
     * decimal percentage of the whole, from the top of the window.
     *
     * @return The width, which should be >=0 and <=1.0 but is not guaranteed to be.
     */
    public float getActualDisplaceY() {
        if (dirty) {
            recalculate();
        }
        return globalDisplacement.y;
    }

    /**
     * Returns the actual scale value of the window as it would be used on a canvas.
     *
     * @return The width, which should be >=0 and <=1.0 but is not guaranteed to be.
     */
    public float getActualHeight() {
        if (dirty) {
            recalculate();
        }
        return globalScale.y;
    }

    /**
     * Returns the actual scale value of the window as it would be used on a canvas.
     *
     * @return The width, which should be >=0 and <=1.0 but is not guaranteed to be.
     */
    public float getActualWidth() {
        if (dirty) {
            recalculate();
        }
        return globalScale.x;
    }

    /**
     * Returns a rectangle representing the actual size and location of this window should it be
     * mapped onto the given canvas, using actual pixels.
     *
     * @param screen The screen area we are drawing on.
     * @return The rectangle representing where this would be located on the canvas.
     */
    public Rect getBoundingRect(@NonNull Rect screen) {
        if (dirty) {
            recalculate();
        }
        Rect bounds = new Rect();
        float x = globalDisplacement.x;
        float y = globalDisplacement.y;
        float w = globalScale.x;
        float h = globalScale.y;
        bounds.set(
                (x * screen.getWidth()),
                (y * screen.getHeight()),
                ((x + w) * screen.getWidth()),
                ((y + h) * screen.getHeight()));
        return bounds;
    }

    /**
     * Returns the number of children belonging to this window.
     *
     * @return The size of the children list.
     */
    public int getChildCount() {
        return children.size();
    }

    /**
     * Returns the float value representing the decimal percentage of the parent window's height
     * that this window takes up.
     *
     * @return The local height.
     */
    public float getLocalHeight() {
        return scale.y;
    }

    /**
     * Returns the float value representing the decimal percentage of the parent window's width that
     * this window takes up.
     *
     * @return The local width.
     */
    public float getLocalWidth() {
        return scale.x;
    }

    /**
     * Returns an actual reference to the current window style. If one does not exist, the default
     * style is returned instead. This should not be modified without proper thread safety in mind
     * as it could cause unpredictable results otherwise.
     *
     * @return The current window style object, or if null, the default style.
     */
    protected Style getStyle() {
        if (style == null) {
            return GuiWindow.getDefaultStyle();
        }
        return style;
    }

    /**
     * Returns true if this window has the given child as one of its children
     *
     * @param child The child to look for.
     * @return True if this window is the child's direct parent, false otherwise.
     */
    public boolean hasChild(final GuiWindow child) {
        return children.contains(child);
    }

    /**
     * Process GUI inputs, which might happen at a different frequency than rendering.
     *
     * @param scene The scene we are rendering.
     * @param window The window we are using.
     */
    boolean handleGuiInput(@NonNull Scene scene, @NonNull Window window) {
        for (GuiWindow child : children) {
            if (child.handleGuiInput(scene, window)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if this window should be drawn on the screen.
     *
     * @return True if this window is visible.
     */
    public boolean isVisible() {
        if (parent != null && (!parent.isVisible())) {
            return false;
        }
        return visible;
    }

    /**
     * Removes the parent and if there is a parent, removes this from the parent. Sets the local
     * dimensions to the actual dimensions so that it will render in the same place it was before.
     */
    public void orphanSelf() {
        if (dirty) {
            recalculate();
        }
        localDisplace.set(globalDisplacement);
        scale.set(globalScale);

        var win = parent;
        parent = null; // so this does not recurse until crashing
        if (win != null) {
            win.removeChild(this);
        }
        children.forEach(GuiWindow::updateHeights);
    }

    /**
     * Recalculates all real displacements and scale, first recursing to the top dirty window and
     * then trickling down until it has recalculated this and everything above it.
     */
    protected void recalculate() {
        if (!dirty) {
            return;
        }

        if (parent != null) {
            parent.recalculate();
        }

        final float xDisplacement =
                switch (align) {
                    case CENTER, NORTH, SOUTH -> (1 - scale.x) / 2;
                    case EAST, NORTH_EAST, SOUTH_EAST -> 1 - (localDisplace.x + scale.x);
                    case NORTH_WEST, SOUTH_WEST, WEST -> localDisplace.x;
                };
        final float yDisplacement =
                switch (align) {
                    case CENTER, WEST, EAST -> (1 - scale.y) / 2;
                    case NORTH, NORTH_WEST, NORTH_EAST -> localDisplace.y;
                    case SOUTH, SOUTH_WEST, SOUTH_EAST -> 1 - (localDisplace.y + scale.y);
                };
        final float parentWidth = parent != null ? parent.getActualWidth() : 1.0f;
        final float parentHeight = parent != null ? parent.getActualHeight() : 1.0f;
        final float parentDisplaceX = parent != null ? parent.getActualDisplaceX() : 0.0f;
        final float parentDisplaceY = parent != null ? parent.getActualDisplaceY() : 0.0f;

        globalDisplacement.set(
                xDisplacement * parentWidth + parentDisplaceX,
                yDisplacement * parentHeight + parentDisplaceY);
        globalScale.set(scale.y * parentHeight, scale.x * parentWidth);

        dirty = false;
    }

    /**
     * Removes the given child from this window. If the child recognizes this as its parent, it will
     * orphan itself. If this is overridden be sure {@link #orphanSelf()} and this do not
     * recursively call each other infinitely.
     *
     * @param item The item to remove.
     */
    public void removeChild(@NonNull GuiWindow item) {
        children.remove(item);
        if (item.parent != null) {
            item.orphanSelf();
        }
    }

    /**
     * Sets the alignment of the window to the given one. This determines from where the window is
     * displaced in the parent.
     *
     * @param newAlignment The new alignment to use.
     */
    public void setAlignment(final @NonNull Alignment newAlignment) {
        align = newAlignment;
        dirty();
    }

    /**
     * Sets the local displacement of a window, as a percentage of x and y. Depending on the
     * alignment of the window in its parent, one or both of these values may not be used as it may
     * be centered and thus ignore the value. Flags the window as dirty.
     *
     * @param displace The displacement to use.
     */
    public void setDisplacement(final @NonNull Vector2f displace) {
        localDisplace.set(displace.x, displace.y);
        dirty();
    }

    /**
     * Sets the local height percentage and flags the window as dirty.
     *
     * @param height The new height of the window as a decimal percentage of the parent.
     */
    public void setLocalHeight(final float height) {
        scale.y = height;
        dirty();
    }

    /**
     * Sets the local width percentage and flags the window as dirty.
     *
     * @param width The new width of the window as a decimal percentage of the parent.
     */
    public void setLocalWidth(final float width) {
        scale.x = width;
        dirty();
    }

    /**
     * Sets the new parent of this window and adds this to the new parent's children.
     *
     * @param newParent The new parent of this window.
     */
    public void setParent(final GuiWindow newParent) {
        parent = newParent;
        if (parent != null && !parent.hasChild(this)) {
            parent.addChild(this);
        }
        dirty();
    }

    /** Updates the heights of the window to 1 above the parent, recursively to all children. */
    protected void updateHeights() {
        if (parent == null) {
            this.height = 0;
        } else {
            this.height = parent.getHeight() + 1;
        }
        children.forEach(GuiWindow::updateHeights);
    }
}
