package com.ikalagaming.factory.kvt;

import com.ikalagaming.factory.FactoryPlugin;
import com.ikalagaming.factory.kvt.KVTParser.ArrayContext;
import com.ikalagaming.factory.kvt.KVTParser.CompilationUnitContext;
import com.ikalagaming.factory.kvt.KVTParser.EntryContext;
import com.ikalagaming.factory.kvt.KVTParser.LiteralContext;
import com.ikalagaming.factory.kvt.KVTParser.NodeContext;
import com.ikalagaming.factory.kvt.KVTParser.ValueContext;
import com.ikalagaming.scripting.ParserErrorListener;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.antlr.v4.runtime.BufferedTokenStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.TokenStream;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Utilities for converting a tree to and from a string format.
 *
 * @author Ches Burks
 *
 */
@Slf4j
public class TreeStringSerialization {

	/**
	 * Matches keys that do not require escaping.
	 */
	static final Pattern NORMAL_KEY_PATTERN =
		Pattern.compile("^[a-zA-Z0-9_\\-\\.\\+]+$");

	/**
	 * Handle the parsing of a character stream.
	 *
	 * @param input The input stream.
	 * @return The corresponding runtime.
	 */
	public static Optional<Node> parse(String input) {
		// Generate parse tree
		ParserErrorListener errorListener = new ParserErrorListener();

		KVTLexer lexer = new KVTLexer(CharStreams.fromString(input));
		lexer.removeErrorListeners();
		lexer.addErrorListener(errorListener);
		TokenStream tokenStream = new BufferedTokenStream(lexer);
		KVTParser parser = new KVTParser(tokenStream);
		parser.removeErrorListeners();
		parser.addErrorListener(errorListener);

		CompilationUnitContext context = parser.compilationUnit();
		if (errorListener.getErrorCount() > 0) {
			return Optional.empty();
		}

		Node root = new Node();

		TreeStringSerialization.processNodeContext(root, context.node());

		return Optional.of(root);
	}

	/**
	 * Process a literal and store it in the root.
	 *
	 * @param root The node that we are adding the literal node to.
	 * @param key The key for this node.
	 * @param context The parse tree information for the node we are processing.
	 */
	private static void processLiteral(@NonNull Node root, @NonNull String key,
		@NonNull LiteralContext context) {
		final String valueText = context.getText();
		if (context.IntegerLiteral() != null) {
			if (valueText.endsWith("b") || valueText.endsWith("B")) {
				root.addByte(key, Byte
					.parseByte(valueText.substring(0, valueText.length() - 1)));
				return;
			}
			if (valueText.endsWith("s") || valueText.endsWith("S")) {
				root.addShort(key, Short.parseShort(
					valueText.substring(0, valueText.length() - 1)));
				return;
			}
			if (valueText.endsWith("l") || valueText.endsWith("L")) {
				root.addLong(key, Long
					.parseLong(valueText.substring(0, valueText.length() - 1)));
				return;
			}
			root.addInteger(key, Integer.parseInt(valueText));
			return;
		}
		if (context.FloatingPointLiteral() != null) {
			if (valueText.endsWith("f") || valueText.endsWith("F")) {
				root.addFloat(key, Float.parseFloat(
					valueText.substring(0, valueText.length() - 1)));
				return;
			}
			if (valueText.endsWith("d") || valueText.endsWith("D")) {
				root.addDouble(key, Double.parseDouble(
					valueText.substring(0, valueText.length() - 1)));
				return;
			}
			root.addDouble(key, Double.parseDouble(valueText));
			return;
		}
		if (context.BooleanLiteral() != null) {
			root.addBoolean(key, Boolean.parseBoolean(valueText));
			return;
		}
		if (context.StringLiteral() != null) {
			root.addString(key, valueText.substring(1, valueText.length() - 1));
			return;
		}
		throw new IllegalArgumentException();
	}

