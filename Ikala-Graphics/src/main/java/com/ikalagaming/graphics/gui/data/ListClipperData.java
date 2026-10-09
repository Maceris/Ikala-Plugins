package com.ikalagaming.graphics.gui.data;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;

/** Temporary list clipper data, shared and reused between clippers. */
public class ListClipperData {
    public ListClipper listClipper;
    public float lossynessOffset;
    public int stepNumber;
    public int itemsFrozen;
    public final List<ListClipperRange> ranges;

    public ListClipperData() {
        ranges = new ArrayList<>();
    }

    /**
     * Reset for use by a clipper.
     *
     * @param clipper The clipper using this data.
     */
    public void reset(@NonNull ListClipper clipper) {
        listClipper = clipper;
        stepNumber = 0;
        itemsFrozen = 0;
        ranges.clear();
    }
}
