package com.ikalagaming.characters.editor;

import com.ikalagaming.graphics.scene.Camera;

import lombok.Getter;
import lombok.NonNull;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * Turns the camera around a point, the way model viewers do, alongside the free flying camera:
 * turning, panning and zooming move the camera around the point. If the camera was flown somewhere
 * since, the point moves with it, staying the same distance in front. A smaller copy of the asset
 * workbench's, which always turns around the point.
 *
 * @author Ches Burks
 */
public class OrbitCamera {
    /** Radians turned per pixel dragged. */
    static final double TURN_PER_PIXEL = 0.005;

    /** How much one notch of the mouse wheel moves the camera closer. */
    static final double ZOOM_STEP = 0.85;

    /** The closest the camera gets to the point, in meters. */
    static final double MIN_DISTANCE = 0.3;

    /** The farthest the camera gets from the point, in meters. */
    static final double MAX_DISTANCE = 40;

    /** How far the camera can look up or down, short of straight, which the camera clamps. */
    static final double MAX_PITCH = Math.PI / 2 - 0.01;

    /** The camera's field of view, vertically. */
    static final double FIELD_OF_VIEW = Math.toRadians(60);

    /** How much room to leave around what is framed, as a multiple of its size. */
    static final double FRAME_MARGIN = 1.15;

    /** How quickly a glide closes in, per second: the gap shrinks by e each 1 / this seconds. */
    static final double GLIDE_RATE = 8;

    /** How close a glide must get to stop, in meters. */
    static final double GLIDE_DONE = 1e-3;

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
    @Getter private double distance = 4;

    /** Where this put the camera last, to notice it being flown elsewhere. */
    private final Vector3d placed = new Vector3d(Double.NaN, 0, 0);

    /** Where a glide is taking the point. */
    private final Vector3d goal = new Vector3d();

    /** How far from the point a glide is taking the camera. */
    private double goalDistance;

    /** Whether a glide is under way. */
    private boolean gliding;

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
        gliding = false;
        forward(camera.getRotation().x, camera.getRotation().y, target)
                .mul(distance)
                .add(camera.getPosition());
        placed.set(camera.getPosition());
    }

    /**
     * Look at a point from a direction and distance.
     *
     * @param camera The camera.
     * @param point What to look at.
     * @param pitch The pitch, positive looking down.
     * @param yaw The yaw, 0 looking along -z.
     * @param newDistance How far away to be.
     */
    public void look(
            @NonNull Camera camera,
            @NonNull Vector3dc point,
            double pitch,
            double yaw,
            double newDistance) {
        target.set(point);
        distance = Math.clamp(newDistance, MIN_DISTANCE, MAX_DISTANCE);
        place(camera, pitch, yaw);
    }

    /**
     * Turn the camera around the point.
     *
     * @param camera The camera.
     * @param dx Pixels dragged right.
     * @param dy Pixels dragged down.
     */
    public void turn(@NonNull Camera camera, double dx, double dy) {
        final double pitch =
                Math.clamp(camera.getRotation().x + dy * TURN_PER_PIXEL, -MAX_PITCH, MAX_PITCH);
        final double yaw = camera.getRotation().y + dx * TURN_PER_PIXEL;
        place(camera, pitch, yaw);
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
        gliding = false;
        place(camera, pitch, yaw);
    }

    /**
     * Move the camera toward or away from the point.
     *
     * @param camera The camera.
     * @param notches How far the wheel turned, positive to come closer.
     */
    public void zoom(@NonNull Camera camera, double notches) {
        distance = Math.clamp(distance * Math.pow(ZOOM_STEP, notches), MIN_DISTANCE, MAX_DISTANCE);
        gliding = false;
        place(camera, camera.getRotation().x, camera.getRotation().y);
    }

    /**
     * Start gliding to frame a sphere from the camera's current direction, from far enough away to
     * see it whole. {@link #glide} moves the camera there over the next frames; panning, zooming or
     * flying stops it.
     *
     * @param center The sphere's center.
     * @param radius Its radius.
     */
    public void glideTo(@NonNull Vector3dc center, double radius) {
        goal.set(center);
        goalDistance =
                Math.clamp(
                        radius / Math.sin(FIELD_OF_VIEW / 2) * FRAME_MARGIN,
                        MIN_DISTANCE,
                        MAX_DISTANCE);
        gliding = true;
    }

    /**
     * Move the camera on toward where a glide is taking it.
     *
     * @param camera The camera.
     * @param seconds How long since the last frame.
     */
    public void glide(@NonNull Camera camera, double seconds) {
        if (!gliding) {
            return;
        }
        final double step = 1 - Math.exp(-seconds * GLIDE_RATE);
        target.lerp(goal, step);
        distance += (goalDistance - distance) * step;
        if (target.distance(goal) < GLIDE_DONE && Math.abs(goalDistance - distance) < GLIDE_DONE) {
            target.set(goal);
            distance = goalDistance;
            gliding = false;
        }
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
