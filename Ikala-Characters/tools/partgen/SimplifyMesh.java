import com.ikalagaming.graphics.lod.LodMesh;
import com.ikalagaming.graphics.lod.Simplifier;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reduce a fitted garment to its triangle budget with Ikala-Graphics' level of detail simplifier
 * (after Liu, Zhang and Yuksel, <i>Simplifying Textured Triangle Meshes in the Wild</i>), which
 * shape_layer.py runs in place of Blender's collapse decimation: it leaves far fewer sliver
 * triangles, and slivers fold over when the garment is pushed out of the body or carried to another
 * body type, leaving holes. Run with the Java source launcher:
 *
 * <pre>
 * java -cp &lt;Ikala-Graphics jar&gt;;&lt;Ikala-Core jar&gt; SimplifyMesh.java in.bin out.bin 3000
 * </pre>
 *
 * <p>Both files are little endian: the vertex count and triangle count as ints, then x, y, z floats
 * per vertex, then three vertex indices per triangle. Vertices in the same place are welded. No
 * virtual edges are added, so a sleeve never joins the side it hangs beside.
 */
public final class SimplifyMesh {

    /** Bytes in a count, an index or a coordinate. */
    private static final int WORD = 4;

    /** Coordinates in a position, and corners in a triangle. */
    private static final int THREE = 3;

    private SimplifyMesh() {}

    /**
     * Reduce a mesh.
     *
     * @param args The input file, the output file and the triangle budget.
     * @throws IOException If a file can't be read or written.
     */
    public static void main(String[] args) throws IOException {
        ByteBuffer in =
                ByteBuffer.wrap(Files.readAllBytes(Path.of(args[0])))
                        .order(ByteOrder.LITTLE_ENDIAN);
        int target = Integer.parseInt(args[2]);
        int vertices = in.getInt();
        int triangles = in.getInt();
        LodMesh.Builder builder = LodMesh.builder(new double[THREE]);
        int[] welded = new int[vertices];
        for (int v = 0; v < vertices; ++v) {
            welded[v] = builder.vertex(in.getFloat(), in.getFloat(), in.getFloat());
        }
        for (int t = 0; t < triangles; ++t) {
            builder.triangle(welded[in.getInt()], welded[in.getInt()], welded[in.getInt()], 0);
        }
        LodMesh mesh = builder.build();
        Simplifier.Options options = Simplifier.Options.toCount(target, 0);
        // Nothing maps the result back to the input, so no history
        options =
                new Simplifier.Options(
                        options.targetTriangles(),
                        options.virtualEdgeRadius(),
                        options.maxError(),
                        false,
                        options.keepOpen(),
                        options.seams(),
                        options.planes(),
                        options.cell(),
                        options.borders(),
                        options.sharers(),
                        options.othersAlong());
        Simplifier.Result result = Simplifier.simplify(mesh, options);
        LodMesh out = result.mesh();
        System.out.printf(
                "[simplify] %d -> %d triangles, error %.5f m, %d flips and %d links rejected%n",
                mesh.getTriangleCount(),
                out.getTriangleCount(),
                result.error(),
                result.rejectedFlips(),
                result.rejectedLinks());

        ByteBuffer bytes =
                ByteBuffer.allocate(
                                2 * WORD
                                        + (out.getVertexCount() + out.getTriangleCount())
                                                * THREE
                                                * WORD)
                        .order(ByteOrder.LITTLE_ENDIAN);
        bytes.putInt(out.getVertexCount()).putInt(out.getTriangleCount());
        for (int v = 0; v < out.getVertexCount(); ++v) {
            for (int axis = 0; axis < THREE; ++axis) {
                bytes.putFloat(out.position(v, axis));
            }
        }
        for (int t = 0; t < out.getTriangleCount(); ++t) {
            for (int corner = 0; corner < THREE; ++corner) {
                bytes.putInt(out.corner(t, corner));
            }
        }
        Files.write(Path.of(args[1]), bytes.array());
    }
}
