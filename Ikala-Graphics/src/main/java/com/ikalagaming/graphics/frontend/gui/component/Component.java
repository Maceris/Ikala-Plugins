package com.ikalagaming.graphics.frontend.gui.component;

import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.frontend.gui.util.Alignment;
import com.ikalagaming.graphics.frontend.gui.util.Rect;
import com.ikalagaming.graphics.scene.Scene;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;

/**
 * A component that we can draw on the screen, can handle input, and can be enabled/disabled. It is
 * expected that any component other than a {@link GuiWindow} is placed inside a window, with a
 * couple notable exceptions like members of a {@link
 * com.ikalagaming.graphics.frontend.gui.component.MainToolbar MainToolbar}.
 */
public class Component implements Drawable {

    /** Whether the component should show. */
    @Setter protected boolean visible;

    /**
     * How far from the alignment edge the component should be placed. The x and y values are floats
     * representing the percentage of the parent's respective width or height to be shifted by.
     * These should be between 0.0f and 1.0f, otherwise it will be outside the parent.
     */
    protected Vector2f localDisplace;

    /** The global screen displacement of the component. */
    private final Vector2f globalDisplacement;

    /**
     * The true screen width of the component. This is a percentage of the canvas this is drawn on.
     * Should be positive and &lt;= 1.0f, where 1 is the entire canvas. X represents width, and Y
     * represents height.
     */
    private final Vector2f globalScale;

    /** Where in the parent should this component snap to, or have displacement measured from. */
    protected Alignment align;

    /**
     * The percent of the parent component's width to take up. Should be positive and &lt;= 1.0f,
     * where 1 is the entire component. X represents width, and Y represents height.
     */
    protected Vector2f scale;

    /**
     * The parent component, or null if there is no parent component. A null parent means this
     * component is a root.
     */
    protected Component parent;

    /** True if the component has been changed and needs to be recalculated. */
    protected boolean dirty;

    /** A list of subcomponents this component contains */
    protected final List<Component> children;

    /** The height of the component, or z depth. Higher numbers are drawn on top. */
    @Getter private int height;

    /**
     * True if this component absorbs touches and stops them from reaching lower components, false
     * if input can reach lower components. Defaults to true.
     *
     * <p>This value only affects whether this component blocks inputs. This means components are
     * allowed to absorb inputs and do nothing, or not absorb inputs and perform an action while
     * allowing lower components to be interacted with by the same event.
     *
     * @return True if input events should stop when they hit this component, false if they can pass
     *     through and potentially affect lower components.
     */
    @Getter @Setter private boolean consumeInputs;

    /** Creates a default component and initializes internal variables. */
    public Component() {
        this(new Vector2f(0, 0), Alignment.NORTH_WEST, new Vector2f(1, 1));
    }

