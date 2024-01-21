package com.ikalagaming.factory.kvt;

// Generated from KVTLexer.g4 by ANTLR 4.12.0

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.RuntimeMetaData;
import org.antlr.v4.runtime.Vocabulary;
import org.antlr.v4.runtime.VocabularyImpl;
import org.antlr.v4.runtime.atn.ATN;
import org.antlr.v4.runtime.atn.ATNDeserializer;
import org.antlr.v4.runtime.atn.LexerATNSimulator;
import org.antlr.v4.runtime.atn.PredictionContextCache;
import org.antlr.v4.runtime.dfa.DFA;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast",
	"CheckReturnValue"})
public class KVTLexer extends Lexer {
	static {
		RuntimeMetaData.checkVersion("4.12.0", RuntimeMetaData.VERSION);
	}

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int StringLiteral = 1, FloatingPointLiteral = 2,
		IntegerLiteral = 3, BooleanLiteral = 4, Identifier = 5, ArrayPrefix = 6,
		LBRACE = 7, RBRACE = 8, LBRACK = 9, RBRACK = 10, COMMA = 11, COLON = 12,
		WS = 13;
	public static String[] channelNames = {"DEFAULT_TOKEN_CHANNEL", "HIDDEN"};

	public static String[] modeNames = {"DEFAULT_MODE"};

	public static final String[] ruleNames = KVTLexer.makeRuleNames();

	private static final String[] _LITERAL_NAMES = KVTLexer.makeLiteralNames();

	private static final String[] _SYMBOLIC_NAMES =
		KVTLexer.makeSymbolicNames();

	public static final Vocabulary VOCABULARY =
		new VocabularyImpl(KVTLexer._LITERAL_NAMES, KVTLexer._SYMBOLIC_NAMES);

	/**
	 * @deprecated Use {@link #VOCABULARY} instead.
	 */
	@Deprecated
	public static final String[] tokenNames;

