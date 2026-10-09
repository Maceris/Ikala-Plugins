package com.ikalagaming.graphics.gui.callback;

import com.ikalagaming.graphics.gui.data.GuiInputTextCallbackData;

import lombok.NoArgsConstructor;

import java.util.function.Consumer;

@NoArgsConstructor
public abstract class GuiInputTextCallback implements Consumer<GuiInputTextCallbackData> {
    public final void accept() {
        this.accept(new GuiInputTextCallbackData());
    }
}
