//
// This file was generated by the Eclipse Implementation of JAXB, v2.3.7
// See https://eclipse-ee4j.github.io/jaxb-ri
// Any modifications to this file will be lost upon recompilation of the source
// schema.
// Generated on: 2023.01.22 at 06:49:20 PM EST
//

package org.mapeditor.core;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Generated;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;

/**
 * If there are multiple `tileset` elements, they are in ascending<br>
 * order of their `firstgid` attribute. The first tileset always<br>
 * has a `firstgid` value of 1\. Since Tiled 0.15, image collection<br>
 * tilesets do not necessarily number their tiles consecutively<br>
 * since gaps can occur when removing tiles.
 *
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "TileSet",
	propOrder = {"tileoffset", "grid", "properties", "imageData",
		"terraintypes", "internalTiles", "wangsets"})
@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
	date = "2023-01-22T18:49:20-05:00")
public class TileSetData {

	/**
	 * @since 0.8
	 * 
	 */
	@XmlElement(required = true)
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	protected TileOffset tileoffset;
	/**
	 * @since 1.0
	 * 
	 */
	@XmlElement(required = true)
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	protected Grid grid;
	/**
	 * @since 0.8
	 * 
	 */
	@XmlElement(required = true, type = Properties.class)
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	protected Properties properties;
	/**
	 * 
	 */
	@XmlElement(name = "image", required = true)
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	protected ImageData imageData;
	/**
	 * @since 0.9
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	protected TerrainTypes terraintypes;
	/**
	 * 
	 */
	@XmlElement(name = "tile", required = true, type = Tile.class)
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	protected List<Tile> internalTiles;
	/**
	 * @since 1.1
	 * 
	 */
	@XmlElement(required = true)
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	protected WangSets wangsets;
	/**
	 * The first global tile ID of this tileset (this global ID<br>
	 * maps to the first tile in this tileset).
	 * 
	 */
	@XmlAttribute(name = "firstgid")
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	protected Integer firstgid;
	/**
	 * The name of this tileset.
	 * 
	 */
	@XmlAttribute(name = "name")
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	protected String name;
	/**
	 * If this tileset is stored in an external TSX (Tile Set XML)<br>
	 * file, this attribute refers to that file. That TSX file has<br>
	 * the same structure as the element described here. (There is<br>
	 * the **firstgid** attribute missing and this source attribute<br>
	 * is also not there. These two attributes are kept in the TMX<br>
	 * map, since they are map specific.)
	 * 
	 */
	@XmlAttribute(name = "source")
	@XmlSchemaType(name = "anyURI")
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	protected String source;
	/**
	 * The (maximum) width of the tiles in this tileset.
	 * 
	 */
	@XmlAttribute(name = "tilewidth", required = true)
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	protected int tileWidth;
	/**
	 * The (maximum) height of the tiles in this tileset.
	 * 
	 */
	@XmlAttribute(name = "tileheight", required = true)
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	protected int tileHeight;
	/**
	 * The spacing in pixels between the tiles in this tileset<br>
	 * (applies to the tileset image).
	 * 
	 */
	@XmlAttribute(name = "spacing")
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	protected Integer tileSpacing;
	/**
	 * The margin around the tiles in this tileset (applies to the<br>
	 * tileset image).
	 * 
	 */
	@XmlAttribute(name = "margin")
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	protected Integer tileMargin;
	/**
	 * The number of tiles in this tileset<br>
	 * <br>
	 *
	 * @since 0.13
	 * 
	 */
	@XmlAttribute(name = "tilecount")
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	protected Integer tilecount;
	/**
	 * The number of tile columns in the tileset. For image<br>
	 * collection tilesets it is editable and is used when<br>
	 * displaying the tileset.<br>
	 * <br>
	 *
	 * @since 0.15
	 * 
	 */
	@XmlAttribute(name = "columns", required = true)
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	protected int columns;

	/**
	 * The number of tile columns in the tileset. For image<br>
	 * collection tilesets it is editable and is used when<br>
	 * displaying the tileset.<br>
	 * <br>
	 *
	 * @since 0.15
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public int getColumns() {
		return this.columns;
	}

	/**
	 * The first global tile ID of this tileset (this global ID<br>
	 * maps to the first tile in this tileset).
	 * 
	 * @return possible object is {@link Integer }
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public Integer getFirstgid() {
		return this.firstgid;
	}

	/**
	 * @since 1.0
	 * 
	 * @return possible object is {@link Grid }
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public Grid getGrid() {
		return this.grid;
	}

	/**
	 * 
	 * @return possible object is {@link ImageData }
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public ImageData getImageData() {
		return this.imageData;
	}

	/**
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public List<Tile> getInternalTiles() {
		if (this.internalTiles == null) {
			this.internalTiles = new ArrayList<>();
		}
		return this.internalTiles;
	}

	/**
	 * The name of this tileset.
	 * 
	 * @return possible object is {@link String }
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public String getName() {
		return this.name;
	}

	/**
	 * @since 0.8
	 * 
	 * @return possible object is {@link PropertiesData }
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public Properties getProperties() {
		return this.properties;
	}

	/**
	 * If this tileset is stored in an external TSX (Tile Set XML)<br>
	 * file, this attribute refers to that file. That TSX file has<br>
	 * the same structure as the element described here. (There is<br>
	 * the **firstgid** attribute missing and this source attribute<br>
	 * is also not there. These two attributes are kept in the TMX<br>
	 * map, since they are map specific.)
	 * 
	 * @return possible object is {@link String }
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public String getSource() {
		return this.source;
	}

	/**
	 * @since 0.9
	 * 
	 * @return possible object is {@link TerrainTypes }
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public TerrainTypes getTerraintypes() {
		return this.terraintypes;
	}

	/**
	 * The number of tiles in this tileset<br>
	 * <br>
	 *
	 * @since 0.13
	 * 
	 * @return possible object is {@link Integer }
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public Integer getTilecount() {
		return this.tilecount;
	}

	/**
	 * The (maximum) height of the tiles in this tileset.
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public int getTileHeight() {
		return this.tileHeight;
	}

	/**
	 * The margin around the tiles in this tileset (applies to the<br>
	 * tileset image).
	 * 
	 * @return possible object is {@link Integer }
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public Integer getTileMargin() {
		return this.tileMargin;
	}

	/**
	 * @since 0.8
	 * 
	 * @return possible object is {@link TileOffset }
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public TileOffset getTileoffset() {
		return this.tileoffset;
	}

	/**
	 * The spacing in pixels between the tiles in this tileset<br>
	 * (applies to the tileset image).
	 * 
	 * @return possible object is {@link Integer }
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public Integer getTileSpacing() {
		return this.tileSpacing;
	}

	/**
	 * The (maximum) width of the tiles in this tileset.
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public int getTileWidth() {
		return this.tileWidth;
	}

	/**
	 * @since 1.1
	 * 
	 * @return possible object is {@link WangSets }
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public WangSets getWangsets() {
		return this.wangsets;
	}

	/**
	 * The number of tile columns in the tileset. For image<br>
	 * collection tilesets it is editable and is used when<br>
	 * displaying the tileset.<br>
	 * <br>
	 *
	 * @since 0.15
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public void setColumns(int value) {
		this.columns = value;
	}

	/**
	 * The first global tile ID of this tileset (this global ID<br>
	 * maps to the first tile in this tileset).
	 * 
	 * @param value allowed object is {@link Integer }
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public void setFirstgid(Integer value) {
		this.firstgid = value;
	}

	/**
	 * @since 1.0
	 * 
	 * @param value allowed object is {@link Grid }
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public void setGrid(Grid value) {
		this.grid = value;
	}

	/**
	 * 
	 * @param value allowed object is {@link ImageData }
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public void setImageData(ImageData value) {
		this.imageData = value;
	}

	/**
	 * The name of this tileset.
	 * 
	 * @param value allowed object is {@link String }
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public void setName(String value) {
		this.name = value;
	}

	/**
	 * @since 0.8
	 * 
	 * @param value allowed object is {@link PropertiesData }
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public void setProperties(Properties value) {
		this.properties = value;
	}

	/**
	 * If this tileset is stored in an external TSX (Tile Set XML)<br>
	 * file, this attribute refers to that file. That TSX file has<br>
	 * the same structure as the element described here. (There is<br>
	 * the **firstgid** attribute missing and this source attribute<br>
	 * is also not there. These two attributes are kept in the TMX<br>
	 * map, since they are map specific.)
	 * 
	 * @param value allowed object is {@link String }
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public void setSource(String value) {
		this.source = value;
	}

	/**
	 * @since 0.9
	 * 
	 * @param value allowed object is {@link TerrainTypes }
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public void setTerraintypes(TerrainTypes value) {
		this.terraintypes = value;
	}

	/**
	 * The number of tiles in this tileset<br>
	 * <br>
	 *
	 * @since 0.13
	 * 
	 * @param value allowed object is {@link Integer }
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public void setTilecount(Integer value) {
		this.tilecount = value;
	}

	/**
	 * The (maximum) height of the tiles in this tileset.
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public void setTileHeight(int value) {
		this.tileHeight = value;
	}

	/**
	 * The margin around the tiles in this tileset (applies to the<br>
	 * tileset image).
	 * 
	 * @param value allowed object is {@link Integer }
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public void setTileMargin(Integer value) {
		this.tileMargin = value;
	}

	/**
	 * @since 0.8
	 * 
	 * @param value allowed object is {@link TileOffset }
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public void setTileoffset(TileOffset value) {
		this.tileoffset = value;
	}

	/**
	 * The spacing in pixels between the tiles in this tileset<br>
	 * (applies to the tileset image).
	 * 
	 * @param value allowed object is {@link Integer }
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public void setTileSpacing(Integer value) {
		this.tileSpacing = value;
	}

	/**
	 * The (maximum) width of the tiles in this tileset.
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public void setTileWidth(int value) {
		this.tileWidth = value;
	}

	/**
	 * @since 1.1
	 * 
	 * @param value allowed object is {@link WangSets }
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public void setWangsets(WangSets value) {
		this.wangsets = value;
	}

}