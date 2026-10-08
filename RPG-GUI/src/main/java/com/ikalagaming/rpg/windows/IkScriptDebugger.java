package com.ikalagaming.rpg.windows;

import com.ikalagaming.graphics.frontend.gui.IkGui;
import com.ikalagaming.graphics.frontend.gui.data.IkString;
import com.ikalagaming.graphics.frontend.gui.enums.ColorType;
import com.ikalagaming.graphics.frontend.gui.enums.Condition;
import com.ikalagaming.graphics.frontend.gui.enums.StyleVariable;
import com.ikalagaming.graphics.frontend.gui.flags.ChildFlags;
import com.ikalagaming.graphics.frontend.gui.flags.WindowFlags;
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

    private IkString scriptContents;
    private IkString ast;
    private ScriptRuntime runtime;
    private TreeValidator validator;

    private InstructionGenerator generator;

    /** String versions of the instructions, calculated when we parse and cached here. */
    private List<String> instructionStrings;

    @Override
    public void draw() {
        IkGui.setNextWindowPos(477, 30, Condition.ONCE);
        IkGui.setNextWindowSize(1260, 590, Condition.ONCE);
        IkGui.begin(
                "Ikala Script Console",
                WindowFlags.NO_SCROLLBAR | WindowFlags.NO_SCROLL_WITH_MOUSE);

        drawCompilerHalf();
        IkGui.sameLine();
        drawRuntimeHalf();

        IkGui.end();
    }

    /** Draw the compiler debugger half. */
    private void drawCompilerHalf() {
        IkGui.beginChild(
                "Compiler Half",
                IkGui.getWindowWidth() / 3,
                IkGui.getWindowHeight(),
                ChildFlags.NONE,
                WindowFlags.NO_SCROLLBAR | WindowFlags.NO_SCROLL_WITH_MOUSE);
        IkGui.inputTextMultiline("Script input", scriptContents);

        if (IkGui.button("Parse")) {
            parse();
        }
        IkGui.sameLine();
        if (IkGui.button("Execute in background")) {
            ScriptManager.runScript(scriptContents.get());
        }
        IkGui.sameLine();
        if (IkGui.button("Copy AST to clipboard")) {
            IkGui.setClipboardText(ast.get());
        }

        if (ast.isNotEmpty()) {
            IkGui.separator();
            IkGui.beginChild("Abstract Syntax Tree");
            IkGui.textWrapped(ast.get());
            IkGui.endChild();
            IkGui.separator();
        }

        IkGui.endChild();
    }

    /** Draw the script runtime half. */
    private void drawRuntimeHalf() {
        IkGui.beginChild(
                "Runtime Half",
                0,
                IkGui.getWindowHeight(),
                ChildFlags.NONE,
                WindowFlags.NO_SCROLLBAR | WindowFlags.NO_SCROLL_WITH_MOUSE);
        if (runtime == null) {
            IkGui.endChild();
            return;
        }

        IkGui.text("Program Counter: " + runtime.getProgramCounter());
        IkGui.sameLine();
        if (IkGui.button("Step")) {
            runtime.step();
        }
        IkGui.text("Last comparison: " + runtime.getLastComparison());

        IkGui.pushStyleVarFloat2(
                StyleVariable.ITEM_SPACING, 0, IkGui.getStyle().variable.itemSpacing.y);

        final float height =
                IkGui.getContentRegionAvailableY() - IkGui.getTextLineHeightWithSpacing() * 2;
        IkGui.beginChild("Instructions", IkGui.getWindowWidth() / 3, height, ChildFlags.BORDERS);
        for (int i = 0; i < instructionStrings.size(); ++i) {
            if (i == runtime.getProgramCounter()) {
                IkGui.pushStyleColor(ColorType.TEXT, 0.15f, 0.47f, 1f, 1f);
            }
            IkGui.textWrapped(instructionStrings.get(i));
            if (i == runtime.getProgramCounter()) {
                IkGui.popStyleColor();
            }
        }
        // end Instructions
        IkGui.endChild();
        IkGui.sameLine();
        IkGui.beginChild("Registers", IkGui.getWindowWidth() / 3, height, ChildFlags.BORDERS);
        for (var entry : runtime.getSymbolTable().entrySet()) {
            IkGui.textWrapped(String.format("%s: %s", entry.getKey(), entry.getValue().toString()));
        }
        // end Registers
        IkGui.endChild();
        IkGui.sameLine();
        IkGui.beginChild("Stack", IkGui.getWindowWidth() / 3, height, ChildFlags.BORDERS);
        for (var entry : runtime.getStack()) {
            IkGui.textWrapped(IkScriptDebugger.format(entry));
        }
        // end Stack
        IkGui.endChild();
        IkGui.popStyleVar();

        // Runtime half
        IkGui.endChild();
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
        scriptContents = new IkString(5000);
        ast = new IkString(2000);

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
    }
}
