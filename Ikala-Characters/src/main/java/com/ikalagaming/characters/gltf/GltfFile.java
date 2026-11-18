package com.ikalagaming.characters.gltf;

import com.ikalagaming.util.FileUtils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import lombok.Getter;
import lombok.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * A glTF 2.0 file, binary ({@code .glb}) or text ({@code .gltf} with its buffers beside it or
 * embedded), with the accessor reads importers need. Only what skinned characters use is supported:
 * no sparse accessors, and no extensions that change how data is read.
 *
 * @author Ches Burks
 */
public final class GltfFile {
    /** The binary container's magic number, "glTF". */
    private static final int GLB_MAGIC = 0x46546C67;

    /** The binary container's JSON chunk type. */
    private static final int CHUNK_JSON = 0x4E4F534A;

    /** The binary container's binary chunk type. */
    private static final int CHUNK_BIN = 0x004E4942;

    /** The field for where a buffer view or accessor starts, in bytes. */
    private static final String BYTE_OFFSET = "byteOffset";

    /** The field for a buffer view's bytes from one element to the next. */
    private static final String BYTE_STRIDE = "byteStride";

    /**
     * The most values (elements times components) one accessor may hold, so a forged count can't
     * allocate more than 256 MB, even for an accessor with no data behind it; 2^26, far more than
     * any character needs.
     */
    static final long MAX_ACCESSOR_VALUES = 1L << 26;

    /** What embedded data URIs start with, up to the base64 data. */
    private static final String DATA_URI_MARKER = ";base64,";

    /** Signed byte components. */
    public static final int BYTE = 5120;

    /** Unsigned byte components. */
    public static final int UNSIGNED_BYTE = 5121;

    /** Signed short components. */
    public static final int SHORT = 5122;

    /** Unsigned short components. */
    public static final int UNSIGNED_SHORT = 5123;

    /** Unsigned int components. */
    public static final int UNSIGNED_INT = 5125;

    /** Float components. */
    public static final int FLOAT = 5126;

    /** The file's JSON. */
    @Getter private final JsonObject json;

    /** The file's buffers, little endian, in order. */
    private final List<ByteBuffer> buffers;

    /** The folder the file is in, for external images. */
    @Getter private final Path folder;

    /**
     * Wrap parsed parts.
     *
     * @param json The JSON.
     * @param buffers The buffers.
     * @param folder The folder the file is in.
     */
    private GltfFile(JsonObject json, List<ByteBuffer> buffers, Path folder) {
        this.json = json;
        this.buffers = buffers;
        this.folder = folder;
    }

    /**
     * Read a file.
     *
     * @param path The file, {@code .glb} or {@code .gltf}.
     * @return The file.
     * @throws IOException If it can't be read or isn't valid glTF.
     */
    public static GltfFile read(@NonNull Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        Path folder = path.toAbsolutePath().getParent();
        ByteBuffer data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if (bytes.length >= 12 && data.getInt(0) == GLB_MAGIC) {
            return readBinary(data, folder);
        }
        JsonObject json = parseJson(new String(bytes, StandardCharsets.UTF_8));
        return new GltfFile(json, loadBuffers(json, null, folder), folder);
    }

    /**
     * Parse the file's JSON, which must be an object.
     *
     * @param text The JSON text.
     * @return The object.
     * @throws IOException If it isn't valid JSON or isn't an object.
     */
    private static JsonObject parseJson(String text) throws IOException {
        try {
            JsonElement parsed = JsonParser.parseString(text);
            if (!parsed.isJsonObject()) {
                throw new IOException("The glTF JSON isn't an object");
            }
            return parsed.getAsJsonObject();
        } catch (JsonParseException e) {
            throw new IOException("The glTF JSON can't be parsed: " + e.getMessage(), e);
        }
    }

