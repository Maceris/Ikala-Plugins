package com.ikalagaming.graphics.frontend.gui.data;

import com.ikalagaming.graphics.frontend.gui.enums.ColorButtonPosition;
import com.ikalagaming.graphics.frontend.gui.enums.WindowMenuButtonPosition;

import lombok.NonNull;
import org.joml.Vector2f;
import org.joml.Vector2i;

public class StyleVariables {
    /** Values between 0 and 1. */
    public float alpha;

    /** Values between 0 and 1. */
    public final Vector2f buttonTextAlign;

    /** Values between 0 and 20. */
    public final Vector2i cellPadding;

    /** Values between 0 and 1. */
    public int childBorderSize;

    /** Values between 0 and 12. */
    public int childRounding;

    public @NonNull ColorButtonPosition colorButtonPosition;

    /** Additional multiplier for alpha of disabled items. Values between 0 and 1. */
    public float disabledAlpha;

    /** Values between 0 and 1. */
    public int frameBorderSize;

    /** Values between 0 and 20. */
    public final Vector2i framePadding;

    /** Values between 0 and 12. */
    public int frameRounding;

    /** Values between 1 and 20. */
    public int grabMinSize;

    /** Values between 0 and 12. */
    public int grabRounding;

    /** Values between 0 and 30. */
    public int indentSpacing;

    /** Values between 0 and 20. */
    public final Vector2i itemInnerSpacing;

    /** Values between 0 and 20. */
    public final Vector2i itemSpacing;

    /** Dead zone around logarithmic sliders that cross zero. Values between 0 and 12. */
    public int logSliderDeadzone;

    /** Values between 0 and 1. */
    public int popupBorderSize;

    /** Values between 0 and 12. */
    public int popupRounding;

    /** Values between 0 and 12. */
    public int scrollbarRounding;

    /** Values between 1 and 20. */
    public int scrollbarSize;

    /** Values between 0 and 1. */
    public final Vector2f selectableTextAlign;

    /** Values between 0 and 10. */
    public int separatorTextBorderSize;

    /** Values between 0 and 1. */
    public final Vector2f separatorTextAlign;

    /** Values between 0 and 40. */
    public final Vector2i separatorTextPadding;

    /** Values between 0 and 2. */
    public int tabBarBorderSize;

    /** Values between 0 and 1. */
    public int tabBorderSize;

    /** Angle in degrees. Values between -50 and 50. */
    public int tableAngledHeadersAngle;

    /** Values between 0 and 1. */
    public Vector2f tableAngledHeadersTextAlign;

    /** Values between 0 and 12. */
    public int tabRounding;

    /** Values between 0 and 10. */
    public final Vector2i touchExtraPadding;

    /** Values between 0 and 1. */
    public int windowBorderSize;

    public @NonNull WindowMenuButtonPosition windowMenuButtonPosition;
    public final Vector2i windowMinSize;

    /** Values between 0 and 20. */
    public final Vector2i windowPadding;

    /** Values between 0 and 12. */
    public int windowRounding;

    /** Values between 0 and 1. */
    public final Vector2f windowTitleAlign;

    public StyleVariables() {
        alpha = 1.0f;
        buttonTextAlign = new Vector2f(0.5f, 0.5f);
        cellPadding = new Vector2i(4, 2);
        childBorderSize = 1;
        childRounding = 0;
        colorButtonPosition = ColorButtonPosition.RIGHT;
        disabledAlpha = 0.6f;
        frameBorderSize = 0;
        framePadding = new Vector2i(4, 3);
        frameRounding = 0;
        grabMinSize = 12;
        grabRounding = 0;
        indentSpacing = 21;
        itemInnerSpacing = new Vector2i(4, 4);
        itemSpacing = new Vector2i(8, 4);
        logSliderDeadzone = 4;
        popupBorderSize = 1;
        popupRounding = 0;
        scrollbarRounding = 9;
        scrollbarSize = 14;
        selectableTextAlign = new Vector2f(0.0f, 0.0f);
        separatorTextBorderSize = 3;
        separatorTextAlign = new Vector2f(0.0f, 0.5f);
        separatorTextPadding = new Vector2i(20, 3);
        tabBarBorderSize = 1;
        tabBorderSize = 0;
        tableAngledHeadersAngle = 35;
        tableAngledHeadersTextAlign = new Vector2f(0.5f, 0.0f);
        tabRounding = 4;
        touchExtraPadding = new Vector2i(0, 0);
        windowBorderSize = 1;
        windowMenuButtonPosition = WindowMenuButtonPosition.LEFT;
        windowMinSize = new Vector2i(32, 32);
        windowPadding = new Vector2i(8, 8);
        windowRounding = 0;
        windowTitleAlign = new Vector2f(0.0f, 0.5f);
    }
}
