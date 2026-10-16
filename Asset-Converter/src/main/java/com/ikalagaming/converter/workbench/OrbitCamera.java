package com.ikalagaming.converter.workbench;

import com.ikalagaming.graphics.scene.Camera;

import lombok.Getter;
import lombok.NonNull;
import org.joml.Matrix3d;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * Turns the camera around a point, the way model viewers do, alongside the free flying camera.
 * Turning, panning and zooming move the camera around the point; if the camera was flown somewhere
 * since, the point moves with it, staying the same distance in front. Plain math on the camera, so
 * it can be tested; the workbench drives it from the mouse.
 */
public class OrbitCamera {
    /** Radians turned per pixel dragged. */
    static final double TURN_PER_PIXEL = 0.005;

    /** How much one notch of the mouse wheel moves the camera closer. */
    static final double ZOOM_STEP = 0.85;

    /** The closest the camera gets to the point. */
    static final double MIN_DISTANCE = 0.01;

    /** How far the camera can look up or down, short of straight, which the camera clamps. */
    static final double MAX_PITCH = Math.PI / 2 - 0.01;

    /** The camera's field of view, vertically, for framing. */
    static final double FIELD_OF_VIEW = Math.toRadians(60);

    /**
     * The point turned around.
     *
     * @return The point. Don't change it.
     */
    @Getter private final Vector3d target = new Vector3d();

    /**
     * How far the camera is from the point.
     *
     * @return The distance.
     */
    @Getter private double distance = 10;

    /** Where this put the camera last, to notice it being flown elsewhere. */
    private final Vector3d placed = new Vector3d(Double.NaN, 0, 0);

    /**
     * The direction a camera with this rotation looks.
     *
     * @param pitch The pitch, positive looking down.
     * @param yaw The yaw.
     * @param out Where to put the direction.
     * @return The output.
     */
    static Vector3d forward(double pitch, double yaw, @NonNull Vector3d out) {
        return out.set(
                Math.cos(pitch) * Math.sin(yaw),
                -Math.sin(pitch),
                -Math.cos(pitch) * Math.cos(yaw));
    }

    /**
     * Follow a camera flown since this last placed it, keeping the point the same distance in front
     * of it.
     *
     * @param camera The camera.
     */
    public void follow(@NonNull Camera camera) {
        if (camera.getPosition().equals(placed, 1e-9)) {
            return;
        }
        forward(camera.getRotation().x, camera.getRotation().y, target)
                .mul(distance)
                .add(camera.getPosition());
        placed.set(camera.getPosition());
    }

    /**
     * Turn the camera around a pivot, which it needn't be looking at: it swings around the pivot as
     * it turns, so whatever was where on screen stays there relative to the pivot, with nothing
     * jumping to the middle. Afterward, panning and zooming work on a point as far in front of the
     * camera as the pivot is from it.
     *
     * @param camera The camera.
     * @param pivot What to turn around.
     * @param dx Pixels dragged right.
     * @param dy Pixels dragged down.
     */
    public void turn(@NonNull Camera camera, @NonNull Vector3dc pivot, double dx, double dy) {
        final double fromPitch = camera.getRotation().x;
        final double fromYaw = camera.getRotation().y;
        final double pitch = Math.clamp(fromPitch + dy * TURN_PER_PIXEL, -MAX_PITCH, MAX_PITCH);
        final double yaw = fromYaw + dx * TURN_PER_PIXEL;
        // Undo the old rotation and apply the new one to where the camera is from the pivot
        final Vector3d offset = new Vector3d(camera.getPosition()).sub(pivot);
        orientation(fromPitch, fromYaw).transpose().transform(offset);
        orientation(pitch, yaw).transform(offset);
        final Vector3d position = offset.add(pivot);
        camera.setRotation((float) pitch, (float) yaw);
        camera.setPosition(position.x, position.y, position.z);
        distance = Math.max(MIN_DISTANCE, position.distance(pivot));
        forward(pitch, yaw, target).mul(distance).add(camera.getPosition());
        placed.set(camera.getPosition());
    }

    /**
     * The rotation that turns -z, which an unturned camera looks along, to where a camera with this
     * pitch and yaw looks, as {@link #forward} gives it.
     *
     * @param pitch The pitch.
     * @param yaw The yaw.
     * @return The rotation.
     */
    static Matrix3d orientation(double pitch, double yaw) {
        return new Matrix3d().rotateY(-yaw).rotateX(-pitch);
    }

    /**
     * Slide the point and the camera across the view.
     *
     * @param camera The camera.
     * @param dx Pixels dragged right.
     * @param dy Pixels dragged down.
     * @param viewHeight The view's height in pixels, so the point keeps up with the mouse.
     */
    public void pan(@NonNull Camera camera, double dx, double dy, double viewHeight) {
        final double pitch = camera.getRotation().x;
        final double yaw = camera.getRotation().y;
        final double perPixel = 2 * distance * Math.tan(FIELD_OF_VIEW / 2) / viewHeight;
        final Vector3d forward = forward(pitch, yaw, new Vector3d());
        final Vector3d right = new Vector3d(Math.cos(yaw), 0, Math.sin(yaw));
        final Vector3d up = right.cross(forward, new Vector3d()).normalize();
        target.fma(-dx * perPixel, right).fma(dy * perPixel, up);
        place(camera, pitch, yaw);
    }

    /**
     * Move the camera toward or away from the point.
     *
     * @param camera The camera.
     * @param notches How far the wheel turned, positive to come closer.
     */
    public void zoom(@NonNull Camera camera, double notches) {
        distance = Math.max(MIN_DISTANCE, distance * Math.pow(ZOOM_STEP, notches));
        place(camera, camera.getRotation().x, camera.getRotation().y);
    }

    /**
     * Look at a sphere from the camera's current direction, from far enough away to see it whole.
     *
     * @param camera The camera.
     * @param center The sphere's center.
     * @param radius Its radius.
     */
    public void frame(@NonNull Camera camera, @NonNull Vector3dc center, double radius) {
        target.set(center);
        distance = Math.max(MIN_DISTANCE, radius / Math.sin(FIELD_OF_VIEW / 2) * 1.1);
        place(camera, camera.getRotation().x, camera.getRotation().y);
    }

    /**
     * Put the camera at the distance from the point, looking at it.
     *
     * @param camera The camera.
     * @param pitch The pitch.
     * @param yaw The yaw.
     */
    private void place(Camera camera, double pitch, double yaw) {
        final Vector3d position = forward(pitch, yaw, new Vector3d()).mul(-distance).add(target);
        camera.setRotation((float) pitch, (float) yaw);
        camera.setPosition(position.x, position.y, position.z);
        placed.set(camera.getPosition());
    }
}
