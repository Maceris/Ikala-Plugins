/*-
 * #%L
 * This file is part of libtiled-java.
 * %%
 * Copyright (C) 2004 - 2020 Thorbjørn Lindeijer <thorbjorn@lindeijer.nl>
 * Copyright (C) 2004 - 2020 Adam Turk <aturk@biggeruniverse.com>
 * Copyright (C) 2016 - 2020 Mike Thomas <mikepthomas@outlook.com>
 * Copyright (C) 2020 Adam Hornacek <adam.hornacek@icloud.com>
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package org.mapeditor.io;

import org.mapeditor.core.AnimatedTile;
import org.mapeditor.core.CustomClass;
import org.mapeditor.core.Group;
import org.mapeditor.core.ImageLayer;
import org.mapeditor.core.Map;
import org.mapeditor.core.MapObject;
import org.mapeditor.core.ObjectGroup;
import org.mapeditor.core.Point;
import org.mapeditor.core.Properties;
import org.mapeditor.core.Tile;
import org.mapeditor.core.TileLayer;
import org.mapeditor.core.TileOffset;
import org.mapeditor.core.TileSet;
import org.mapeditor.util.BasicTileCutter;
import org.mapeditor.util.ImageHelper;
import org.mapeditor.util.StreamHelper;
import org.mapeditor.util.URLHelper;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.awt.Color;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map.Entry;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import javax.imageio.ImageIO;
import javax.xml.bind.DatatypeConverter;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * The standard map reader for TMX files. Supports reading .tmx, .tmx.gz and
 * *.tsx files.
 *
 * @version 1.4.2
 */
public class TMXMapReader {

	private static class MapEntityResolver implements EntityResolver {

		@Override
		public InputSource resolveEntity(String publicId, String systemId) {
			if (systemId.equals("http://mapeditor.org/dtd/1.0/map.dtd")) {
				return new InputSource(this.getClass().getClassLoader()
					.getResourceAsStream("map.dtd"));
			}
			return null;
		}
	}

	public static final long FLIPPED_HORIZONTALLY_FLAG = 0x0000000080000000L;
	public static final long FLIPPED_VERTICALLY_FLAG = 0x0000000040000000L;

	public static final long FLIPPED_DIAGONALLY_FLAG = 0x0000000020000000L;

	public static final long ALL_FLAGS = TMXMapReader.FLIPPED_HORIZONTALLY_FLAG
		| TMXMapReader.FLIPPED_VERTICALLY_FLAG
		| TMXMapReader.FLIPPED_DIAGONALLY_FLAG;

	/**
	 * This utility function will check the specified string to see if it starts
	 * with one of the OS root designations. (Ex.: '/' on Unix, 'C:' on Windows)
	 *
	 * @param filename a filename to check for absolute or relative path
	 * @return <code>true</code> if the specified filename starts with a
	 *         filesystem root, <code>false</code> otherwise.
	 */
	public static boolean checkRoot(String filename) {
		File[] roots = File.listRoots();

		for (File root : roots) {
			try {
				String canonicalRoot = root.getCanonicalPath().toLowerCase();
				if (filename.toLowerCase().startsWith(canonicalRoot)) {
					return true;
				}
			}
			catch (IOException e) {
				// Do we care?
			}
		}

		return false;
	}

	private static int getAttribute(Node node, String attribname, int def) {
		final String attr = TMXMapReader.getAttributeValue(node, attribname);
		if (attr != null) {
			return Integer.parseInt(attr);
		}
		return def;
	}

	private static String getAttributeValue(Node node, String attribname) {
		final NamedNodeMap attributes = node.getAttributes();
		String value = null;
		if (attributes != null) {
			Node attribute = attributes.getNamedItem(attribname);
			if (attribute != null) {
				value = attribute.getNodeValue();
			}
		}
		return value;
	}

	private static double getDoubleAttribute(Node node, String attribname,
		double def) {
		final String attr = TMXMapReader.getAttributeValue(node, attribname);
		if (attr != null) {
			return Double.parseDouble(attr);
		}
		return def;
	}

	private static float getFloatAttribute(Node node, String attribname,
		float def) {
		final String attr = TMXMapReader.getAttributeValue(node, attribname);
		if (attr != null) {
			return Float.parseFloat(attr);
		}
		return def;
	}

	private static URL makeUrl(final String filename)
		throws MalformedURLException {
		if (filename.indexOf("://") > 0 || filename.startsWith("file:")) {
			return new URL(filename);
		}
		return new File(filename).toURI().toURL();
	}