    /**
     * Constructs a new component with displacement, alignment, and size as given. These are points
     * with x and y values given as floats representing decimal percentage of the parent component's
     * size.
     *
     * @param displacement The percent of the parent to displace away from whatever edge it is
     *     aligned to.
     * @param alignment What part of the parent should the displacement be measured from.
     * @param scale The width and height of this component as a percent of the parents respective
     *     width and height.
     */
    public Component(
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
     * Adds a child to the component, sets the child to have this as a parent, and then dirties this
     * component (and thus children).
     *
     * @param item The child to add to the component.
     */
    public void addChild(@NonNull Component item) {
        children.add(item);
        if (item.parent != this) {
            item.setParent(this);
        }

        item.updateHeights();
        dirty();
    }

    /**
     * Returns true if this component contains the given point, given a certain screen width and
     * height.
     *
     * @param screenWidth The width of the screen/canvas the component is in.
     * @param screenHeight The height of the screen/canvas the component is in.
     * @param p The point to check for.
     * @return True if the component contains the point, otherwise false.
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
     * Returns true if this component contains the given point, given a certain screen width and
     * height.
     *
     * @param screenWidth The width of the screen/canvas the component is in.
     * @param screenHeight The height of the screen/canvas the component is in.
     * @param x The x location to check for.
     * @param y The y location to check for.
     * @return True if the component contains the point, otherwise false.
     */
    public boolean containsPoint(
            final int screenWidth, final int screenHeight, final float x, final float y) {
        final var left = getActualDisplaceX() * screenWidth;
        final var top = getActualDisplaceY() * screenHeight;
        final var right = (getActualDisplaceX() + getActualWidth()) * screenWidth;
        final var bottom = (getActualDisplaceY() + getActualHeight()) * screenHeight;

        return (x >= left) && (x <= right) && (y >= top) && (y <= bottom);
    }

    /**
     * Sets the dirty flag for this component and also calls dirty on all children. This signifies
     * the component needs to be recalculated.
     */
    public void dirty() {
        dirty = true;
        children.forEach(Component::dirty);
    }

    @Override
    public void draw(final int width, final int height) {
        if (!isVisible()) {
            return;
        }
        recalculate();
        children.forEach(Component::recalculate);
        children.forEach(child -> child.draw(width, height));
    }

    /**
     * Returns the actual displacement value of the component as it would be used on a canvas as a
     * decimal percentage of the whole, from the left side of the component.
     *
     * @return The width, which should be &gt;=0 and &lt;=1.0 but is not guaranteed to be
     */
    public float getActualDisplaceX() {
        recalculate();
        return globalDisplacement.x;
    }

    /**
     * Returns the actual displacement value of the component as it would be used on a canvas as a
     * decimal percentage of the whole, from the top of the component.
     *
     * @return The width, which should be &gt;=0 and &lt;=1.0 but is not guaranteed to be.
     */
    public float getActualDisplaceY() {
        recalculate();
        return globalDisplacement.y;
    }

    /**
     * Returns the actual scale value of the component as it would be used on a canvas.
     *
     * @return The width, which should be &gt;=0 and &lt;=1.0 but is not guaranteed to be.
     */
    public float getActualHeight() {
        recalculate();
        return globalScale.y;
    }

    /**
     * Returns the actual scale value of the component as it would be used on a canvas.
     *
     * @return The width, which should be &gt;=0 and &lt;=1.0 but is not guaranteed to be.
     */
    public float getActualWidth() {
        recalculate();
        return globalScale.x;
    }

    /**
     * Returns a rectangle representing the actual size and location of this component should it be
     * mapped onto the given canvas, using actual pixels.
     *
     * @param screen The screen area we are drawing on.
     * @return The rectangle representing where this would be located on the canvas.
     */
    public Rect getBoundingRect(@NonNull Rect screen) {
        recalculate();
        Rect bounds = new Rect();
        final float x = globalDisplacement.x;
        final float y = globalDisplacement.y;
        final float w = globalScale.x;
        final float h = globalScale.y;
        bounds.set(
                (x * screen.getWidth()),
                (y * screen.getHeight()),
                ((x + w) * screen.getWidth()),
                ((y + h) * screen.getHeight()));
        return bounds;
    }

    /**
     * Returns the number of children belonging to this component.
     *
     * @return The size of the children list.
     */
    public int getChildCount() {
        return children.size();
    }

    /**
     * Returns the float value representing the decimal percentage of the parent component's height
     * that this component takes up.
     *
     * @return The local height.
     */
    public float getLocalHeight() {
        return scale.y;
    }

    /**
     * Returns the float value representing the decimal percentage of the parent component's width
     * that this component takes up.
     *
     * @return The local width.
     */
    public float getLocalWidth() {
        return scale.x;
    }

    /**
     * Returns true if this component has the given child as one of its children
     *
     * @param child The child to look for.
     * @return True if this component is the child's direct parent, false otherwise.
     */
    public boolean hasChild(final Component child) {
        return children.contains(child);
    }

    @Override
    public boolean handleGuiInput(@NonNull Scene scene, @NonNull Window window) {
        for (Component child : children) {
            if (!child.isVisible()) {
                continue;
            }
            if (child.handleGuiInput(scene, window)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if this component should be drawn on the screen.
     *
     * @return True if this component is visible.
     */
    public boolean isVisible() {
        if (parent != null && (!parent.isVisible())) {
            return false;
        }
        return visible;
    }

    /**
     * Recalculates all real displacements and scale, first recursing to the top dirty component and
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
                    case NORTH_WEST, WEST, SOUTH_WEST -> localDisplace.x;
                    case NORTH, CENTER, SOUTH -> (1 - scale.x) / 2 + localDisplace.x;
                    case NORTH_EAST, EAST, SOUTH_EAST -> 1 - (localDisplace.x + scale.x);
                };
        final float yDisplacement =
                switch (align) {
                    case NORTH_WEST, NORTH, NORTH_EAST -> localDisplace.y;
                    case WEST, CENTER, EAST -> (1 - scale.y) / 2 + localDisplace.y;
                    case SOUTH_WEST, SOUTH, SOUTH_EAST -> 1 - (localDisplace.y + scale.y);
                };
        final float parentWidth = parent != null ? parent.getActualWidth() : 1.0f;
        final float parentHeight = parent != null ? parent.getActualHeight() : 1.0f;
        final float parentDisplaceX = parent != null ? parent.getActualDisplaceX() : 0.0f;
        final float parentDisplaceY = parent != null ? parent.getActualDisplaceY() : 0.0f;

        globalDisplacement.set(
                xDisplacement * parentWidth + parentDisplaceX,
                yDisplacement * parentHeight + parentDisplaceY);
        globalScale.set(scale.x * parentWidth, scale.y * parentHeight);

        dirty = false;
    }

    /**
     * Removes the given child from this component.
     *
     * @param item The item to remove.
     */
    public void removeChild(@NonNull Component item) {
        children.remove(item);
    }

    /**
     * Sets the alignment of the component to the given one. This determines from where the
     * component is displaced in the parent.
     *
     * @param newAlignment The new alignment to use.
     */
    public void setAlignment(final @NonNull Alignment newAlignment) {
        align = newAlignment;
        dirty();
    }

    /**
     * Sets the local displacement of a component, as a percentage of x and y. Depending on the
     * alignment of the component in its parent, one or both of these values may not be used as it
     * may be centered and thus ignore the value. Flags the component as dirty.
     *
     * @param displace The displacement to use.
     */
    public void setDisplacement(final @NonNull Vector2f displace) {
        setDisplacement(displace.x, displace.y);
    }

    /**
     * Sets the local displacement of a component, as a percentage of x and y. Depending on the
     * alignment of the component in its parent, one or both of these values may not be used as it
     * may be centered and thus ignore the value. Flags the component as dirty.
     *
     * @param x The x displacement to use as a percentage of the parent component's width.
     * @param y The y displacement to use as a percentage of the parent component's height.
     */
    public void setDisplacement(final float x, final float y) {
        localDisplace.set(x, y);
        dirty();
    }

    /**
     * Set the percent of the parent component's width to take up. Should be positive and &lt;=
     * 1.0f, where 1 is the entire component. X represents width, and Y represents height.
     *
     * @param scale The new scale to use.
     */
    public void setScale(final @NonNull Vector2f scale) {
        setScale(scale.x, scale.y);
    }

    /**
     * Set the percent of the parent component's width to take up. Should be positive and &lt;=
     * 1.0f, where 1 is the entire component.
     *
     * @param width The decimal percentage of the parent component's width to take up.
     * @param height The decimal percentage of the parent component's height to take up.
     */
    public void setScale(final float width, final float height) {
        scale.x = width;
        scale.y = height;
        dirty();
    }

    /**
     * Sets the local height percentage and flags the component as dirty.
     *
     * @param height The new height of the component as a decimal percentage of the parent.
     */
    public void setLocalHeight(final float height) {
        scale.y = height;
        dirty();
    }

    /**
     * Sets the local width percentage and flags the component as dirty.
     *
     * @param width The new width of the component as a decimal percentage of the parent.
     */
    public void setLocalWidth(final float width) {
        scale.x = width;
        dirty();
    }

    /**
     * Sets the new parent of this component and adds this to the new parent's children.
     *
     * @param newParent The new parent of this component.
     */
    public void setParent(final Component newParent) {
        parent = newParent;
        if (parent != null && !parent.hasChild(this)) {
            parent.addChild(this);
        }
        dirty();
    }

    /** Updates the heights of the component to 1 above the parent, recursively to all children. */
    protected void updateHeights() {
        if (parent == null) {
            this.height = 0;
        } else {
            this.height = parent.getHeight() + 1;
        }
        children.forEach(Component::updateHeights);
    }

    @Override
    public void updateValues(@NonNull Scene scene, @NonNull Window window) {
        for (Component child : children) {
            if (child.isVisible()) {
                child.updateValues(scene, window);
            }
        }
    }
}
