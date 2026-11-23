package com.ikalagaming.graphics.input;

import com.ikalagaming.graphics.gui.enums.Key;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Every action players can bind, their bindings, and which are held. Plugins register actions, the
 * player rebinds them on the Controls screen, and the bindings that differ from the defaults are
 * saved to a file. Each drawn frame, {@link #update} reads the keys once; code then asks {@link
 * #isDown} for held actions like walking and {@link #wasPressed} for presses like opening a menu.
 *
 * <p>When two held bindings share a key, the one with more modifiers wins that frame, so Ctrl+F3
 * doesn't also press F3. Bindings for actions no plugin has registered, as when a mod is unloaded,
 * are kept and saved.
 */
@Slf4j
public final class Controls {

    /** The settings file's map of action ID to binding. */
    public static final String BINDINGS_KEY = "bindings";

    /** An action and the plugin that registered it. */
    private record Registered(@NonNull InputAction action, @NonNull String owner) {}

    /** Guards everything here. */
    private final ReentrantLock lock = new ReentrantLock();

    /** Registered actions by ID, in registration order. */
    private final Map<String, Registered> actions = new LinkedHashMap<>();

    /** Bindings that differ from the defaults, by action ID, including unregistered actions. */
    private final Map<String, KeyBinding> overrides = new TreeMap<>();

    /** The actions held as of the last update. */
    private final Map<String, Boolean> down = new HashMap<>();

    /** The actions pressed or released by the last update. */
    private final Map<String, Boolean> pressed = new HashMap<>();

    private final Map<String, Boolean> released = new HashMap<>();

    /**
     * Whether the keyboard was taken at the last update. A key still held when it comes back, like
     * the Enter that sent a chat line, isn't a new press.
     */
    private boolean wasBlocked;

    /** Whether every action reads as up, as while the Controls screen waits for a key. */
    private boolean suspended;

    /** Where bindings are saved, or null to not save them. */
    private volatile Path file;

    /**
     * Read the saved bindings from a file, and save to it from now on. A missing file is no
     * bindings; a broken entry is skipped with a warning.
     *
     * @param settingsFile The settings file, or null to keep bindings only in memory.
     */
    public void open(Path settingsFile) {
        file = settingsFile;
        load();
    }

    /**
     * Register an action. Registering the same action again from the same owner replaces it.
     *
     * @param owner Who registered it, so it goes when they unload.
     * @param action The action.
     * @throws IllegalArgumentException If another owner registered the ID.
     */
    public void register(@NonNull String owner, @NonNull InputAction action) {
        lock.lock();
        try {
            final Registered existing = actions.get(action.id());
            if (existing != null && !existing.owner().equals(owner)) {
                throw new IllegalArgumentException(
                        action.id() + " is already registered by " + existing.owner());
            }
            actions.put(action.id(), new Registered(action, owner));
        } finally {
            lock.unlock();
        }
    }

    /**
     * Remove every action an owner registered. Their bindings are kept.
     *
     * @param owner The owner.
     * @return How many were removed.
     */
    public int removeAllOwnedBy(@NonNull String owner) {
        lock.lock();
        try {
            final List<String> gone =
                    actions.values().stream()
                            .filter(registered -> registered.owner().equals(owner))
                            .map(registered -> registered.action().id())
                            .toList();
            for (String id : gone) {
                actions.remove(id);
                down.remove(id);
                pressed.remove(id);
                released.remove(id);
            }
            return gone.size();
        } finally {
            lock.unlock();
        }
    }

    /**
     * The registered actions, by category and then in registration order, as the Controls screen
     * lists them.
     *
     * @return A copy of the list.
     */
    public List<InputAction> actions() {
        lock.lock();
        try {
            final Map<String, Integer> categoryOrder = new HashMap<>();
            final List<InputAction> list = new ArrayList<>();
            for (Registered registered : actions.values()) {
                categoryOrder.putIfAbsent(registered.action().category(), categoryOrder.size());
                list.add(registered.action());
            }
            list.sort(Comparator.comparingInt(action -> categoryOrder.get(action.category())));
            return list;
        } finally {
            lock.unlock();
        }
    }

    /**
     * A registered action.
     *
     * @param id Its ID.
     * @return The action, if registered.
     */
    public Optional<InputAction> action(@NonNull String id) {
        lock.lock();
        try {
            return Optional.ofNullable(actions.get(id)).map(Registered::action);
        } finally {
            lock.unlock();
        }
    }

    /**
     * What an action is bound to.
     *
     * @param id The action's ID.
     * @return The player's binding, else the action's default, else {@link KeyBinding#NONE}.
     */
    public KeyBinding binding(@NonNull String id) {
        lock.lock();
        try {
            final KeyBinding override = overrides.get(id);
            if (override != null) {
                return override;
            }
            final Registered registered = actions.get(id);
            return registered == null ? KeyBinding.NONE : registered.action().defaultBinding();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Bind an action to a key, and save. Binding it to its default forgets the override.
     *
     * @param id The action's ID.
     * @param binding The new binding, which may be {@link KeyBinding#NONE}.
     */
    public void rebind(@NonNull String id, @NonNull KeyBinding binding) {
        lock.lock();
        try {
            final Registered registered = actions.get(id);
            if (registered != null && registered.action().defaultBinding().equals(binding)) {
                overrides.remove(id);
            } else {
                overrides.put(id, binding);
            }
        } finally {
            lock.unlock();
        }
        save();
    }

    /**
     * Put an action back on its default, and save.
     *
     * @param id The action's ID.
     */
    public void reset(@NonNull String id) {
        lock.lock();
        try {
            overrides.remove(id);
        } finally {
            lock.unlock();
        }
        save();
    }

    /** Put every registered action back on its default, and save. */
    public void resetAll() {
        lock.lock();
        try {
            overrides.keySet().removeIf(actions::containsKey);
        } finally {
            lock.unlock();
        }
        save();
    }

    /**
     * Whether the player changed an action's binding.
     *
     * @param id The action's ID.
     * @return True if it isn't on its default.
     */
    public boolean isChanged(@NonNull String id) {
        lock.lock();
        try {
            return overrides.containsKey(id);
        } finally {
            lock.unlock();
        }
    }

    /**
     * The other registered actions bound to the same key and modifiers as one.
     *
     * @param id The action's ID.
     * @return Their IDs, empty if it is unbound.
     */
    public List<String> conflicts(@NonNull String id) {
        lock.lock();
        try {
            final KeyBinding binding = binding(id);
            if (binding.isNone()) {
                return List.of();
            }
            return actions.keySet().stream()
                    .filter(other -> !other.equals(id) && binding(other).equals(binding))
                    .toList();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Read the keys and work out which actions are held, pressed and released since the last
     * update. Once per drawn frame, on the main thread.
     *
     * @param keys The keys held now.
     */
    public void update(@NonNull KeyState keys) {
        lock.lock();
        try {
            final KeyState state = suspended ? KeyState.NONE : keys;
            // The most modifiers among held bindings on each key wins
            final Map<String, KeyBinding> held = new HashMap<>();
            final Map<Key, Integer> most = new EnumMap<>(Key.class);
            for (String id : actions.keySet()) {
                final KeyBinding binding = binding(id);
                if (binding.isHeld(state)) {
                    held.put(id, binding);
                    most.merge(binding.key(), binding.modifierCount(), Math::max);
                }
            }
            for (String id : actions.keySet()) {
                final KeyBinding binding = held.get(id);
                final boolean now =
                        binding != null && binding.modifierCount() == most.get(binding.key());
                final boolean before = Boolean.TRUE.equals(down.put(id, now));
                pressed.put(id, now && !before && !wasBlocked);
                released.put(id, !now && before);
            }
            wasBlocked = keys.keyboardBlocked();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Whether an action is held, as of the last update.
     *
     * @param id The action's ID.
     * @return True if held; false if not, or if it isn't registered.
     */
    public boolean isDown(@NonNull String id) {
        lock.lock();
        try {
            return Boolean.TRUE.equals(down.get(id));
        } finally {
            lock.unlock();
        }
    }

    /**
     * Whether the last update saw an action go down.
     *
     * @param id The action's ID.
     * @return True on the one update it was pressed.
     */
    public boolean wasPressed(@NonNull String id) {
        lock.lock();
        try {
            return Boolean.TRUE.equals(pressed.get(id));
        } finally {
            lock.unlock();
        }
    }

    /**
     * Whether the last update saw an action come up.
     *
     * @param id The action's ID.
     * @return True on the one update it was released.
     */
    public boolean wasReleased(@NonNull String id) {
        lock.lock();
        try {
            return Boolean.TRUE.equals(released.get(id));
        } finally {
            lock.unlock();
        }
    }

    /**
     * Make every action read as up until resumed, as while the player picks a key to bind, so the
     * key they press doesn't also do what it is bound to.
     *
     * @param suspend True to suspend, false to resume.
     */
    public void setSuspended(boolean suspend) {
        lock.lock();
        try {
            suspended = suspend;
        } finally {
            lock.unlock();
        }
    }

    /** Forget what is held, as when a menu takes the keys, so nothing stays held or fires later. */
    public void clearHeld() {
        update(KeyState.NONE);
    }

    /** Read the saved bindings from the file. */
    private void load() {
        final Path file = this.file;
        if (file == null || !Files.isRegularFile(file)) {
            return;
        }
        final Object root;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            root = new Yaml().load(reader);
        } catch (IOException | RuntimeException e) {
            log.warn("Could not read the key bindings in {}", file, e);
            return;
        }
        if (!(root instanceof Map<?, ?> map)
                || !(map.get(BINDINGS_KEY) instanceof Map<?, ?> saved)) {
            return;
        }
        lock.lock();
        try {
            overrides.clear();
            for (Map.Entry<?, ?> entry : saved.entrySet()) {
                try {
                    overrides.put(
                            String.valueOf(entry.getKey()),
                            KeyBinding.parse(String.valueOf(entry.getValue())));
                } catch (IllegalArgumentException e) {
                    log.warn("Skipping the key binding {} in {}: {}", entry, file, e.getMessage());
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /** Write the bindings that differ from the defaults, if there is a file. */
    public void save() {
        final Path file = this.file;
        if (file == null) {
            return;
        }
        final Map<String, String> saved = new LinkedHashMap<>();
        lock.lock();
        try {
            overrides.forEach((id, binding) -> saved.put(id, binding.toString()));
        } finally {
            lock.unlock();
        }
        if (saved.isEmpty() && !Files.exists(file)) {
            return;
        }
        final DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            final Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                writer.write("# Key bindings that differ from the defaults, by action ID\n");
                new Yaml(options).dump(Map.of(BINDINGS_KEY, saved), writer);
            }
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.warn("Could not save the key bindings to {}", file, e);
        }
    }
}
