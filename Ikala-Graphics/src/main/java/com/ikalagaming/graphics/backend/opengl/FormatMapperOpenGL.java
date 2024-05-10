package com.ikalagaming.graphics.backend.opengl;

import static org.lwjgl.opengl.GL46.*;

import com.ikalagaming.graphics.GraphicsPlugin;
import com.ikalagaming.graphics.exceptions.TextureException;
import com.ikalagaming.graphics.frontend.Format;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.NonNull;

public class FormatMapperOpenGL {

    private static final String UNSUPPORTED_TEXTURE_FORMAT = "UNSUPPORTED_TEXTURE_FORMAT";
    private static final String BACKEND_NAME = "OpenGL";

    /**
     * Convert the specified format into an OpenGL internal format constant.
     *
     * @param format The format we want to represent.
     * @return The OpenGL internal format value.
     * @throws TextureException If the format is not supported by this backend.
     */
    public static int mapInternalFormat(@NonNull Format format) {
        return switch (format) {
            case D16_UNORM -> GL_DEPTH_COMPONENT16;
            case D24_UNORM_S8_UINT -> GL_DEPTH24_STENCIL8;
            case D32_SFLOAT -> GL_DEPTH_STENCIL;
            case D32_SFLOAT_S8_UINT -> GL_DEPTH32F_STENCIL8;
            case R16_SFLOAT -> GL_R16F;
            case R16_SINT -> GL_R16I;
            case R16_SNORM -> GL_R16_SNORM;
            case R16_UINT -> GL_R16UI;
            case R16G16_SFLOAT -> GL_RG16F;
            case R16G16_SINT -> GL_RG16I;
            case R16G16_SNORM -> GL_RG16_SNORM;
            case R16G16_UINT -> GL_RG16UI;
            case R16G16B16_SFLOAT -> GL_RGB16F;
            case R16G16B16_SINT -> GL_RGB16I;
            case R16G16B16_SNORM -> GL_RGB16_SNORM;
            case R16G16B16_UINT -> GL_RGB16UI;
            case R16G16B16A16_SFLOAT -> GL_RGBA16F;
            case R16G16B16A16_SINT -> GL_RGBA16I;
            case R16G16B16A16_SNORM -> GL_RGBA16_SNORM;
            case R16G16B16A16_UINT -> GL_RGBA16UI;
            case R32_SFLOAT -> GL_R32F;
            case R32_SINT -> GL_R32I;
            case R32_UINT -> GL_R32UI;
            case R32G32_SFLOAT -> GL_RG32F;
            case R32G32_SINT -> GL_RG32I;
            case R32G32_UINT -> GL_RG32UI;
            case R32G32B32_SFLOAT -> GL_RGB32F;
            case R32G32B32_SINT -> GL_RGB32I;
            case R32G32B32_UINT -> GL_RGB32UI;
            case R32G32B32A32_SFLOAT -> GL_RGBA32F;
            case R32G32B32A32_SINT -> GL_RGBA32I;
            case R32G32B32A32_UINT -> GL_RGBA32UI;
            case R4G4B4A4_UNORM_PACK16 -> GL_RGBA4;
            case R5G5B5A1_UNORM_PACK16 -> GL_RGB5_A1;
            case R8_SINT -> GL_R8I;
            case R8_SNORM -> GL_R8_SNORM;
            case R8_UINT -> GL_R8UI;
            case R8G8_SINT -> GL_RG8I;
            case R8G8_SNORM -> GL_RG8_SNORM;
            case R8G8_UINT -> GL_RG8UI;
            case R8G8B8_SINT -> GL_RGB8I;
            case R8G8B8_SNORM -> GL_RGB8_SNORM;
            case R8G8B8_SRGB -> GL_SRGB8;
            case R8G8B8_UINT -> GL_RGB8UI;
            case R8G8B8A8_SINT -> GL_RGBA8I;
            case R8G8B8A8_SNORM -> GL_RGBA8_SNORM;
            case R8G8B8A8_SRGB -> GL_SRGB8_ALPHA8;
            case R8G8B8A8_UINT -> GL_RGBA8UI;
            case UNDEFINED,
                            X8_D24_UNORM_PACK32,
                            S8_UINT,
                            R8G8B8A8_USCALED,
                            R8G8B8A8_UNORM,
                            R8G8B8A8_SSCALED,
                            R8G8B8_USCALED,
                            R8G8B8_UNORM,
                            R8G8B8_SSCALED,
                            R8G8_USCALED,
                            R8G8_UNORM,
                            R8G8_SSCALED,
                            R8G8_SRGB,
                            R8_USCALED,
                            R8_UNORM,
                            R8_SSCALED,
                            R8_SRGB,
                            R64G64B64A64_UINT,
                            R64G64B64A64_SINT,
                            R64G64B64A64_SFLOAT,
                            R64G64B64_UINT,
                            R64G64B64_SINT,
                            R64G64B64_SFLOAT,
                            R64G64_UINT,
                            R64G64_SINT,
                            R64G64_SFLOAT,
                            R64_UINT,
                            R64_SINT,
                            R64_SFLOAT,
                            R5G6B5_UNORM_PACK16,
                            R4G4_UNORM_PACK8,
                            R16G16B16A16_USCALED,
                            R16G16B16A16_UNORM,
                            R16G16B16A16_SSCALED,
                            R16G16B16_USCALED,
                            R16G16B16_UNORM,
                            R16G16B16_SSCALED,
                            R16G16_USCALED,
                            R16G16_UNORM,
                            R16G16_SSCALED,
                            R16_USCALED,
                            R16_UNORM,
                            R16_SSCALED,
                            ETC2_R8G8B8A8_UNORM_BLOCK,
                            ETC2_R8G8B8A8_SRGB_BLOCK,
                            ETC2_R8G8B8A1_UNORM_BLOCK,
                            ETC2_R8G8B8A1_SRGB_BLOCK,
                            ETC2_R8G8B8_UNORM_BLOCK,
                            ETC2_R8G8B8_SRGB_BLOCK,
                            EAC_R11G11_UNORM_BLOCK,
                            EAC_R11G11_SNORM_BLOCK,
                            EAC_R11_UNORM_BLOCK,
                            EAC_R11_SNORM_BLOCK,
                            E5B9G9R9_UFLOAT_PACK32,
                            D16_UNORM_S8_UINT,
                            BC7_UNORM_BLOCK,
                            BC7_SRGB_BLOCK,
                            BC6H_UFLOAT_BLOCK,
                            BC6H_SFLOAT_BLOCK,
                            BC5_UNORM_BLOCK,
                            BC5_SNORM_BLOCK,
                            BC4_UNORM_BLOCK,
                            BC4_SNORM_BLOCK,
                            BC3_UNORM_BLOCK,
                            BC3_SRGB_BLOCK,
                            BC2_UNORM_BLOCK,
                            BC2_SRGB_BLOCK,
                            BC1_RGBA_UNORM_BLOCK,
                            BC1_RGBA_SRGB_BLOCK,
                            BC1_RGB_UNORM_BLOCK,
                            BC1_RGB_SRGB_BLOCK,
                            B8G8R8A8_USCALED,
                            B8G8R8A8_UNORM,
                            B8G8R8A8_UINT,
                            B8G8R8A8_SSCALED,
                            B8G8R8A8_SRGB,
                            B8G8R8A8_SNORM,
                            B8G8R8A8_SINT,
                            B8G8R8_USCALED,
                            B8G8R8_UNORM,
                            B8G8R8_UINT,
                            B8G8R8_SSCALED,
                            B8G8R8_SRGB,
                            B8G8R8_SNORM,
                            B8G8R8_SINT,
                            B5G6R5_UNORM_PACK16,
                            B5G5R5A1_UNORM_PACK16,
                            B4G4R4A4_UNORM_PACK16,
                            B10G11R11_UFLOAT_PACK32,
                            ASTC_8X8_UNORM_BLOCK,
                            ASTC_8X8_SRGB_BLOCK,
                            ASTC_8X8_SFLOAT_BLOCK,
                            ASTC_8X6_UNORM_BLOCK,
                            ASTC_8X6_SRGB_BLOCK,
                            ASTC_8X6_SFLOAT_BLOCK,
                            ASTC_8X5_UNORM_BLOCK,
                            ASTC_8X5_SRGB_BLOCK,
                            ASTC_8X5_SFLOAT_BLOCK,
                            ASTC_6X6_UNORM_BLOCK,
                            ASTC_6X6_SRGB_BLOCK,
                            ASTC_6X6_SFLOAT_BLOCK,
                            ASTC_6X5_UNORM_BLOCK,
                            ASTC_6X5_SRGB_BLOCK,
                            ASTC_6X5_SFLOAT_BLOCK,
                            ASTC_5X5_UNORM_BLOCK,
                            ASTC_5X5_SRGB_BLOCK,
                            ASTC_5X5_SFLOAT_BLOCK,
                            ASTC_5X4_UNORM_BLOCK,
                            ASTC_5X4_SRGB_BLOCK,
                            ASTC_5X4_SFLOAT_BLOCK,
                            ASTC_4X4_UNORM_BLOCK,
                            ASTC_4X4_SRGB_BLOCK,
                            ASTC_4X4_SFLOAT_BLOCK,
                            ASTC_12X12_UNORM_BLOCK,
                            ASTC_12X12_SRGB_BLOCK,
                            ASTC_12X12_SFLOAT_BLOCK,
                            ASTC_12X10_UNORM_BLOCK,
                            ASTC_12X10_SRGB_BLOCK,
                            ASTC_12X10_SFLOAT_BLOCK,
                            ASTC_10X8_UNORM_BLOCK,
                            ASTC_10X8_SRGB_BLOCK,
                            ASTC_10X8_SFLOAT_BLOCK,
                            ASTC_10X6_UNORM_BLOCK,
                            ASTC_10X6_SRGB_BLOCK,
                            ASTC_10X6_SFLOAT_BLOCK,
                            ASTC_10X5_UNORM_BLOCK,
                            ASTC_10X5_SRGB_BLOCK,
                            ASTC_10X5_SFLOAT_BLOCK,
                            ASTC_10X10_UNORM_BLOCK,
                            ASTC_10X10_SRGB_BLOCK,
                            ASTC_10X10_SFLOAT_BLOCK,
                            A8B8G8R8_USCALED_PACK32,
                            A8B8G8R8_UNORM_PACK32,
                            A8B8G8R8_UINT_PACK32,
                            A8B8G8R8_SSCALED_PACK32,
                            A8B8G8R8_SRGB_PACK32,
                            A8B8G8R8_SNORM_PACK32,
                            A8B8G8R8_SINT_PACK32,
                            A8_UNORM,
                            A4R4G4B4_UNORM_PACK16,
                            A4B4G4R4_UNORM_PACK16,
                            A2R10G10B10_USCALED_PACK32,
                            A2R10G10B10_UNORM_PACK32,
                            A2R10G10B10_UINT_PACK32,
                            A2R10G10B10_SSCALED_PACK32,
                            A2R10G10B10_SNORM_PACK32,
                            A2R10G10B10_SINT_PACK32,
                            A2B10G10R10_USCALED_PACK32,
                            A2B10G10R10_UNORM_PACK32,
                            A2B10G10R10_UINT_PACK32,
                            A2B10G10R10_SSCALED_PACK32,
                            A2B10G10R10_SNORM_PACK32,
                            A2B10G10R10_SINT_PACK32,
                            A1R5G5B5_UNORM_PACK16 ->
                    throw new TextureException(
                            SafeResourceLoader.getStringFormatted(
                                    UNSUPPORTED_TEXTURE_FORMAT,
                                    GraphicsPlugin.getResourceBundle(),
                                    format.toString(),
                                    BACKEND_NAME));
        };
    }

