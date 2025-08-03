package com.ikalagaming.graphics.frontend.gui.data;

import org.joml.Vector2f;
import org.joml.Vector4f;

import java.util.ArrayList;

public class DrawListSharedData {
    private FontAtlas fontAtlas;
    private Font font;
    private float fontSize;
    private float fontScale;
    private int drawListFlags;
    private Vector4f clipRectFullscreen;
    private ArrayList<Vector2f> tempBuffer;
    private ArrayList<DrawList> drawLists;

    private Vector2f[] fastArcVertex;
    private float fastArcRadiusCutoff;
    private int[] circleSegmentCounts;
}
