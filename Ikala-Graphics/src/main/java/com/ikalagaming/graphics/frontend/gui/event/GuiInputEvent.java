package com.ikalagaming.graphics.frontend.gui.event;

import com.ikalagaming.graphics.frontend.gui.enums.GuiInputEventType;
import com.ikalagaming.graphics.frontend.gui.enums.GuiInputSource;
import com.ikalagaming.graphics.frontend.gui.enums.Key;
import com.ikalagaming.graphics.frontend.gui.enums.MouseSource;

import lombok.NonNull;

public record GuiInputEvent(
        @NonNull GuiInputEventType type,
        @NonNull GuiInputSource source,
        int eventID,
        @NonNull EventData data) {
    public interface EventData {}

    public record MousePos(float posX, float posY, MouseSource source) implements EventData {}

    public record MouseWheel(float wheelX, float wheelY, MouseSource source) implements EventData {}

    public record MouseButton(int button, boolean down, MouseSource source) implements EventData {}

    public record KeyPress(Key key, boolean down, float analogValue) implements EventData {}

    public record Text(char character) implements EventData {}

    public record Focused(boolean focused) implements EventData {}
}
