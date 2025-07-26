package com.ikalagaming.graphics.frontend.gui.callback;

import com.ikalagaming.graphics.frontend.gui.data.GuiInputTextCallbackData;

import lombok.NoArgsConstructor;

import java.util.function.Consumer;

@NoArgsConstructor
public abstract class GuiInputTextCallback implements Consumer<GuiInputTextCallbackData> {
    public final void accept() {
        this.accept(new GuiInputTextCallbackData());
    }
}
