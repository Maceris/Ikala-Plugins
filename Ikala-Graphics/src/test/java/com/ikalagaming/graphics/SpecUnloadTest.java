package com.ikalagaming.graphics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.data.Context;
import com.ikalagaming.graphics.ui.UiManager;
import com.ikalagaming.graphics.ui.spec.SpecBindings;
import com.ikalagaming.graphics.ui.spec.SpecInstance;
import com.ikalagaming.graphics.ui.spec.SpecLoader;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Checks that nothing a plugin gave the UI keeps its classes alive after it unloads: its node
 * types, observables, list items, handlers, and other plugins' specs built from its node types.
 */
class SpecUnloadTest {

    /** The package of the stand-in plugin's classes. */
    private static final String PLUGIN_PACKAGE = "com.ikalagaming.graphics.testplugin.";

    /**
     * Loads the stand-in plugin's classes itself, like a plugin class loader, and the rest from its
     * parent.
     */
    private static final class PluginLoader extends ClassLoader {
        private final ReentrantLock lock = new ReentrantLock();

        PluginLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.startsWith(PLUGIN_PACKAGE)) {
                return super.loadClass(name, resolve);
            }
            lock.lock();
            try {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    String resource = name.replace('.', '/') + ".class";
                    try (InputStream stream = getParent().getResourceAsStream(resource)) {
                        if (stream == null) {
                            throw new ClassNotFoundException(name);
                        }
                        byte[] bytes = stream.readAllBytes();
                        loaded = defineClass(name, bytes, 0, bytes.length);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
                return loaded;
            } finally {
                lock.unlock();
            }
        }
    }

    private UiManager manager;

    @BeforeEach
    void setUp() {
        Context context = IkGui.createContext();
        context.io.iniFilename = null;
        context.io.displaySize.set(1280, 720);
        manager = new UiManager();
    }

    @AfterEach
    void tearDown() {
        IkGui.destroyContext();
    }

    private void frames(int count) {
        for (int i = 0; i < count; ++i) {
            IkGui.newFrame();
            manager.draw();
            IkGui.render();
            manager.dispatchEvents();
        }
    }

    private static int call(Class<?> side, String method) throws ReflectiveOperationException {
        Object result = side.getMethod(method).invoke(null);
        return result instanceof Integer count ? count : 0;
    }

    /**
     * Load and install the stand-in plugin, unload it, and check its observables lost their
     * listeners, all in a method of its own so no local keeps its classes alive afterwards.
     *
     * @param plugin The plugin's context.
     * @param other Another plugin's context, which opens a spec using the plugin's node type.
     * @return A weak reference to the plugin's class loader.
     */
    private WeakReference<ClassLoader> installUseAndUnload(
            GraphicsContext plugin, GraphicsContext other) throws ReflectiveOperationException {
        PluginLoader loader = new PluginLoader(getClass().getClassLoader());
        Class<?> side = loader.loadClass(PLUGIN_PACKAGE + "PluginSide");
        assertNotEquals(
                getClass().getClassLoader(), side.getClassLoader(), "Loaded by the plugin loader");
        side.getMethod("install", UiManager.class, GraphicsContext.class)
                .invoke(null, manager, plugin);

        String yaml =
                "surface: { id: other-ui }\ncontent: { type: testplugin.slot, id: borrowed }\n";
        SpecInstance usesType =
                SpecInstance.open(
                        manager,
                        other,
                        SpecLoader.load(
                                new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)),
                                "other"),
                        new SpecBindings());
        frames(3);
        assertEquals(2, manager.specCount());
        assertTrue(call(side, "listeners") > 0);

        manager.removeAllOwnedBy(plugin);
        frames(3);

        assertTrue(usesType.isClosed(), "Another plugin's spec using the type was closed");
        assertEquals(0, manager.specCount());
        assertEquals(0, manager.surfaceCount());
        assertEquals(0, call(side, "listeners"), "Graphics stopped listening");
        side.getMethod("poke").invoke(null);
        frames(2);
        assertEquals(0, manager.surfaceCount(), "Changes after unloading do nothing");
        return new WeakReference<>(loader);
    }

    @Test
    void unloadedPluginClassesCanBeCollected() throws Exception {
        GraphicsContext plugin = new GraphicsContext("Plugin", null);
        GraphicsContext other = new GraphicsContext("Other", null);

        WeakReference<ClassLoader> loader = installUseAndUnload(plugin, other);
        // Graphics stays running, it just must not hold anything of the plugin's
        assertNotNull(manager);

        for (int i = 0; i < 50 && loader.get() != null; ++i) {
            System.gc();
            Thread.sleep(20);
        }
        if (loader.get() != null) {
            // The JDK links a record's equals through method handles it caches softly, so a
            // plugin that compared its records stays softly reachable until memory runs low.
            // Soft references don't keep anything alive past that, so apply the pressure.
            clearSoftReferences();
            System.gc();
        }
        assertNull(loader.get(), "Something in graphics still holds the unloaded plugin's classes");
    }

    /** Fill the heap until soft references are cleared, which the JVM does before running out. */
    private static void clearSoftReferences() {
        try {
            List<long[]> filler = new ArrayList<>();
            while (true) {
                filler.add(new long[16 << 20]);
            }
        } catch (OutOfMemoryError e) {
            // Expected: the filler is dropped here, and soft references were cleared to make room
        }
    }
}
