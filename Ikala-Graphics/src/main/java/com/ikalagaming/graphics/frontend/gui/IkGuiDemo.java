package com.ikalagaming.graphics.frontend.gui;

import com.ikalagaming.graphics.frontend.gui.data.IkBoolean;
import com.ikalagaming.graphics.frontend.gui.flags.WindowFlags;

import imgui.ImGui;

class IkGuiDemo {

    static void showDemoWindow(final IkBoolean open) {

        int windowFlags = WindowFlags.NONE;

        if (!IkGui.begin("IkGui Demo Window", open, windowFlags)) {
            IkGui.end();
            return;
        }

        IkGui.text("Hello world!");

        showHelpSection();
        showConfigurationSection();
        showWindowOptionsSection();
        showWidgetsSection();
        showLayoutSection();
        showPopupsSection();
        showTablesSection();
        showInputsSection();

        IkGui.end();
    }

    private static void showHelpSection() {
        if (IkGui.collapsingHeader("Help")) {
            // TODO(ches) fill this out
            ImGui.text("Not yet completed");
        }
    }

    private static void showConfigurationSection() {
        if (IkGui.collapsingHeader("Configuration")) {
            // TODO(ches) fill this out
            ImGui.text("Not yet completed");
        }
    }

    private static void showWindowOptionsSection() {
        if (IkGui.collapsingHeader("Window options")) {
            // TODO(ches) fill this out
            ImGui.text("Not yet completed");
        }
    }

    private static void showWidgetsSection() {
        if (IkGui.collapsingHeader("Widgets")) {
            // TODO(ches) fill this out
            ImGui.text("Not yet completed");
        }
    }

    private static void showLayoutSection() {
        if (IkGui.collapsingHeader("Layout & Scrolling")) {
            // TODO(ches) fill this out
            ImGui.text("Not yet completed");
        }
    }

    private static void showPopupsSection() {
        if (IkGui.collapsingHeader("Popups & Modal Windows")) {
            // TODO(ches) fill this out
            ImGui.text("Not yet completed");
        }
    }

    private static void showTablesSection() {
        if (IkGui.collapsingHeader("Tables & Columns")) {
            // TODO(ches) fill this out
            ImGui.text("Not yet completed");
        }
    }

    private static void showInputsSection() {
        if (IkGui.collapsingHeader("Inputs & Focus")) {
            // TODO(ches) fill this out
            ImGui.text("Not yet completed");
        }
    }
}
