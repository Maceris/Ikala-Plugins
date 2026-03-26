package com.ikalagaming.graphics.frontend.gui.event;

import com.ikalagaming.graphics.frontend.gui.enums.*;

import lombok.NonNull;

public record GuiInputEvent(
        @NonNull GuiInputEventType type, @NonNull GuiInputSource source, @NonNull EventData data) {
    public interface EventData {}

    public record MousePosition(float posX, float posY, @NonNull MouseSource source)
            implements EventData {}

    public record MouseWheel(float wheelX, float wheelY, @NonNull MouseSource source)
            implements EventData {}

    public record MouseButton(
            @NonNull com.ikalagaming.graphics.frontend.gui.enums.MouseButton button,
            boolean down,
            @NonNull MouseSource source)
            implements EventData {}

    public record KeyPress(@NonNull Key key, boolean down, float analogValue)
            implements EventData {}

    //TODO(ches) do we need this?
    public record Text(char character) implements EventData {}

    public record Focused(boolean focused) implements EventData {}

    public record Viewport(int id) implements EventData {}
}
