package com.ikalagaming.graphics.frontend.gui.data;

public class FontPage {
    public final int fontSize;
    private final int maxCharacters;
    private StringBuilder characters;

    public FontPage(int fontSize) {
        this.fontSize = fontSize;
        this.maxCharacters = FontAtlas.charactersPerPage(fontSize);
    }

    public boolean addCharacter(char c) {
        if (characters.length() >= maxCharacters) {
            return false;
        }
        characters.append(c);
        return true;
    }

    public void bake() {
        // TODO(ches) complete this
    }
}
