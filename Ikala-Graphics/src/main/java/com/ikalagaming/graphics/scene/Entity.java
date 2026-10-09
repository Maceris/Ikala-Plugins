package com.ikalagaming.graphics.scene;

import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.graph.Model;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

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
     * @param animationState The new animation state.
     * @return The animation state.
     */
    @Setter private AnimationState animationState;

    /**
     * The position in world space, in double precision so it stays exact far from the origin.
     *
     * @return The position.
     */
    private final Vector3d position;

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
        position = new Vector3d();
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
     * Add to the rotation.
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
     * Set the position.
     *
     * @param x The new x position, in world space.
     * @param y The new y position, in world space.
     * @param z The new z position, in world space.
     */
    public final void setPosition(double x, double y, double z) {
        position.set(x, y, z);
    }

    /**
     * Set the rotation.
     *
     * @param x The x component of the rotation axis.
     * @param y The y component of the rotation axis.
     * @param z The z component of the rotation axis.
     * @param angle The angle in radians.
     */
    public void setRotation(float x, float y, float z, float angle) {
        rotation.fromAxisAngleRad(x, y, z, angle);
    }

    /**
     * Calculate the model matrix in render space, which is world space moved so that the origin is
     * at {@code origin}. The position is made relative in double precision before converting to
     * float, so entities near the origin stay exact however far they are from the world origin.
     *
     * @param origin The world position of the render space origin, usually the camera position.
     * @param dest Where to store the matrix.
     * @return The destination matrix.
     */
    public Matrix4f getRenderMatrix(@NonNull Vector3dc origin, @NonNull Matrix4f dest) {
        return renderMatrix(position, origin, rotation, scale, dest);
    }

    /**
     * Calculate a model matrix in render space, making the position relative to the origin in
     * double precision before converting to float.
     *
     * @param position The world position.
     * @param origin The world position of the render space origin.
     * @param rotation The rotation.
     * @param scale The uniform scale.
     * @param dest Where to store the matrix.
     * @return The destination matrix.
     */
    public static Matrix4f renderMatrix(
            @NonNull Vector3dc position,
            @NonNull Vector3dc origin,
            @NonNull Quaternionfc rotation,
            float scale,
            @NonNull Matrix4f dest) {
        return dest.translationRotateScale(
                (float) (position.x() - origin.x()),
                (float) (position.y() - origin.y()),
                (float) (position.z() - origin.z()),
                rotation.x(),
                rotation.y(),
                rotation.z(),
                rotation.w(),
                scale,
                scale,
                scale);
    }
}