    /**
     * Read a binary container: a JSON chunk, then an optional binary chunk.
     *
     * @param data The whole file.
     * @param folder The folder it is in.
     * @return The file.
     * @throws IOException If the container is malformed.
     */
    private static GltfFile readBinary(ByteBuffer data, Path folder) throws IOException {
        if (data.getInt(4) != 2) {
            throw new IOException("Only glTF version 2 is supported, not " + data.getInt(4));
        }
        int offset = 12;
        JsonObject json = null;
        ByteBuffer binary = null;
        while (offset + 8 <= data.limit()) {
            int length = data.getInt(offset);
            int type = data.getInt(offset + 4);
            if (length < 0) {
                throw new IOException("A chunk has a negative length, " + length);
            }
            if (offset + 8L + length > data.limit()) {
                throw new IOException("A chunk runs past the end of the file");
            }
            ByteBuffer chunk = data.slice(offset + 8, length).order(ByteOrder.LITTLE_ENDIAN);
            if (type == CHUNK_JSON) {
                byte[] text = new byte[length];
                chunk.get(0, text);
                json = parseJson(new String(text, StandardCharsets.UTF_8));
            } else if (type == CHUNK_BIN && binary == null) {
                binary = chunk;
            }
            // Chunks are padded to 4 bytes; in long math, as the padding can pass the end
            long next = offset + 8L + ((length + 3L) & ~3L);
            if (next > data.limit()) {
                break;
            }
            offset = (int) next;
        }
        if (json == null) {
            throw new IOException("The file has no JSON chunk");
        }
        return new GltfFile(json, loadBuffers(json, binary, folder), folder);
    }

    /**
     * Load every buffer: the binary chunk for the first buffer with no URI, embedded data URIs, or
     * files beside the glTF file.
     *
     * @param json The JSON.
     * @param binary The binary chunk, or null.
     * @param folder The folder the file is in.
     * @return The buffers.
     * @throws IOException If a buffer can't be loaded.
     */
    private static List<ByteBuffer> loadBuffers(JsonObject json, ByteBuffer binary, Path folder)
            throws IOException {
        List<ByteBuffer> buffers = new ArrayList<>();
        for (JsonElement element : array(json, "buffers")) {
            JsonObject buffer = element.getAsJsonObject();
            if (!buffer.has("uri")) {
                if (binary == null) {
                    throw new IOException("A buffer has no URI and there is no binary chunk");
                }
                buffers.add(binary);
            } else {
                buffers.add(
                        ByteBuffer.wrap(uriBytes(buffer.get("uri").getAsString(), folder))
                                .order(ByteOrder.LITTLE_ENDIAN));
            }
        }
        return buffers;
    }

    /**
     * Read what a URI points at: embedded base64 data, or a file beside the glTF file.
     *
     * @param uri The URI.
     * @param folder The folder the file is in.
     * @return The bytes.
     * @throws IOException If it can't be read.
     */
    private static byte[] uriBytes(String uri, Path folder) throws IOException {
        int marker = uri.indexOf(DATA_URI_MARKER);
        if (uri.startsWith("data:") && marker > 0) {
            return Base64.getDecoder().decode(uri.substring(marker + DATA_URI_MARKER.length()));
        }
        String file = percentDecode(uri);
        Path path =
                FileUtils.resolveInside(folder, file)
                        .orElseThrow(
                                () ->
                                        new IOException(
                                                "The URI "
                                                        + uri
                                                        + " leaves the folder of the glTF file"));
        return Files.readAllBytes(path);
    }

