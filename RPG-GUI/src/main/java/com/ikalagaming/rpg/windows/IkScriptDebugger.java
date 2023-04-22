package com.ikalagaming.rpg.windows;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.scripting.IkalaScriptLexer;
import com.ikalagaming.scripting.IkalaScriptParser;
import com.ikalagaming.scripting.IkalaScriptParser.CompilationUnitContext;
import com.ikalagaming.scripting.ast.AbstractSyntaxTree;
import com.ikalagaming.scripting.ast.CompilationUnit;
import com.ikalagaming.scripting.ast.visitors.OptimizationPass;
import com.ikalagaming.scripting.ast.visitors.TreeValidator;
import com.ikalagaming.scripting.ast.visitors.TypePreprocessor;
import com.ikalagaming.scripting.interpreter.Instruction;
import com.ikalagaming.scripting.interpreter.InstructionGenerator;
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
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * A window for running lua scripts.
 *
 * @author Ches Burks
 *
 */
public class IkScriptDebugger implements GUIWindow {

	/**
	 * Format an instruction.
	 *
	 * @param i The instruction to format.
	 * @return The string form.
	 */
	private static String format(Instruction i) {
		return String.format("%s%s%s%s", i.type().toString(),
			i.firstLocation() == null ? ""
				: IkScriptDebugger.format(i.firstLocation()),
			i.secondLocation() == null ? ""
				: IkScriptDebugger.format(i.secondLocation()),
			i.targetLocation() == null ? ""
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
		}
		else if (loc.isChar()) {
			type = 'c';
		}
		else if (loc.isDouble()) {
			type = 'd';
		}
		else if (loc.isInt()) {
			type = 'i';
		}
		else if (loc.isString()) {
			type = 's';
		}

		String value;
		switch (loc.area()) {
			case STACK:
				value = "stack";
				break;
			case IMMEDIATE, VARIABLE:
			default:
				value = loc.value().toString();
				break;
		}
		return String.format(" (%c) %s", type, value);
	}

	/**
	 * Format a memory item.
	 *
	 * @param item The item to format.
	 * @return The string form
	 */
	private static String format(MemoryItem item) {
		return String.format("%s %s", item.type().getSimpleName(),
			item.value().toString());
	}

	private ImString scriptContents;
	private ImString ast;
	private ScriptRuntime runtime;
	private TreeValidator validator;

	private InstructionGenerator generator;

	/**
	 * String versions of the instructions, calculated when we parse and cached
	 * here.
	 */
	private List<String> instructionStrings;

	@Override
	public void draw() {
		ImGui.setNextWindowPos(477, 30, ImGuiCond.Once);
		ImGui.setNextWindowSize(1260, 590, ImGuiCond.Once);
		ImGui.begin("Ikala Script Console",
			ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse);

		this.drawCompilerHalf();
		ImGui.sameLine();
		this.drawRuntimeHalf();

		ImGui.end();
	}

	/**
	 * Draw the compiler debugger half.
	 */
	private void drawCompilerHalf() {
		ImGui.beginChild("Compiler Half", ImGui.getWindowWidth() / 3,
			ImGui.getWindowHeight(), false,
			ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse);
		ImGui.inputTextMultiline("Script input", this.scriptContents);

		if (ImGui.button("Parse")) {
			this.parse();
		}
		ImGui.sameLine();
		if (ImGui.button("Copy AST to clipboard")) {
			ImGui.setClipboardText(this.ast.get());
		}

		if (this.ast.isNotEmpty()) {
			ImGui.separator();
			ImGui.beginChild("Abstract Syntax Tree");
			ImGui.textWrapped(this.ast.get());
			ImGui.endChild();
			ImGui.separator();
		}

		ImGui.endChild();
	}