	/**
	 * Reads properties from amongst the given children. When a "properties"
	 * element is encountered, it recursively calls itself with the children of
	 * this node. This function ensures backward compatibility with tmx version
	 * 0.99a.
	 *
	 * Support for reading property values stored as character data was added in
	 * Tiled 0.7.0 (tmx version 0.99c).
	 *
	 * @param children the children amongst which to find properties
	 * @param props the properties object to set the properties of
	 */
	private static void readProperties(NodeList children, Properties props) {
		for (int i = 0; i < children.getLength(); i++) {
			Node child = children.item(i);

			if ("property".equalsIgnoreCase(child.getNodeName())) {
				final String key =
					TMXMapReader.getAttributeValue(child, "name");
				final String type =
					TMXMapReader.getAttributeValue(child, "type");
				if ("class".equals(type)) {
					CustomClass childClass = new CustomClass(key);
					TMXMapReader.readProperties(child.getChildNodes(),
						childClass);
					props.setProperty(key, childClass.toString());
					continue;
				}
				String value = TMXMapReader.getAttributeValue(child, "value");
				if (value == null) {
					Node grandChild = child.getFirstChild();
					if (grandChild != null) {
						value = grandChild.getNodeValue();
						if (value != null) {
							value = value.trim();
						}
					}
				}
				if (value != null) {
					props.setProperty(key, value);
				}
			}
			else if ("properties".equals(child.getNodeName())) {
				TMXMapReader.readProperties(child.getChildNodes(), props);
			}
		}
	}

	/**
	 * Reads in class and enum properties.
	 *
	 * @param children the children amongst which to find properties
	 * @param parent The parent object we are loading.
	 */
	private static void readProperties(NodeList children, CustomClass parent) {
		for (int i = 0; i < children.getLength(); i++) {
			Node child = children.item(i);

			if ("property".equalsIgnoreCase(child.getNodeName())) {
				final String key =
					TMXMapReader.getAttributeValue(child, "name");
				final String type =
					TMXMapReader.getAttributeValue(child, "type");
				final String propertyType =
					TMXMapReader.getAttributeValue(child, "propertytype");
				if ("class".equals(type)) {
					CustomClass childClass = new CustomClass(key);
					CustomClass.Member member =
						new CustomClass.Member(key, type, propertyType);
					member.setChild(childClass);
					parent.addMember(member);
					TMXMapReader.readProperties(child.getChildNodes(),
						childClass);
				}
				String value = TMXMapReader.getAttributeValue(child, "value");
				if (value == null) {
					Node grandChild = child.getFirstChild();
					if (grandChild != null) {
						value = grandChild.getNodeValue();
						if (value != null) {
							value = value.trim();
						}
					}
				}
				if (value != null) {
					if ("enum".equals(type)) {
						CustomClass.Member member = new CustomClass.Member(key,
							type, value, propertyType);
						parent.addMember(member);
					}
					else {
						CustomClass.Member member =
							new CustomClass.Member(key, type, value);
						parent.addMember(member);
					}
				}
			}
			else if ("properties".equals(child.getNodeName())) {
				TMXMapReader.readProperties(child.getChildNodes(), parent);
			}
		}
	}

	private Map map;

	private URL xmlPath;

	private String error;

	private final EntityResolver entityResolver = new MapEntityResolver();

	private TreeMap<Integer, TileSet> tilesetPerFirstGid;

	private TilesetCache tilesetCache;

	/**
	 * Unmarshaller capable of unmarshalling all classes available from context
	 *
	 * @see #unmarshalClass(Node, Class)
	 */
	private final Unmarshaller unmarshaller;

	/**
	 * Constructor for TMXMapReader.
	 */
	public TMXMapReader() throws JAXBException {
		this.unmarshaller = JAXBContext
			.newInstance(Map.class, TileSet.class, Tile.class,
				AnimatedTile.class, ObjectGroup.class, ImageLayer.class)
			.createUnmarshaller();
	}

	/**
	 * accept.
	 *
	 * @param pathName a {@link java.io.File} object.
	 * @return a boolean.
	 */
	public boolean accept(File pathName) {
		try {
			String path = pathName.getCanonicalPath();
			if (path.endsWith(".tmx") || path.endsWith(".tsx")
				|| path.endsWith(".tmx.gz")) {
				return true;
			}
		}
		catch (IOException e) {
		}
		return false;
	}

