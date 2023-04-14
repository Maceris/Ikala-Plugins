package com.ikalagaming.rpg.windows;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.scripting.IkalaScriptLexer;
import com.ikalagaming.scripting.IkalaScriptParser;
import com.ikalagaming.scripting.IkalaScriptParser.CompilationUnitContext;
import com.ikalagaming.scripting.ast.AbstractSyntaxTree;
import com.ikalagaming.scripting.ast.CompilationUnit;
import com.ikalagaming.scripting.ast.visitors.OptimizationPass;
import com.ikalagaming.scripting.ast.visitors.TreeValidator;
import com.ikalagaming.scripting.ast.visitors.TypePreprocessor;
import com.ikalagaming.scripting.interpreter.InstructionGenerator;

import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.callback.ImStrConsumer;
import imgui.callback.ImStrSupplier;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;
import lombok.NonNull;
import org.antlr.v4.runtime.BufferedTokenStream;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.TokenStream;
import org.lwjgl.glfw.GLFW;

/**
 * A window for running lua scripts.
 *
 * @author Ches Burks
 *
 */
public class IkScriptDebugger implements GUIWindow {

	private ImString scriptContents;
	private ImString AST;

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
		ImGui.beginChild("Compiler Half", ImGui.getWindowWidth() / 2,
			ImGui.getWindowHeight(), false,
			ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse);
		ImGui.textWrapped(
			"This is a console for testing the Ikala scripting language");

		ImGui.separator();

		ImGui.inputTextMultiline("Script input", this.scriptContents);

		if (ImGui.button("Parse")) {
			this.parse();
		}
		ImGui.sameLine();
		if (ImGui.button("Copy AST to clipboard")) {
			ImGui.setClipboardText(this.AST.get());
		}

		if (this.AST.isNotEmpty()) {
			ImGui.separator();
			ImGui.beginChild("Abstract Syntax Tree");
			ImGui.textWrapped(this.AST.get());
			ImGui.endChild();
			ImGui.separator();
		}

		ImGui.endChild();
	}

	/**
	 * Draw the script runtime half.
	 */
	private void drawRuntimeHalf() {
		ImGui.beginChild("Runtime Half", 0, ImGui.getWindowHeight());
		ImGui.textWrapped("This is where we will debug the runtime");
		ImGui.endChild();
	}

	@Override
	public void handleGuiInput(@NonNull Scene scene, @NonNull Window window) {}

	/**
	 * Parse the input and put the resulting string tree in the output.
	 */
	private void parse() {
		CharStream stream = CharStreams.fromString(this.scriptContents.get());

		IkalaScriptLexer lexer = new IkalaScriptLexer(stream);
		TokenStream tokenStream = new BufferedTokenStream(lexer);
		IkalaScriptParser parser = new IkalaScriptParser(tokenStream);

		CompilationUnitContext context = parser.compilationUnit();
		CompilationUnit ast = AbstractSyntaxTree.process(context);

		TypePreprocessor processor = new TypePreprocessor();
		processor.processTreeTypes(ast);

		TreeValidator validator = new TreeValidator();
		if (validator.validate(ast)) {
			OptimizationPass optimizer = new OptimizationPass();
			optimizer.optimize(ast);

			InstructionGenerator gen = new InstructionGenerator();
			gen.process(ast);
			this.AST.set(ast.toString());
		}
		else {
			this.AST.set("Invalid tree!");
		}
	}

	@Override
	public void setup(@NonNull Scene scene) {
		this.scriptContents = new ImString(500);
		this.AST = new ImString(2000);
		this.scriptContents.set("for (int i = 0; i < 10; ++i){}");

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
