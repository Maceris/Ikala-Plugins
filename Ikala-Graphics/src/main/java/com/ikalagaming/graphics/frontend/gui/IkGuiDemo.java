package com.ikalagaming.graphics.frontend.gui;

import com.ikalagaming.graphics.frontend.gui.data.DrawData;
import com.ikalagaming.graphics.frontend.gui.data.DrawList;
import com.ikalagaming.graphics.frontend.gui.data.IkBoolean;
import com.ikalagaming.graphics.frontend.gui.data.IkIO;
import com.ikalagaming.graphics.frontend.gui.enums.Condition;
import com.ikalagaming.graphics.frontend.gui.enums.MouseButton;
import com.ikalagaming.graphics.frontend.gui.flags.WindowFlags;

import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCond;

import java.nio.ByteBuffer;

class IkGuiDemo {

    static void showDemoWindow(final IkBoolean open) {
        int windowFlags = WindowFlags.NONE;

        IkGui.setNextWindowSize(520, 700, Condition.ONCE);
        IkGui.setNextWindowPos(30, 30, Condition.ONCE);
        if (!IkGui.begin("IkGui Demo Window", open, windowFlags)) {
            IkGui.end();
            return;
        }

        IkGui.setFontSize(12);
        IkGui.text("Hello world size 12!");
        IkGui.sameLine();
        IkGui.text("This should be on the same line.");
        IkGui.setFontSize(24);
        IkGui.text("Hello world size 24!");
        IkGui.setFontSize(72);
        IkGui.text("Hello world size 72!");
        IkGui.setFontSize(12);

        showHelpSection();
        showConfigurationSection();
        showWindowOptionsSection();
        showWidgetsSection();
        showLayoutSection();
        showPopupsSection();
        showTablesSection();
        showInputsSection();

        IkGui.end();

        showDebugWindow();
    }

    private static void showDebugWindow() {
        ImGui.setNextWindowSize(520, 600, ImGuiCond.Once);
        if (!ImGui.begin("IkGui Debug Window (via ImGui)")) {
            ImGui.end();
            return;
        }

        IkIO ikIO = IkGui.getIO();

        if (ImGui.treeNode("DrawLists")) {
            DrawData drawData = IkGui.getContext().drawData;
            int listCount = drawData.getDrawListCount();
            for (int drawListIndex = 0; drawListIndex < listCount; ++drawListIndex) {

                final int commandCount = drawData.getDrawListCommandCount(drawListIndex);
                final int vertexCount = drawData.getDrawListVertexCount(drawListIndex);
                String name =
                        String.format(
                                "DrawList: %s - %d vtx, %d commands, %d points, %d point details",
                                drawData.drawLists.get(drawListIndex).windowName,
                                vertexCount,
                                commandCount,
                                drawData.getDrawListPointCount(drawListIndex),
                                drawData.getDrawListPointDetailCount(drawListIndex));

                if (ImGui.treeNode(name)) {
                    if (ImGui.treeNode("Vertices")) {
                        ByteBuffer vertexBuffer = drawData.getDrawListVertexBuffer(drawListIndex);

                        for (int vertex = 0; vertex < vertexCount; ++vertex) {
                            int vertexIndex = vertex * DrawData.SIZE_OF_VERTEX;

                            float x = vertexBuffer.getFloat(vertexIndex);
                            vertexIndex += Float.BYTES;
                            float y = vertexBuffer.getFloat(vertexIndex);

                            ImGui.text(
                                    String.format(
                                            "Vertex %d - (%f, %f) = (%f, %f)",
                                            vertex,
                                            x,
                                            y,
                                            x * (2 / ikIO.displaySize.x) - 1,
                                            y * (-2 / ikIO.displaySize.x) + 1));
                        }
                        ImGui.treePop();
                    }

                    ByteBuffer commandBuffer = drawData.getDrawListCommandBuffer(drawListIndex);

                    if (commandCount > 10) {
                        for (int commandGroup = 0;
                                commandGroup < commandCount / 10;
                                commandGroup++) {
                            final int groupStart = commandGroup * 10;
                            final int groupEnd = Math.min((commandGroup + 1) * 10, commandCount);
                            if (ImGui.treeNode(
                                    String.format("Commands %d-%d", groupStart, groupEnd))) {
                                for (int commandIndex = groupStart;
                                        commandIndex < groupEnd;
                                        ++commandIndex) {
                                    drawCommandDetails(
                                            commandIndex, commandBuffer, drawData, drawListIndex);
                                }
                                ImGui.treePop();
                            }
                        }
                    } else {
                        for (int commandIndex = 0; commandIndex < commandCount; ++commandIndex) {
                            drawCommandDetails(
                                    commandIndex, commandBuffer, drawData, drawListIndex);
                        }
                    }

                    ImGui.treePop();
                }
            }

            ImGui.treePop();
        }

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

        ImGui.end();
    }

