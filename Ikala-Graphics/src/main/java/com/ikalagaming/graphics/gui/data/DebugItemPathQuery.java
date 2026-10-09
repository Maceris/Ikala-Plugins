package com.ikalagaming.graphics.gui.data;

import java.util.ArrayList;
import java.util.List;

/**
 * A query for how the ID of an item was built, one ID stack level at a time. Used by the ID stack
 * tool. We can only answer one level each frame, by hooking the ID functions when they produce the
 * ID we are looking for, so this keeps the progress between frames.
 */
public class DebugItemPathQuery {
    /** What we found out about one level of the ID stack. */
    public static class StackLevelInfo {
        /** The ID at this level of the stack. */
        public int id;

        /** The number of frames we have been looking for this level, more than 0 while active. */
        public int queryFrameCount;

        /** If we found out what produced this ID. */
        public boolean querySuccess;

        /**
         * A description of what produced the ID, like the string or integer that was hashed. Null
         * if unknown.
         */
        public String description;

        /** If the ID was produced by hashing a string, which we display in quotes. */
        public boolean fromString;

        public StackLevelInfo(int id) {
            this.id = id;
            queryFrameCount = 0;
            querySuccess = false;
            description = null;
            fromString = false;
        }
    }

    /** The ID we are querying the stack of. */
    public int mainID;

    /**
     * Whether the query is active, used to disambiguate the case where the ID is 0 and code pushes
     * an override ID of 0.
     */
    public boolean active;

    /** All sub-queries are finished, though some may have failed. */
    public boolean complete;

    /** -1 to query the stack and set up the results, 0 or more when filling each stack level. */
    public int step;

    /** The info for each level of the stack, starting with the window. */
    public final List<StackLevelInfo> results;

    public DebugItemPathQuery() {
        mainID = 0;
        active = false;
        complete = false;
        step = 0;
        results = new ArrayList<>();
    }
}