	static {
		tokenNames = new String[KVTLexer._SYMBOLIC_NAMES.length];
		for (int i = 0; i < KVTLexer.tokenNames.length; i++) {
			KVTLexer.tokenNames[i] = KVTLexer.VOCABULARY.getLiteralName(i);
			if (KVTLexer.tokenNames[i] == null) {
				KVTLexer.tokenNames[i] = KVTLexer.VOCABULARY.getSymbolicName(i);
			}

			if (KVTLexer.tokenNames[i] == null) {
				KVTLexer.tokenNames[i] = "<INVALID>";
			}
		}
	}
	public static final String _serializedATN =
		"\u0004\u0000\r\u00a4\u0006\uffff\uffff\u0002\u0000\u0007\u0000\u0002\u0001"
			+ "\u0007\u0001\u0002\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002\u0004"
			+ "\u0007\u0004\u0002\u0005\u0007\u0005\u0002\u0006\u0007\u0006\u0002\u0007"
			+ "\u0007\u0007\u0002\b\u0007\b\u0002\t\u0007\t\u0002\n\u0007\n\u0002\u000b"
			+ "\u0007\u000b\u0002\f\u0007\f\u0002\r\u0007\r\u0002\u000e\u0007\u000e\u0002"
			+ "\u000f\u0007\u000f\u0002\u0010\u0007\u0010\u0002\u0011\u0007\u0011\u0002"
			+ "\u0012\u0007\u0012\u0002\u0013\u0007\u0013\u0002\u0014\u0007\u0014\u0002"
			+ "\u0015\u0007\u0015\u0002\u0016\u0007\u0016\u0002\u0017\u0007\u0017\u0001"
			+ "\u0000\u0004\u00003\b\u0000\u000b\u0000\f\u00004\u0001\u0001\u0001\u0001"
			+ "\u0001\u0001\u0001\u0002\u0001\u0002\u0001\u0003\u0001\u0003\u0001\u0004"
			+ "\u0001\u0004\u0001\u0005\u0001\u0005\u0001\u0006\u0003\u0006C\b\u0006"
			+ "\u0001\u0006\u0001\u0006\u0001\u0007\u0001\u0007\u0003\u0007I\b\u0007"
			+ "\u0001\u0007\u0001\u0007\u0001\b\u0004\bN\b\b\u000b\b\f\bO\u0001\t\u0001"
			+ "\t\u0003\tT\b\t\u0001\n\u0001\n\u0001\n\u0001\u000b\u0001\u000b\u0001"
			+ "\u000b\u0003\u000b\\\b\u000b\u0001\u000b\u0003\u000b_\b\u000b\u0001\u000b"
			+ "\u0003\u000bb\b\u000b\u0001\u000b\u0003\u000be\b\u000b\u0001\u000b\u0001"
			+ "\u000b\u0001\u000b\u0003\u000bj\b\u000b\u0001\u000b\u0003\u000bm\b\u000b"
			+ "\u0001\u000b\u0001\u000b\u0001\u000b\u0003\u000br\b\u000b\u0001\u000b"
			+ "\u0001\u000b\u0001\u000b\u0003\u000bw\b\u000b\u0001\f\u0001\f\u0003\f"
			+ "{\b\f\u0001\r\u0001\r\u0001\r\u0001\r\u0001\r\u0001\r\u0001\r\u0001\r"
			+ "\u0001\r\u0003\r\u0086\b\r\u0001\u000e\u0004\u000e\u0089\b\u000e\u000b"
			+ "\u000e\f\u000e\u008a\u0001\u000f\u0001\u000f\u0001\u0010\u0001\u0010\u0001"
			+ "\u0010\u0001\u0011\u0001\u0011\u0001\u0012\u0001\u0012\u0001\u0013\u0001"
			+ "\u0013\u0001\u0014\u0001\u0014\u0001\u0015\u0001\u0015\u0001\u0016\u0001"
			+ "\u0016\u0001\u0017\u0004\u0017\u009f\b\u0017\u000b\u0017\f\u0017\u00a0"
			+ "\u0001\u0017\u0001\u0017\u0000\u0000\u0018\u0001\u0000\u0003\u0000\u0005"
			+ "\u0000\u0007\u0000\t\u0000\u000b\u0000\r\u0000\u000f\u0001\u0011\u0000"
			+ "\u0013\u0000\u0015\u0000\u0017\u0002\u0019\u0003\u001b\u0004\u001d\u0005"
			+ "\u001f\u0000!\u0006#\u0007%\b\'\t)\n+\u000b-\f/\r\u0001\u0000\n\u0001"
			+ "\u000009\u0002\u0000++--\u0002\u0000EEee\u0004\u0000DDFFddff\u0006\u0000"
			+ "BBLLSSbbllss\u0002\u0000\"\"\\\\\b\u0000\"\"\'\'\\\\bbffnnrrtt\u0006\u0000"
			+ "++-.09AZ__az\b\u0000BBDDFFIILLNNSTZZ\u0003\u0000\t\n\f\r  \u00ab\u0000"
			+ "\u000f\u0001\u0000\u0000\u0000\u0000\u0017\u0001\u0000\u0000\u0000\u0000"
			+ "\u0019\u0001\u0000\u0000\u0000\u0000\u001b\u0001\u0000\u0000\u0000\u0000"
			+ "\u001d\u0001\u0000\u0000\u0000\u0000!\u0001\u0000\u0000\u0000\u0000#\u0001"
			+ "\u0000\u0000\u0000\u0000%\u0001\u0000\u0000\u0000\u0000\'\u0001\u0000"
			+ "\u0000\u0000\u0000)\u0001\u0000\u0000\u0000\u0000+\u0001\u0000\u0000\u0000"
			+ "\u0000-\u0001\u0000\u0000\u0000\u0000/\u0001\u0000\u0000\u0000\u00012"
			+ "\u0001\u0000\u0000\u0000\u00036\u0001\u0000\u0000\u0000\u00059\u0001\u0000"
			+ "\u0000\u0000\u0007;\u0001\u0000\u0000\u0000\t=\u0001\u0000\u0000\u0000"
			+ "\u000b?\u0001\u0000\u0000\u0000\rB\u0001\u0000\u0000\u0000\u000fF\u0001"
			+ "\u0000\u0000\u0000\u0011M\u0001\u0000\u0000\u0000\u0013S\u0001\u0000\u0000"
			+ "\u0000\u0015U\u0001\u0000\u0000\u0000\u0017v\u0001\u0000\u0000\u0000\u0019"
			+ "x\u0001\u0000\u0000\u0000\u001b\u0085\u0001\u0000\u0000\u0000\u001d\u0088"
			+ "\u0001\u0000\u0000\u0000\u001f\u008c\u0001\u0000\u0000\u0000!\u008e\u0001"
			+ "\u0000\u0000\u0000#\u0091\u0001\u0000\u0000\u0000%\u0093\u0001\u0000\u0000"
			+ "\u0000\'\u0095\u0001\u0000\u0000\u0000)\u0097\u0001\u0000\u0000\u0000"
			+ "+\u0099\u0001\u0000\u0000\u0000-\u009b\u0001\u0000\u0000\u0000/\u009e"
			+ "\u0001\u0000\u0000\u000013\u0007\u0000\u0000\u000021\u0001\u0000\u0000"
			+ "\u000034\u0001\u0000\u0000\u000042\u0001\u0000\u0000\u000045\u0001\u0000"
			+ "\u0000\u00005\u0002\u0001\u0000\u0000\u000067\u0003\u0007\u0003\u0000"
			+ "78\u0003\r\u0006\u00008\u0004\u0001\u0000\u0000\u00009:\u0007\u0001\u0000"
			+ "\u0000:\u0006\u0001\u0000\u0000\u0000;<\u0007\u0002\u0000\u0000<\b\u0001"
			+ "\u0000\u0000\u0000=>\u0007\u0003\u0000\u0000>\n\u0001\u0000\u0000\u0000"
			+ "?@\u0007\u0004\u0000\u0000@\f\u0001\u0000\u0000\u0000AC\u0003\u0005\u0002"
			+ "\u0000BA\u0001\u0000\u0000\u0000BC\u0001\u0000\u0000\u0000CD\u0001\u0000"
			+ "\u0000\u0000DE\u0003\u0001\u0000\u0000E\u000e\u0001\u0000\u0000\u0000"
			+ "FH\u0005\"\u0000\u0000GI\u0003\u0011\b\u0000HG\u0001\u0000\u0000\u0000"
			+ "HI\u0001\u0000\u0000\u0000IJ\u0001\u0000\u0000\u0000JK\u0005\"\u0000\u0000"
			+ "K\u0010\u0001\u0000\u0000\u0000LN\u0003\u0013\t\u0000ML\u0001\u0000\u0000"
			+ "\u0000NO\u0001\u0000\u0000\u0000OM\u0001\u0000\u0000\u0000OP\u0001\u0000"
			+ "\u0000\u0000P\u0012\u0001\u0000\u0000\u0000QT\b\u0005\u0000\u0000RT\u0003"
			+ "\u0015\n\u0000SQ\u0001\u0000\u0000\u0000SR\u0001\u0000\u0000\u0000T\u0014"
			+ "\u0001\u0000\u0000\u0000UV\u0005\\\u0000\u0000VW\u0007\u0006\u0000\u0000"
			+ "W\u0016\u0001\u0000\u0000\u0000XY\u0003\r\u0006\u0000Y[\u0005.\u0000\u0000"
			+ "Z\\\u0003\u0001\u0000\u0000[Z\u0001\u0000\u0000\u0000[\\\u0001\u0000\u0000"
			+ "\u0000\\^\u0001\u0000\u0000\u0000]_\u0003\u0003\u0001\u0000^]\u0001\u0000"
			+ "\u0000\u0000^_\u0001\u0000\u0000\u0000_a\u0001\u0000\u0000\u0000`b\u0003"
			+ "\t\u0004\u0000a`\u0001\u0000\u0000\u0000ab\u0001\u0000\u0000\u0000bw\u0001"
			+ "\u0000\u0000\u0000ce\u0003\u0005\u0002\u0000dc\u0001\u0000\u0000\u0000"
			+ "de\u0001\u0000\u0000\u0000ef\u0001\u0000\u0000\u0000fg\u0005.\u0000\u0000"
			+ "gi\u0003\u0001\u0000\u0000hj\u0003\u0003\u0001\u0000ih\u0001\u0000\u0000"
			+ "\u0000ij\u0001\u0000\u0000\u0000jl\u0001\u0000\u0000\u0000km\u0003\t\u0004"
			+ "\u0000lk\u0001\u0000\u0000\u0000lm\u0001\u0000\u0000\u0000mw\u0001\u0000"
			+ "\u0000\u0000no\u0003\r\u0006\u0000oq\u0003\u0003\u0001\u0000pr\u0003\t"
			+ "\u0004\u0000qp\u0001\u0000\u0000\u0000qr\u0001\u0000\u0000\u0000rw\u0001"
			+ "\u0000\u0000\u0000st\u0003\r\u0006\u0000tu\u0003\t\u0004\u0000uw\u0001"
			+ "\u0000\u0000\u0000vX\u0001\u0000\u0000\u0000vd\u0001\u0000\u0000\u0000"
			+ "vn\u0001\u0000\u0000\u0000vs\u0001\u0000\u0000\u0000w\u0018\u0001\u0000"
			+ "\u0000\u0000xz\u0003\r\u0006\u0000y{\u0003\u000b\u0005\u0000zy\u0001\u0000"
			+ "\u0000\u0000z{\u0001\u0000\u0000\u0000{\u001a\u0001\u0000\u0000\u0000"
			+ "|}\u0005t\u0000\u0000}~\u0005r\u0000\u0000~\u007f\u0005u\u0000\u0000\u007f"
			+ "\u0086\u0005e\u0000\u0000\u0080\u0081\u0005f\u0000\u0000\u0081\u0082\u0005"
			+ "a\u0000\u0000\u0082\u0083\u0005l\u0000\u0000\u0083\u0084\u0005s\u0000"
			+ "\u0000\u0084\u0086\u0005e\u0000\u0000\u0085|\u0001\u0000\u0000\u0000\u0085"
			+ "\u0080\u0001\u0000\u0000\u0000\u0086\u001c\u0001\u0000\u0000\u0000\u0087"
			+ "\u0089\u0007\u0007\u0000\u0000\u0088\u0087\u0001\u0000\u0000\u0000\u0089"
			+ "\u008a\u0001\u0000\u0000\u0000\u008a\u0088\u0001\u0000\u0000\u0000\u008a"
			+ "\u008b\u0001\u0000\u0000\u0000\u008b\u001e\u0001\u0000\u0000\u0000\u008c"
			+ "\u008d\u0007\b\u0000\u0000\u008d \u0001\u0000\u0000\u0000\u008e\u008f"
			+ "\u0003\u001f\u000f\u0000\u008f\u0090\u0005;\u0000\u0000\u0090\"\u0001"
			+ "\u0000\u0000\u0000\u0091\u0092\u0005{\u0000\u0000\u0092$\u0001\u0000\u0000"
			+ "\u0000\u0093\u0094\u0005}\u0000\u0000\u0094&\u0001\u0000\u0000\u0000\u0095"
			+ "\u0096\u0005[\u0000\u0000\u0096(\u0001\u0000\u0000\u0000\u0097\u0098\u0005"
			+ "]\u0000\u0000\u0098*\u0001\u0000\u0000\u0000\u0099\u009a\u0005,\u0000"
			+ "\u0000\u009a,\u0001\u0000\u0000\u0000\u009b\u009c\u0005:\u0000\u0000\u009c"
			+ ".\u0001\u0000\u0000\u0000\u009d\u009f\u0007\t\u0000\u0000\u009e\u009d"
			+ "\u0001\u0000\u0000\u0000\u009f\u00a0\u0001\u0000\u0000\u0000\u00a0\u009e"
			+ "\u0001\u0000\u0000\u0000\u00a0\u00a1\u0001\u0000\u0000\u0000\u00a1\u00a2"
			+ "\u0001\u0000\u0000\u0000\u00a2\u00a3\u0006\u0017\u0000\u0000\u00a30\u0001"
			+ "\u0000\u0000\u0000\u0012\u00004BHOS[^adilqvz\u0085\u008a\u00a0\u0001\u0006"
			+ "\u0000\u0000";

