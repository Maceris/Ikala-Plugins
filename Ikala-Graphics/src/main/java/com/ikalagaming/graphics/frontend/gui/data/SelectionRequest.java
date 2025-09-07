package com.ikalagaming.graphics.frontend.gui.data;

import com.ikalagaming.graphics.frontend.gui.enums.SelectionRequestType;

public class SelectionRequest {
    public SelectionRequestType type;
    public boolean selected;
    public IkByte rangeDirection;
    public Object rangeFirstItem;
    public Object rangeLastItem;
}
