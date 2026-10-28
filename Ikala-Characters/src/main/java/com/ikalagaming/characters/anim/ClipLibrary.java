package com.ikalagaming.characters.anim;

import com.ikalagaming.characters.definition.AnimationSet;
import com.ikalagaming.characters.gltf.ImportedCharacter;
import com.ikalagaming.characters.pose.Clip;
import com.ikalagaming.characters.pose.Skeleton;
import com.ikalagaming.characters.registry.CharacterRegistry;
import com.ikalagaming.characters.view.CharacterFiles;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The clips one rig's actions play, read from their files the first time they are asked for and
 * moved onto the rig, straight on or through a rig map, like parts are. Each file is read once.
 * Clips that can't be found are left out with one warning each. Safe from any thread.
 *
 * @author Ches Burks
 */
@Slf4j
public final class ClipLibrary {
    /**
     * A clip use from a file, which is what a bound clip depends on.
     *
     * @param file The file.
     * @param use The use.
     */
    private record Key(Path file, AnimationSet.ClipUse use) {}

    /** The loaded definitions, which find files. */
    private final CharacterRegistry registry;

    /** The rig's reference file, for moving clips from other rigs onto it. */
    private final Path reference;

    /** A skeleton of the rig, which clips are bound to. */
    private final Skeleton skeleton;

    /** Each file's clips by name, or null for a file that didn't load. */
    private final Map<Path, Map<String, ImportedCharacter.ClipData>> files = new HashMap<>();

    /** Bound clips, or null for one that couldn't be found. */
    private final Map<Key, ActionClip> bound = new HashMap<>();

    /** Clips already warned about. */
    private final Set<String> warned = new HashSet<>();

    /** Guards the caches. */
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * Start with nothing read.
     *
     * @param registry The loaded definitions, which find files.
     * @param reference The rig's reference file, for moving clips from other rigs onto it.
     * @param skeleton A skeleton of the rig; every skeleton of a rig has the same bones.
     */
    public ClipLibrary(
            @NonNull CharacterRegistry registry,
            @NonNull Path reference,
            @NonNull Skeleton skeleton) {
        this.registry = registry;
        this.reference = reference;
        this.skeleton = skeleton;
    }

    /**
     * The clips a resolved action plays, reading their files if they haven't been.
     *
     * @param resolved The action.
     * @return Its clips that could be found, in order, maybe none.
     */
    public List<ActionClip> clips(@NonNull ActionLookup.Resolved resolved) {
        List<ActionClip> clips = new ArrayList<>();
        for (AnimationSet.ClipUse use : resolved.action().clips()) {
            ActionClip clip = clip(resolved.source().namespace(), use);
            if (clip != null) {
                clips.add(clip);
            }
        }
        return clips;
    }

    /**
     * One clip.
     *
     * @param namespace The namespace whose folder the file is in.
     * @param use The use.
     * @return The clip, or null if it couldn't be found.
     */
    private ActionClip clip(String namespace, AnimationSet.ClipUse use) {
        Path file = registry.asset(namespace, use.file());
        if (file == null) {
            warn(use.clip(), "its namespace " + namespace + " has no folder on disk");
            return null;
        }
        lock.lock();
        try {
            Key key = new Key(file, use);
            if (bound.containsKey(key)) {
                return bound.get(key);
            }
            ActionClip clip = null;
            Map<String, ImportedCharacter.ClipData> named = read(file);
            ImportedCharacter.ClipData data = named == null ? null : named.get(use.name());
            if (named != null && data == null) {
                warn(use.clip(), "the file has no clip by that name");
            } else if (data != null) {
                try {
                    boolean looping = use.loop() == null ? data.looping() : use.loop();
                    Clip bones =
                            new Clip(
                                    skeleton,
                                    data.name(),
                                    data.duration(),
                                    looping,
                                    data.rotations(),
                                    data.translations());
                    clip = new ActionClip(bones, use);
                } catch (IllegalArgumentException e) {
                    warn(use.clip(), e.getMessage());
                }
            }
            bound.put(key, clip);
            return clip;
        } finally {
            lock.unlock();
        }
    }

    /**
     * A file's clips, reading it the first time.
     *
     * @param file The file.
     * @return Its clips by name, or null if it couldn't be read.
     */
    private Map<String, ImportedCharacter.ClipData> read(Path file) {
        if (files.containsKey(file)) {
            return files.get(file);
        }
        Map<String, ImportedCharacter.ClipData> named = null;
        try {
            ImportedCharacter character = CharacterFiles.readAny(file, reference, registry);
            named = new HashMap<>();
            for (ImportedCharacter.ClipData data : character.clips()) {
                named.putIfAbsent(data.name(), data);
            }
        } catch (IOException | UncheckedIOException | IllegalArgumentException e) {
            log.warn("Couldn't read clips from {}: {}", file, e.getMessage());
        }
        files.put(file, named);
        return named;
    }

    /**
     * Warn about a clip once.
     *
     * @param clip The clip.
     * @param why Why it is left out.
     */
    private void warn(String clip, String why) {
        lock.lock();
        try {
            if (warned.add(clip)) {
                log.warn("Leaving out the clip {}: {}", clip, why);
            }
        } finally {
            lock.unlock();
        }
    }
}