	public static final ATN _ATN = new ATNDeserializer()
		.deserialize(KVTLexer._serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[KVTLexer._ATN.getNumberOfDecisions()];
		for (int i = 0; i < KVTLexer._ATN.getNumberOfDecisions(); i++) {
			KVTLexer._decisionToDFA[i] =
				new DFA(KVTLexer._ATN.getDecisionState(i), i);
		}
	}

	private static String[] makeLiteralNames() {
		return new String[] {null, null, null, null, null, null, null, "'{'",
			"'}'", "'['", "']'", "','", "':'"};
	}

	private static String[] makeRuleNames() {
		return new String[] {"Digits", "ExponentPart", "Sign",
			"ExponentIndicator", "FloatTypeSuffix", "IntegerTypeSuffix",
			"SignedInteger", "StringLiteral", "StringCharacters",
			"StringCharacter", "EscapeSequence", "FloatingPointLiteral",
			"IntegerLiteral", "BooleanLiteral", "Identifier",
			"ArrayPrefixLetter", "ArrayPrefix", "LBRACE", "RBRACE", "LBRACK",
			"RBRACK", "COMMA", "COLON", "WS"};
	}

	private static String[] makeSymbolicNames() {
		return new String[] {null, "StringLiteral", "FloatingPointLiteral",
			"IntegerLiteral", "BooleanLiteral", "Identifier", "ArrayPrefix",
			"LBRACE", "RBRACE", "LBRACK", "RBRACK", "COMMA", "COLON", "WS"};
	}

	public KVTLexer(CharStream input) {
		super(input);
		this._interp = new LexerATNSimulator(this, KVTLexer._ATN,
			KVTLexer._decisionToDFA, KVTLexer._sharedContextCache);
	}

	@Override
	public ATN getATN() {
		return KVTLexer._ATN;
	}

	@Override
	public String[] getChannelNames() {
		return KVTLexer.channelNames;
	}

	@Override
	public String getGrammarFileName() {
		return "KVTLexer.g4";
	}

	@Override
	public String[] getModeNames() {
		return KVTLexer.modeNames;
	}

	@Override
	public String[] getRuleNames() {
		return KVTLexer.ruleNames;
	}

	@Override
	public String getSerializedATN() {
		return KVTLexer._serializedATN;
	}

	@Override
	@Deprecated
	public String[] getTokenNames() {
		return KVTLexer.tokenNames;
	}

	@Override

	public Vocabulary getVocabulary() {
		return KVTLexer.VOCABULARY;
	}
}