package com.ikalagaming.rpg.windows;

import static org.lwjgl.glfw.GLFW.*;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.scripting.IkalaScriptLexer;
import com.ikalagaming.scripting.IkalaScriptParser;
import com.ikalagaming.scripting.IkalaScriptParser.CompilationUnitContext;
import com.ikalagaming.scripting.ParserErrorListener;
import com.ikalagaming.scripting.ScriptManager;
import com.ikalagaming.scripting.ast.AbstractSyntaxTree;
import com.ikalagaming.scripting.ast.CompilationUnit;
import com.ikalagaming.scripting.ast.visitors.NodeAnnotationPass;
import com.ikalagaming.scripting.ast.visitors.OptimizationPass;
import com.ikalagaming.scripting.ast.visitors.TreeValidator;
import com.ikalagaming.scripting.ast.visitors.TypePreprocessor;
import com.ikalagaming.scripting.interpreter.Instruction;
import com.ikalagaming.scripting.interpreter.InstructionGenerator;
import com.ikalagaming.scripting.interpreter.InstructionType;
import com.ikalagaming.scripting.interpreter.MemArea;
import com.ikalagaming.scripting.interpreter.MemLocation;
import com.ikalagaming.scripting.interpreter.MemoryItem;
import com.ikalagaming.scripting.interpreter.ScriptRuntime;

import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.callback.ImStrConsumer;
import imgui.callback.ImStrSupplier;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;
import lombok.NonNull;
import org.antlr.v4.runtime.BufferedTokenStream;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.TokenStream;

import java.util.ArrayList;
import java.util.List;

/**
 * A window for running lua scripts.
 *
 * @author Ches Burks
 */
public class IkScriptDebugger implements GUIWindow {

    /**
     * Format an instruction.
     *
     * @param i The instruction to format.
     * @return The string form.
     */
    private static String format(Instruction i) {
        if (i.type() == InstructionType.CALL) {
            String object = i.firstLocation().area() == MemArea.IMMEDIATE ? "static." : "object.";
            return String.format(
                    "%s%s(%s)",
                    object,
                    i.firstLocation().value().toString(),
                    i.secondLocation().value().toString());
        }
        return String.format(
                "%s%s%s%s",
                i.type().toString(),
                i.firstLocation() == null ? "" : IkScriptDebugger.format(i.firstLocation()),
                i.secondLocation() == null ? "" : IkScriptDebugger.format(i.secondLocation()),
                i.targetLocation() == null
                        ? ""
                        : " ->" + IkScriptDebugger.format(i.targetLocation()));
    }

    /**
     * Format a memory location.
     *
     * @param loc The location to format.
     * @return The string form.
     */
    private static String format(MemLocation loc) {
        char type = '?';
        if (loc.isBoolean()) {
            type = 'b';
        } else if (loc.isChar()) {
            type = 'c';
        } else if (loc.isDouble()) {
            type = 'd';
        } else if (loc.isInt()) {
            type = 'i';
        } else if (loc.isString()) {
            type = 's';
        }

        String value;
        switch (loc.area()) {
            case STACK:
                value = "stack";
                break;
            case IMMEDIATE, VARIABLE:
            default:
                value = loc.value() == null ? "null" : loc.value().toString();
                break;
        }
        return String.format(" <%c> %s", type, value);
    }

    /**
     * Format a memory item.
     *
     * @param item The item to format.
     * @return The string form
     */
    private static String format(MemoryItem item) {
        return String.format("%s %s", item.type().getSimpleName(), item.value().toString());
    }

    private ImString scriptContents;
    private ImString ast;
    private ScriptRuntime runtime;
    private TreeValidator validator;

    private InstructionGenerator generator;

    /** String versions of the instructions, calculated when we parse and cached here. */
    private List<String> instructionStrings;

    @Override
    public void draw() {
        ImGui.setNextWindowPos(477, 30, ImGuiCond.Once);
        ImGui.setNextWindowSize(1260, 590, ImGuiCond.Once);
        ImGui.begin(
                "Ikala Script Console",
                ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse);

        drawCompilerHalf();
        ImGui.sameLine();
        drawRuntimeHalf();

        ImGui.end();
    }

    /** Draw the compiler debugger half. */
    private void drawCompilerHalf() {
        ImGui.beginChild(
                "Compiler Half",
                ImGui.getWindowWidth() / 3,
                ImGui.getWindowHeight(),
                false,
                ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse);
        ImGui.inputTextMultiline("Script input", scriptContents);

        if (ImGui.button("Parse")) {
            parse();
        }
        ImGui.sameLine();
        if (ImGui.button("Execute in background")) {
            ScriptManager.runScript(scriptContents.get());
        }
        ImGui.sameLine();
        if (ImGui.button("Copy AST to clipboard")) {
            ImGui.setClipboardText(ast.get());
        }

        if (ast.isNotEmpty()) {
            ImGui.separator();
            ImGui.beginChild("Abstract Syntax Tree");
            ImGui.textWrapped(ast.get());
            ImGui.endChild();
            ImGui.separator();
        }

        ImGui.endChild();
    }

