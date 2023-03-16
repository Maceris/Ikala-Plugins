package com.ikalagaming.rpg.windows;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.scripting.IkalaScriptLexer;
import com.ikalagaming.scripting.IkalaScriptParser;
import com.ikalagaming.scripting.IkalaScriptParser.CompilationUnitContext;
import com.ikalagaming.scripting.ast.AbstractSyntaxTree;

import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.callback.ImStrConsumer;
import imgui.callback.ImStrSupplier;
import imgui.flag.ImGuiCond;
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
	private ImString parsedTree;

	@Override
	public void draw() {
		ImGui.setNextWindowPos(600, 15, ImGuiCond.Once);
		ImGui.setNextWindowSize(830, 590, ImGuiCond.Once);
		ImGui.begin("Ikala Script Console");

		ImGui.textWrapped(
			"This is a console for testing the Ikala scripting language");

		ImGui.separator();

		ImGui.inputTextMultiline("Script input", this.scriptContents);

		if (ImGui.button("Parse")) {
			this.parse();
		}

		ImGui.sameLine();
		if (ImGui.button("Copy result to clipboard")) {
			ImGui.setClipboardText(parsedTree.get());
		}

		ImGui.inputTextMultiline("Parsed", this.parsedTree);

		ImGui.end();
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

		this.parsedTree.set(context.toStringTree(parser));
		
		AbstractSyntaxTree.process(context);
	}

	@Override
	public void setup(@NonNull Scene scene) {
		this.scriptContents = new ImString(500);
		this.parsedTree = new ImString(2000);

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
