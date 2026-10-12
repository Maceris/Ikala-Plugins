package com.ikalagaming.graphics.vulkan;

import com.ikalagaming.graphics.MeshHandle;
import com.ikalagaming.graphics.scene.lights.LightRegistry;
import com.ikalagaming.graphics.scene.lights.LightTree;

import lombok.Getter;
import lombok.NonNull;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which point lights cast shadows this frame, and their cube maps. A {@link LightTree} picks what
 * the budget reaches; each map is six faces in a shared atlas of tiles, drawn from its
 * representative light, the brightest of the lights sharing it.
 *
 * <p>What doesn't move is drawn into a map's faces once and kept: a face is drawn again only when
 * its map is new, its light moved or reaches further, or a section or level of detail cell inside
 * it changed. At most {@link #MAX_FACE_REDRAWS_PER_FRAME} faces are drawn a frame, nearest the
 * front of the cut first; a new map casts no shadows until all its faces are drawn. What moves is
 * drawn into a second atlas every frame there is any in a map's reach.
 *
 * <p>Render thread only.
 */
public class PointShadows {

    /** The width and height of each atlas, in texels. */
    public static final int ATLAS_SIZE = 4096;

    /** The width and height of one cube face's tile, in texels. */
    public static final int TILE_SIZE = 512;

    /** How many tiles fit across an atlas. */
    public static final int TILES_ACROSS = ATLAS_SIZE / TILE_SIZE;

    /** Faces of a cube map. */
    public static final int FACES = 6;

    /** The most maps the atlas holds. */
    public static final int MAX_MAPS = TILES_ACROSS * TILES_ACROSS / FACES;

    /**
     * Texels drawn around each face beyond its 90 degrees, so the light stage's filter, which reads
     * two texels around a point, never reads the next tile.
     */
    public static final int GUARD_TEXELS = 3;

    /** How close to its light a face starts, in meters. */
    public static final float NEAR = 0.05f;

    /** The most faces drawn again in a frame, so a burst of changes is spread over frames. */
    public static final int MAX_FACE_REDRAWS_PER_FRAME = 12;

    /** The size of one map's entry for the light stage: six face matrices, a center, and info. */
    public static final int MAP_ENTRY_SIZE = FACES * 16 * Float.BYTES + 4 * Float.BYTES + 4 * 4;

    /** Where a map entry's center is, render space x, y, z and its range. */
    public static final int MAP_CENTER_OFFSET = FACES * 16 * Float.BYTES;

    /** Where a map entry's info is: its first tile, and whether it has a dynamic layer. */
    public static final int MAP_INFO_OFFSET = MAP_CENTER_OFFSET + 4 * Float.BYTES;

    /** Every face's bit. */
    private static final int ALL_FACES = (1 << FACES) - 1;

    /** Which way each face looks, +x, -x, +y, -y, +z, -z. */
    private static final Vector3f[] DIRECTIONS = {
        new Vector3f(1, 0, 0),
        new Vector3f(-1, 0, 0),
        new Vector3f(0, 1, 0),
        new Vector3f(0, -1, 0),
        new Vector3f(0, 0, 1),
        new Vector3f(0, 0, -1)
    };

    /** Each face's up, any that isn't along it. */
    private static final Vector3f[] UPS = {
        new Vector3f(0, 1, 0),
        new Vector3f(0, 1, 0),
        new Vector3f(0, 0, 1),
        new Vector3f(0, 0, 1),
        new Vector3f(0, 1, 0),
        new Vector3f(0, 1, 0)
    };

    /** One cube map. */
    private static final class CubeMap {
        /** The slot of the light it is drawn from. */
        int representative;

        /** Where it is drawn from, in the world. */
        final Vector3d center = new Vector3d();

        /** How far it reaches, in meters. */
        float range;

        /** The faces still to draw, one bit each. */
        int dirty = ALL_FACES;

        /** Whether every face has been drawn at least once. */
        boolean ready;

        /** The slots of the lights that use it. */
        int[] members = new int[0];

        /** The instances that move within its reach this frame. */
        final List<Dynamic> dynamics = new ArrayList<>();
    }

    /**
     * Something that moves, to draw into a map's dynamic layer.
     *
     * @param slot Its instance slot, for its model matrix.
     * @param position Where it is, in the world.
     * @param radius How far its meshes reach from there, in meters.
     * @param meshes Its standard meshes.
     */
    public record Dynamic(
            int slot,
            @NonNull Vector3dc position,
            double radius,
            @NonNull List<MeshHandle> meshes) {}

    /**
     * One face to draw this frame.
     *
     * @param map The map.
     * @param face The face, 0 to 5.
     */
    public record Face(int map, int face) {}

    /** The maps, by index; null where free. */
    private final CubeMap[] maps = new CubeMap[MAX_MAPS];

    /** Which map each shadow-casting light uses this frame, by light slot, ready maps only. */
    private final Map<Integer, Integer> mapOfLight = new HashMap<>();

    /** The maps in the order the cut put them, most important first. */
    private final List<Integer> order = new ArrayList<>();

    /** Faces drawn again since this was made. */
    @Getter private long faceRedraws;

    /** How many maps there are this frame. */
    @Getter private int mapCount;

    /** How many shadow-casting lights there were this frame, and how many maps reach. */
    @Getter private int castingLights;

    /** How many shadow-casting lights a map reaches this frame. */
    @Getter private int shadowedLights;

    /**
     * Pick this frame's maps from the shadow-casting lights that can reach what is seen.
     *
     * @param registry The scene's lights.
     * @param eye Where the scene is seen from, in the world.
     * @param planes The observer's frustum planes in render space, {@link FrustumPlanes#FLOATS}.
     * @param camera The camera's world position, the render space origin.
     * @param budget The most maps.
     * @param shareExtent The farthest apart lights may be and share a map, in meters.
     */
    public void plan(
            @NonNull LightRegistry registry,
            @NonNull Vector3dc eye,
            float @NonNull [] planes,
            @NonNull Vector3dc camera,
            int budget,
            double shareExtent) {
        final List<LightTree.Light> lights = new ArrayList<>();
        registry.visit(
                light -> {
                    if (!light.castsShadows() || light.intensity() <= 0) {
                        return;
                    }
                    final Vector3dc at = light.position();
                    final float range = light.range();
                    if (!sphereInside(
                            planes,
                            (float) (at.x() - camera.x()),
                            (float) (at.y() - camera.y()),
                            (float) (at.z() - camera.z()),
                            range)) {
                        return;
                    }
                    lights.add(
                            new LightTree.Light(
                                    light.slot(),
                                    at.x(),
                                    at.y(),
                                    at.z(),
                                    light.intensity(),
                                    range));
                });
        castingLights = lights.size();
        final Set<Integer> kept = new HashSet<>();
        for (CubeMap map : maps) {
            if (map != null) {
                kept.add(map.representative);
            }
        }
        final LightTree tree = new LightTree(lights);
        final List<LightTree.Node> cut =
                tree.cut(
                        new double[] {eye.x(), eye.y(), eye.z()},
                        Math.clamp(budget, 0, MAX_MAPS),
                        shareExtent,
                        kept);

        // Maps keep their tiles while their representative stays the same
        final boolean[] used = new boolean[MAX_MAPS];
        final int[] chosen = new int[cut.size()];
        for (int c = 0; c < cut.size(); ++c) {
            final int representative = tree.lights().get(cut.get(c).representative()).slot();
            chosen[c] = -1;
            for (int m = 0; m < MAX_MAPS; ++m) {
                if (maps[m] != null && !used[m] && maps[m].representative == representative) {
                    chosen[c] = m;
                    used[m] = true;
                    break;
                }
            }
        }
        for (int m = 0; m < MAX_MAPS; ++m) {
            if (!used[m]) {
                maps[m] = null;
            }
        }
        order.clear();
        mapOfLight.clear();
        shadowedLights = 0;
        for (int c = 0; c < cut.size(); ++c) {
            final LightTree.Node node = cut.get(c);
            final LightTree.Light center = tree.lights().get(node.representative());
            int m = chosen[c];
            if (m < 0) {
                m = freeMap();
                maps[m] = new CubeMap();
                maps[m].representative = center.slot();
            }
            final CubeMap map = maps[m];
            final float range = (float) node.range();
            if (!map.center.equals(center.x(), center.y(), center.z()) || map.range != range) {
                // Moved or reaching further: draw it all again
                map.center.set(center.x(), center.y(), center.z());
                map.range = range;
                map.dirty = ALL_FACES;
            }
            map.members = new int[node.lights().length];
            for (int i = 0; i < map.members.length; ++i) {
                map.members[i] = tree.lights().get(node.lights()[i]).slot();
                if (map.ready) {
                    mapOfLight.put(map.members[i], m);
                    shadowedLights += 1;
                }
            }
            order.add(m);
        }
        mapCount = cut.size();
    }

    /**
     * A free map index. There always is one, since the cut is no bigger than the atlas.
     *
     * @return The index.
     */
    private int freeMap() {
        for (int m = 0; m < MAX_MAPS; ++m) {
            if (maps[m] == null) {
                return m;
            }
        }
        throw new IllegalStateException("No free point shadow map");
    }

    /**
     * Whether a sphere is at least partly inside every plane.
     *
     * @param planes The planes.
     * @param x The center's x.
     * @param y Its y.
     * @param z Its z.
     * @param radius The radius.
     * @return True if it may be seen.
     */
    private static boolean sphereInside(
            float @NonNull [] planes, float x, float y, float z, float radius) {
        for (int i = 0; i < FrustumPlanes.FLOATS / 4; ++i) {
            final int base = i * 4;
            if (planes[base] * x + planes[base + 1] * y + planes[base + 2] * z + planes[base + 3]
                    < -radius) {
                return false;
            }
        }
        return true;
    }

    /**
     * Note that something that doesn't move changed inside a box, so every map reaching it draws
     * its faces again.
     *
     * @param min The box's minimum corner, in the world.
     * @param max The box's maximum corner, in the world.
     */
    public void invalidate(double @NonNull [] min, double @NonNull [] max) {
        for (CubeMap map : maps) {
            if (map == null) {
                continue;
            }
            double squared = 0;
            final double[] center = {map.center.x, map.center.y, map.center.z};
            for (int axis = 0; axis < 3; ++axis) {
                final double d =
                        Math.max(0, Math.max(min[axis] - center[axis], center[axis] - max[axis]));
                squared += d * d;
            }
            if (squared <= (double) map.range * map.range) {
                map.dirty = ALL_FACES;
            }
        }
    }

    /** Forget what every map holds, so all are drawn again. */
    public void invalidateAll() {
        for (CubeMap map : maps) {
            if (map != null) {
                map.dirty = ALL_FACES;
                map.ready = false;
            }
        }
    }

    /**
     * Note what moves this frame, so maps reaching it draw it into their dynamic layer.
     *
     * @param dynamics Everything that moves, with where it is.
     */
    public void setDynamics(@NonNull List<Dynamic> dynamics) {
        for (CubeMap map : maps) {
            if (map == null) {
                continue;
            }
            map.dynamics.clear();
            for (Dynamic dynamic : dynamics) {
                if (dynamic.position().distance(map.center) <= map.range + dynamic.radius()) {
                    map.dynamics.add(dynamic);
                }
            }
        }
    }

    /**
     * The faces to draw this frame, in the cut's order, up to {@link #MAX_FACE_REDRAWS_PER_FRAME}.
     *
     * @return The faces.
     */
    public List<Face> facesToDraw() {
        final List<Face> faces = new ArrayList<>();
        for (int m : order) {
            final CubeMap map = maps[m];
            for (int face = 0; face < FACES && faces.size() < MAX_FACE_REDRAWS_PER_FRAME; ++face) {
                if ((map.dirty & (1 << face)) != 0) {
                    faces.add(new Face(m, face));
                }
            }
        }
        return faces;
    }

    /**
     * Note that a face was drawn. A map with every face drawn is ready from next frame.
     *
     * @param face The face.
     */
    public void drawn(@NonNull Face face) {
        final CubeMap map = maps[face.map()];
        if (map == null) {
            return;
        }
        map.dirty &= ~(1 << face.face());
        if (map.dirty == 0) {
            map.ready = true;
        }
        faceRedraws += 1;
    }

    /**
     * The maps this frame, in the cut's order.
     *
     * @return Their indices.
     */
    public List<Integer> maps() {
        return order;
    }

    /**
     * Where a map is drawn from.
     *
     * @param map The map.
     * @return Its center, in the world.
     */
    public Vector3dc center(int map) {
        return maps[map].center;
    }

    /**
     * How far a map reaches.
     *
     * @param map The map.
     * @return Its range, in meters.
     */
    public float range(int map) {
        return maps[map].range;
    }

    /**
     * What moves within a map's reach this frame.
     *
     * @param map The map.
     * @return The instances.
     */
    public List<Dynamic> dynamics(int map) {
        return maps[map].dynamics;
    }

    /**
     * Which map a light uses this frame.
     *
     * @param slot The light's slot.
     * @return The map, or -1 if it casts no shadows this frame.
     */
    public int mapOf(int slot) {
        final Integer map = mapOfLight.get(slot);
        return map == null ? -1 : map;
    }

    /**
     * Where a map's face is in the atlas: its first texel, x and y, with y counting up like the
     * light stage's map coordinates.
     *
     * @param map The map.
     * @param face The face.
     * @return x and y, in texels.
     */
    public static int[] tileOrigin(int map, int face) {
        final int tile = map * FACES + face;
        return new int[] {(tile % TILES_ACROSS) * TILE_SIZE, (tile / TILES_ACROSS) * TILE_SIZE};
    }

    /**
     * A face's matrix, from render space to its tile's clip space: a perspective a little wider
     * than 90 degrees, by {@link #GUARD_TEXELS}, looking along the face's axis.
     *
     * @param map The map.
     * @param face The face.
     * @param camera The camera's world position, the render space origin.
     * @param dest Where the matrix goes.
     * @return dest.
     */
    public Matrix4f faceMatrix(
            int map, int face, @NonNull Vector3dc camera, @NonNull Matrix4f dest) {
        final CubeMap cube = maps[map];
        final Vector3f eye =
                new Vector3f(
                        (float) (cube.center.x - camera.x()),
                        (float) (cube.center.y - camera.y()),
                        (float) (cube.center.z - camera.z()));
        final float halfWidth = TILE_SIZE / (float) (TILE_SIZE - 2 * GUARD_TEXELS);
        final float fieldOfView = 2 * (float) Math.atan(halfWidth);
        final Vector3f target = new Vector3f(eye).add(DIRECTIONS[face]);
        return dest.setPerspective(fieldOfView, 1, NEAR, Math.max(cube.range, 2 * NEAR), true)
                .lookAt(eye, target, UPS[face]);
    }

    /**
     * Write this frame's map entries for the light stage, by map index.
     *
     * @param out Where they go, {@link #MAX_MAPS} entries of {@link #MAP_ENTRY_SIZE} bytes.
     * @param camera The camera's world position, the render space origin.
     */
    public void write(@NonNull ByteBuffer out, @NonNull Vector3dc camera) {
        final Matrix4f matrix = new Matrix4f();
        for (int m = 0; m < MAX_MAPS; ++m) {
            final CubeMap map = maps[m];
            if (map == null) {
                continue;
            }
            final int base = m * MAP_ENTRY_SIZE;
            for (int face = 0; face < FACES; ++face) {
                faceMatrix(m, face, camera, matrix).get(base + face * 16 * Float.BYTES, out);
            }
            out.putFloat(base + MAP_CENTER_OFFSET, (float) (map.center.x - camera.x()));
            out.putFloat(
                    base + MAP_CENTER_OFFSET + Float.BYTES, (float) (map.center.y - camera.y()));
            out.putFloat(
                    base + MAP_CENTER_OFFSET + 2 * Float.BYTES,
                    (float) (map.center.z - camera.z()));
            out.putFloat(base + MAP_CENTER_OFFSET + 3 * Float.BYTES, map.range);
            out.putInt(base + MAP_INFO_OFFSET, m * FACES);
            out.putInt(base + MAP_INFO_OFFSET + Integer.BYTES, map.dynamics.isEmpty() ? 0 : 1);
        }
    }
}
