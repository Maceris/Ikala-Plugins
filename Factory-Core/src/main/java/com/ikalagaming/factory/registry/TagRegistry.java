package com.ikalagaming.factory.registry;

import com.ikalagaming.factory.FactoryPlugin;
import com.ikalagaming.factory.world.Tag;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
public class TagRegistry extends Registry<Tag> {

    /**
     * Add a tag to the list of tags. If the tag already exists, this will fail.
     *
     * @param tag The name of the tag.
     * @return Whether we successfully added the tag.
     */
    public boolean addTag(@NonNull String tag) {
        return this.addTag(tag, null);
    }

    /**
     * Add a tag to the list of tags. If the tag already exists, or the parent does not, this will
     * fail.
     *
     * @param tag The name of the tag.
     * @param parentName The name of the parent tag, or null if there is no parent tag.
     * @return Whether we successfully added the tag.
     */
    public boolean addTag(@NonNull String tag, String parentName) {
        if (containsKey(tag)) {
            log.warn(
                    SafeResourceLoader.getStringFormatted(
                            "TAG_DUPLICATE", FactoryPlugin.getResourceBundle(), tag));
            return false;
        }
        if (parentName != null && !containsKey(parentName)) {
            log.warn(
                    SafeResourceLoader.getStringFormatted(
                            "TAG_MISSING_PARENT",
                            FactoryPlugin.getResourceBundle(),
                            tag,
                            parentName));
            return false;
        }

        Tag parent = null;

        if (parentName != null) {
            parent = findTag(parentName).orElse(null);
        }

        definitions.put(tag, new Tag(tag, parent));

        return true;
    }

    /**
     * Look through the list of tags and search for one with the given name.
     *
     * @param tagName The name of the tag to look for.
     * @return The tag with the given name.
     * @throws NullPointerException If the tag name is null.
     */
    public Optional<Tag> findTag(@NonNull String tagName) {
        if (containsKey(tagName)) {
            return Optional.of(definitions.get(tagName));
        }
        log.error(
                SafeResourceLoader.getString("TAG_MISSING", FactoryPlugin.getResourceBundle()),
                tagName);
        return Optional.empty();
    }
}
