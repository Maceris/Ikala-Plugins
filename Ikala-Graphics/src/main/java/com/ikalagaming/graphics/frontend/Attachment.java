package com.ikalagaming.graphics.frontend;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Specifies an attachment description.
 *
 * @param flagBits Specifies additional properties of an attachment.
 * @param format The format of the image used in the attachment.
 * @param sampleCountFlagBits Bit mask specifying the number of samples per pixel that are supported
 *     by the image.
 * @param loadOp If there is a color format, used for the color data. If the format has depth and/or
 *     stencil components, this applies only to depth data. If an attachment is not used, this is
 *     ignored.
 * @param storeOp If there is a color format, used for the color data. If the format has depth
 *     and/or stencil components, this applies only to depth data. If an attachment is not used,
 *     this is ignored.
 * @param stencilLoadOp If there is a color format, this is ignored. If the format has depth and/or
 *     stencil components, this applies only to the stencil data. If an attachment is not used, this
 *     is ignored.
 * @param stencilStoreOp If there is a color format, this is ignored. If the format has depth and/or
 *     stencil components, this applies only to the stencil data. If an attachment is not used, this
 *     is ignored.
 * @param initialLayout The layout which the attachment image resource will be in when the render
 *     pass instance begins.
 * @param finalLayout The layout which the attachment image resource will be transitioned to when a
 *     render pass ends.
 */
public record Attachment(
        int flagBits,
        Format format,
        int sampleCountFlagBits,
        LoadOp loadOp,
        StoreOp storeOp,
        LoadOp stencilLoadOp,
        StoreOp stencilStoreOp,
        ImageLayout initialLayout,
        ImageLayout finalLayout) {
    /**
     * Specifies additional properties of an attachment. Values are:
     *
     * <ul>
     *   <li>{@link #NONE} - No flags.
     *   <li>{@link #CAN_ALIAS} - If an attachment can be aliased, sharing the same device memory as
     *       other attachments.
     * </ul>
     */
    @Getter
    @RequiredArgsConstructor
    public enum Flag {
        /** No flags. */
        NONE(0x0000),
        /** If an attachment can be aliased, sharing the same device memory as other attachments. */
        CAN_ALIAS(0x0001);
        private final int bits;

        /**
         * Combine the bitmasks of multiple flags.
         *
         * @param flags The list of flags we want to combine the bits of.
         * @return The resulting integer bitmask, with all the provided flags set.
         */
        public static int combining(@NonNull Flag... flags) {
            int result = 0;
            for (var flag : flags) {
                result |= flag.bits;
            }
            return result;
        }

        /**
         * Convert a bitmask to a list of flags that it represents.
         *
         * @param flagBits The bitmask we want to pull flags from.
         * @return The corresponding list of flags.
         */
        public static List<Flag> fromBits(final int flagBits) {
            var result = new ArrayList<Flag>();
            for (var flag : Flag.values()) {
                if ((flagBits & flag.bits) != 0) {
                    result.add(flag);
                }
            }
            return result;
        }
    }

    /**
     * Specifies the number of sample counts that are supported for an image. Values are:
     *
     * <ul>
     *   <li>{@link #SUPPORTS_1} - Supports images with 1 sample per pixel.
     *   <li>{@link #SUPPORTS_2} - Supports images with 2 sample per pixel.
     *   <li>{@link #SUPPORTS_4} - Supports images with 4 sample per pixel.
     *   <li>{@link #SUPPORTS_8} - Supports images with 8 sample per pixel.
     *   <li>{@link #SUPPORTS_16} - Supports images with 16 sample per pixel.
     *   <li>{@link #SUPPORTS_32} - Supports images with 32 sample per pixel.
     *   <li>{@link #SUPPORTS_64} - Supports images with 64 sample per pixel.
     * </ul>
     */
    @Getter
    @RequiredArgsConstructor
    public enum SampleCountFlag {
        /** Supports images with 1 sample per pixel. */
        SUPPORTS_1(0x0001),
        /** Supports images with 2 samples per pixel. */
        SUPPORTS_2(0x0002),
        /** Supports images with 4 samples per pixel. */
        SUPPORTS_4(0x0004),
        /** Supports images with 8 samples per pixel. */
        SUPPORTS_8(0x0008),
        /** Supports images with 16 samples per pixel. */
        SUPPORTS_16(0x0010),
        /** Supports images with 32 samples per pixel. */
        SUPPORTS_32(0x0020),
        /** Supports images with 64 samples per pixel. */
        SUPPORTS_64(0x0040);

        private final int bits;

        /**
         * Combine the bitmasks of multiple flags.
         *
         * @param flags The list of flags we want to combine the bits of.
         * @return The resulting integer bitmask, with all the provided flags set.
         */
        public static int combining(@NonNull SampleCountFlag... flags) {
            int result = 0;
            for (var flag : flags) {
                result |= flag.bits;
            }
            return result;
        }

        /**
         * Convert a bitmask to a list of flags that it represents.
         *
         * @param flagBits The bitmask we want to pull flags from.
         * @return The corresponding list of flags.
         */
        public static List<SampleCountFlag> fromBits(final int flagBits) {
            var result = new ArrayList<SampleCountFlag>();
            for (var flag : SampleCountFlag.values()) {
                if ((flagBits & flag.bits) != 0) {
                    result.add(flag);
                }
            }
            return result;
        }
    }

    /**
     * Specifies how attachments are initialized at the beginning of a subpass. Values are:
     *
     * <ul>
     *   <li>{@link #LOAD} - The previous contents of the render area will be preserved as initial
     *       values.
     *   <li>{@link #CLEAR} - The contents of the render area will be cleared to a uniform value.
     *   <li>{@link #DONT_CARE} - The previous contents of the render area do not need to be
     *       preserved, the content is undefined.
     *   <li>{@link #NONE} - The contents of the image is undefined, and it is not used at all.
     * </ul>
     */
    public enum LoadOp {
        /** The previous contents of the render area will be preserved as initial values. */
        LOAD,
        /** The contents of the render area will be cleared to a uniform value. */
        CLEAR,
        /**
         * The previous contents of the render area do not need to be preserved, the content is
         * undefined.
         */
        DONT_CARE,
        /** The contents of the image is undefined, and it is not used at all. */
        NONE
    }

    /**
     * Specifies how attachments are stored to memory at the end of a subpass. Values are:
     *
     * <ul>
     *   <li>{@link #STORE} - Contents generated during the render pass, within the render area, are
     *       written to memory.
     *   <li>{@link #DONT_CARE} - The contents of the render area are not needed after rendering,
     *       and can be discarded.
     *   <li>{@link #NONE} - The render area will not be written to.
     * </ul>
     */
    public enum StoreOp {
        /**
         * Contents generated during the render pass, within the render area, are written to memory.
         */
        STORE,
        /** The contents of the render area are not needed after rendering, and can be discarded. */
        DONT_CARE,
        /** The render area will not be written to. */
        NONE
    }
}
