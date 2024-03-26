package com.ikalagaming.factory.registry;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class TestRegistries {

    @Test
    void testRegistryCreation() {
        var registries = new Registries();

        assertNotNull(registries.getTagRegistry());
        assertNotNull(registries.getMaterialRegistry());
        assertNotNull(registries.getItemRegistry());
        assertNotNull(registries.getBlockRegistry());
    }
}
