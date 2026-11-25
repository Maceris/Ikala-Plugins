package com.ikalagaming.factory.networking.request.clientbound;

import com.ikalagaming.factory.networking.base.Request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Blocks that changed in chunks the client has, all in one command frame. A change stamped with
 * frame F happened after the players' movement in F, so movement sees it from frame F + 1; the
 * client keeps it to replay frames before that correctly. See the Command Frames design doc,
 * "Blocks in Frames".
 *
 * <p>Fields have setters and there is a no-argument constructor, so remote connections can
 * serialize it. A local connection passes the object itself, so nothing may change it after it is
 * sent.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BlockChanges implements Request {

    /** The command frame the changes happened in. */
    private long frame;

    /** Each changed block's x, y and z, three numbers a block. */
    private List<Integer> positions = new ArrayList<>();

    /** Each changed block's new block, by name, in the same order. */
    private List<String> blocks = new ArrayList<>();

    /**
     * Changes in a frame, with none yet.
     *
     * @param frame The command frame.
     */
    public BlockChanges(long frame) {
        this.frame = frame;
    }

    /**
     * Add a change.
     *
     * @param x The block's x.
     * @param y The block's y.
     * @param z The block's z.
     * @param block The new block's name.
     */
    public void add(int x, int y, int z, @NonNull String block) {
        positions.add(x);
        positions.add(y);
        positions.add(z);
        blocks.add(block);
    }

    /**
     * How many blocks changed.
     *
     * @return The count.
     */
    public int size() {
        return blocks.size();
    }

    @Override
    public void handleUsing(@NonNull ClientBoundRequestHandler handler) {
        handler.handle(this);
    }
}