	/**
	 * Draw the script runtime half.
	 */
	private void drawRuntimeHalf() {
		ImGui.beginChild("Runtime Half", 0, ImGui.getWindowHeight(), false,
			ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse);
		if (this.runtime == null) {
			ImGui.endChild();
			return;
		}

		ImGui.text("Program Counter: " + this.runtime.getProgramCounter());
		ImGui.sameLine();
		if (ImGui.button("Step")) {
			this.runtime.step();
		}
		ImGui.text("Last comparison: " + this.runtime.getLastComparison());

		ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 0,
			ImGui.getStyle().getItemSpacingY());

		final float height = ImGui.getContentRegionAvailY()
			- ImGui.getTextLineHeightWithSpacing() * 2;
		ImGui.beginChild("Instructions", ImGui.getWindowWidth() / 3, height,
			true);
		for (int i = 0; i < this.instructionStrings.size(); ++i) {
			if (i == this.runtime.getProgramCounter()) {
				ImGui.pushStyleColor(ImGuiCol.Text, 0.15f, 0.47f, 1f, 1f);
			}
			ImGui.textWrapped(this.instructionStrings.get(i));
			if (i == this.runtime.getProgramCounter()) {
				ImGui.popStyleColor();
			}
		}
		// Instructions
		ImGui.endChild();
		ImGui.sameLine();
		ImGui.beginChild("Registers", ImGui.getWindowWidth() / 3, height, true);
		for (var entry : this.runtime.getSymbolTable().entrySet()) {
			ImGui.textWrapped(String.format("%s: %s", entry.getKey(),
				entry.getValue().toString()));
		}
		// Registers
		ImGui.endChild();
		ImGui.sameLine();
		ImGui.beginChild("Stack", ImGui.getWindowWidth() / 3, height, true);
		for (var entry : this.runtime.getStack()) {
			ImGui.textWrapped(IkScriptDebugger.format(entry));
		}
		// Stack
		ImGui.endChild();
		ImGui.popStyleVar();

		// Runtime half
		ImGui.endChild();
	}

	/**
	 * Parse the input and put the resulting string tree in the output.
	 */
	private void parse() {
		CharStream stream = CharStreams.fromString(this.scriptContents.get());

		IkalaScriptLexer lexer = new IkalaScriptLexer(stream);
		TokenStream tokenStream = new BufferedTokenStream(lexer);
		IkalaScriptParser parser = new IkalaScriptParser(tokenStream);

		CompilationUnitContext context = parser.compilationUnit();
		CompilationUnit program = AbstractSyntaxTree.process(context);

		TypePreprocessor processor = new TypePreprocessor();
		processor.processTreeTypes(program);

		if (this.validator.validate(program)) {
			OptimizationPass optimizer = new OptimizationPass();
			optimizer.optimize(program);

			InstructionGenerator gen = new InstructionGenerator();
			gen.process(program);
			this.ast.set(program.toString());
		}
		else {
			this.ast.set("Invalid tree!");
		}

		List<Instruction> instructions = this.generator.process(program);
		this.runtime = new ScriptRuntime(instructions);

		this.instructionStrings = new ArrayList<>();
		for (int i = 0; i < this.runtime.getInstructions().size(); ++i) {
			Instruction instr = this.runtime.getInstructions().get(i);
			this.instructionStrings.add(
				String.format("%04d: %s", i, IkScriptDebugger.format(instr)));
		}
	}

	@Override
	public void setup(@NonNull Scene scene) {
		this.scriptContents = new ImString(500);
		this.ast = new ImString(2000);
		this.scriptContents.set("for (int i = 0; i < 10; ++i){}");
		this.validator = new TreeValidator();
		this.generator = new InstructionGenerator();

		long windowHandle = GraphicsManager.getWindow().getWindowHandle();
		ImGuiIO io = ImGui.getIO();
		io.setGetClipboardTextFn(new ImStrSupplier() {
			@Override
			public String get() {
				final String clipboardString =
					GLFW.glfwGetClipboardString(windowHandle);
				return clipboardString != null ? clipboardString : "";
			}
		});

		io.setSetClipboardTextFn(new ImStrConsumer() {
			@Override
			public void accept(final String str) {
				GLFW.glfwSetClipboardString(windowHandle, str);
			}
		});

	}
}