    /** Draw the script runtime half. */
    private void drawRuntimeHalf() {
        ImGui.beginChild(
                "Runtime Half",
                0,
                ImGui.getWindowHeight(),
                false,
                ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse);
        if (runtime == null) {
            ImGui.endChild();
            return;
        }

        ImGui.text("Program Counter: " + runtime.getProgramCounter());
        ImGui.sameLine();
        if (ImGui.button("Step")) {
            runtime.step();
        }
        ImGui.text("Last comparison: " + runtime.getLastComparison());

        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 0, ImGui.getStyle().getItemSpacingY());

        final float height =
                ImGui.getContentRegionAvailY() - ImGui.getTextLineHeightWithSpacing() * 2;
        ImGui.beginChild("Instructions", ImGui.getWindowWidth() / 3, height, true);
        for (int i = 0; i < instructionStrings.size(); ++i) {
            if (i == runtime.getProgramCounter()) {
                ImGui.pushStyleColor(ImGuiCol.Text, 0.15f, 0.47f, 1f, 1f);
            }
            ImGui.textWrapped(instructionStrings.get(i));
            if (i == runtime.getProgramCounter()) {
                ImGui.popStyleColor();
            }
        }
        // end Instructions
        ImGui.endChild();
        ImGui.sameLine();
        ImGui.beginChild("Registers", ImGui.getWindowWidth() / 3, height, true);
        for (var entry : runtime.getSymbolTable().entrySet()) {
            ImGui.textWrapped(String.format("%s: %s", entry.getKey(), entry.getValue().toString()));
        }
        // end Registers
        ImGui.endChild();
        ImGui.sameLine();
        ImGui.beginChild("Stack", ImGui.getWindowWidth() / 3, height, true);
        for (var entry : runtime.getStack()) {
            ImGui.textWrapped(IkScriptDebugger.format(entry));
        }
        // end Stack
        ImGui.endChild();
        ImGui.popStyleVar();

        // Runtime half
        ImGui.endChild();
    }

    /** Parse the input and put the resulting string tree in the output. */
    private void parse() {
        final String INVALID = "Invalid tree!";
        CharStream stream = CharStreams.fromString(scriptContents.get());

        ParserErrorListener errorListener = new ParserErrorListener();

        IkalaScriptLexer lexer = new IkalaScriptLexer(stream);
        lexer.removeErrorListeners();
        lexer.addErrorListener(errorListener);
        TokenStream tokenStream = new BufferedTokenStream(lexer);
        IkalaScriptParser parser = new IkalaScriptParser(tokenStream);
        parser.removeErrorListeners();
        parser.addErrorListener(errorListener);

        CompilationUnitContext context = parser.compilationUnit();
        if (errorListener.getErrorCount() > 0) {
            ast.set(INVALID);
            return;
        }

        CompilationUnit program = AbstractSyntaxTree.process(context);
        if (program.isInvalid()) {
            ast.set(INVALID);
            return;
        }

        TypePreprocessor processor = new TypePreprocessor();
        processor.processTreeTypes(program);

        List<Instruction> instructions;

        if (validator.validate(program)) {
            OptimizationPass optimizer = new OptimizationPass();
            optimizer.optimize(program);

            NodeAnnotationPass annotator = new NodeAnnotationPass();
            annotator.annotate(program);

            instructions = generator.process(program);
            ast.set(program.toString());
        } else {
            ast.set(INVALID);
            return;
        }

        runtime = new ScriptRuntime(instructions);

        instructionStrings = new ArrayList<>();
        for (int i = 0; i < runtime.getInstructions().size(); ++i) {
            Instruction instr = runtime.getInstructions().get(i);
            instructionStrings.add(String.format("%04d: %s", i, IkScriptDebugger.format(instr)));
        }
    }

    @Override
    public void setup(@NonNull Scene scene) {
        scriptContents = new ImString(5000);
        ast = new ImString(2000);

        String contents =
                """
			clearDialogue();
			leftChat("Hi!");
			rightChat("... Oh");
			option("okay");
			option("stop trying to make fetch happen!");
			showDialogue();
			yield("Dialogue");
			int choice = getLastDialogueSelection();
			clearDialogue();

			switch(choice) {
				case 0:
					goto okay;
				case 1:
					goto fetch;
				default:
					goto end;
			}

			okay:
			rightChat("Okay.");
			leftChat("Wow, rude");
			goto end;

			fetch:
			rightChat("Stop trying to make fetch happen!");
			leftChat("Don't tell me what to do, mom!");
			goto end;

			end:
			option("Leave");
			yield("Dialogue");
			hideDialogue();
			""";
        scriptContents.set(contents);
        validator = new TreeValidator();
        generator = new InstructionGenerator();

        long windowHandle = GraphicsManager.getWindow().getWindowHandle();
        ImGuiIO io = ImGui.getIO();
        io.setGetClipboardTextFn(
                new ImStrSupplier() {
                    @Override
                    public String get() {
                        final String clipboardString = glfwGetClipboardString(windowHandle);
                        return clipboardString != null ? clipboardString : "";
                    }
                });

        io.setSetClipboardTextFn(
                new ImStrConsumer() {
                    @Override
                    public void accept(final String str) {
                        glfwSetClipboardString(windowHandle, str);
                    }
                });
    }
}
