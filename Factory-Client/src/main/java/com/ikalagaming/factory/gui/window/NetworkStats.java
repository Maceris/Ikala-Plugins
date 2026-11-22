package com.ikalagaming.factory.gui.window;

import static com.ikalagaming.factory.gui.DefaultWindows.NETWORK;

import com.ikalagaming.factory.SinglePlayerGame;
import com.ikalagaming.factory.client.ClientSession;
import com.ikalagaming.factory.client.FrameClock;
import com.ikalagaming.factory.client.Prediction;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.component.GuiWindow;
import com.ikalagaming.graphics.gui.enums.Condition;
import com.ikalagaming.graphics.gui.flags.WindowFlags;
import com.ikalagaming.graphics.gui.util.Alignment;
import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;

/**
 * How the command frames are doing against the server: the round trip, how far ahead the client
 * runs, the server's input buffer, time dilation, and the misses, mispredicts and resyncs so far.
 */
public class NetworkStats extends GuiWindow {

    /** A window, hidden until turned on from the toolbar. */
    public NetworkStats() {
        super(NETWORK.getName(), WindowFlags.NONE);
        setScale(0.18f, 0.18f);
        setDisplacement(0.01f, 0.25f);
        setAlignment(Alignment.CENTER);
    }

    @Override
    public void draw(final int width, final int height) {
        setNextWindowPosInMainWindow(
                getActualDisplaceX() * width, getActualDisplaceY() * height, Condition.ONCE);
        IkGui.setNextWindowSize(
                getActualWidth() * width, getActualHeight() * height, Condition.ONCE);
        IkGui.begin(title, windowOpen, windowFlags);
        final ClientSession session = SinglePlayerGame.getSession();
        final Prediction prediction = session == null ? null : session.getPrediction();
        final FrameClock clock = session == null ? null : session.getFrameClock();
        if (prediction == null || clock == null) {
            IkGui.text("Not playing");
        } else {
            final double roundTrip = clock.roundTripMillis();
            IkGui.text(
                    roundTrip < 0
                            ? "Round trip: not measured"
                            : String.format("Round trip: %.1f ms", roundTrip));
            IkGui.text(
                    String.format(
                            "Frame %d, %d ahead of the server's last answer",
                            prediction.getFrame(),
                            prediction.getFrame() - prediction.getAcknowledged()));
            IkGui.text(
                    String.format(
                            "Server buffer: %d (smoothed %.1f), aiming for %d",
                            prediction.getServerBuffered(),
                            clock.getBuffered(),
                            clock.getTarget()));
            IkGui.text(String.format("Frame rate: %.1f%% of normal", clock.rate() * 100));
            IkGui.text(
                    String.format(
                            "Missed inputs: %d, mispredicts: %d, resyncs: %d",
                            prediction.getServerMisses(),
                            prediction.getMispredicts(),
                            clock.getResyncs()));
        }
        IkGui.end();
    }

    @Override
    public boolean handleGuiInput(@NonNull Scene scene) {
        return false;
    }
}
