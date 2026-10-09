package com.ikalagaming.graphics.vulkan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.Format;
import com.ikalagaming.graphics.TextureHandle;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

class TextureRegistryTest {

    private static final String OWNER_A = "Plugin-A";
    private static final String OWNER_B = "Plugin-B";

    private TextureRegistry registry;

    /**
     * Make a texture that sits in a bindless slot, without touching Vulkan.
     *
     * @param slot The bindless slot.
     * @return The texture.
     */
    private static TextureInfoVulkan inSlot(int slot) {
        TextureInfoVulkan info = new TextureInfoVulkan();
        info.bindlessIndex = slot;
        return info;
    }

    private TextureHandle add(String owner, TextureInfoVulkan info) {
        return registry.add(owner, info, 4, 2, Format.R8G8B8A8_UNORM);
    }

    @BeforeEach
    void setUp() {
        registry = new TextureRegistry(8);
    }

    @Test
    void addGivesHandleWithFacts() {
        TextureInfoVulkan info = inSlot(3);
        TextureHandle handle = add(OWNER_A, info);

        assertEquals(new TextureHandle(3, 0, 4, 2, Format.R8G8B8A8_UNORM), handle);
        assertTrue(registry.isValid(handle));
        assertSame(info, registry.resolve(handle));
        assertEquals(3, registry.slotOrDefault(handle));
        assertEquals(OWNER_A, registry.ownerOf(handle));
    }

    @Test
    void removedHandleIsStale() {
        TextureInfoVulkan info = inSlot(3);
        TextureHandle handle = add(OWNER_A, info);

        assertSame(info, registry.remove(handle));

        assertFalse(registry.isValid(handle));
        assertNull(registry.resolve(handle));
        assertNull(registry.ownerOf(handle));
        assertEquals(
                ShaderBindings.BindlessTextures.DEFAULT_TEXTURE_INDEX,
                registry.slotOrDefault(handle));
        assertNull(registry.remove(handle), "Removing twice does nothing");
    }

    @Test
    void reusedSlotDoesNotResolveStaleHandle() {
        TextureHandle oldHandle = add(OWNER_A, inSlot(3));
        registry.remove(oldHandle);

        TextureInfoVulkan newInfo = inSlot(3);
        TextureHandle newHandle = add(OWNER_B, newInfo);

        assertEquals(oldHandle.slot(), newHandle.slot());
        assertNotEquals(oldHandle.generation(), newHandle.generation());
        assertFalse(registry.isValid(oldHandle));
        assertNull(registry.resolve(oldHandle));
        assertNull(registry.remove(oldHandle), "A stale handle can't remove the new texture");
        assertSame(newInfo, registry.resolve(newHandle));
    }

    @Test
    void nullAndOutOfRangeHandlesAreInvalid() {
        assertFalse(registry.isValid(null));
        assertNull(registry.resolve(null));
        assertEquals(
                ShaderBindings.BindlessTextures.DEFAULT_TEXTURE_INDEX,
                registry.slotOrDefault(null));

        TextureHandle outOfRange = new TextureHandle(99, 0, 1, 1, Format.R8G8B8A8_UNORM);
        assertFalse(registry.isValid(outOfRange));
        assertFalse(registry.isValid(new TextureHandle(-1, 0, 1, 1, Format.R8G8B8A8_UNORM)));
    }

    @Test
    void forgedHandleWithWrongFactsIsInvalid() {
        TextureHandle handle = add(OWNER_A, inSlot(3));
        TextureHandle forged = new TextureHandle(3, 0, 100, 100, Format.R8G8B8A8_UNORM);

        assertTrue(registry.isValid(handle));
        assertFalse(registry.isValid(forged));
    }

    @Test
    void removeAllOwnedByOnlyTouchesThatOwner() {
        TextureInfoVulkan a1 = inSlot(1);
        TextureInfoVulkan a2 = inSlot(4);
        TextureHandle handleA1 = add(OWNER_A, a1);
        TextureHandle handleA2 = add(OWNER_A, a2);
        TextureHandle handleB = add(OWNER_B, inSlot(2));

        assertEquals(2, registry.countOwnedBy(OWNER_A));
        List<TextureInfoVulkan> removed = registry.removeAllOwnedBy(OWNER_A);

        assertEquals(List.of(a1, a2), removed);
        assertFalse(registry.isValid(handleA1));
        assertFalse(registry.isValid(handleA2));
        assertTrue(registry.isValid(handleB));
        assertEquals(0, registry.countOwnedBy(OWNER_A));
        assertEquals(1, registry.countOwnedBy(OWNER_B));
    }

    @Test
    void removeAllEmptiesTheRegistry() {
        TextureHandle handleA = add(OWNER_A, inSlot(1));
        TextureHandle handleB = add(OWNER_B, inSlot(2));

        assertEquals(2, registry.removeAll().size());

        assertFalse(registry.isValid(handleA));
        assertFalse(registry.isValid(handleB));
        assertTrue(registry.removeAll().isEmpty());
    }

    @Test
    void addRejectsBadSlots() {
        add(OWNER_A, inSlot(3));

        assertThrows(IllegalArgumentException.class, () -> add(OWNER_B, inSlot(3)));
        assertThrows(IllegalArgumentException.class, () -> add(OWNER_A, inSlot(8)));
        assertThrows(
                IllegalArgumentException.class,
                () -> add(OWNER_A, inSlot(TextureInfoVulkan.NO_BINDLESS_INDEX)));
    }
}
