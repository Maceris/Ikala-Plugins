package com.ikalagaming.graphics.frontend.gui;

import com.ikalagaming.graphics.frontend.gui.data.Context;
import com.ikalagaming.graphics.frontend.gui.enums.TableBackgroundTarget;

import lombok.NonNull;

class IkGuiImplTables {
    static Context context;

    public static boolean beginTable(
            String name,
            int columns,
            int tableFlags,
            float outerWidth,
            float outerHeight,
            float innerWidth) {
        // TODO(ches) complete this
        return false;
    }

    public static void endTable() {
        // TODO(ches) complete this
    }

    public static int tableGetColumnCount() {
        // TODO(ches) complete this
        return 0;
    }

    public static int tableGetColumnFlags(int index) {
        // TODO(ches) complete this
        return 0;
    }

    public static int tableGetColumnIndex() {
        // TODO(ches) complete this
        return 0;
    }

    public static String tableGetColumnName(int index) {
        // TODO(ches) complete this
        return null;
    }

    public static int tableGetRowIndex() {
        // TODO(ches) complete this
        return 0;
    }

    public static void tableHeader(String label) {
        // TODO(ches) complete this
    }

    public static void tableHeadersRow() {
        // TODO(ches) complete this
    }

    public static boolean tableNextColumn() {
        // TODO(ches) complete this
        return false;
    }

    public static void tableNextRow(int tableRowFlags, float minHeight) {
        // TODO(ches) complete this
    }

    public static void tableSetBackgroundColor(
            @NonNull TableBackgroundTarget target, int color, int columnIndex) {
        // TODO(ches) complete this
    }

    public static boolean tableSetColumnIndex(int index) {
        // TODO(ches) complete this
        return false;
    }

    public static void tableSetupColumn(
            String label, int tableColumnFlags, float widthOrWeight, int userID) {
        // TODO(ches) complete this
    }

    public static void tableSetupScrollFreeze(int frozenRows, int frozenColumns) {
        // TODO(ches) complete this
    }

    /** Private constructor so this is not instantiated. */
    private IkGuiImplTables() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
