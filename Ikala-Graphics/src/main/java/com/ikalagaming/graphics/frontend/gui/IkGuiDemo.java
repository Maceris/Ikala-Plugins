package com.ikalagaming.graphics.frontend.gui;

import com.ikalagaming.graphics.frontend.gui.data.IkBoolean;
import com.ikalagaming.graphics.frontend.gui.data.IkIO;
import com.ikalagaming.graphics.frontend.gui.enums.Condition;
import com.ikalagaming.graphics.frontend.gui.enums.MouseButton;
import com.ikalagaming.graphics.frontend.gui.flags.WindowFlags;

import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCond;

class IkGuiDemo {

    static void showDemoWindow(final IkBoolean open) {
        int windowFlags = WindowFlags.NONE;

        showDebugWindow();

        IkGui.setNextWindowSize(520, 600, Condition.ONCE);
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

    private static void showDebugWindow() {
        ImGui.setNextWindowSize(520, 600, ImGuiCond.Once);
        if (!ImGui.begin("IkGui Debug Window (via ImGui)")) {
            ImGui.end();
            return;
        }

        showShadowConfigurationSection();
        showShadowWindowOptionsSection();
        showShadowWidgetsSection();
        showShadowLayoutSection();
        showShadowPopupsSection();
        showShadowTablesSection();
        showShadowInputsSection();

        ImGui.end();
    }

    private static void showHelpSection() {
        if (!IkGui.collapsingHeader("Help")) {
            return;
        }
        // TODO(ches) fill this out
        IkGui.text("Not yet completed");
    }

    private static void showConfigurationSection() {
        if (!IkGui.collapsingHeader("Configuration")) {
            return;
        }
        // TODO(ches) fill this out
        IkGui.text("Not yet completed");
    }

    private static void showShadowConfigurationSection() {
        if (!ImGui.collapsingHeader("Configuration")) {
            return;
        }
        // TODO(ches) fill this out
        ImGui.text("Not yet completed");
    }

    private static void showWindowOptionsSection() {
        if (!IkGui.collapsingHeader("Window options")) {
            return;
        }
        // TODO(ches) fill this out
        IkGui.text("Not yet completed");
    }

    private static void showShadowWindowOptionsSection() {
        if (!ImGui.collapsingHeader("Window options")) {
            return;
        }
        // TODO(ches) fill this out
        ImGui.text("Not yet completed");
    }

    private static void showWidgetsSection() {
        if (!IkGui.collapsingHeader("Widgets")) {
            return;
        }
        // TODO(ches) fill this out
        IkGui.text("Not yet completed");
    }

    private static void showShadowWidgetsSection() {
        if (!ImGui.collapsingHeader("Widgets")) {
            return;
        }
        // TODO(ches) fill this out
        ImGui.text("Not yet completed");
    }

    private static void showLayoutSection() {
        if (!IkGui.collapsingHeader("Layout & Scrolling")) {
            return;
        }
        // TODO(ches) fill this out
        IkGui.text("Not yet completed");
    }

    private static void showShadowLayoutSection() {
        if (!ImGui.collapsingHeader("Layout & Scrolling")) {
            return;
        }
        // TODO(ches) fill this out
        ImGui.text("Not yet completed");
    }

    private static void showPopupsSection() {
        if (!IkGui.collapsingHeader("Popups & Modal Windows")) {
            return;
        }
        // TODO(ches) fill this out
        IkGui.text("Not yet completed");
    }

    private static void showShadowPopupsSection() {
        if (!ImGui.collapsingHeader("Popups & Modal Windows")) {
            return;
        }
        // TODO(ches) fill this out
        ImGui.text("Not yet completed");
    }

    private static void showTablesSection() {
        if (!IkGui.collapsingHeader("Tables & Columns")) {
            return;
        }
        // TODO(ches) fill this out
        IkGui.text("Not yet completed");
    }

    private static void showShadowTablesSection() {
        if (!ImGui.collapsingHeader("Tables & Columns")) {
            return;
        }
        // TODO(ches) fill this out
        ImGui.text("Not yet completed");
    }

    private static void showInputsSection() {
        if (!IkGui.collapsingHeader("Inputs & Focus")) {
            return;
        }
        // TODO(ches) fill this out
        IkGui.text("Not yet completed");
    }

    private static void showShadowInputsSection() {
        if (!ImGui.collapsingHeader("Inputs & Focus")) {
            return;
        }
        IkIO ikIO = IkGui.getIO();

        if (ImGui.treeNode("Inputs")) {
            ImGui.text(String.format("Mouse inside window: %b", ikIO.mouseInsideWindow));
            ImGui.text(
                    String.format(
                            "Mouse pos: (%.2f, %.2f)", ikIO.mousePosition.x, ikIO.mousePosition.y));
            ImGui.text(
                    String.format(
                            "Mouse pos prev: (%.2f, %.2f)",
                            ikIO.mousePositionPrevious.x, ikIO.mousePositionPrevious.y));

            ImVec2 prevImGui = ImGui.getIO().getMousePosPrev();
            ImGui.text(
                    String.format(
                            "Mouse pos prev (imgui): (%.2f, %.2f)", prevImGui.x, prevImGui.y));
            ImGui.text(
                    String.format(
                            "Mouse delta: (%.2f, %.2f)", ikIO.mouseDelta.x, ikIO.mouseDelta.y));

            StringBuilder mouseDownText = new StringBuilder("Mouse down:");
            for (int i = 0; i < MouseButton.COUNT; ++i) {
                if (ikIO.mouseDown[i]) {
                    mouseDownText.append(" b");
                    mouseDownText.append(i);
                    mouseDownText.append(" (");
                    long seconds = ikIO.mouseDownDuration[i] / 1000;
                    long msRemainder = ikIO.mouseDownDuration[i] % 1000;
                    mouseDownText.append(seconds);
                    mouseDownText.append('.');
                    mouseDownText.append(msRemainder);
                    mouseDownText.append(" secs)");
                }
            }
            ImGui.text(mouseDownText.toString());

            // TODO(ches) mouse wheel
            // TODO(ches) keys down
            // TODO(ches) key mods
            // TODO(ches) chars queue

            ImGui.treePop();
        }
        if (ImGui.treeNode("Outputs")) {
            // TODO(ches) fill this out

            ImGui.treePop();
        }
    }
}