	private void buildMap(Document doc) throws Exception {
		Node item, mapNode;

		mapNode = doc.getDocumentElement();

		if (!"map".equals(mapNode.getNodeName())) {
			throw new Exception("Not a valid tmx map file.");
		}

		// unmarshall the map using JAX-B
		this.map = this.unmarshalClass(mapNode, Map.class);
		if (this.map == null) {
			throw new Exception("Couldn't load map.");
		}

		// Don't need to load properties again.

		// We need to load layers and tilesets manually so that they are loaded
		// correctly
		this.map.getTileSets().clear();
		this.map.getLayers().clear();

		// Load tilesets first, in case order is munged
		this.tilesetPerFirstGid = new TreeMap<>();
		NodeList l = doc.getElementsByTagName("tileset");
		for (int i = 0; (item = l.item(i)) != null; i++) {
			int firstGid = TMXMapReader.getAttribute(item, "firstgid", 1);
			TileSet tileset = this.unmarshalTileset(item);
			this.tilesetPerFirstGid.put(firstGid, tileset);
			this.map.addTileset(tileset);
		}

		// Load the layers and groups
		for (Node sibs = mapNode.getFirstChild(); sibs != null; sibs =
			sibs.getNextSibling()) {
			if ("group".equals(sibs.getNodeName())) {
				Group group = this.unmarshalGroup(sibs);
				if (group != null) {
					this.map.addLayer(group);
				}
			}
			if ("layer".equals(sibs.getNodeName())) {
				TileLayer layer = this.readLayer(sibs);
				if (layer != null) {
					this.map.addLayer(layer);
				}
			}
			else if ("objectgroup".equals(sibs.getNodeName())) {
				ObjectGroup group = this.unmarshalObjectGroup(sibs);
				if (group != null) {
					this.map.addLayer(group);
				}
			}
			else if ("imagelayer".equals(sibs.getNodeName())) {
				ImageLayer imageLayer = this.unmarshalImageLayer(sibs);
				if (imageLayer != null) {
					this.map.addLayer(imageLayer);
				}
			}
		}
		this.map.getProperties().clear();
		TMXMapReader.readProperties(mapNode.getChildNodes(),
			this.map.getProperties());
		this.tilesetPerFirstGid = null;
	}

	/**
	 * Get the tile set and its corresponding firstgid that matches the given
	 * global tile id.
	 *
	 * @param gid a global tile id
	 * @return the tileset containing the tile with the given global tile id, or
	 *         <code>null</code> when no such tileset exists
	 */
	private Entry<Integer, TileSet> findTileSetForTileGID(int gid) {
		return this.tilesetPerFirstGid.floorEntry(gid);
	}

	String getError() {
		return this.error;
	}

	/**
	 * Helper method to get the tile based on its global id.
	 *
	 * @param tileId global id of the tile
	 * @return
	 *         <ul>
	 *         <li>{@link Tile} object corresponding to the global id, if
	 *         found</li>
	 *         <li><code>null</code>, otherwise</li>
	 *         </ul>
	 */
	private Tile getTileForTileGID(int tileId) {
		Tile tile = null;
		java.util.Map.Entry<Integer, TileSet> ts =
			this.findTileSetForTileGID(tileId);
		if (ts != null) {
			tile = ts.getValue().getTile(tileId - ts.getKey());
		}
		return tile;
	}

	private TileSet processTileset(Node t) throws Exception {
		TileSet set = new TileSet();

		final String name = TMXMapReader.getAttributeValue(t, "name");
		set.setName(name);

		final int tileWidth = TMXMapReader.getAttribute(t, "tilewidth",
			this.map != null ? this.map.getTileWidth() : 0);
		final int tileHeight = TMXMapReader.getAttribute(t, "tileheight",
			this.map != null ? this.map.getTileHeight() : 0);
		final int tileSpacing = TMXMapReader.getAttribute(t, "spacing", 0);
		final int tileMargin = TMXMapReader.getAttribute(t, "margin", 0);

		boolean hasTilesetImage = false;
		NodeList children = t.getChildNodes();

		for (int i = 0; i < children.getLength(); i++) {
			Node child = children.item(i);

			if (child.getNodeName().equalsIgnoreCase("image")) {
				if (hasTilesetImage) {
					System.out.println(
						"Ignoring illegal image element after tileset image.");
					continue;
				}

				String imgSource =
					TMXMapReader.getAttributeValue(child, "source");
				String transStr =
					TMXMapReader.getAttributeValue(child, "trans");

				if (imgSource != null) {
					// Not a shared image, but an entire set in one image
					// file. There should be only one image element in this
					// case.
					hasTilesetImage = true;

					URL sourcePath;
					if (!new File(imgSource).isAbsolute()) {
						imgSource = this.replacePathSeparator(imgSource);
						sourcePath = URLHelper.resolve(this.xmlPath, imgSource);
					}
					else {
						sourcePath = TMXMapReader.makeUrl(imgSource);
					}

					if (transStr != null) {
						if (transStr.startsWith("#")) {
							transStr = transStr.substring(1);
						}

						int colorInt = Integer.parseInt(transStr, 16);
						Color color = new Color(colorInt);
						set.setTransparentColor(color);
					}

					set.importTileBitmap(sourcePath, new BasicTileCutter(
						tileWidth, tileHeight, tileSpacing, tileMargin));
				}
			}
			else if (child.getNodeName().equalsIgnoreCase("tile")) {
				Tile tile = this.unmarshalTile(set, child, this.xmlPath);
				if (!hasTilesetImage || tile.getId() > set.getMaxTileId()) {
					set.addTile(tile);
				}
				else {
					Tile myTile = set.getTile(tile.getId());
					myTile.setProperties(tile.getProperties());
					// TODO: there is the possibility here of overlaying images,
					// which some people may want
				}
			}
			else if (child.getNodeName().equalsIgnoreCase("tileoffset")) {
				TileOffset tileoffset = new TileOffset();
				tileoffset.setX(Integer
					.valueOf(TMXMapReader.getAttributeValue(child, "x")));
				tileoffset.setY(Integer
					.valueOf(TMXMapReader.getAttributeValue(child, "y")));
				set.setTileoffset(tileoffset);
			}
		}

		return set;
	}

