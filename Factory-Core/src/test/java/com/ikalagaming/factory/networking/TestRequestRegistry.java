package com.ikalagaming.factory.networking;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ikalagaming.factory.networking.base.RequestDirection;

import org.junit.jupiter.api.Test;

class TestRequestRegistry {

    @Test
    void testRegistry() {
        if (RequestRegistry.isSetUp()) {
            RequestRegistry.purge();
        }
        assertDoesNotThrow(RequestRegistry::registerDefaults);

        var type = RequestRegistry.getType(RequestDirection.CLIENT_BOUND, 0);
        assertEquals(0, RequestRegistry.getID(RequestDirection.CLIENT_BOUND, type));
    }
}
