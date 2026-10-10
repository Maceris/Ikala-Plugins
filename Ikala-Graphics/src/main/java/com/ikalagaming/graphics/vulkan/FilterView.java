package com.ikalagaming.graphics.vulkan;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;

/**
 * What the filter stage shows. The default runs the regular filter over the lit scene, and the rest
 * are debug views that show one g-buffer texture instead. The g-buffer views need the scene stage,
 * and show black without it.
 */
@Getter
@AllArgsConstructor
public enum FilterView {
    /** The regular filter over the lit scene. */
    DEFAULT("Default", RenderStage.Type.FILTER, "shaders/filters/default.frag"),
    /** The g-buffer base color. */
    BASE_COLOR(
            "Base color",
            RenderStage.Type.FILTER_BASE_COLOR,
            "shaders/filters/gbuffer_base_color.frag"),
    /** The g-buffer view space normals. */
    NORMAL("Normals", RenderStage.Type.FILTER_NORMAL, "shaders/filters/gbuffer_normal.frag"),
    /** The g-buffer view space tangents. */
    TANGENT("Tangents", RenderStage.Type.FILTER_TANGENT, "shaders/filters/gbuffer_tangent.frag"),
    /** The g-buffer material indices, a color per material. */
    MATERIAL(
            "Material index",
            RenderStage.Type.FILTER_MATERIAL,
            "shaders/filters/gbuffer_material.frag"),
    /** The g-buffer depth, on a log scale. */
    DEPTH("Depth", RenderStage.Type.FILTER_DEPTH, "shaders/filters/gbuffer_depth.frag"),
    /** Every g-buffer texture side by side, in vertical strips in the order above. */
    SPLIT("All (split)", RenderStage.Type.FILTER_SPLIT, "shaders/filters/gbuffer_split.frag");

    /** The name to show in menus. */
    private final @NonNull String displayName;

    /** The shader map slot that holds this view's shader. */
    private final @NonNull RenderStage.Type shaderType;

    /** The bundled fragment shader, used with the default filter vertex shader. */
    private final @NonNull String fragmentShader;
}
