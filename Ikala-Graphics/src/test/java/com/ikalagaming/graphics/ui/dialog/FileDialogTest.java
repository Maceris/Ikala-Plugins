package com.ikalagaming.graphics.ui.dialog;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/** Tests for which {@link FileDialog} is open, without showing one. */
class FileDialogTest {
    /** A folder to start in. */
    @TempDir Path folder;

    /**
     * Make a dialog that isn't shown.
     *
     * @param ownerClosed Whether its plugin has unloaded.
     * @return The dialog.
     */
    private FileDialog dialog(AtomicBoolean ownerClosed) {
        return new FileDialog(
                null, ownerClosed::get, FileDialog.Options.folder("Pick", folder), picked -> {});
    }

    /** A newer dialog replaces the open one, which is handed back to be canceled. */
    @Test
    void newerDialogReplacesOpenOne() {
        AtomicBoolean firstClosed = new AtomicBoolean();
        AtomicBoolean secondClosed = new AtomicBoolean();
        FileDialog first = dialog(firstClosed);
        FileDialog second = dialog(secondClosed);
        try {
            FileDialog.remember(first);
            assertTrue(FileDialog.isOpen());
            assertSame(first, FileDialog.current());
            assertSame(first, FileDialog.remember(second));
            assertSame(second, FileDialog.current());
        } finally {
            firstClosed.set(true);
            secondClosed.set(true);
            FileDialog.current();
        }
    }

    /** A dialog whose plugin unloaded is dropped, so its callback isn't kept. */
    @Test
    void unloadedOwnersDialogIsDropped() {
        AtomicBoolean ownerClosed = new AtomicBoolean();
        FileDialog dialog = dialog(ownerClosed);
        FileDialog.remember(dialog);
        assertTrue(FileDialog.isOpen());
        ownerClosed.set(true);
        assertFalse(FileDialog.isOpen());
        assertNull(FileDialog.current());
        // Nor is it handed back to be canceled, since its plugin can't be answered
        AtomicBoolean nextClosed = new AtomicBoolean();
        FileDialog.remember(dialog);
        try {
            assertNull(FileDialog.remember(dialog(nextClosed)));
        } finally {
            nextClosed.set(true);
            FileDialog.current();
        }
    }
}
