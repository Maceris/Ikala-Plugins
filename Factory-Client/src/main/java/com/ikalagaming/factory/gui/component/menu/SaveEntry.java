package com.ikalagaming.factory.gui.component.menu;

import com.ikalagaming.factory.gui.Component;
import com.ikalagaming.factory.gui.component.Button;
import com.ikalagaming.factory.gui.component.Text;
import com.ikalagaming.factory.gui.component.util.Alignment;

import lombok.NonNull;

public class SaveEntry extends Component {

    private final Button delete;

    /**
     * An entry in the save selection menu.
     *
     * @param saveName The name of the save file.
     */
    public SaveEntry(@NonNull String saveName) {
        var save = new Text(saveName);
        save.setDisplacement(0.01f, 0.05f);
        save.setScale(0.50f, 0.50f);
        save.setAlignment(Alignment.NORTH_WEST);
        addChild(save);

        delete = new Button("X");
        delete.setScale(0.10f, 0.90f);
        delete.setAlignment(Alignment.EAST);
        addChild(delete);
    }
}