    /**
     * Convert the specified format into an OpenGL format constant for the pixel data.
     *
     * @param format The format we want to represent.
     * @return The OpenGL internal format value.
     * @throws TextureException If the format is not supported by this backend.
     */
    public static int mapFormat(@NonNull Format format) {
        return switch (format) {
            case D16_UNORM -> GL_DEPTH_COMPONENT;
            case D24_UNORM_S8_UINT, D32_SFLOAT_S8_UINT, D32_SFLOAT -> GL_DEPTH_STENCIL;
            case R16_SFLOAT,
                            R8_UINT,
                            R8_SNORM,
                            R8_SINT,
                            R32_UINT,
                            R32_SINT,
                            R32_SFLOAT,
                            R16_UINT,
                            R16_SNORM,
                            R16_SINT ->
                    GL_RED;
            case R16G16_SFLOAT,
                            R8G8_UINT,
                            R8G8_SNORM,
                            R8G8_SINT,
                            R32G32_UINT,
                            R32G32_SINT,
                            R32G32_SFLOAT,
                            R16G16_UINT,
                            R16G16_SNORM,
                            R16G16_SINT ->
                    GL_RG;
            case R16G16B16_SFLOAT,
                            R8G8B8_UINT,
                            R8G8B8_SRGB,
                            R8G8B8_SNORM,
                            R8G8B8_SINT,
                            R32G32B32_UINT,
                            R32G32B32_SINT,
                            R32G32B32_SFLOAT,
                            R16G16B16_UINT,
                            R16G16B16_SNORM,
                            R16G16B16_SINT ->
                    GL_RGB;
            case R16G16B16A16_SFLOAT,
                            R8G8B8A8_UINT,
                            R8G8B8A8_SRGB,
                            R8G8B8A8_SNORM,
                            R8G8B8A8_SINT,
                            R5G5B5A1_UNORM_PACK16,
                            R4G4B4A4_UNORM_PACK16,
                            R32G32B32A32_UINT,
                            R32G32B32A32_SINT,
                            R32G32B32A32_SFLOAT,
                            R16G16B16A16_UINT,
                            R16G16B16A16_SNORM,
                            R16G16B16A16_SINT ->
                    GL_RGBA;
            case UNDEFINED,
                            X8_D24_UNORM_PACK32,
                            S8_UINT,
                            R8G8B8A8_USCALED,
                            R8G8B8A8_UNORM,
                            R8G8B8A8_SSCALED,
                            R8G8B8_USCALED,
                            R8G8B8_UNORM,
                            R8G8B8_SSCALED,
                            R8G8_USCALED,
                            R8G8_UNORM,
                            R8G8_SSCALED,
                            R8G8_SRGB,
                            R8_USCALED,
                            R8_UNORM,
                            R8_SSCALED,
                            R8_SRGB,
                            R64G64B64A64_UINT,
                            R64G64B64A64_SINT,
                            R64G64B64A64_SFLOAT,
                            R64G64B64_UINT,
                            R64G64B64_SINT,
                            R64G64B64_SFLOAT,
                            R64G64_UINT,
                            R64G64_SINT,
                            R64G64_SFLOAT,
                            R64_UINT,
                            R64_SINT,
                            R64_SFLOAT,
                            R5G6B5_UNORM_PACK16,
                            R4G4_UNORM_PACK8,
                            R16G16B16A16_USCALED,
                            R16G16B16A16_UNORM,
                            R16G16B16A16_SSCALED,
                            R16G16B16_USCALED,
                            R16G16B16_UNORM,
                            R16G16B16_SSCALED,
                            R16G16_USCALED,
                            R16G16_UNORM,
                            R16G16_SSCALED,
                            R16_USCALED,
                            R16_UNORM,
                            R16_SSCALED,
                            ETC2_R8G8B8A8_UNORM_BLOCK,
                            ETC2_R8G8B8A8_SRGB_BLOCK,
                            ETC2_R8G8B8A1_UNORM_BLOCK,
                            ETC2_R8G8B8A1_SRGB_BLOCK,
                            ETC2_R8G8B8_UNORM_BLOCK,
                            ETC2_R8G8B8_SRGB_BLOCK,
                            EAC_R11G11_UNORM_BLOCK,
                            EAC_R11G11_SNORM_BLOCK,
                            EAC_R11_UNORM_BLOCK,
                            EAC_R11_SNORM_BLOCK,
                            E5B9G9R9_UFLOAT_PACK32,
                            D16_UNORM_S8_UINT,
                            BC7_UNORM_BLOCK,
                            BC7_SRGB_BLOCK,
                            BC6H_UFLOAT_BLOCK,
                            BC6H_SFLOAT_BLOCK,
                            BC5_UNORM_BLOCK,
                            BC5_SNORM_BLOCK,
                            BC4_UNORM_BLOCK,
                            BC4_SNORM_BLOCK,
                            BC3_UNORM_BLOCK,
                            BC3_SRGB_BLOCK,
                            BC2_UNORM_BLOCK,
                            BC2_SRGB_BLOCK,
                            BC1_RGBA_UNORM_BLOCK,
                            BC1_RGBA_SRGB_BLOCK,
                            BC1_RGB_UNORM_BLOCK,
                            BC1_RGB_SRGB_BLOCK,
                            B8G8R8A8_USCALED,
                            B8G8R8A8_UNORM,
                            B8G8R8A8_UINT,
                            B8G8R8A8_SSCALED,
                            B8G8R8A8_SRGB,
                            B8G8R8A8_SNORM,
                            B8G8R8A8_SINT,
                            B8G8R8_USCALED,
                            B8G8R8_UNORM,
                            B8G8R8_UINT,
                            B8G8R8_SSCALED,
                            B8G8R8_SRGB,
                            B8G8R8_SNORM,
                            B8G8R8_SINT,
                            B5G6R5_UNORM_PACK16,
                            B5G5R5A1_UNORM_PACK16,
                            B4G4R4A4_UNORM_PACK16,
                            B10G11R11_UFLOAT_PACK32,
                            ASTC_8X8_UNORM_BLOCK,
                            ASTC_8X8_SRGB_BLOCK,
                            ASTC_8X8_SFLOAT_BLOCK,
                            ASTC_8X6_UNORM_BLOCK,
                            ASTC_8X6_SRGB_BLOCK,
                            ASTC_8X6_SFLOAT_BLOCK,
                            ASTC_8X5_UNORM_BLOCK,
                            ASTC_8X5_SRGB_BLOCK,
                            ASTC_8X5_SFLOAT_BLOCK,
                            ASTC_6X6_UNORM_BLOCK,
                            ASTC_6X6_SRGB_BLOCK,
                            ASTC_6X6_SFLOAT_BLOCK,
                            ASTC_6X5_UNORM_BLOCK,
                            ASTC_6X5_SRGB_BLOCK,
                            ASTC_6X5_SFLOAT_BLOCK,
                            ASTC_5X5_UNORM_BLOCK,
                            ASTC_5X5_SRGB_BLOCK,
                            ASTC_5X5_SFLOAT_BLOCK,
                            ASTC_5X4_UNORM_BLOCK,
                            ASTC_5X4_SRGB_BLOCK,
                            ASTC_5X4_SFLOAT_BLOCK,
                            ASTC_4X4_UNORM_BLOCK,
                            ASTC_4X4_SRGB_BLOCK,
                            ASTC_4X4_SFLOAT_BLOCK,
                            ASTC_12X12_UNORM_BLOCK,
                            ASTC_12X12_SRGB_BLOCK,
                            ASTC_12X12_SFLOAT_BLOCK,
                            ASTC_12X10_UNORM_BLOCK,
                            ASTC_12X10_SRGB_BLOCK,
                            ASTC_12X10_SFLOAT_BLOCK,
                            ASTC_10X8_UNORM_BLOCK,
                            ASTC_10X8_SRGB_BLOCK,
                            ASTC_10X8_SFLOAT_BLOCK,
                            ASTC_10X6_UNORM_BLOCK,
                            ASTC_10X6_SRGB_BLOCK,
                            ASTC_10X6_SFLOAT_BLOCK,
                            ASTC_10X5_UNORM_BLOCK,
                            ASTC_10X5_SRGB_BLOCK,
                            ASTC_10X5_SFLOAT_BLOCK,
                            ASTC_10X10_UNORM_BLOCK,
                            ASTC_10X10_SRGB_BLOCK,
                            ASTC_10X10_SFLOAT_BLOCK,
                            A8B8G8R8_USCALED_PACK32,
                            A8B8G8R8_UNORM_PACK32,
                            A8B8G8R8_UINT_PACK32,
                            A8B8G8R8_SSCALED_PACK32,
                            A8B8G8R8_SRGB_PACK32,
                            A8B8G8R8_SNORM_PACK32,
                            A8B8G8R8_SINT_PACK32,
                            A8_UNORM,
                            A4R4G4B4_UNORM_PACK16,
                            A4B4G4R4_UNORM_PACK16,
                            A2R10G10B10_USCALED_PACK32,
                            A2R10G10B10_UNORM_PACK32,
                            A2R10G10B10_UINT_PACK32,
                            A2R10G10B10_SSCALED_PACK32,
                            A2R10G10B10_SNORM_PACK32,
                            A2R10G10B10_SINT_PACK32,
                            A2B10G10R10_USCALED_PACK32,
                            A2B10G10R10_UNORM_PACK32,
                            A2B10G10R10_UINT_PACK32,
                            A2B10G10R10_SSCALED_PACK32,
                            A2B10G10R10_SNORM_PACK32,
                            A2B10G10R10_SINT_PACK32,
                            A1R5G5B5_UNORM_PACK16 ->
                    throw new TextureException(
                            SafeResourceLoader.getStringFormatted(
                                    UNSUPPORTED_TEXTURE_FORMAT,
                                    GraphicsPlugin.getResourceBundle(),
                                    format.toString(),
                                    BACKEND_NAME));
        };
    }

