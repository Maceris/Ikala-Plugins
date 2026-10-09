package com.ikalagaming.graphics.ui.spec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class ObservableTest {

    @Test
    void listenersHearChangesOnly() {
        Observable<String> value = Observable.of("a");
        List<String> heard = new ArrayList<>();
        Subscription subscription = value.subscribe(heard::add);

        value.set("a");
        value.set("b");
        value.set(null);
        subscription.close();
        value.set("c");

        assertEquals(java.util.Arrays.asList("b", null), heard);
        assertEquals("c", value.get());
        assertEquals(0, value.listenerCount());
    }

    @Test
    void listsAreSnapshots() {
        ObservableList<String> list = new ObservableList<>();
        List<List<String>> heard = new ArrayList<>();
        list.subscribe(heard::add);

        list.add("a");
        list.add("b");
        list.remove("a");
        list.remove("missing");
        list.clear();

        assertEquals(List.of(List.of("a"), List.of("a", "b"), List.of("b"), List.of()), heard);
        assertThrows(UnsupportedOperationException.class, () -> list.get().add("x"));
    }
}
