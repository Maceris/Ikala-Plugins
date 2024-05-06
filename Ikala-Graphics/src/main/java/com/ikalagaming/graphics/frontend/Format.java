package com.ikalagaming.graphics.frontend;

/** The format of an image in memory. */
public enum Format {
    /** Format is not specified. */
    UNDEFINED,
    /**
     * A four-component, 16-bit packed unsigned normalized format that has a 1-bit A component in
     * bit 15, a 5-bit R component in bits 10..14, a 5-bit G component in bits 5..9, and a 5-bit B
     * component in bits 0..4.
     */
    A1R5G5B5_UNORM_PACK16,
    /**
     * A four-component, 32-bit packed signed integer format that has a 2-bit A component in bits
     * 30..31, a 10-bit B component in bits 20..29, a 10-bit G component in bits 10..19, and a
     * 10-bit R component in bits 0..9.
     */
    A2B10G10R10_SINT_PACK32,
    /**
     * A four-component, 32-bit packed signed normalized format that has a 2-bit A component in bits
     * 30..31, a 10-bit B component in bits 20..29, a 10-bit G component in bits 10..19, and a
     * 10-bit R component in bits 0..9.
     */
    A2B10G10R10_SNORM_PACK32,
    /**
     * A four-component, 32-bit packed signed scaled integer format that has a 2-bit A component in
     * bits 30..31, a 10-bit B component in bits 20..29, a 10-bit G component in bits 10..19, and a
     * 10-bit R component in bits 0..9.
     */
    A2B10G10R10_SSCALED_PACK32,
    /**
     * A four-component, 32-bit packed unsigned integer format that has a 2-bit A component in bits
     * 30..31, a 10-bit B component in bits 20..29, a 10-bit G component in bits 10..19, and a
     * 10-bit R component in bits 0..9.
     */
    A2B10G10R10_UINT_PACK32,
    /**
     * A four-component, 32-bit packed unsigned normalized format that has a 2-bit A component in
     * bits 30..31, a 10-bit B component in bits 20..29, a 10-bit G component in bits 10..19, and a
     * 10-bit R component in bits 0..9.
     */
    A2B10G10R10_UNORM_PACK32,
    /**
     * A four-component, 32-bit packed unsigned scaled integer format that has a 2-bit A component
     * in bits 30..31, a 10-bit B component in bits 20..29, a 10-bit G component in bits 10..19, and
     * a 10-bit R component in bits 0..9.
     */
    A2B10G10R10_USCALED_PACK32,
    /**
     * A four-component, 32-bit packed signed integer format that has a 2-bit A component in bits
     * 30..31, a 10-bit R component in bits 20..29, a 10-bit G component in bits 10..19, and a
     * 10-bit B component in bits 0..9.
     */
    A2R10G10B10_SINT_PACK32,
    /**
     * A four-component, 32-bit packed signed normalized format that has a 2-bit A component in bits
     * 30..31, a 10-bit R component in bits 20..29, a 10-bit G component in bits 10..19, and a
     * 10-bit B component in bits 0..9.
     */
    A2R10G10B10_SNORM_PACK32,
    /**
     * A four-component, 32-bit packed signed scaled integer format that has a 2-bit A component in
     * bits 30..31, a 10-bit R component in bits 20..29, a 10-bit G component in bits 10..19, and a
     * 10-bit B component in bits 0..9.
     */
    A2R10G10B10_SSCALED_PACK32,
    /**
     * A four-component, 32-bit packed unsigned integer format that has a 2-bit A component in bits
     * 30..31, a 10-bit R component in bits 20..29, a 10-bit G component in bits 10..19, and a
     * 10-bit B component in bits 0..9.
     */
    A2R10G10B10_UINT_PACK32,
    /**
     * A four-component, 32-bit packed unsigned normalized format that has a 2-bit A component in
     * bits 30..31, a 10-bit R component in bits 20..29, a 10-bit G component in bits 10..19, and a
     * 10-bit B component in bits 0..9.
     */
    A2R10G10B10_UNORM_PACK32,
    /**
     * A four-component, 32-bit packed unsigned scaled integer format that has a 2-bit A component
     * in bits 30..31, a 10-bit R component in bits 20..29, a 10-bit G component in bits 10..19, and
     * a 10-bit B component in bits 0..9.
     */
    A2R10G10B10_USCALED_PACK32,
    /**
     * A four-component, 16-bit packed unsigned normalized format that has a 4-bit A component in
     * bits 12..15, a 4-bit B component in bits 8..11, a 4-bit G component in bits 4..7, and a 4-bit
     * R component in bits 0..3.
     */
    A4B4G4R4_UNORM_PACK16,
    /**
     * A four-component, 16-bit packed unsigned normalized format that has a 4-bit A component in
     * bits 12..15, a 4-bit R component in bits 8..11, a 4-bit G component in bits 4..7, and a 4-bit
     * B component in bits 0..3.
     */
    A4R4G4B4_UNORM_PACK16,
    /** A one-component, 8-bit unsigned normalized format that has a single 8-bit A component. */
    A8_UNORM,
    /**
     * A four-component, 32-bit packed signed integer format that has an 8-bit A component in bits
     * 24..31, an 8-bit B component in bits 16..23, an 8-bit G component in bits 8..15, and an 8-bit
     * R component in bits 0..7.
     */
    A8B8G8R8_SINT_PACK32,
    /**
     * A four-component, 32-bit packed signed normalized format that has an 8-bit A component in
     * bits 24..31, an 8-bit B component in bits 16..23, an 8-bit G component in bits 8..15, and an
     * 8-bit R component in bits 0..7.
     */
    A8B8G8R8_SNORM_PACK32,
    /**
     * A four-component, 32-bit packed unsigned normalized format that has an 8-bit A component in
     * bits 24..31, an 8-bit B component stored with sRGB nonlinear encoding in bits 16..23, an
     * 8-bit G component stored with sRGB nonlinear encoding in bits 8..15, and an 8-bit R component
     * stored with sRGB nonlinear encoding in bits 0..7.
     */
    A8B8G8R8_SRGB_PACK32,
    /**
     * A four-component, 32-bit packed signed scaled integer format that has an 8-bit A component in
     * bits 24..31, an 8-bit B component in bits 16..23, an 8-bit G component in bits 8..15, and an
     * 8-bit R component in bits 0..7.
     */
    A8B8G8R8_SSCALED_PACK32,
    /**
     * A four-component, 32-bit packed unsigned integer format that has an 8-bit A component in bits
     * 24..31, an 8-bit B component in bits 16..23, an 8-bit G component in bits 8..15, and an 8-bit
     * R component in bits 0..7.
     */
    A8B8G8R8_UINT_PACK32,
    /**
     * A four-component, 32-bit packed unsigned normalized format that has an 8-bit A component in
     * bits 24..31, an 8-bit B component in bits 16..23, an 8-bit G component in bits 8..15, and an
     * 8-bit R component in bits 0..7.
     */
    A8B8G8R8_UNORM_PACK32,
    /**
     * A four-component, 32-bit packed unsigned scaled integer format that has an 8-bit A component
     * in bits 24..31, an 8-bit B component in bits 16..23, an 8-bit G component in bits 8..15, and
     * an 8-bit R component in bits 0..7.
     */
    A8B8G8R8_USCALED_PACK32,
    /**
     * A four-component, ASTC compressed format where each 128-bit compressed texel block encodes a
     * 10x10 rectangle of signed floating-point RGBA texel data.
     */
    ASTC_10X10_SFLOAT_BLOCK,
    /**
     * A four-component, ASTC compressed format where each 128-bit compressed texel block encodes a
     * 10x10 rectangle of unsigned normalized RGBA texel data with sRGB nonlinear encoding applied
     * to the RGB components.
     */
    ASTC_10X10_SRGB_BLOCK,
    /**
     * A four-component, ASTC compressed format where each 128-bit compressed texel block encodes a
     * 10x10 rectangle of unsigned normalized RGBA texel data.
     */
    ASTC_10X10_UNORM_BLOCK,
    /**
     * A four-component, ASTC compressed format where each 128-bit compressed texel block encodes a
     * 10x5 rectangle of signed floating-point RGBA texel data.
     */
    ASTC_10X5_SFLOAT_BLOCK,
    /**
     * A four-component, ASTC compressed format where each 128-bit compressed texel block encodes a
     * 10x5 rectangle of unsigned normalized RGBA texel data with sRGB nonlinear encoding applied to
     * the RGB components.
     */
    ASTC_10X5_SRGB_BLOCK,
    /**
     * A four-component, ASTC compressed format where each 128-bit compressed texel block encodes a
     * 10x5 rectangle of unsigned normalized RGBA texel data.
     */
    ASTC_10X5_UNORM_BLOCK,
    /**
     * A four-component, ASTC compressed format where each 128-bit compressed texel block encodes a
     * 10x6 rectangle of signed floating-point RGBA texel data.
     */
    ASTC_10X6_SFLOAT_BLOCK,
    /**
     * A four-component, ASTC compressed format where each 128-bit compressed texel block encodes a
     * 10x6 rectangle of unsigned normalized RGBA texel data with sRGB nonlinear encoding applied to
     * the RGB components.
     */
    ASTC_10X6_SRGB_BLOCK,
    /**
     * A four-component, ASTC compressed format where each 128-bit compressed texel block encodes a
     * 10x6 rectangle of unsigned normalized RGBA texel data.
     */
    ASTC_10X6_UNORM_BLOCK,
    /**
     * A four-component, ASTC compressed format where each 128-bit compressed texel block encodes a
     * 10x8 rectangle of signed floating-point RGBA texel data.
     */
    ASTC_10X8_SFLOAT_BLOCK,
    /**
     * A four-component, ASTC compressed format where each 128-bit compressed texel block encodes a
     * 10x8 rectangle of unsigned normalized RGBA texel data with sRGB nonlinear encoding applied to
     * the RGB components.
     */
    ASTC_10X8_SRGB_BLOCK,
    /**
     * A four-component, ASTC compressed format where each 128-bit compressed texel block encodes a
     * 10x8 rectangle of unsigned normalized RGBA texel data.
     */
    ASTC_10X8_UNORM_BLOCK,
    /**
     * A four-component, ASTC compressed format where each 128-bit compressed texel block encodes a
     * 12x10 rectangle of signed floating-point RGBA texel data.
     */
    ASTC_12X10_SFLOAT_BLOCK,
    /**
     * A four-component, ASTC compressed format where each 128-bit compressed texel block encodes a
     * 12x10 rectangle of unsigned normalized RGBA texel data with sRGB nonlinear encoding applied
     * to the RGB components.
     */
    ASTC_12X10_SRGB_BLOCK,
    /**
     * A four-component, ASTC compressed format where each 128-bit compressed texel block encodes a
     * 12x10 rectangle of unsigned normalized RGBA texel data.
     */
    ASTC_12X10_UNORM_BLOCK,
    /**
     * A four-component, ASTC compressed format where each 128-bit compressed texel block encodes a
     * 12x12 rectangle of signed floating-point RGBA texel data.
     */
    ASTC_12X12_SFLOAT_BLOCK,
    /**
     * A four-component, ASTC compressed format where each 128-bit compressed texel block encodes a
     * 12x12 rectangle of unsigned normalized RGBA texel data with sRGB nonlinear encoding applied
     * to the RGB components.
     */
    ASTC_12X12_SRGB_BLOCK,
    /**
     * A four-component, ASTC compressed format where each 128-bit compressed texel block encodes a
     * 12x12 rectangle of unsigned normalized RGBA texel data.
     */
    ASTC_12X12_UNORM_BLOCK,
    /**
     * A four-component, ASTC compressed format where each 128-bit compressed texel block encodes a
     * 4x4 rectangle of signed floating-point RGBA texel data.
     */
    ASTC_4X4_SFLOAT_BLOCK,
    /**
     * A four-component, ASTC compressed format where each 128-bit compressed texel block encodes a
     * 4x4 rectangle of unsigned normalized RGBA texel data with sRGB nonlinear encoding applied to
     * the RGB components.
     */
    ASTC_4X4_SRGB_BLOCK,
    /**
     * A four-component, ASTC compressed format where each 128-bit compressed texel block encodes a
     * 4x4 rectangle of unsigned normalized RGBA texel data.
     */
    ASTC_4X4_UNORM_BLOCK,
    /**
     * A four-component, ASTC compressed format where each 128-bit compressed texel block encodes a
     * 5x4 rectangle of signed floating-point RGBA texel data.
     */
    ASTC_5X4_SFLOAT_BLOCK,
    /**
     * A four-component, ASTC compressed format where each 128-bit compressed texel block encodes a
     * 5x4 rectangle of unsigned normalized RGBA texel data with sRGB nonlinear encoding applied to
     * the RGB components.
     */
    ASTC_5X4_SRGB_BLOCK,
    /**
     * A four-component, ASTC compressed format where each 128-bit compressed texel block encodes a
     * 5x4 rectangle of unsigned normalized RGBA texel data.
     */
    ASTC_5X4_UNORM_BLOCK,
    /**
     * A four-component, ASTC compressed format where each 128-bit compressed texel block encodes a
     * 5x5 rectangle of signed floating-point RGBA texel data.
     */
    ASTC_5X5_SFLOAT_BLOCK,
    /**
     * A four-component, ASTC compressed format where each 128-bit compressed texel block encodes a
     * 5x5 rectangle of unsigned normalized RGBA texel data with sRGB nonlinear encoding applied to
     * the RGB components.
     */
    ASTC_5X5_SRGB_BLOCK,
    /**
     * A four-component, ASTC compressed format where each 128-bit compressed texel block encodes a
     * 5x5 rectangle of unsigned normalized RGBA texel data.
     */
    ASTC_5X5_UNORM_BLOCK,
    /**
     * A four-component, ASTC compressed format where each 128-bit compressed texel block encodes a
     * 6x5 rectangle of signed floating-point RGBA texel data.
     */
    ASTC_6X5_SFLOAT_BLOCK,
    /**
     * A four-component, ASTC compressed format where each 128-bit compressed texel block encodes a
     * 6x5 rectangle of unsigned normalized RGBA texel data with sRGB nonlinear encoding applied to
     * the RGB components.
     */
    ASTC_6X5_SRGB_BLOCK,
    /**
     * A four-component, ASTC compressed format where each 128-bit compressed texel block encodes a
     * 6x5 rectangle of unsigned normalized RGBA texel data.
     */
    ASTC_6X5_UNORM_BLOCK,
    /**
     * A four-component, ASTC compressed format where each 128-bit compressed texel block encodes a
     * 6x6 rectangle of signed floating-point RGBA texel data.
     */
    ASTC_6X6_SFLOAT_BLOCK,
    /**
     * A four-component, ASTC compressed format where each 128-bit compressed texel block encodes a
     * 6x6 rectangle of unsigned normalized RGBA texel data with sRGB nonlinear encoding applied to
     * the RGB components.
     */
    ASTC_6X6_SRGB_BLOCK,
    /**
     * A four-component, ASTC compressed format where each 128-bit compressed texel block encodes a
     * 6x6 rectangle of unsigned normalized RGBA texel data.
     */
    ASTC_6X6_UNORM_BLOCK,
    /**
     * A four-component, ASTC compressed format where each 128-bit compressed texel block encodes a
     * 8x5 rectangle of signed floating-point RGBA texel data.
     */
    ASTC_8X5_SFLOAT_BLOCK,
    /**
     * A four-component, ASTC compressed format where each 128-bit compressed texel block encodes an
     * 8x5 rectangle of unsigned normalized RGBA texel data with sRGB nonlinear encoding applied to
     * the RGB components.
     */
    ASTC_8X5_SRGB_BLOCK,
    /**
     * A four-component, ASTC compressed format where each 128-bit compressed texel block encodes an
     * 8x5 rectangle of unsigned normalized RGBA texel data.
     */
    ASTC_8X5_UNORM_BLOCK,
    /**
     * A four-component, ASTC compressed format where each 128-bit compressed texel block encodes a
     * 8x6 rectangle of signed floating-point RGBA texel data.
     */
    ASTC_8X6_SFLOAT_BLOCK,
    /**
     * A four-component, ASTC compressed format where each 128-bit compressed texel block encodes an
     * 8x6 rectangle of unsigned normalized RGBA texel data with sRGB nonlinear encoding applied to
     * the RGB components.
     */
    ASTC_8X6_SRGB_BLOCK,
    /**
     * A four-component, ASTC compressed format where each 128-bit compressed texel block encodes an
     * 8x6 rectangle of unsigned normalized RGBA texel data.
     */
    ASTC_8X6_UNORM_BLOCK,
    /**
     * A four-component, ASTC compressed format where each 128-bit compressed texel block encodes a
     * 8x8 rectangle of signed floating-point RGBA texel data.
     */
    ASTC_8X8_SFLOAT_BLOCK,
    /**
     * A four-component, ASTC compressed format where each 128-bit compressed texel block encodes an
     * 8x8 rectangle of unsigned normalized RGBA texel data with sRGB nonlinear encoding applied to
     * the RGB components.
     */
    ASTC_8X8_SRGB_BLOCK,
    /**
     * A four-component, ASTC compressed format where each 128-bit compressed texel block encodes an
     * 8x8 rectangle of unsigned normalized RGBA texel data.
     */
    ASTC_8X8_UNORM_BLOCK,
    /**
     * A three-component, 32-bit packed unsigned floating-point format that has a 10-bit B component
     * in bits 22..31, an 11-bit G component in bits 11..21, an 11-bit R component in bits 0..10.
     */
    B10G11R11_UFLOAT_PACK32,
    /**
     * A four-component, 16-bit packed unsigned normalized format that has a 4-bit B component in
     * bits 12..15, a 4-bit G component in bits 8..11, a 4-bit R component in bits 4..7, and a 4-bit
     * A component in bits 0..3.
     */
    B4G4R4A4_UNORM_PACK16,
    /**
     * A four-component, 16-bit packed unsigned normalized format that has a 5-bit B component in
     * bits 11..15, a 5-bit G component in bits 6..10, a 5-bit R component in bits 1..5, and a 1-bit
     * A component in bit 0.
     */
    B5G5R5A1_UNORM_PACK16,
    /**
     * A three-component, 16-bit packed unsigned normalized format that has a 5-bit B component in
     * bits 11..15, a 6-bit G component in bits 5..10, and a 5-bit R component in bits 0..4.
     */
    B5G6R5_UNORM_PACK16,
    /**
     * A three-component, 24-bit signed integer format that has an 8-bit B component in byte 0, an
     * 8-bit G component in byte 1, and an 8-bit R component in byte 2.
     */
    B8G8R8_SINT,
    /**
     * A three-component, 24-bit signed normalized format that has an 8-bit B component in byte 0,
     * an 8-bit G component in byte 1, and an 8-bit R component in byte 2.
     */
    B8G8R8_SNORM,
    /**
     * A three-component, 24-bit unsigned normalized format that has an 8-bit B component stored
     * with sRGB nonlinear encoding in byte 0, an 8-bit G component stored with sRGB nonlinear
     * encoding in byte 1, and an 8-bit R component stored with sRGB nonlinear encoding in byte 2.
     */
    B8G8R8_SRGB,
    /**
     * A three-component, 24-bit signed scaled format that has an 8-bit B component in byte 0, an
     * 8-bit G component in byte 1, and an 8-bit R component in byte 2.
     */
    B8G8R8_SSCALED,
    /**
     * A three-component, 24-bit unsigned integer format that has an 8-bit B component in byte 0, an
     * 8-bit G component in byte 1, and an 8-bit R component in byte 2.
     */
    B8G8R8_UINT,
    /**
     * A three-component, 24-bit unsigned normalized format that has an 8-bit B component in byte 0,
     * an 8-bit G component in byte 1, and an 8-bit R component in byte 2.
     */
    B8G8R8_UNORM,
    /**
     * A three-component, 24-bit unsigned scaled format that has an 8-bit B component in byte 0, an
     * 8-bit G component in byte 1, and an 8-bit R component in byte 2.
     */
    B8G8R8_USCALED,
    /**
     * A four-component, 32-bit signed integer format that has an 8-bit B component in byte 0, an
     * 8-bit G component in byte 1, an 8-bit R component in byte 2, and an 8-bit A component in byte
     * 3.
     */
    B8G8R8A8_SINT,
    /**
     * A four-component, 32-bit signed normalized format that has an 8-bit B component in byte 0, an
     * 8-bit G component in byte 1, an 8-bit R component in byte 2, and an 8-bit A component in byte
     * 3.
     */
    B8G8R8A8_SNORM,
    /**
     * A four-component, 32-bit unsigned normalized format that has an 8-bit B component stored with
     * sRGB nonlinear encoding in byte 0, an 8-bit G component stored with sRGB nonlinear encoding
     * in byte 1, an 8-bit R component stored with sRGB nonlinear encoding in byte 2, and an 8-bit A
     * component in byte 3.
     */
    B8G8R8A8_SRGB,
    /**
     * A four-component, 32-bit signed scaled format that has an 8-bit B component in byte 0, an
     * 8-bit G component in byte 1, an 8-bit R component in byte 2, and an 8-bit A component in byte
     * 3.
     */
    B8G8R8A8_SSCALED,
    /**
     * A four-component, 32-bit unsigned integer format that has an 8-bit B component in byte 0, an
     * 8-bit G component in byte 1, an 8-bit R component in byte 2, and an 8-bit A component in byte
     * 3.
     */
    B8G8R8A8_UINT,
    /**
     * A four-component, 32-bit unsigned normalized format that has an 8-bit B component in byte 0,
     * an 8-bit G component in byte 1, an 8-bit R component in byte 2, and an 8-bit A component in
     * byte 3.
     */
    B8G8R8A8_UNORM,
    /**
     * A four-component, 32-bit unsigned scaled format that has an 8-bit B component in byte 0, an
     * 8-bit G component in byte 1, an 8-bit R component in byte 2, and an 8-bit A component in byte
     * 3.
     */
    B8G8R8A8_USCALED,
    /**
     * A three-component, block-compressed format where each 64-bit compressed texel block encodes a
     * 4x4 rectangle of unsigned normalized RGB texel data with sRGB nonlinear encoding. This format
     * has no alpha and is considered opaque.
     */
    BC1_RGB_SRGB_BLOCK,
    /**
     * A three-component, block-compressed format where each 64-bit compressed texel block encodes a
     * 4x4 rectangle of unsigned normalized RGB texel data. This format has no alpha and is
     * considered opaque.
     */
    BC1_RGB_UNORM_BLOCK,
    /**
     * A four-component, block-compressed format where each 64-bit compressed texel block encodes a
     * 4x4 rectangle of unsigned normalized RGB texel data with sRGB nonlinear encoding, and
     * provides 1 bit of alpha.
     */
    BC1_RGBA_SRGB_BLOCK,
    /**
     * A four-component, block-compressed format where each 64-bit compressed texel block encodes a
     * 4x4 rectangle of unsigned normalized RGB texel data, and provides 1 bit of alpha.
     */
    BC1_RGBA_UNORM_BLOCK,
    /**
     * A four-component, block-compressed format where each 128-bit compressed texel block encodes a
     * 4x4 rectangle of unsigned normalized RGBA texel data with the first 64 bits encoding alpha
     * values followed by 64 bits encoding RGB values with sRGB nonlinear encoding.
     */
    BC2_SRGB_BLOCK,
    /**
     * A four-component, block-compressed format where each 128-bit compressed texel block encodes a
     * 4x4 rectangle of unsigned normalized RGBA texel data with the first 64 bits encoding alpha
     * values followed by 64 bits encoding RGB values.
     */
    BC2_UNORM_BLOCK,
    /**
     * A four-component, block-compressed format where each 128-bit compressed texel block encodes a
     * 4x4 rectangle of unsigned normalized RGBA texel data with the first 64 bits encoding alpha
     * values followed by 64 bits encoding RGB values with sRGB nonlinear encoding.
     */
    BC3_SRGB_BLOCK,
    /**
     * A four-component, block-compressed format where each 128-bit compressed texel block encodes a
     * 4x4 rectangle of unsigned normalized RGBA texel data with the first 64 bits encoding alpha
     * values followed by 64 bits encoding RGB values.
     */
    BC3_UNORM_BLOCK,
    /**
     * A one-component, block-compressed format where each 64-bit compressed texel block encodes a
     * 4x4 rectangle of signed normalized red texel data.
     */
    BC4_SNORM_BLOCK,
    /**
     * A one-component, block-compressed format where each 64-bit compressed texel block encodes a
     * 4x4 rectangle of unsigned normalized red texel data.
     */
    BC4_UNORM_BLOCK,
    /**
     * A two-component, block-compressed format where each 128-bit compressed texel block encodes a
     * 4x4 rectangle of signed normalized RG texel data with the first 64 bits encoding red values
     * followed by 64 bits encoding green values.
     */
    BC5_SNORM_BLOCK,
    /**
     * A two-component, block-compressed format where each 128-bit compressed texel block encodes a
     * 4x4 rectangle of unsigned normalized RG texel data with the first 64 bits encoding red values
     * followed by 64 bits encoding green values.
     */
    BC5_UNORM_BLOCK,
    /**
     * A three-component, block-compressed format where each 128-bit compressed texel block encodes
     * a 4x4 rectangle of signed floating-point RGB texel data.
     */
    BC6H_SFLOAT_BLOCK,
    /**
     * A three-component, block-compressed format where each 128-bit compressed texel block encodes
     * a 4x4 rectangle of unsigned floating-point RGB texel data.
     */
    BC6H_UFLOAT_BLOCK,
    /**
     * A four-component, block-compressed format where each 128-bit compressed texel block encodes a
     * 4x4 rectangle of unsigned normalized RGBA texel data with sRGB nonlinear encoding applied to
     * the RGB components.
     */
    BC7_SRGB_BLOCK,
    /**
     * A four-component, block-compressed format where each 128-bit compressed texel block encodes a
     * 4x4 rectangle of unsigned normalized RGBA texel data.
     */
    BC7_UNORM_BLOCK,
    /**
     * A one-component, 16-bit unsigned normalized format that has a single 16-bit depth component.
     */
    D16_UNORM,
    /**
     * A two-component, 24-bit format that has 16 unsigned normalized bits in the depth component
     * and 8 unsigned integer bits in the stencil component.
     */
    D16_UNORM_S8_UINT,
    /**
     * A two-component, 32-bit packed format that has 8 unsigned integer bits in the stencil
     * component, and 24 unsigned normalized bits in the depth component.
     */
    D24_UNORM_S8_UINT,
    /**
     * A one-component, 32-bit signed floating-point format that has 32 bits in the depth component.
     */
    D32_SFLOAT,
    /**
     * A two-component format that has 32 signed float bits in the depth component and 8 unsigned
     * integer bits in the stencil component. There are optionally 24 bits that are unused.
     */
    D32_SFLOAT_S8_UINT,
    /**
     * A three-component, 32-bit packed unsigned floating-point format that has a 5-bit shared
     * exponent in bits 27..31, a 9-bit B component mantissa in bits 18..26, a 9-bit G component
     * mantissa in bits 9..17, and a 9-bit R component mantissa in bits 0..8.
     */
    E5B9G9R9_UFLOAT_PACK32,
    /**
     * A one-component, ETC2 compressed format where each 64-bit compressed texel block encodes a
     * 4x4 rectangle of signed normalized red texel data.
     */
    EAC_R11_SNORM_BLOCK,
    /**
     * A one-component, ETC2 compressed format where each 64-bit compressed texel block encodes a
     * 4x4 rectangle of unsigned normalized red texel data.
     */
    EAC_R11_UNORM_BLOCK,
    /**
     * A two-component, ETC2 compressed format where each 128-bit compressed texel block encodes a
     * 4x4 rectangle of signed normalized RG texel data with the first 64 bits encoding red values
     * followed by 64 bits encoding green values.
     */
    EAC_R11G11_SNORM_BLOCK,
    /**
     * A two-component, ETC2 compressed format where each 128-bit compressed texel block encodes a
     * 4x4 rectangle of unsigned normalized RG texel data with the first 64 bits encoding red values
     * followed by 64 bits encoding green values.
     */
    EAC_R11G11_UNORM_BLOCK,
    /**
     * A three-component, ETC2 compressed format where each 64-bit compressed texel block encodes a
     * 4x4 rectangle of unsigned normalized RGB texel data with sRGB nonlinear encoding. This format
     * has no alpha and is considered opaque.
     */
    ETC2_R8G8B8_SRGB_BLOCK,
    /**
     * A three-component, ETC2 compressed format where each 64-bit compressed texel block encodes a
     * 4x4 rectangle of unsigned normalized RGB texel data. This format has no alpha and is
     * considered opaque.
     */
    ETC2_R8G8B8_UNORM_BLOCK,
    /**
     * A four-component, ETC2 compressed format where each 64-bit compressed texel block encodes a
     * 4x4 rectangle of unsigned normalized RGB texel data with sRGB nonlinear encoding, and
     * provides 1 bit of alpha.
     */
    ETC2_R8G8B8A1_SRGB_BLOCK,
    /**
     * A four-component, ETC2 compressed format where each 64-bit compressed texel block encodes a
     * 4x4 rectangle of unsigned normalized RGB texel data, and provides 1 bit of alpha.
     */
    ETC2_R8G8B8A1_UNORM_BLOCK,
    /**
     * A four-component, ETC2 compressed format where each 128-bit compressed texel block encodes a
     * 4x4 rectangle of unsigned normalized RGBA texel data with the first 64 bits encoding alpha
     * values followed by 64 bits encoding RGB values with sRGB nonlinear encoding applied.
     */
    ETC2_R8G8B8A8_SRGB_BLOCK,
    /**
     * A four-component, ETC2 compressed format where each 128-bit compressed texel block encodes a
     * 4x4 rectangle of unsigned normalized RGBA texel data with the first 64 bits encoding alpha
     * values followed by 64 bits encoding RGB values.
     */
    ETC2_R8G8B8A8_UNORM_BLOCK,
    /**
     * A one-component, 16-bit signed floating-point format that has a single 16-bit R component.
     */
    R16_SFLOAT,
    /** A one-component, 16-bit signed integer format that has a single 16-bit R component. */
    R16_SINT,
    /** A one-component, 16-bit signed normalized format that has a single 16-bit R component. */
    R16_SNORM,
    /**
     * A one-component, 16-bit signed scaled integer format that has a single 16-bit R component.
     */
    R16_SSCALED,
    /** A one-component, 16-bit unsigned integer format that has a single 16-bit R component. */
    R16_UINT,
    /** A one-component, 16-bit unsigned normalized format that has a single 16-bit R component. */
    R16_UNORM,
    /**
     * A one-component, 16-bit unsigned scaled integer format that has a single 16-bit R component.
     */
    R16_USCALED,
    /**
     * A two-component, 32-bit signed floating-point format that has a 16-bit R component in bytes
     * 0..1, and a 16-bit G component in bytes 2..3.
     */
    R16G16_SFLOAT,
    /**
     * A two-component, 32-bit signed integer format that has a 16-bit R component in bytes 0..1,
     * and a 16-bit G component in bytes 2..3.
     */
    R16G16_SINT,
    /**
     * A two-component, 32-bit signed normalized format that has a 16-bit R component in bytes 0..1,
     * and a 16-bit G component in bytes 2..3.
     */
    R16G16_SNORM,
    /**
     * A two-component, 32-bit signed scaled integer format that has a 16-bit R component in bytes
     * 0..1, and a 16-bit G component in bytes 2..3.
     */
    R16G16_SSCALED,
    /**
     * A two-component, 32-bit unsigned integer format that has a 16-bit R component in bytes 0..1,
     * and a 16-bit G component in bytes 2..3.
     */
    R16G16_UINT,
    /**
     * A two-component, 32-bit unsigned normalized format that has a 16-bit R component in bytes
     * 0..1, and a 16-bit G component in bytes 2..3.
     */
    R16G16_UNORM,
    /**
     * A two-component, 32-bit unsigned scaled integer format that has a 16-bit R component in bytes
     * 0..1, and a 16-bit G component in bytes 2..3.
     */
    R16G16_USCALED,
    /**
     * A three-component, 48-bit signed floating-point format that has a 16-bit R component in bytes
     * 0..1, a 16-bit G component in bytes 2..3, and a 16-bit B component in bytes 4..5.
     */
    R16G16B16_SFLOAT,
    /**
     * A three-component, 48-bit signed integer format that has a 16-bit R component in bytes 0..1,
     * a 16-bit G component in bytes 2..3, and a 16-bit B component in bytes 4..5.
     */
    R16G16B16_SINT,
    /**
     * A three-component, 48-bit signed normalized format that has a 16-bit R component in bytes
     * 0..1, a 16-bit G component in bytes 2..3, and a 16-bit B component in bytes 4..5.
     */
    R16G16B16_SNORM,
    /**
     * A three-component, 48-bit signed scaled integer format that has a 16-bit R component in bytes
     * 0..1, a 16-bit G component in bytes 2..3, and a 16-bit B component in bytes 4..5.
     */
    R16G16B16_SSCALED,
    /**
     * A three-component, 48-bit unsigned integer format that has a 16-bit R component in bytes
     * 0..1, a 16-bit G component in bytes 2..3, and a 16-bit B component in bytes 4..5.
     */
    R16G16B16_UINT,
    /**
     * A three-component, 48-bit unsigned normalized format that has a 16-bit R component in bytes
     * 0..1, a 16-bit G component in bytes 2..3, and a 16-bit B component in bytes 4..5.
     */
    R16G16B16_UNORM,
    /**
     * A three-component, 48-bit unsigned scaled integer format that has a 16-bit R component in
     * bytes 0..1, a 16-bit G component in bytes 2..3, and a 16-bit B component in bytes 4..5.
     */
    R16G16B16_USCALED,
    /**
     * A four-component, 64-bit signed floating-point format that has a 16-bit R component in bytes
     * 0..1, a 16-bit G component in bytes 2..3, a 16-bit B component in bytes 4..5, and a 16-bit A
     * component in bytes 6..7.
     */
    R16G16B16A16_SFLOAT,
    /**
     * A four-component, 64-bit signed integer format that has a 16-bit R component in bytes 0..1, a
     * 16-bit G component in bytes 2..3, a 16-bit B component in bytes 4..5, and a 16-bit A
     * component in bytes 6..7.
     */
    R16G16B16A16_SINT,
    /**
     * A four-component, 64-bit signed normalized format that has a 16-bit R component in bytes
     * 0..1, a 16-bit G component in bytes 2..3, a 16-bit B component in bytes 4..5, and a 16-bit A
     * component in bytes 6..7.
     */
    R16G16B16A16_SNORM,
    /**
     * A four-component, 64-bit signed scaled integer format that has a 16-bit R component in bytes
     * 0..1, a 16-bit G component in bytes 2..3, a 16-bit B component in bytes 4..5, and a 16-bit A
     * component in bytes 6..7.
     */
    R16G16B16A16_SSCALED,
    /**
     * A four-component, 64-bit unsigned integer format that has a 16-bit R component in bytes 0..1,
     * a 16-bit G component in bytes 2..3, a 16-bit B component in bytes 4..5, and a 16-bit A
     * component in bytes 6..7.
     */
    R16G16B16A16_UINT,
    /**
     * A four-component, 64-bit unsigned normalized format that has a 16-bit R component in bytes
     * 0..1, a 16-bit G component in bytes 2..3, a 16-bit B component in bytes 4..5, and a 16-bit A
     * component in bytes 6..7.
     */
    R16G16B16A16_UNORM,
    /**
     * A four-component, 64-bit unsigned scaled integer format that has a 16-bit R component in
     * bytes 0..1, a 16-bit G component in bytes 2..3, a 16-bit B component in bytes 4..5, and a
     * 16-bit A component in bytes 6..7.
     */
    R16G16B16A16_USCALED,
    /**
     * A one-component, 32-bit signed floating-point format that has a single 32-bit R component.
     */
    R32_SFLOAT,
    /** A one-component, 32-bit signed integer format that has a single 32-bit R component. */
    R32_SINT,
    /** A one-component, 32-bit unsigned integer format that has a single 32-bit R component. */
    R32_UINT,
    /**
     * A two-component, 64-bit signed floating-point format that has a 32-bit R component in bytes
     * 0..3, and a 32-bit G component in bytes 4..7.
     */
    R32G32_SFLOAT,
    /**
     * A two-component, 64-bit signed integer format that has a 32-bit R component in bytes 0..3,
     * and a 32-bit G component in bytes 4..7.
     */
    R32G32_SINT,
    /**
     * A two-component, 64-bit unsigned integer format that has a 32-bit R component in bytes 0..3,
     * and a 32-bit G component in bytes 4..7.
     */
    R32G32_UINT,
    /**
     * A three-component, 96-bit signed floating-point format that has a 32-bit R component in bytes
     * 0..3, a 32-bit G component in bytes 4..7, and a 32-bit B component in bytes 8..11.
     */
    R32G32B32_SFLOAT,
    /**
     * A three-component, 96-bit signed integer format that has a 32-bit R component in bytes 0..3,
     * a 32-bit G component in bytes 4..7, and a 32-bit B component in bytes 8..11.
     */
    R32G32B32_SINT,
    /**
     * A three-component, 96-bit unsigned integer format that has a 32-bit R component in bytes
     * 0..3, a 32-bit G component in bytes 4..7, and a 32-bit B component in bytes 8..11.
     */
    R32G32B32_UINT,
    /**
     * A four-component, 128-bit signed floating-point format that has a 32-bit R component in bytes
     * 0..3, a 32-bit G component in bytes 4..7, a 32-bit B component in bytes 8..11, and a 32-bit A
     * component in bytes 12..15.
     */
    R32G32B32A32_SFLOAT,
    /**
     * A four-component, 128-bit signed integer format that has a 32-bit R component in bytes 0..3,
     * a 32-bit G component in bytes 4..7, a 32-bit B component in bytes 8..11, and a 32-bit A
     * component in bytes 12..15.
     */
    R32G32B32A32_SINT,
    /**
     * A four-component, 128-bit unsigned integer format that has a 32-bit R component in bytes
     * 0..3, a 32-bit G component in bytes 4..7, a 32-bit B component in bytes 8..11, and a 32-bit A
     * component in bytes 12..15.
     */
    R32G32B32A32_UINT,
    /**
     * A two-component, 8-bit packed unsigned normalized format that has a 4-bit R component in bits
     * 4..7, and a 4-bit G component in bits 0..3.
     */
    R4G4_UNORM_PACK8,
    /**
     * A four-component, 16-bit packed unsigned normalized format that has a 4-bit R component in
     * bits 12..15, a 4-bit G component in bits 8..11, a 4-bit B component in bits 4..7, and a 4-bit
     * A component in bits 0..3.
     */
    R4G4B4A4_UNORM_PACK16,
    /**
     * A four-component, 16-bit packed unsigned normalized format that has a 5-bit R component in
     * bits 11..15, a 5-bit G component in bits 6..10, a 5-bit B component in bits 1..5, and a 1-bit
     * A component in bit 0.
     */
    R5G5B5A1_UNORM_PACK16,
    /**
     * A three-component, 16-bit packed unsigned normalized format that has a 5-bit R component in
     * bits 11..15, a 6-bit G component in bits 5..10, and a 5-bit B component in bits 0..4.
     */
    R5G6B5_UNORM_PACK16,
    /**
     * A one-component, 64-bit signed floating-point format that has a single 64-bit R component.
     */
    R64_SFLOAT,
    /** A one-component, 64-bit signed integer format that has a single 64-bit R component. */
    R64_SINT,
    /** A one-component, 64-bit unsigned integer format that has a single 64-bit R component. */
    R64_UINT,
    /**
     * A two-component, 128-bit signed floating-point format that has a 64-bit R component in bytes
     * 0..7, and a 64-bit G component in bytes 8..15.
     */
    R64G64_SFLOAT,
    /**
     * A two-component, 128-bit signed integer format that has a 64-bit R component in bytes 0..7,
     * and a 64-bit G component in bytes 8..15.
     */
    R64G64_SINT,
    /**
     * A two-component, 128-bit unsigned integer format that has a 64-bit R component in bytes 0..7,
     * and a 64-bit G component in bytes 8..15.
     */
    R64G64_UINT,
    /**
     * A three-component, 192-bit signed floating-point format that has a 64-bit R component in
     * bytes 0..7, a 64-bit G component in bytes 8..15, and a 64-bit B component in bytes 16..23.
     */
    R64G64B64_SFLOAT,
    /**
     * A three-component, 192-bit signed integer format that has a 64-bit R component in bytes 0..7,
     * a 64-bit G component in bytes 8..15, and a 64-bit B component in bytes 16..23.
     */
    R64G64B64_SINT,
    /**
     * A three-component, 192-bit unsigned integer format that has a 64-bit R component in bytes
     * 0..7, a 64-bit G component in bytes 8..15, and a 64-bit B component in bytes 16..23.
     */
    R64G64B64_UINT,
    /**
     * A four-component, 256-bit signed floating-point format that has a 64-bit R component in bytes
     * 0..7, a 64-bit G component in bytes 8..15, a 64-bit B component in bytes 16..23, and a 64-bit
     * A component in bytes 24..31.
     */
    R64G64B64A64_SFLOAT,
    /**
     * A four-component, 256-bit signed integer format that has a 64-bit R component in bytes 0..7,
     * a 64-bit G component in bytes 8..15, a 64-bit B component in bytes 16..23, and a 64-bit A
     * component in bytes 24..31.
     */
    R64G64B64A64_SINT,
    /**
     * A four-component, 256-bit unsigned integer format that has a 64-bit R component in bytes
     * 0..7, a 64-bit G component in bytes 8..15, a 64-bit B component in bytes 16..23, and a 64-bit
     * A component in bytes 24..31.
     */
    R64G64B64A64_UINT,
    /** A one-component, 8-bit signed integer format that has a single 8-bit R component. */
    R8_SINT,
    /** A one-component, 8-bit signed normalized format that has a single 8-bit R component. */
    R8_SNORM,
    /**
     * A one-component, 8-bit unsigned normalized format that has a single 8-bit R component stored
     * with sRGB nonlinear encoding.
     */
    R8_SRGB,
    /** A one-component, 8-bit signed scaled integer format that has a single 8-bit R component. */
    R8_SSCALED,
    /** A one-component, 8-bit unsigned integer format that has a single 8-bit R component. */
    R8_UINT,
    /** A one-component, 8-bit unsigned normalized format that has a single 8-bit R component. */
    R8_UNORM,
    /**
     * A one-component, 8-bit unsigned scaled integer format that has a single 8-bit R component.
     */
    R8_USCALED,
    /**
     * A two-component, 16-bit signed integer format that has an 8-bit R component in byte 0, and an
     * 8-bit G component in byte 1.
     */
    R8G8_SINT,
    /**
     * A two-component, 16-bit signed normalized format that has an 8-bit R component in byte 0, and
     * an 8-bit G component in byte 1.
     */
    R8G8_SNORM,
    /**
     * A two-component, 16-bit unsigned normalized format that has an 8-bit R component stored with
     * sRGB nonlinear encoding in byte 0, and an 8-bit G component stored with sRGB nonlinear
     * encoding in byte 1.
     */
    R8G8_SRGB,
    /**
     * A two-component, 16-bit signed scaled integer format that has an 8-bit R component in byte 0,
     * and an 8-bit G component in byte 1.
     */
    R8G8_SSCALED,
    /**
     * A two-component, 16-bit unsigned integer format that has an 8-bit R component in byte 0, and
     * an 8-bit G component in byte 1.
     */
    R8G8_UINT,
    /**
     * A two-component, 16-bit unsigned normalized format that has an 8-bit R component in byte 0,
     * and an 8-bit G component in byte 1.
     */
    R8G8_UNORM,
    /**
     * A two-component, 16-bit unsigned scaled integer format that has an 8-bit R component in byte
     * 0, and an 8-bit G component in byte 1.
     */
    R8G8_USCALED,
    /**
     * A three-component, 24-bit signed integer format that has an 8-bit R component in byte 0, an
     * 8-bit G component in byte 1, and an 8-bit B component in byte 2.
     */
    R8G8B8_SINT,
    /**
     * A three-component, 24-bit signed normalized format that has an 8-bit R component in byte 0,
     * an 8-bit G component in byte 1, and an 8-bit B component in byte 2.
     */
    R8G8B8_SNORM,
    /**
     * A three-component, 24-bit unsigned normalized format that has an 8-bit R component stored
     * with sRGB nonlinear encoding in byte 0, an 8-bit G component stored with sRGB nonlinear
     * encoding in byte 1, and an 8-bit B component stored with sRGB nonlinear encoding in byte 2.
     */
    R8G8B8_SRGB,
    /**
     * A three-component, 24-bit signed scaled format that has an 8-bit R component in byte 0, an
     * 8-bit G component in byte 1, and an 8-bit B component in byte 2.
     */
    R8G8B8_SSCALED,
    /**
     * A three-component, 24-bit unsigned integer format that has an 8-bit R component in byte 0, an
     * 8-bit G component in byte 1, and an 8-bit B component in byte 2.
     */
    R8G8B8_UINT,
    /**
     * A three-component, 24-bit unsigned normalized format that has an 8-bit R component in byte 0,
     * an 8-bit G component in byte 1, and an 8-bit B component in byte 2.
     */
    R8G8B8_UNORM,
    /**
     * A three-component, 24-bit unsigned scaled format that has an 8-bit R component in byte 0, an
     * 8-bit G component in byte 1, and an 8-bit B component in byte 2.
     */
    R8G8B8_USCALED,
    /**
     * A four-component, 32-bit signed integer format that has an 8-bit R component in byte 0, an
     * 8-bit G component in byte 1, an 8-bit B component in byte 2, and an 8-bit A component in byte
     * 3.
     */
    R8G8B8A8_SINT,
    /**
     * A four-component, 32-bit signed normalized format that has an 8-bit R component in byte 0, an
     * 8-bit G component in byte 1, an 8-bit B component in byte 2, and an 8-bit A component in byte
     * 3.
     */
    R8G8B8A8_SNORM,
    /**
     * A four-component, 32-bit unsigned normalized format that has an 8-bit R component stored with
     * sRGB nonlinear encoding in byte 0, an 8-bit G component stored with sRGB nonlinear encoding
     * in byte 1, an 8-bit B component stored with sRGB nonlinear encoding in byte 2, and an 8-bit A
     * component in byte 3.
     */
    R8G8B8A8_SRGB,
    /**
     * A four-component, 32-bit signed scaled format that has an 8-bit R component in byte 0, an
     * 8-bit G component in byte 1, an 8-bit B component in byte 2, and an 8-bit A component in byte
     * 3.
     */
    R8G8B8A8_SSCALED,
    /**
     * A four-component, 32-bit unsigned integer format that has an 8-bit R component in byte 0, an
     * 8-bit G component in byte 1, an 8-bit B component in byte 2, and an 8-bit A component in byte
     * 3.
     */
    R8G8B8A8_UINT,
    /**
     * A four-component, 32-bit unsigned normalized format that has an 8-bit R component in byte 0,
     * an 8-bit G component in byte 1, an 8-bit B component in byte 2, and an 8-bit A component in
     * byte 3.
     */
    R8G8B8A8_UNORM,
    /**
     * A four-component, 32-bit unsigned scaled format that has an 8-bit R component in byte 0, an
     * 8-bit G component in byte 1, an 8-bit B component in byte 2, and an 8-bit A component in byte
     * 3.
     */
    R8G8B8A8_USCALED,
    /** A one-component, 8-bit unsigned integer format that has 8 bits in the stencil component. */
    S8_UINT,
    /**
     * A two-component, 32-bit format that has 24 unsigned normalized bits in the depth component
     * and, optionally, 8 bits that are unused.
     */
    X8_D24_UNORM_PACK32
}
