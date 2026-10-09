package com.ikalagaming.graphics.gui.event;

import com.ikalagaming.graphics.gui.enums.*;

import lombok.NonNull;

public record GuiInputEvent(
        @NonNull GuiInputEventType type, @NonNull GuiInputSource source, @NonNull EventData data) {
    public interface EventData {}

    public record MousePosition(float posX, float posY, @NonNull MouseSource source)
            implements EventData {}

    public record MouseWheel(float wheelX, float wheelY, @NonNull MouseSource source)
            implements EventData {}

    public record MouseButton(
            @NonNull com.ikalagaming.graphics.gui.enums.MouseButton button,
            boolean down,
            @NonNull MouseSource source)
            implements EventData {}

    public record KeyPress(@NonNull Key key, boolean down, float analogValue)
            implements EventData {}

    /**
     * A typed character, like Dear ImGui's text input event. Characters go through the event queue
     * so they keep their order relative to key and mouse events when trickling. This holds a UTF-16
     * char, where upstream holds a whole code point, so a code point outside the basic plane is
     * queued as two events, one per surrogate.
     *
     * @param character The UTF-16 character.
     */
    public record Text(char character) implements EventData {}

    public record Focused(boolean focused) implements EventData {}

    public record Viewport(int id) implements EventData {}
}