	/**
	 * Loads a map layer from a layer node.
	 *
	 * @param t the node representing the "layer" element
	 * @return the loaded map layer
	 * @throws Exception
	 */
	private TileLayer readLayer(Node t) throws Exception {
		final int layerId = TMXMapReader.getAttribute(t, "id", 0);
		final int layerWidth =
			TMXMapReader.getAttribute(t, "width", this.map.getWidth());
		final int layerHeight =
			TMXMapReader.getAttribute(t, "height", this.map.getHeight());

		TileLayer ml = new TileLayer(layerWidth, layerHeight);

		ml.setId(layerId);

		final int offsetX = TMXMapReader.getAttribute(t, "x", 0);
		final int offsetY = TMXMapReader.getAttribute(t, "y", 0);
		final int visible = TMXMapReader.getAttribute(t, "visible", 1);
		String opacity = TMXMapReader.getAttributeValue(t, "opacity");

		ml.setName(TMXMapReader.getAttributeValue(t, "name"));

		if (opacity != null) {
			ml.setOpacity(Float.parseFloat(opacity));
		}

		for (Node child = t.getFirstChild(); child != null; child =
			child.getNextSibling()) {
			String nodeName = child.getNodeName();
			if ("data".equalsIgnoreCase(nodeName)) {
				String encoding =
					TMXMapReader.getAttributeValue(child, "encoding");
				String comp =
					TMXMapReader.getAttributeValue(child, "compression");

				if ("base64".equalsIgnoreCase(encoding)) {
					Node cdata = child.getFirstChild();
					if (cdata != null) {
						String enc = cdata.getNodeValue().trim();
						byte[] dec = DatatypeConverter.parseBase64Binary(enc);
						ByteArrayInputStream bais =
							new ByteArrayInputStream(dec);
						InputStream is;

						if ("gzip".equalsIgnoreCase(comp)) {
							final int len = layerWidth * layerHeight * 4;
							is = new GZIPInputStream(bais, len);
						}
						else if ("zlib".equalsIgnoreCase(comp)) {
							is = new InflaterInputStream(bais);
						}
						else if (comp != null && !comp.isEmpty()) {
							throw new IOException(
								"Unrecognized compression method \"" + comp
									+ "\" for map layer " + ml.getName());
						}
						else {
							is = bais;
						}

						for (int y = 0; y < ml.getHeight(); y++) {
							for (int x = 0; x < ml.getWidth(); x++) {
								int tileId = 0;
								tileId |= is.read();
								tileId |= is.read() << Byte.SIZE;
								tileId |= is.read() << Byte.SIZE * 2;
								tileId |= is.read() << Byte.SIZE * 3;

								this.setTileAtFromTileId(ml, y, x, tileId);
							}
						}
					}
				}
				else if ("csv".equalsIgnoreCase(encoding)) {
					String csvText = child.getTextContent();

					if (comp != null && !comp.isEmpty()) {
						throw new IOException(
							"Unrecognized compression method \"" + comp
								+ "\" for map layer " + ml.getName()
								+ " and encoding " + encoding);
					}

					String[] csvTileIds = csvText.trim() // trim 'space', 'tab',
															// 'newline'. pay
															// attention to
															// additional
															// unicode chars
															// like \u2028,
															// \u2029, \u0085 if
															// necessary
						.split("[\\s]*,[\\s]*");

					if (csvTileIds.length != ml.getHeight() * ml.getWidth()) {
						throw new IOException(
							"Number of tiles does not match the layer's width and height");
					}

					for (int y = 0; y < ml.getHeight(); y++) {
						for (int x = 0; x < ml.getWidth(); x++) {
							String gid = csvTileIds[x + y * ml.getWidth()];
							long tileId = Long.parseLong(gid);

							this.setTileAtFromTileId(ml, y, x, (int) tileId);
						}
					}
				}
				else {
					int x = 0, y = 0;
					for (Node dataChild =
						child.getFirstChild(); dataChild != null; dataChild =
							dataChild.getNextSibling()) {
						if ("tile".equalsIgnoreCase(dataChild.getNodeName())) {
							int tileId =
								TMXMapReader.getAttribute(dataChild, "gid", -1);
							this.setTileAtFromTileId(ml, y, x, tileId);

							x++;
							if (x == ml.getWidth()) {
								x = 0;
								y++;
							}
							if (y == ml.getHeight()) {
								break;
							}
						}
					}
				}
			}
			else if ("tileproperties".equalsIgnoreCase(nodeName)) {
				for (Node tpn = child.getFirstChild(); tpn != null; tpn =
					tpn.getNextSibling()) {
					if ("tile".equalsIgnoreCase(tpn.getNodeName())) {
						int x = TMXMapReader.getAttribute(tpn, "x", -1);
						int y = TMXMapReader.getAttribute(tpn, "y", -1);

						Properties tip = new Properties();

						TMXMapReader.readProperties(tpn.getChildNodes(), tip);
						ml.setTileInstancePropertiesAt(x, y, tip);
					}
				}
			}
		}

		// This is done at the end, otherwise the offset is applied during
		// the loading of the tiles.
		ml.setOffset(offsetX, offsetY);

		// Invisible layers are automatically locked, so it is important to
		// set the layer to potentially invisible _after_ the layer data is
		// loaded.
		// todo: Shouldn't this be just a user interface feature, rather than
		// todo: something to keep in mind at this level?
		ml.setVisible(visible == 1);

		final int locked = TMXMapReader.getAttribute(t, "locked", 0);
		if (locked != 0) {
			ml.setLocked(1);
		}

		return ml;
	}

