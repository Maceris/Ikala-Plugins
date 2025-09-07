package com.ikalagaming.graphics.frontend.gui.data;

import java.util.ArrayList;
import java.util.List;

public class MultiSelectIO {
    public List<SelectionRequest> requests;
    public Object rangeSourceItem;
    public Object navIDItem;
    public boolean navIDSelected;
    public boolean rangeSourceReset;
    public int itemCount;

    public MultiSelectIO() {
        requests = new ArrayList<>();
        rangeSourceItem = null;
        navIDItem = null;
        navIDSelected = false;
        rangeSourceReset = false;
        itemCount = 0;
    }
}
