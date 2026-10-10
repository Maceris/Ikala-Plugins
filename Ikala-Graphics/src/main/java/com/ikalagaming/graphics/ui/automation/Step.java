package com.ikalagaming.graphics.ui.automation;

/** One thing a run does, like a click. Advanced once a frame on the render thread. */
interface Step {
    /**
     * Do this frame's part of the step, after the UI was laid out and submitted. Input it queues is
     * seen by IkGui next frame.
     *
     * @param run The run the step is part of.
     * @return True once the step is finished.
     * @throws UiAutomationException If the step failed.
     */
    boolean advance(Run run);

    /**
     * What the step does, for errors.
     *
     * @return A short description, like {@code click(about//close)}.
     */
    String describe();
}