	/**
	 * Read a Map from the given InputStream, using {@code user.dir} to load
	 * relative assets.
	 *
	 * @see #readMap(InputStream, String)
	 */
	public Map readMap(InputStream in) throws Exception {
		return this.readMap(in, System.getProperty("user.dir"));
	}

	/**
	 * Read a Map from the given InputStream, using searchDirectory to load
	 * relative assets.
	 *
	 * @param in a {@link java.io.InputStream} containing the Map.
	 * @param searchDirectory Directory to search for relative assets.
	 * @return a {@link org.mapeditor.core.Map} object.
	 * @throws java.lang.Exception if any.
	 */
	public Map readMap(InputStream in, String searchDirectory)
		throws Exception {
		this.xmlPath =
			TMXMapReader.makeUrl(searchDirectory + File.separatorChar);

		return this.unmarshal(in);
	}

	/**
	 * readMap.
	 *
	 * @param filename a {@link java.lang.String} object.
	 * @return a {@link org.mapeditor.core.Map} object.
	 * @throws java.lang.Exception if any.
	 */
	public Map readMap(String filename) throws Exception {
		filename = this.replacePathSeparator(filename);
		return this.readMap(TMXMapReader.makeUrl(filename));
	}

	/**
	 * readMap.
	 *
	 * @param url an url to the map file.
	 * @return a {@link org.mapeditor.core.Map} object.
	 * @throws java.lang.Exception if any.
	 */
	public Map readMap(final URL url) throws Exception {
		if (url == null) {
			throw new IllegalArgumentException("Cannot read map from null URL");
		}
		this.xmlPath = URLHelper.getParent(url);

		// Wrap with GZIP decoder for .tmx.gz files
		try (InputStream in = StreamHelper.openStream(url)) {
			Map unmarshalledMap = this.unmarshal(in);
			unmarshalledMap.setFilename(url.toString());

			this.map = null;

			return unmarshalledMap;
		}
	}

