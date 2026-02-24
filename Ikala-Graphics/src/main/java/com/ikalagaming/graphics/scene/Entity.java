package com.ikalagaming.graphics.scene;

import com.ikalagaming.graphics.frontend.Material;
import com.ikalagaming.graphics.graph.Model;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Something that is part of the 3D scene. */
@Getter
public class Entity {
    /** Numbers for a unique entity name. */
    public static final AtomicInteger NEXT_ID = new AtomicInteger();

    /**
     * A unique ID.
     *
     * @return The unique ID.
     */
    private final String entityID;

    /**
     * The model that this entity is an instance of.
     *
     * @return The model this belongs to.
     */
    private final Model model;

    /**
     * Animation state associated with the entity.
     *
     * @param animationData The new animation state.
     * @return The animation state.
     */
    private AnimationState animationState;

    /**
     * The combined translation, rotation, and scale transformations.
     *
     * @return The model matrix.
     */
    private final Matrix4f modelMatrix;

    /**
     * The transformation matrix.
     *
     * @return The position matrix.
     */
    private final Vector3f position;

    /**
     * The rotation, as a quaternion to prevent gimbal lock.
     *
     * @return The rotation.
     */
    private final Quaternionf rotation;

    /**
     * Used to modify rotations. We just keep around an instance to avoid object creation every time
     * we rotate a model.
     */
    private final Quaternionf delta;

    /**
     * The scale factor.
     *
     * @param scale The new scale.
     * @return The scale.
     */
    @Setter private float scale;

    /**
     * Used to select an alternative material for the meshes of a model. This must be the same size
     * as the number of meshes. The values are either null (no override) or the material to use as
     * an override. If these are not stored in the material cache, they will be ignored.
     *
     * <p>The textures can't be overwritten by these, only the material properties. However,
     * textures can be disabled by using a material with no texture and providing a base color.
     */
    private final List<Material> materialOverrides;

    /**
     * Create a new entity.
     *
     * @param id The ID of the entity.
     * @param model The model associated with this entity.
     */
    public Entity(@NonNull String id, @NonNull Model model) {
        entityID = id;
        this.model = model;
        modelMatrix = new Matrix4f();
        position = new Vector3f();
        rotation = new Quaternionf();
        delta = new Quaternionf();
        scale = 1;
        materialOverrides = new ArrayList<>();
        model.getMeshDataList().forEach(ignored -> materialOverrides.add(null));
    }

    /**
     * Set the material override for a particular mesh.
     *
     * @param material The material to use.
     * @param meshIndex The index of the mesh to set.
     * @throws IndexOutOfBoundsException If meshIndex is &lt; 0 or &gt;= the number of meshes in the
     *     model.
     */
    public void setMaterialOverride(@NonNull Material material, int meshIndex) {
        materialOverrides.set(meshIndex, material);
    }

    /**
     * Add to the rotation. {@link #updateModelMatrix()} must be called once transformations are
     * done, or they won't be reflected.
     *
     * @param x The x component of the rotation axis.
     * @param y The y component of the rotation axis.
     * @param z The z component of the rotation axis.
     * @param angle The angle in radians.
     */
    public void addRotation(float x, float y, float z, float angle) {
        delta.fromAxisAngleRad(x, y, z, angle);
        delta.mul(rotation, rotation);
    }

    /**
     * Set the position. {@link #updateModelMatrix()} must be called once transformations are done,
     * or they won't be reflected.
     *
     * @param x The new x position.
     * @param y The new y position.
     * @param z The new z position.
     */
    public final void setPosition(float x, float y, float z) {
        position.set(x, y, z);
    }

    /**
     * Set the rotation. {@link #updateModelMatrix()} must be called once transformations are done,
     * or they won't be reflected.
     *
     * @param x The x component of the rotation axis.
     * @param y The y component of the rotation axis.
     * @param z The z component of the rotation axis.
     * @param angle The angle in radians.
     */
    public void setRotation(float x, float y, float z, float angle) {
        rotation.fromAxisAngleRad(x, y, z, angle);
    }

    /** Update the model matrix based on the current position, rotation, and scale. */
    public void updateModelMatrix() {
        modelMatrix.translationRotateScale(position, rotation, scale);
    }
}