	/**
	 * Process an ArrayNode and store the contents in the given parent node.
	 *
	 * @param root The node that we are adding the array node to.
	 * @param key The key for this node.
	 * @param context The parse tree information for the node we are processing.
	 */
	private static void processNodeArray(@NonNull Node root,
		@NonNull String key, @NonNull ArrayContext context) {

		List<ValueContext> values;
		if (context.arrayElements() == null
			|| context.arrayElements().value() == null) {
			values = new ArrayList<>();
		}
		else {
			values = context.arrayElements().value();
		}

		char typeIndicator = context.ArrayPrefix().getText().charAt(0);
		NodeType type = ArrayNode.toArrayLetter(typeIndicator);

		switch (type) {
			case BOOLEAN, BYTE, DOUBLE, FLOAT, INTEGER, LONG, NODE, SHORT,
				STRING:
				throw new IllegalArgumentException();
			case BOOLEAN_ARRAY:
				root.addBooleanArray(key,
					values.stream().map(ValueContext::getText)
						.map(Boolean::parseBoolean).toList());
				return;
			case BYTE_ARRAY:
				root.addByteArray(key, values.stream()
					.map(ValueContext::getText).map(Byte::parseByte).toList());
				return;
			case DOUBLE_ARRAY:
				root.addDoubleArray(key,
					values.stream().map(ValueContext::getText)
						.map(Double::parseDouble).toList());
				return;
			case FLOAT_ARRAY:
				root.addFloatArray(key,
					values.stream().map(ValueContext::getText)
						.map(Float::parseFloat).toList());
				return;
			case INTEGER_ARRAY:
				root.addIntegerArray(key,
					values.stream().map(ValueContext::getText)
						.map(Integer::parseInt).toList());
				return;
			case LONG_ARRAY:
				root.addLongArray(key, values.stream()
					.map(ValueContext::getText).map(Long::parseLong).toList());
				return;
			case SHORT_ARRAY:
				root.addShortArray(key,
					values.stream().map(ValueContext::getText)
						.map(Short::parseShort).toList());
				return;
			case NODE_ARRAY:
				root.addNodeArray(key);
				List<Node> list = root.getNodeArray(key);
				for (ValueContext entry : values) {
					Node node = new Node();
					list.add(node);
					TreeStringSerialization.processNodeContext(node,
						entry.node());
				}
				return;
			case STRING_ARRAY:
				root.addStringArray(key,
					values.stream().map(ValueContext::getText)
						.map(TreeStringSerialization::trimQuotes).toList());
		}
	}

	/**
	 * Process a node recursively.
	 *
	 * @param root The node we are processing and adding contents to.
	 * @param context The context of the node we are processing.
	 */
	private static void processNodeContext(@NonNull Node root,
		@NonNull NodeContext context) {
		try {
			if (context.entryList() != null) {
				final int size = context.entryList().entry().size();
				for (int i = 0; i < size; ++i) {
					EntryContext entry = context.entryList().entry(i);

					String keyText = TreeStringSerialization
						.trimQuotes(entry.key().getText());

					if (entry.value().literal() != null) {
						TreeStringSerialization.processLiteral(root, keyText,
							entry.value().literal());
					}
					else if (entry.value().array() != null) {
						TreeStringSerialization.processNodeArray(root, keyText,
							entry.value().array());
					}
					else if (entry.value().node() != null) {
						Node child = new Node();
						root.addNode(keyText, child);
						TreeStringSerialization.processNodeContext(child,
							entry.value().node());
					}
					else {
						throw new IllegalArgumentException();
					}
				}
			}

		}
		catch (IllegalArgumentException ignored) {
			TreeStringSerialization.log.warn(
				SafeResourceLoader.getStringFormatted("NODE_INVALID_FORMAT",
					FactoryPlugin.getResourceBundle(), context.getText()));
		}
	}

	/**
	 * Convert a tree to a string.
	 *
	 * @param tree The tree to convert.
	 * @return The string version.
	 */
	public static String toString(final @NonNull NodeTree tree) {
		return tree.toString();
	}

	private static String trimQuotes(final @NonNull String text) {
		if (text.length() > 2 && text.startsWith("\"") && text.endsWith("\"")) {
			return text.substring(1, text.length() - 1);
		}
		return text;
	}

	/**
	 * Private constructor so that this class is not instantiated.
	 */
	private TreeStringSerialization() {
		throw new UnsupportedOperationException(
			"This utility class should not be instantiated");
	}
}