    /**
     * Decode the %XX escapes in a relative URI. Unlike form decoding, + stays a plus sign.
     *
     * @param uri The URI.
     * @return The decoded text.
     * @throws IOException If an escape is cut short or isn't hexadecimal.
     */
    static String percentDecode(String uri) throws IOException {
        if (uri.indexOf('%') < 0) {
            return uri;
        }
        byte[] text = uri.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream(text.length);
        for (int i = 0; i < text.length; ++i) {
            if (text[i] != '%') {
                out.write(text[i]);
                continue;
            }
            int high = i + 2 < text.length ? Character.digit(text[i + 1], 16) : -1;
            int low = i + 2 < text.length ? Character.digit(text[i + 2], 16) : -1;
            if (high < 0 || low < 0) {
                throw new IOException("A broken % escape in the URI " + uri);
            }
            out.write(high << 4 | low);
            i += 2;
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    /**
     * A top-level array, or an empty one if the file has none.
     *
     * @param object The object.
     * @param name The array's name.
     * @return The array.
     */
    static JsonArray array(JsonObject object, String name) {
        return object.has(name) ? object.getAsJsonArray(name) : new JsonArray();
    }

    /**
     * One element of a top-level array.
     *
     * @param name The array's name, like "nodes".
     * @param index The element's index.
     * @return The element.
     * @throws IllegalArgumentException If there is no such element.
     */
    public JsonObject get(@NonNull String name, int index) {
        JsonArray items = array(json, name);
        if (index < 0 || index >= items.size()) {
            throw new IllegalArgumentException("There is no " + name + "[" + index + "]");
        }
        return items.get(index).getAsJsonObject();
    }

    /**
     * How many elements a top-level array has.
     *
     * @param name The array's name.
     * @return The count.
     */
    public int count(@NonNull String name) {
        return array(json, name).size();
    }

    /**
     * How many components an accessor type has.
     *
     * @param type The type, like "VEC3".
     * @return The component count.
     */
    static int components(String type) {
        return switch (type) {
            case "SCALAR" -> 1;
            case "VEC2" -> 2;
            case "VEC3" -> 3;
            case "VEC4", "MAT2" -> 4;
            case "MAT3" -> 9;
            case "MAT4" -> 16;
            default -> throw new IllegalArgumentException("Unknown accessor type " + type);
        };
    }

    /**
     * The size of a component type, in bytes.
     *
     * @param componentType The component type.
     * @return The size.
     */
    static int componentSize(int componentType) {
        return switch (componentType) {
            case BYTE, UNSIGNED_BYTE -> 1;
            case SHORT, UNSIGNED_SHORT -> 2;
            case UNSIGNED_INT, FLOAT -> 4;
            default ->
                    throw new IllegalArgumentException("Unknown component type " + componentType);
        };
    }

    /**
     * How many components each element of an accessor has.
     *
     * @param accessor The accessor's index.
     * @return The component count.
     */
    public int components(int accessor) {
        return components(get("accessors", accessor).get("type").getAsString());
    }

    /**
     * How many elements an accessor has.
     *
     * @param accessor The accessor's index.
     * @return The element count.
     */
    public int count(int accessor) {
        return get("accessors", accessor).get("count").getAsInt();
    }

    /**
     * Read an accessor as floats. Normalized integers become 0 to 1 (or -1 to 1), as glTF says;
     * other integers keep their values.
     *
     * @param accessor The accessor's index.
     * @return The values, element by element.
     * @throws IllegalArgumentException If the accessor can't be read.
     */
    public float[] floats(int accessor) {
        Reader reader = reader(accessor);
        float[] values = new float[reader.count * reader.components];
        for (int e = 0; e < reader.count; ++e) {
            for (int c = 0; c < reader.components; ++c) {
                values[e * reader.components + c] = reader.floatAt(e, c);
            }
        }
        return values;
    }

    /**
     * Read an accessor as unsigned integers, like indices and joints.
     *
     * @param accessor The accessor's index.
     * @return The values, element by element.
     * @throws IllegalArgumentException If the accessor can't be read or holds floats.
     */
    public int[] ints(int accessor) {
        Reader reader = reader(accessor);
        if (reader.componentType == FLOAT) {
            throw new IllegalArgumentException("Accessor " + accessor + " holds floats");
        }
        int[] values = new int[reader.count * reader.components];
        for (int e = 0; e < reader.count; ++e) {
            for (int c = 0; c < reader.components; ++c) {
                values[e * reader.components + c] = reader.intAt(e, c);
            }
        }
        return values;
    }

    /**
     * The bytes of a buffer view, like an embedded image.
     *
     * @param bufferView The buffer view's index.
     * @return A copy of its bytes.
     */
    public byte[] bufferViewBytes(int bufferView) {
        View view = view(bufferView);
        byte[] bytes = new byte[view.length];
        view.buffer.get(view.offset, bytes);
        return bytes;
    }

    /**
     * Look up a buffer view, checking it lies inside its buffer.
     *
     * @param bufferView The buffer view's index.
     * @return The view.
     * @throws IllegalArgumentException If there is no such view, or it leaves its buffer.
     */
    private View view(int bufferView) {
        JsonObject view = get("bufferViews", bufferView);
        int bufferIndex = view.get("buffer").getAsInt();
        if (bufferIndex < 0 || bufferIndex >= buffers.size()) {
            throw new IllegalArgumentException(
                    "Buffer view " + bufferView + " names the missing buffer " + bufferIndex);
        }
        ByteBuffer buffer = buffers.get(bufferIndex);
        long offset = view.has(BYTE_OFFSET) ? view.get(BYTE_OFFSET).getAsLong() : 0;
        long length = view.get("byteLength").getAsLong();
        // Subtracted, so huge values can't wrap past the check
        if (offset < 0
                || length < 0
                || offset > buffer.limit()
                || length > buffer.limit() - offset) {
            throw new IllegalArgumentException(
                    "Buffer view " + bufferView + " runs past its buffer");
        }
        int stride = view.has(BYTE_STRIDE) ? view.get(BYTE_STRIDE).getAsInt() : 0;
        if (stride < 0) {
            throw new IllegalArgumentException(
                    "Buffer view " + bufferView + " has a negative stride");
        }
        return new View(buffer, (int) offset, (int) length, stride);
    }

    /**
     * Read the bytes an external image URI points at.
     *
     * @param uri The image's URI.
     * @return The bytes.
     * @throws IOException If it can't be read.
     */
    public byte[] uriBytes(@NonNull String uri) throws IOException {
        return uriBytes(uri, folder);
    }

    /**
     * Set up a reader for an accessor.
     *
     * @param accessor The accessor's index.
     * @return The reader.
     */
    private Reader reader(int accessor) {
        JsonObject object = get("accessors", accessor);
        if (object.has("sparse")) {
            throw new IllegalArgumentException("Sparse accessors aren't supported");
        }
        int componentType = object.get("componentType").getAsInt();
        int components = components(object.get("type").getAsString());
        long count = object.get("count").getAsLong();
        boolean normalized = object.has("normalized") && object.get("normalized").getAsBoolean();
        int size = componentSize(componentType);
        // Divided, so a forged count can't overflow the product
        if (count < 0 || count > MAX_ACCESSOR_VALUES / components) {
            throw new IllegalArgumentException(
                    "Accessor " + accessor + " has a bad element count, " + count);
        }
        int elementSize = size * components;
        if (!object.has("bufferView")) {
            // All zeroes, as glTF says
            return new Reader(
                    null, 0, elementSize, componentType, components, (int) count, normalized);
        }
        View view = view(object.get("bufferView").getAsInt());
        long accessorOffset = object.has(BYTE_OFFSET) ? object.get(BYTE_OFFSET).getAsLong() : 0;
        int stride = view.stride == 0 ? elementSize : view.stride;
        if (accessorOffset < 0 || accessorOffset > view.length || stride < elementSize) {
            throw new IllegalArgumentException(
                    "Accessor " + accessor + " has a bad offset or a stride under its elements");
        }
        // Inside the view, not just the buffer, in long math so huge values can't wrap
        long end = accessorOffset + (long) stride * (count - 1) + elementSize;
        if (count > 0 && end > view.length) {
            throw new IllegalArgumentException("Accessor " + accessor + " runs past its view");
        }
        return new Reader(
                view.buffer,
                (int) (view.offset + accessorOffset),
                stride,
                componentType,
                components,
                (int) count,
                normalized);
    }

    /**
     * A buffer view's checked place in its buffer.
     *
     * @param buffer The buffer.
     * @param offset Where the view starts.
     * @param length The view's length in bytes.
     * @param stride Bytes from one element to the next, or 0 for tightly packed.
     */
    private record View(ByteBuffer buffer, int offset, int length, int stride) {}

    /**
     * Reads the elements of one accessor.
     *
     * @param buffer The buffer, or null for all zeroes.
     * @param offset Where the first element starts.
     * @param stride Bytes from one element to the next.
     * @param componentType The component type.
     * @param components Components per element.
     * @param count How many elements.
     * @param normalized Whether integers map to 0 to 1 or -1 to 1.
     */
    private record Reader(
            ByteBuffer buffer,
            int offset,
            int stride,
            int componentType,
            int components,
            int count,
            boolean normalized) {

        /**
         * Read one component as an integer.
         *
         * @param element The element.
         * @param component The component.
         * @return The value; unsigned types are never negative.
         */
        int intAt(int element, int component) {
            if (buffer == null) {
                return 0;
            }
            int at = offset + element * stride + component * componentSize(componentType);
            return switch (componentType) {
                case BYTE -> buffer.get(at);
                case UNSIGNED_BYTE -> buffer.get(at) & 0xFF;
                case SHORT -> buffer.getShort(at);
                case UNSIGNED_SHORT -> buffer.getShort(at) & 0xFFFF;
                case UNSIGNED_INT -> buffer.getInt(at);
                default -> (int) buffer.getFloat(at);
            };
        }

        /**
         * Read one component as a float.
         *
         * @param element The element.
         * @param component The component.
         * @return The value.
         */
        float floatAt(int element, int component) {
            if (buffer == null) {
                return 0;
            }
            if (componentType == FLOAT) {
                return buffer.getFloat(offset + element * stride + component * 4);
            }
            int value = intAt(element, component);
            if (!normalized) {
                return value;
            }
            return switch (componentType) {
                case BYTE -> Math.max(value / 127f, -1);
                case UNSIGNED_BYTE -> value / 255f;
                case SHORT -> Math.max(value / 32_767f, -1);
                case UNSIGNED_SHORT -> value / 65_535f;
                default -> value;
            };
        }
    }
}
