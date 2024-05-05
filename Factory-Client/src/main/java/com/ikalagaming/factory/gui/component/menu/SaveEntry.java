package com.ikalagaming.factory.gui.component.menu;

import com.ikalagaming.factory.gui.Component;
import com.ikalagaming.factory.gui.component.Button;
import com.ikalagaming.factory.gui.component.util.Alignment;

import lombok.NonNull;

public class SaveEntry extends Component {

    /** The save file name. */
    private final String saveName;

    private final Button delete;

    public SaveEntry(@NonNull String saveName) {
        this.saveName = saveName;

        delete = new Button("X");
        delete.setScale(0.10f, 0.90f);
        delete.setAlignment(Alignment.EAST);
        addChild(delete);
    }
}