    /**
     * Convert the specified format into an OpenGL constant for texel type.
     *
     * @param format The format we want to represent.
     * @return The OpenGL internal format value.
     * @throws TextureException If the format is not supported by this backend.
     */
    public static int mapType(@NonNull Format format) {
        return switch (format) {
            case D16_UNORM, R16G16B16A16_UINT, R16G16B16_UINT, R16G16_UINT, R16_UINT ->
                    GL_UNSIGNED_SHORT;
            case D24_UNORM_S8_UINT,
                            R32G32B32A32_UINT,
                            R32G32B32_UINT,
                            R32G32_UINT,
                            R32_UINT,
                            D32_SFLOAT_S8_UINT ->
                    GL_UNSIGNED_INT;
            case D32_SFLOAT, R32G32B32A32_SINT, R32G32B32_SINT, R32G32_SINT, R32_SINT -> GL_INT;
            case R16_SFLOAT,
                            R32G32B32A32_SFLOAT,
                            R32G32B32_SFLOAT,
                            R32G32_SFLOAT,
                            R32_SFLOAT,
                            R16G16B16A16_SFLOAT,
                            R16G16B16_SFLOAT,
                            R16G16_SFLOAT ->
                    GL_FLOAT;
            case R16_SINT,
                            R16G16B16A16_SNORM,
                            R16G16B16A16_SINT,
                            R16G16B16_SNORM,
                            R16G16B16_SINT,
                            R16G16_SNORM,
                            R16G16_SINT,
                            R16_SNORM ->
                    GL_SHORT;
            case R4G4B4A4_UNORM_PACK16 -> GL_UNSIGNED_SHORT_4_4_4_4;
            case R5G5B5A1_UNORM_PACK16 -> GL_UNSIGNED_SHORT_5_5_5_1;
            case R8_SINT,
                            R8G8B8A8_SRGB,
                            R8G8B8A8_SNORM,
                            R8G8B8A8_SINT,
                            R8G8B8_SNORM,
                            R8G8B8_SINT,
                            R8G8_SNORM,
                            R8G8_SINT,
                            R8_SNORM ->
                    GL_BYTE;
            case R8_UINT, R8G8B8_UINT, R8G8B8A8_UINT, R8G8_UINT -> GL_UNSIGNED_BYTE;
            case R8G8B8_SRGB -> GL_SRGB8;
            case UNDEFINED,
                            X8_D24_UNORM_PACK32,
                            S8_UINT,
                            R8G8B8A8_USCALED,
                            R8G8B8A8_UNORM,
                            R8G8B8A8_SSCALED,
                            R8G8B8_USCALED,
                            R8G8B8_UNORM,
                            R8G8B8_SSCALED,
                            R8G8_USCALED,
                            R8G8_UNORM,
                            R8G8_SSCALED,
                            R8G8_SRGB,
                            R8_USCALED,
                            R8_UNORM,
                            R8_SSCALED,
                            R8_SRGB,
                            R64G64B64A64_UINT,
                            R64G64B64A64_SINT,
                            R64G64B64A64_SFLOAT,
                            R64G64B64_UINT,
                            R64G64B64_SINT,
                            R64G64B64_SFLOAT,
                            R64G64_UINT,
                            R64G64_SINT,
                            R64G64_SFLOAT,
                            R64_UINT,
                            R64_SINT,
                            R64_SFLOAT,
                            R5G6B5_UNORM_PACK16,
                            R4G4_UNORM_PACK8,
                            R16G16B16A16_USCALED,
                            R16G16B16A16_UNORM,
                            R16G16B16A16_SSCALED,
                            R16G16B16_USCALED,
                            R16G16B16_UNORM,
                            R16G16B16_SSCALED,
                            R16G16_USCALED,
                            R16G16_UNORM,
                            R16G16_SSCALED,
                            R16_USCALED,
                            R16_UNORM,
                            R16_SSCALED,
                            ETC2_R8G8B8A8_UNORM_BLOCK,
                            ETC2_R8G8B8A8_SRGB_BLOCK,
                            ETC2_R8G8B8A1_UNORM_BLOCK,
                            ETC2_R8G8B8A1_SRGB_BLOCK,
                            ETC2_R8G8B8_UNORM_BLOCK,
                            ETC2_R8G8B8_SRGB_BLOCK,
                            EAC_R11G11_UNORM_BLOCK,
                            EAC_R11G11_SNORM_BLOCK,
                            EAC_R11_UNORM_BLOCK,
                            EAC_R11_SNORM_BLOCK,
                            E5B9G9R9_UFLOAT_PACK32,
                            D16_UNORM_S8_UINT,
                            BC7_UNORM_BLOCK,
                            BC7_SRGB_BLOCK,
                            BC6H_UFLOAT_BLOCK,
                            BC6H_SFLOAT_BLOCK,
                            BC5_UNORM_BLOCK,
                            BC5_SNORM_BLOCK,
                            BC4_UNORM_BLOCK,
                            BC4_SNORM_BLOCK,
                            BC3_UNORM_BLOCK,
                            BC3_SRGB_BLOCK,
                            BC2_UNORM_BLOCK,
                            BC2_SRGB_BLOCK,
                            BC1_RGBA_UNORM_BLOCK,
                            BC1_RGBA_SRGB_BLOCK,
                            BC1_RGB_UNORM_BLOCK,
                            BC1_RGB_SRGB_BLOCK,
                            B8G8R8A8_USCALED,
                            B8G8R8A8_UNORM,
                            B8G8R8A8_UINT,
                            B8G8R8A8_SSCALED,
                            B8G8R8A8_SRGB,
                            B8G8R8A8_SNORM,
                            B8G8R8A8_SINT,
                            B8G8R8_USCALED,
                            B8G8R8_UNORM,
                            B8G8R8_UINT,
                            B8G8R8_SSCALED,
                            B8G8R8_SRGB,
                            B8G8R8_SNORM,
                            B8G8R8_SINT,
                            B5G6R5_UNORM_PACK16,
                            B5G5R5A1_UNORM_PACK16,
                            B4G4R4A4_UNORM_PACK16,
                            B10G11R11_UFLOAT_PACK32,
                            ASTC_8X8_UNORM_BLOCK,
                            ASTC_8X8_SRGB_BLOCK,
                            ASTC_8X8_SFLOAT_BLOCK,
                            ASTC_8X6_UNORM_BLOCK,
                            ASTC_8X6_SRGB_BLOCK,
                            ASTC_8X6_SFLOAT_BLOCK,
                            ASTC_8X5_UNORM_BLOCK,
                            ASTC_8X5_SRGB_BLOCK,
                            ASTC_8X5_SFLOAT_BLOCK,
                            ASTC_6X6_UNORM_BLOCK,
                            ASTC_6X6_SRGB_BLOCK,
                            ASTC_6X6_SFLOAT_BLOCK,
                            ASTC_6X5_UNORM_BLOCK,
                            ASTC_6X5_SRGB_BLOCK,
                            ASTC_6X5_SFLOAT_BLOCK,
                            ASTC_5X5_UNORM_BLOCK,
                            ASTC_5X5_SRGB_BLOCK,
                            ASTC_5X5_SFLOAT_BLOCK,
                            ASTC_5X4_UNORM_BLOCK,
                            ASTC_5X4_SRGB_BLOCK,
                            ASTC_5X4_SFLOAT_BLOCK,
                            ASTC_4X4_UNORM_BLOCK,
                            ASTC_4X4_SRGB_BLOCK,
                            ASTC_4X4_SFLOAT_BLOCK,
                            ASTC_12X12_UNORM_BLOCK,
                            ASTC_12X12_SRGB_BLOCK,
                            ASTC_12X12_SFLOAT_BLOCK,
                            ASTC_12X10_UNORM_BLOCK,
                            ASTC_12X10_SRGB_BLOCK,
                            ASTC_12X10_SFLOAT_BLOCK,
                            ASTC_10X8_UNORM_BLOCK,
                            ASTC_10X8_SRGB_BLOCK,
                            ASTC_10X8_SFLOAT_BLOCK,
                            ASTC_10X6_UNORM_BLOCK,
                            ASTC_10X6_SRGB_BLOCK,
                            ASTC_10X6_SFLOAT_BLOCK,
                            ASTC_10X5_UNORM_BLOCK,
                            ASTC_10X5_SRGB_BLOCK,
                            ASTC_10X5_SFLOAT_BLOCK,
                            ASTC_10X10_UNORM_BLOCK,
                            ASTC_10X10_SRGB_BLOCK,
                            ASTC_10X10_SFLOAT_BLOCK,
                            A8B8G8R8_USCALED_PACK32,
                            A8B8G8R8_UNORM_PACK32,
                            A8B8G8R8_UINT_PACK32,
                            A8B8G8R8_SSCALED_PACK32,
                            A8B8G8R8_SRGB_PACK32,
                            A8B8G8R8_SNORM_PACK32,
                            A8B8G8R8_SINT_PACK32,
                            A8_UNORM,
                            A4R4G4B4_UNORM_PACK16,
                            A4B4G4R4_UNORM_PACK16,
                            A2R10G10B10_USCALED_PACK32,
                            A2R10G10B10_UNORM_PACK32,
                            A2R10G10B10_UINT_PACK32,
                            A2R10G10B10_SSCALED_PACK32,
                            A2R10G10B10_SNORM_PACK32,
                            A2R10G10B10_SINT_PACK32,
                            A2B10G10R10_USCALED_PACK32,
                            A2B10G10R10_UNORM_PACK32,
                            A2B10G10R10_UINT_PACK32,
                            A2B10G10R10_SSCALED_PACK32,
                            A2B10G10R10_SNORM_PACK32,
                            A2B10G10R10_SINT_PACK32,
                            A1R5G5B5_UNORM_PACK16 ->
                    throw new TextureException(
                            SafeResourceLoader.getStringFormatted(
                                    UNSUPPORTED_TEXTURE_FORMAT,
                                    GraphicsPlugin.getResourceBundle(),
                                    format.toString(),
                                    BACKEND_NAME));
        };
    }

    /** Private constructor so this is not instantiated. */
    private FormatMapperOpenGL() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
