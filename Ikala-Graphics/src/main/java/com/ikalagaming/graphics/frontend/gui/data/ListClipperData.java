package com.ikalagaming.graphics.frontend.gui.data;

import java.util.ArrayList;

public class ListClipperData {
    private ListClipper listClipper;
    private float lossynessOffset;
    private int stepNumber;
    private int itemsFrozen;
    private ArrayList<ListClipperRange> ranges;

    public void reset(ListClipper clipper) {
        listClipper = clipper;
        stepNumber = 0;
        itemsFrozen = 0;
        ranges.clear();
    }
}