	private MapObject readMapObject(Node t) throws Exception {
		final int id = TMXMapReader.getAttribute(t, "id", 0);
		final String name = TMXMapReader.getAttributeValue(t, "name");
		final String type = TMXMapReader.getAttributeValue(t, "type");
		final String gid = TMXMapReader.getAttributeValue(t, "gid");
		final String clazz = TMXMapReader.getAttributeValue(t, "class");
		final double x = TMXMapReader.getDoubleAttribute(t, "x", 0);
		final double y = TMXMapReader.getDoubleAttribute(t, "y", 0);
		final double width = TMXMapReader.getDoubleAttribute(t, "width", 0);
		final double height = TMXMapReader.getDoubleAttribute(t, "height", 0);
		final double rotation =
			TMXMapReader.getDoubleAttribute(t, "rotation", 0);

		MapObject obj = new MapObject(x, y, width, height, rotation);
		obj.setShape(obj.getBounds());
		if (id != 0) {
			obj.setId(id);
		}
		if (name != null) {
			obj.setName(name);
		}
		if (type != null) {
			obj.setType(type);
		}
		if (clazz != null) {
			obj.setClass(clazz);
		}
		if (gid != null) {
			long tileId = Long.parseLong(gid);
			if ((tileId & TMXMapReader.ALL_FLAGS) != 0) {
				// Read out the flags
				long flippedHorizontally =
					tileId & TMXMapReader.FLIPPED_HORIZONTALLY_FLAG;
				long flippedVertically =
					tileId & TMXMapReader.FLIPPED_VERTICALLY_FLAG;
				long flippedDiagonally =
					tileId & TMXMapReader.FLIPPED_DIAGONALLY_FLAG;

				obj.setFlipHorizontal(flippedHorizontally != 0);
				obj.setFlipVertical(flippedVertically != 0);
				obj.setFlipDiagonal(flippedDiagonally != 0);

				// Clear the flags
				tileId &= ~(TMXMapReader.FLIPPED_HORIZONTALLY_FLAG
					| TMXMapReader.FLIPPED_VERTICALLY_FLAG
					| TMXMapReader.FLIPPED_DIAGONALLY_FLAG);
			}
			Tile tile = this.getTileForTileGID((int) tileId);
			obj.setTile(tile);
		}

		NodeList children = t.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			Node child = children.item(i);
			if ("image".equalsIgnoreCase(child.getNodeName())) {
				String source = TMXMapReader.getAttributeValue(child, "source");
				if (source != null) {
					if (!new File(source).isAbsolute()) {
						source =
							URLHelper.resolve(this.xmlPath, source).toString();
					}
					obj.setImageSource(source);
				}
				break;
			}
			else if ("ellipse".equalsIgnoreCase(child.getNodeName())) {
				obj.setShape(new Ellipse2D.Double(x, y, width, height));
			}
			else if ("polygon".equalsIgnoreCase(child.getNodeName())
				|| "polyline".equalsIgnoreCase(child.getNodeName())) {
				Path2D.Double shape = new Path2D.Double();
				final String pointsAttribute =
					TMXMapReader.getAttributeValue(child, "points");
				StringTokenizer st = new StringTokenizer(pointsAttribute, ", ");
				boolean firstPoint = true;
				while (st.hasMoreElements()) {
					double pointX = Double.parseDouble(st.nextToken());
					double pointY = Double.parseDouble(st.nextToken());
					if (firstPoint) {
						shape.moveTo(x + pointX, y + pointY);
						firstPoint = false;
					}
					else {
						shape.lineTo(x + pointX, y + pointY);
					}
				}
				shape.closePath();
				obj.setShape(shape);
				obj.setBounds((Rectangle2D.Double) shape.getBounds2D());
			}
			else if ("point".equalsIgnoreCase(child.getNodeName())) {
				obj.setPoint(new Point());
			}
		}

		Properties props = new Properties();
		TMXMapReader.readProperties(children, props);

