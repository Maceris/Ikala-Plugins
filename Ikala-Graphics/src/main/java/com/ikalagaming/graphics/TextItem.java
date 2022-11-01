package com.ikalagaming.graphics;

import com.ikalagaming.graphics.graph.FontTexture;
import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.graph.Mesh;
import com.ikalagaming.graphics.scene.SceneItem;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * Used to draw text on the HUD.
 *
 * @author Ches Burks
 *
 */
public class TextItem extends SceneItem {

	/**
	 * The Z position to render at.
	 */
	private static final float ZPOS = 0.0f;
	/**
	 * How many vertices there are in each quad, four here because the letters
	 * are a rectangular texture.
	 */
	private static final int VERTICES_PER_QUAD = 4;
	/**
	 * The text to draw.
	 *
	 * @return The text that this item draws.
	 */
	@Getter
	private String text;

	private final FontTexture fontTexture;

	/**
	 * Create a new text item.
	 *
	 * @param text The text to draw.
	 * @param fontTexture The font texture to use for rendering.
	 */
	public TextItem(String text, FontTexture fontTexture) {
		super();
		this.text = text;
		this.fontTexture = fontTexture;
		this.setMesh(this.buildMesh());
	}

	private Mesh buildMesh() {
		List<Float> positions = new ArrayList<>();
		List<Float> textCoords = new ArrayList<>();
		float[] normals = new float[0];
		List<Integer> indices = new ArrayList<>();
		char[] characters = this.text.toCharArray();
		final int numChars = characters.length;

		float startX = 0;
		for (int i = 0; i < numChars; ++i) {
			FontTexture.CharInfo charInfo =
				this.fontTexture.getCharInfo(characters[i]);

			// Build a character tile composed of two triangles

			// Top Left vertex
			positions.add(startX);
			positions.add(0.0f);
			positions.add(TextItem.ZPOS);
			textCoords.add((float) charInfo.getStartX()
				/ (float) this.fontTexture.getWidth());
			textCoords.add(0.0f);
			indices.add(i * TextItem.VERTICES_PER_QUAD);

			// Bottom Left vertex
			positions.add(startX);
			positions.add((float) this.fontTexture.getHeight());
			positions.add(TextItem.ZPOS);
			textCoords.add((float) charInfo.getStartX()
				/ (float) this.fontTexture.getWidth());
			textCoords.add(1.0f);
			indices.add(i * TextItem.VERTICES_PER_QUAD + 1);

			// Bottom Right vertex
			positions.add(startX + charInfo.getWidth());
			positions.add((float) this.fontTexture.getHeight());
			positions.add(TextItem.ZPOS);
			textCoords.add((float) (charInfo.getStartX() + charInfo.getWidth())
				/ (float) this.fontTexture.getWidth());
			textCoords.add(1.0f);
			indices.add(i * TextItem.VERTICES_PER_QUAD + 2);

			// Top Right vertex
			positions.add(startX + charInfo.getWidth());
			positions.add(0.0f);
			positions.add(TextItem.ZPOS);
			textCoords.add((float) (charInfo.getStartX() + charInfo.getWidth())
				/ (float) this.fontTexture.getWidth());
			textCoords.add(0.0f);
			indices.add(i * TextItem.VERTICES_PER_QUAD + 3);

			// Add indices for left top and bottom right vertices
			indices.add(i * TextItem.VERTICES_PER_QUAD);
			indices.add(i * TextItem.VERTICES_PER_QUAD + 2);

			startX += charInfo.getWidth();
		}

		float[] positionArray = Utils.listToArray(positions);
		float[] textCoordinatesArr = Utils.listToArray(textCoords);
		int[] indicesArray = indices.stream().mapToInt(i -> i).toArray();
		Mesh mesh =
			new Mesh(positionArray, textCoordinatesArr, normals, indicesArray);
		mesh.setMaterial(new Material(this.fontTexture.getTexture()));
		return mesh;
	}

	/**
	 * Set the new text, which will clear and rebuild the mesh.
	 *
	 * @param text The new text to draw.
	 */
	public void setText(String text) {
		this.text = text;
		this.getMesh().deleteBuffers();
		this.setMesh(this.buildMesh());
	}

}