    private static void drawCommandDetails(
            int commandIndex, ByteBuffer commandBuffer, DrawData drawData, int drawListIndex) {
        IkIO ikIO = IkGui.getIO();
        if (ImGui.treeNode(String.format("Command %d", commandIndex))) {
            int commandBufferIndex = commandIndex * DrawData.SIZE_OF_DRAW_COMMAND;
            final int pointIndex = commandBuffer.getInt(commandBufferIndex);
            commandBufferIndex += Integer.BYTES;
            final int detailIndex = commandBuffer.getInt(commandBufferIndex);
            commandBufferIndex += Integer.BYTES;
            final int pointCount = commandBuffer.getInt(commandBufferIndex);
            commandBufferIndex += Integer.BYTES;
            final int detailCount = commandBuffer.getInt(commandBufferIndex);
            commandBufferIndex += Integer.BYTES;
            final int type = commandBuffer.getInt(commandBufferIndex);
            commandBufferIndex += Integer.BYTES;
            final int style = commandBuffer.getInt(commandBufferIndex);
            commandBufferIndex += Integer.BYTES;
            final float stroke = commandBuffer.getFloat(commandBufferIndex);

            ImGui.text(String.format("Point Index: %d", pointIndex));
            ImGui.text(String.format("Detail Index: %d", detailIndex));
            ImGui.text(String.format("Point Count: %d", pointCount));
            ImGui.text(String.format("Detail Count: %d", detailCount));
            try {
                DrawList.ElementType namedType = DrawList.ElementType.fromID(type);
                ImGui.text(String.format("Type: %s", namedType));
            } catch (IllegalArgumentException e) {
                ImGui.text("Type: ???");
            }
            try {
                DrawList.ElementStyle namedStyle = DrawList.ElementStyle.fromID(style);
                ImGui.text(String.format("Style: %s", namedStyle));
            } catch (IllegalArgumentException e) {
                ImGui.text("Style: ???");
            }
            ImGui.text(String.format("Stroke: %f", stroke));

            if (ImGui.treeNode("Points")) {
                for (int debugPointIndex = 0; debugPointIndex < pointCount; ++debugPointIndex) {

                    if (ImGui.treeNode(String.format("Point %d", debugPointIndex))) {
                        ByteBuffer pointBuffer = drawData.getDrawListPointBuffer(drawListIndex);

                        int pointBufferIndex =
                                (pointIndex + debugPointIndex) * DrawData.SIZE_OF_POINT;

                        float x = pointBuffer.getFloat(pointBufferIndex);
                        pointBufferIndex += Float.BYTES;
                        float y = pointBuffer.getFloat(pointBufferIndex);
                        pointBufferIndex += Float.BYTES;
                        float a = pointBuffer.getFloat(pointBufferIndex);
                        pointBufferIndex += Float.BYTES;
                        float b = pointBuffer.getFloat(pointBufferIndex);

                        if (type == DrawList.ElementType.RECTANGLE.getTypeID()
                                || type == DrawList.ElementType.TEXT.getTypeID()) {
                            float xScaled = (2 / ikIO.displaySize.x) * x - 1;
                            float yScaled = (-2 / ikIO.displaySize.y) * x + 1;
                            float width = (1 / ikIO.displaySize.x) * a;
                            float height = (1 / ikIO.displaySize.y) * b;

                            ImGui.text(String.format("x: %f (%f)", x, xScaled));
                            ImGui.text(String.format("y: %f (%f)", y, yScaled));
                            ImGui.text(String.format("width: %f (%f)", a, width));
                            ImGui.text(String.format("height: %f (%f)", b, height));
                        } else {
                            float xScaled = (2 / ikIO.displaySize.x) * x - 1;
                            float yScaled = (-2 / ikIO.displaySize.y) * x + 1;
                            float aScaled = (2 / ikIO.displaySize.x) * a - 1;
                            float bScaled = (-2 / ikIO.displaySize.y) * b + 1;

                            ImGui.text(String.format("x: %f (%f)", x, xScaled));
                            ImGui.text(String.format("y: %f (%f)", y, yScaled));
                            ImGui.text(String.format("a: %f (%f)", a, aScaled));
                            ImGui.text(String.format("b: %f (%f)", b, bScaled));
                        }

                        ImGui.treePop();
                    }
                }
                ImGui.treePop();
            }
            if (ImGui.treeNode("Point Details")) {
                for (int debugPointDetailIndex = 0;
                        debugPointDetailIndex < detailCount;
                        ++debugPointDetailIndex) {
                    if (ImGui.treeNode(String.format("Point Detail %d", debugPointDetailIndex))) {
                        ByteBuffer pointDetailBuffer =
                                drawData.getDrawListPointDetailBuffer(drawListIndex);
                        int pointDetailBufferIndex =
                                (detailIndex + debugPointDetailIndex)
                                        * DrawData.SIZE_OF_POINT_DETAIL;

                        float radius = pointDetailBuffer.getFloat(pointDetailBufferIndex);
                        pointDetailBufferIndex += Float.BYTES;
                        int colorOrTextureID = pointDetailBuffer.getInt(pointDetailBufferIndex);
                        pointDetailBufferIndex += Integer.BYTES;
                        int tint = pointDetailBuffer.getInt(pointDetailBufferIndex);

                        ImGui.text(String.format("Radius: %f", radius));
                        ImGui.text(String.format("Color or texture ID: %#08X", colorOrTextureID));
                        ImGui.text(String.format("Tint: %d", tint));

                        ImGui.treePop();
                    }
                }
                ImGui.treePop();
            }
            ImGui.treePop();
        }
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

    private static void showWindowOptionsSection() {
        if (!IkGui.collapsingHeader("Window options")) {
            return;
        }
        // TODO(ches) fill this out
        IkGui.text("Not yet completed");
    }

    private static void showWidgetsSection() {
        if (!IkGui.collapsingHeader("Widgets")) {
            return;
        }
        // TODO(ches) fill this out
        IkGui.text("Not yet completed");
    }

    private static void showLayoutSection() {
        if (!IkGui.collapsingHeader("Layout & Scrolling")) {
            return;
        }
        // TODO(ches) fill this out
        IkGui.text("Not yet completed");
    }

    private static void showPopupsSection() {
        if (!IkGui.collapsingHeader("Popups & Modal Windows")) {
            return;
        }
        // TODO(ches) fill this out
        IkGui.text("Not yet completed");
    }

    private static void showTablesSection() {
        if (!IkGui.collapsingHeader("Tables & Columns")) {
            return;
        }
        // TODO(ches) fill this out
        IkGui.text("Not yet completed");
    }

    private static void showInputsSection() {
        if (!IkGui.collapsingHeader("Inputs & Focus")) {
            return;
        }
        // TODO(ches) fill this out
        IkGui.text("Not yet completed");
    }
}
