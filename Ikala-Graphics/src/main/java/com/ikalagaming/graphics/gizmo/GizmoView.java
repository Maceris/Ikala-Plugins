package com.ikalagaming.graphics.gizmo;

import com.ikalagaming.graphics.scene.Camera;
import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector2f;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * How the scene maps to the screen for one frame: the camera, its projection and the rectangle the
 * scene is drawn in. Points go to the screen and the mouse comes back as a ray, both in render
 * space, the world less the camera's position, so floats keep their precision far from the origin.
 *
 * @param projectionView From render space to clip space.
 * @param camera The camera's position, in the world.
 * @param left The left edge of the scene on the screen, in pixels.
 * @param top Its top edge.
 * @param width Its width, in pixels.
 * @param height Its height.
 * @param ySign 1 if clip space y points down the screen as it goes up, -1 if the projection
 *     already flips it.
 */
public record GizmoView(
        @NonNull Matrix4fc projectionView,
        @NonNull Vector3dc camera,
        float left,
        float top,
        float width,
        float height,
        float ySign) {

    /**
     * The view of a scene, drawn in a rectangle of the screen.
     *
     * @param scene The scene.
     * @param left The left edge of the scene on the screen, in pixels.
     * @param top Its top edge.
     * @param width Its width, in pixels.
     * @param height Its height.
     * @return The view.
     */
    public static GizmoView of(
            @NonNull Scene scene, float left, float top, float width, float height) {
        Camera camera = scene.getCamera();
        // The view matrix only turns; positions are taken from the camera
        Matrix4f projectionView =
                new Matrix4f(scene.getProjection().getProjectionMatrix())
                        .mul(camera.getViewMatrix());
        // The projection's y may point either way; higher points must come out higher on screen
        Vector4f up =
                projectionView.transform(
                        new Vector4f(
                                camera.getInvViewMatrix()
                                        .transformDirection(new Vector3f(0, 1, 0)),
                                0));
        return new GizmoView(
                projectionView,
                new Vector3d(camera.getPosition()),
                left,
                top,
                width,
                height,
                up.y >= 0 ? 1 : -1);
    }

    /**
     * Where a point in the world shows on the screen.
     *
     * @param point The point, in the world.
     * @param out Receives where it shows, in pixels.
     * @return False if it is behind the camera, when out is left undefined.
     */
    public boolean toScreen(@NonNull Vector3dc point, @NonNull Vector2f out) {
        return toScreen(
                (float) (point.x() - camera.x()),
                (float) (point.y() - camera.y()),
                (float) (point.z() - camera.z()),
                out);
    }

    /**
     * Where a point in render space shows on the screen.
     *
     * @param x The point's x, relative to the camera.
     * @param y Its y.
     * @param z Its z.
     * @param out Receives where it shows, in pixels.
     * @return False if it is behind the camera, when out is left undefined.
     */
    public boolean toScreen(float x, float y, float z, @NonNull Vector2f out) {
        Vector4f clip = projectionView.transform(new Vector4f(x, y, z, 1));
        if (clip.w <= 1e-6f) {
            return false;
        }
        float ndcX = clip.x / clip.w;
        float ndcY = clip.y / clip.w;
        out.set(left + (ndcX + 1) / 2 * width, top + (1 - ySign * ndcY) / 2 * height);
        return true;
    }

    /**
     * The ray from the camera through a point on the screen, in render space: it starts at the
     * camera, which is the origin there.
     *
     * @param screenX The point's x, in pixels.
     * @param screenY Its y.
     * @param direction Receives the ray's direction, unit length.
     * @return direction.
     */
    public Vector3f ray(float screenX, float screenY, @NonNull Vector3f direction) {
        float ndcX = (screenX - left) / width * 2 - 1;
        float ndcY = ySign * (1 - (screenY - top) / height * 2);
        // Any depth between the near and far planes lies on the ray, whichever way depth runs
        Vector4f point =
                new Matrix4f(projectionView).invert().transform(new Vector4f(ndcX, ndcY, 0.5f, 1));
        return direction.set(point.x / point.w, point.y / point.w, point.z / point.w).normalize();
    }

    /**
     * How long something in the world must be to look a number of pixels long at a distance from
     * the camera, so a gizmo stays the same size on the screen as the camera moves.
     *
     * @param point Where it is, in render space.
     * @param pixels How long it should look.
     * @return The length, in world units.
     */
    public float worldSize(@NonNull Vector3f point, float pixels) {
        // Two points the length apart across the view at that depth, measured on the screen
        Vector2f a = new Vector2f();
        Vector2f b = new Vector2f();
        Vector3f across = ray(left + width / 2, top + height / 2, new Vector3f());
        Vector3f side = new Vector3f(across).cross(0, 1, 0);
        if (side.lengthSquared() < 1e-6f) {
            side.set(1, 0, 0);
        }
        side.normalize();
        if (!toScreen(point.x, point.y, point.z, a)
                || !toScreen(point.x + side.x, point.y + side.y, point.z + side.z, b)) {
            return 0;
        }
        float perUnit = a.distance(b);
        return perUnit <= 1e-6f ? 0 : pixels / perUnit;
    }
}
