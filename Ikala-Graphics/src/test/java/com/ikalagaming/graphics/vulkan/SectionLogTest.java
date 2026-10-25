package com.ikalagaming.graphics.vulkan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ikalagaming.graphics.lod.LodStore;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/** How the changes to a set of sections are kept for readers to catch up with. */
class SectionLogTest {

    /** A reader gets the changes since it last looked, in order, and where it is now. */
    @Test
    void testChangesSinceInOrder() {
        final SectionLog log = new SectionLog();
        final LodStore.SectionPos a = new LodStore.SectionPos(1, 2, 3);
        final LodStore.SectionPos b = new LodStore.SectionPos(-4, 0, 9);
        log.record(a, true);
        final long seen = log.count();
        log.record(b, true);
        log.record(a, false);
        final List<String> changes = new ArrayList<>();
        final long now = log.since(seen, (position, in) -> changes.add(position + " " + in));
        assertEquals(List.of(b + " true", a + " false"), changes);
        assertEquals(log.count(), now);
        // Nothing since now
        assertEquals(now, log.since(now, (position, in) -> changes.add("more")));
        assertEquals(2, changes.size());
    }

    /** A reader too far behind, or one that never looked, is told to look at the set whole. */
    @Test
    void testTooFarBehind() {
        final SectionLog log = new SectionLog();
        final LodStore.SectionPos a = new LodStore.SectionPos(0, 0, 0);
        log.record(a, true);
        final long seen = log.count();
        for (int i = 0; i < SectionLog.CAPACITY; ++i) {
            log.record(a, i % 2 == 0);
        }
        assertEquals(log.count(), log.since(seen, (position, in) -> {}));
        log.record(a, true);
        assertEquals(-1, log.since(seen, (position, in) -> {}));
        assertEquals(-1, log.since(-1, (position, in) -> {}));
        // Emptied: every reader looks again
        final long before = log.count();
        log.reset();
        assertEquals(-1, log.since(before, (position, in) -> {}));
    }
}