		obj.setProperties(props);
		return obj;
	}

	/**
	 * readTileset.
	 *
	 * @param in a {@link java.io.InputStream} object.
	 * @return a {@link org.mapeditor.core.TileSet} object.
	 * @throws java.lang.Exception if any.
	 */
	public TileSet readTileset(InputStream in) throws Exception {
		return this.unmarshalTilesetFile(in, new File(".").toURI().toURL());
	}

	/**
	 * readTileset.
	 *
	 * @param filename a {@link java.lang.String} object.
	 * @return a {@link org.mapeditor.core.TileSet} object.
	 * @throws java.lang.Exception if any.
	 */
	public TileSet readTileset(String filename) throws Exception {
		filename = this.replacePathSeparator(filename);

		URL url = TMXMapReader.makeUrl(filename);
		this.xmlPath = URLHelper.getParent(url);

		try (InputStream in = StreamHelper.openStream(url)) {
			return this.unmarshalTilesetFile(in, url);
		}
	}

	/**
	 * Tile map can be assembled on UNIX system, but read on Microsoft Windows
	 * system.
	 *
	 * @param path path to imageSource, tileSet, etc.
	 * @return path with the correct {@link File#separator}
	 */
	private String replacePathSeparator(String path) {
		if (path == null) {
			throw new IllegalArgumentException("path cannot be null.");
		}
		if (path.isEmpty() || path.lastIndexOf(File.separatorChar) >= 0) {
			return path;
		}
		return path.replace("/", File.separator);
	}

	/**
	 * Helper method to set the tile based on its global id.
	 *
	 * @param ml tile layer
	 * @param y y-coordinate
	 * @param x x-coordinate
	 * @param tileGid global id of the tile as read from the file
	 */
	private void setTileAtFromTileId(TileLayer ml, int y, int x, int tileGid) {
		Tile tile =
			this.getTileForTileGID((tileGid & (int) ~TMXMapReader.ALL_FLAGS));

		long flags = tileGid & TMXMapReader.ALL_FLAGS;
		ml.setTileAt(x, y, tile);
		ml.setFlagsAt(x, y, (int) flags);
	}

	public TMXMapReader setTilesetCache(TilesetCache tilesetCache) {
		this.tilesetCache = tilesetCache;
		return this;
	}

	private Map unmarshal(InputStream in) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		Document doc;
		try {
			factory.setIgnoringComments(true);
			factory.setIgnoringElementContentWhitespace(true);
			factory.setExpandEntityReferences(false);
			DocumentBuilder builder = factory.newDocumentBuilder();
			builder.setEntityResolver(this.entityResolver);
			InputSource insrc = new InputSource(StreamHelper.buffered(in));
			insrc.setSystemId(this.xmlPath.toString());
			insrc.setEncoding("UTF-8");
			doc = builder.parse(insrc);
		}
		catch (SAXException e) {
			// todo: replace with log message
			e.printStackTrace();
			throw new Exception(
				"Error while parsing map file: " + e.toString());
		}

		this.buildMap(doc);

		return this.map;
	}

	private <T> T unmarshalClass(Node node, Class<T> type)
		throws JAXBException {
		// we expect that all classes are already bounded to JAXBContext, so we
		// don't need to create unmarshaller
		// dynamicaly cause it's kinda heavy operation
		// if you got exception wich tells that SomeClass is not known to this
		// context - just add it to the list
		// passed to JAXBContext constructor
		return this.unmarshaller.unmarshal(node, type).getValue();
	}

	private Group unmarshalGroup(Node t) throws Exception {
		Group g = null;
		try {
			g = this.unmarshalClass(t, Group.class);
		}
		catch (JAXBException e) {
			// todo: replace with log message
			e.printStackTrace();
			return g;
		}

		final int offsetX = TMXMapReader.getAttribute(t, "x", 0);
		final int offsetY = TMXMapReader.getAttribute(t, "y", 0);
		g.setOffset(offsetX, offsetY);

		String opacity = TMXMapReader.getAttributeValue(t, "opacity");
		if (opacity != null) {
			g.setOpacity(Float.parseFloat(opacity));
		}

		final int locked = TMXMapReader.getAttribute(t, "locked", 0);
		if (locked != 0) {
			g.setLocked(1);
		}

		g.getLayers().clear();

		// Load the layers and objectgroups
		for (Node sibs = t.getFirstChild(); sibs != null; sibs =
			sibs.getNextSibling()) {
			if ("group".equals(sibs.getNodeName())) {
				Group group = this.unmarshalGroup(sibs);
				if (group != null) {
					g.getLayers().add(group);
				}
			}
			else if ("layer".equals(sibs.getNodeName())) {
				TileLayer layer = this.readLayer(sibs);
				if (layer != null) {
					g.getLayers().add(layer);
				}
			}
			else if ("objectgroup".equals(sibs.getNodeName())) {
				ObjectGroup group = this.unmarshalObjectGroup(sibs);
				if (group != null) {
					g.getLayers().add(group);
				}
			}
			else if ("imagelayer".equals(sibs.getNodeName())) {
				ImageLayer imageLayer = this.unmarshalImageLayer(sibs);
				if (imageLayer != null) {
					g.getLayers().add(imageLayer);
				}
			}
		}

		return g;
	}

	private BufferedImage unmarshalImage(Node t, URL baseDir)
		throws IOException {
		BufferedImage img = null;

		String source = TMXMapReader.getAttributeValue(t, "source");

		if (source != null) {
			URL url;
			if (TMXMapReader.checkRoot(source)) {
				url = TMXMapReader.makeUrl(source);
			}
			else {
				try {
					url = URLHelper.resolve(baseDir, source);
				}
				catch (URISyntaxException e) {
					throw new IOException(e);
				}
			}
			img = ImageIO.read(url);
		}
		else {
			NodeList nl = t.getChildNodes();

			for (int i = 0; i < nl.getLength(); i++) {
				Node node = nl.item(i);
				if ("data".equals(node.getNodeName())) {
					Node cdata = node.getFirstChild();
					if (cdata != null) {
						String sdata = cdata.getNodeValue();
						String enc = sdata.trim();
						byte[] dec = DatatypeConverter.parseBase64Binary(enc);
						img = ImageHelper.bytesToImage(dec);
					}
					break;
				}
			}
		}

		return img;
	}

	private ImageLayer unmarshalImageLayer(Node t) throws Exception {
		ImageLayer il = null;
		try {
			il = this.unmarshalClass(t, ImageLayer.class);
		}
		catch (JAXBException e) {
			// todo: replace with log message
			e.printStackTrace();
			return il;
		}
		return il;
	}

	private ObjectGroup unmarshalObjectGroup(Node t) throws Exception {
		ObjectGroup og = null;
		try {
			og = this.unmarshalClass(t, ObjectGroup.class);
		}
		catch (JAXBException e) {
			// todo: replace with log message
			e.printStackTrace();
			return og;
		}

		final int offsetX = TMXMapReader.getAttribute(t, "x", 0);
		final int offsetY = TMXMapReader.getAttribute(t, "y", 0);
		og.setOffset(offsetX, offsetY);

		final int locked = TMXMapReader.getAttribute(t, "locked", 0);
		if (locked != 0) {
			og.setLocked(1);
		}

		// Manually parse the objects in object group
		og.getObjects().clear();

		NodeList children = t.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			Node child = children.item(i);
			if ("object".equalsIgnoreCase(child.getNodeName())) {
				og.addObject(this.readMapObject(child));
			}
		}

		return og;
	}

	private Tile unmarshalTile(TileSet set, Node t, URL baseDir)
		throws Exception {
		Tile tile = null;
		NodeList children = t.getChildNodes();
		boolean isAnimated = false;

		for (int i = 0; i < children.getLength(); i++) {
			Node child = children.item(i);
			if ("animation".equalsIgnoreCase(child.getNodeName())) {
				isAnimated = true;
				break;
			}
		}

		try {
			if (isAnimated) {
				tile = this.unmarshalClass(t, AnimatedTile.class);
			}
			else {
				tile = this.unmarshalClass(t, Tile.class);
			}
		}
		catch (JAXBException e) {
			this.error = "Failed creating tile: " + e.getLocalizedMessage();
			return tile;
		}

		tile.setTileSet(set);

		for (int i = 0; i < children.getLength(); i++) {
			Node child = children.item(i);
			if ("image".equalsIgnoreCase(child.getNodeName())) {
				BufferedImage img = this.unmarshalImage(child, baseDir);
				tile.setImage(img);
			}
			else if ("animation".equalsIgnoreCase(child.getNodeName())) {
				// TODO: fill this in once TMXMapWriter is complete
			}
		}

		return tile;
	}

	private TileSet unmarshalTileset(Node t) throws Exception {
		return this.unmarshalTileset(t, false);
	}

	/**
	 * @param t xml node to begin unmarshalling from
	 * @param isExternalTileset is this a node of external tileset located in
	 *            separate tsx file
	 */
	private TileSet unmarshalTileset(Node t, boolean isExternalTileset)
		throws Exception {
		TileSet set = this.unmarshalClass(t, TileSet.class);

		String source = set.getSource();
		// if we have a "source" attribute in the external tileset - we ignore
		// it and display a warning
		if (source != null && isExternalTileset) {
			source = null;
			set.setSource(null);
			System.out
				.printf(
					"Warning: recursive external tilesets are not supported - "
						+ "ignoring source option for tileset %s%n",
					set.getName());
		}

		if (source != null) {
			source = this.replacePathSeparator(source);
			URL url = URLHelper.resolve(this.xmlPath, source);
			try (InputStream in = StreamHelper.openStream(url)) {

				TileSet ext = this.unmarshalTilesetFile(in, url);
				if (ext == null) {
					this.error =
						"Tileset " + source + " was not loaded correctly!";
					return set;
				}
				return ext;
			}
		}
		if (this.tilesetCache != null) {
			final String name = TMXMapReader.getAttributeValue(t, "name");
			return this.tilesetCache.getTileset(name,
				() -> this.processTileset(t));
		}

		return this.processTileset(t);
	}

	private TileSet unmarshalTilesetFile(InputStream in, URL file)
		throws Exception {
		TileSet set = null;
		Node tsNode;

		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		try {
			DocumentBuilder builder = factory.newDocumentBuilder();
			// builder.setErrorHandler(new XMLErrorHandler());
			Document tsDoc = builder.parse(StreamHelper.buffered(in), ".");

			URL xmlPathSave = this.xmlPath;
			if (file.getPath().contains("/")) {
				this.xmlPath = URLHelper.getParent(file);
			}

			NodeList tsNodeList = tsDoc.getElementsByTagName("tileset");

			// There can be only one tileset in a .tsx file.
			tsNode = tsNodeList.item(0);
			if (tsNode != null) {
				set = this.unmarshalTileset(tsNode, true);
				set.setSource(file.toString());
			}

			this.xmlPath = xmlPathSave;
		}
		catch (SAXException e) {
			this.error =
				"Failed while loading " + file + ": " + e.getLocalizedMessage();
		}

		return set;
	}
}