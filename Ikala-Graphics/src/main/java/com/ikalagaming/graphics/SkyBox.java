package com.ikalagaming.graphics;

import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.graph.Mesh;
import com.ikalagaming.graphics.graph.Texture;

/**
 * A sky box that provides a backdrop for the environment.
 * 
 * @author Ches Burks
 *
 */
public class SkyBox extends SceneItem {

	/**
	 * Set up a new skybox.
	 * 
	 * @param objModel The model to use for the box.
	 * @param textureFile The name of the file to use for the texture.
	 */
	public SkyBox(String objModel, String textureFile) {
		super();
		Mesh skyBoxMesh = OBJLoader.loadMesh(objModel);
		Texture skyBoxtexture = new Texture(textureFile);
		skyBoxMesh.setMaterial(new Material(skyBoxtexture, 0.0f));
		setMesh(skyBoxMesh);
		setPosition(0, 0, 0);
	}
}
