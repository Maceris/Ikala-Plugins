package com.ikalagaming.factory.kvt;

// Generated from KVTParser.g4 by ANTLR 4.12.0

import org.antlr.v4.runtime.NoViableAltException;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.RuntimeMetaData;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.Vocabulary;
import org.antlr.v4.runtime.VocabularyImpl;
import org.antlr.v4.runtime.atn.ATN;
import org.antlr.v4.runtime.atn.ATNDeserializer;
import org.antlr.v4.runtime.atn.ParserATNSimulator;
import org.antlr.v4.runtime.atn.PredictionContextCache;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.tree.ParseTreeListener;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.List;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast",
	"CheckReturnValue"})
public class KVTParser extends Parser {
	@SuppressWarnings("CheckReturnValue")
	public static class ArrayContext extends ParserRuleContext {
		public ArrayContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}

		public ArrayElementsContext arrayElements() {
			return this.getRuleContext(ArrayElementsContext.class, 0);
		}

		public TerminalNode ArrayPrefix() {
			return this.getToken(KVTParser.ArrayPrefix, 0);
		}

		@Override
		public void enterRule(ParseTreeListener listener) {
			if (listener instanceof KVTParserListener) {
				((KVTParserListener) listener).enterArray(this);
			}
		}

		@Override
		public void exitRule(ParseTreeListener listener) {
			if (listener instanceof KVTParserListener) {
				((KVTParserListener) listener).exitArray(this);
			}
		}

		@Override
		public int getRuleIndex() {
			return KVTParser.RULE_array;
		}

		public TerminalNode LBRACK() {
			return this.getToken(KVTParser.LBRACK, 0);
		}

		public TerminalNode RBRACK() {
			return this.getToken(KVTParser.RBRACK, 0);
		}
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ArrayElementsContext extends ParserRuleContext {
		public ArrayElementsContext(ParserRuleContext parent,
			int invokingState) {
			super(parent, invokingState);
		}

		public List<TerminalNode> COMMA() {
			return this.getTokens(KVTParser.COMMA);
		}

		public TerminalNode COMMA(int i) {
			return this.getToken(KVTParser.COMMA, i);
		}

		@Override
		public void enterRule(ParseTreeListener listener) {
			if (listener instanceof KVTParserListener) {
				((KVTParserListener) listener).enterArrayElements(this);
			}
		}

		@Override
		public void exitRule(ParseTreeListener listener) {
			if (listener instanceof KVTParserListener) {
				((KVTParserListener) listener).exitArrayElements(this);
			}
		}

		@Override
		public int getRuleIndex() {
			return KVTParser.RULE_arrayElements;
		}

		public List<ValueContext> value() {
			return this.getRuleContexts(ValueContext.class);
		}

		public ValueContext value(int i) {
			return this.getRuleContext(ValueContext.class, i);
		}
	}

	@SuppressWarnings("CheckReturnValue")
	public static class CompilationUnitContext extends ParserRuleContext {
		public CompilationUnitContext(ParserRuleContext parent,
			int invokingState) {
			super(parent, invokingState);
		}

		@Override
		public void enterRule(ParseTreeListener listener) {
			if (listener instanceof KVTParserListener) {
				((KVTParserListener) listener).enterCompilationUnit(this);
			}
		}

		public TerminalNode EOF() {
			return this.getToken(Recognizer.EOF, 0);
		}

		@Override
		public void exitRule(ParseTreeListener listener) {
			if (listener instanceof KVTParserListener) {
				((KVTParserListener) listener).exitCompilationUnit(this);
			}
		}

		@Override
		public int getRuleIndex() {
			return KVTParser.RULE_compilationUnit;
		}

		public NodeContext node() {
			return this.getRuleContext(NodeContext.class, 0);
		}
	}

	@SuppressWarnings("CheckReturnValue")
	public static class EntryContext extends ParserRuleContext {
		public EntryContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}

		public TerminalNode COLON() {
			return this.getToken(KVTParser.COLON, 0);
		}

		@Override
		public void enterRule(ParseTreeListener listener) {
			if (listener instanceof KVTParserListener) {
				((KVTParserListener) listener).enterEntry(this);
			}
		}

		@Override
		public void exitRule(ParseTreeListener listener) {
			if (listener instanceof KVTParserListener) {
				((KVTParserListener) listener).exitEntry(this);
			}
		}

		@Override
		public int getRuleIndex() {
			return KVTParser.RULE_entry;
		}

		public KeyContext key() {
			return this.getRuleContext(KeyContext.class, 0);
		}

		public ValueContext value() {
			return this.getRuleContext(ValueContext.class, 0);
		}
	}

	@SuppressWarnings("CheckReturnValue")
	public static class EntryListContext extends ParserRuleContext {
		public EntryListContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}

		public List<TerminalNode> COMMA() {
			return this.getTokens(KVTParser.COMMA);
		}

		public TerminalNode COMMA(int i) {
			return this.getToken(KVTParser.COMMA, i);
		}

		@Override
		public void enterRule(ParseTreeListener listener) {
			if (listener instanceof KVTParserListener) {
				((KVTParserListener) listener).enterEntryList(this);
			}
		}

		public List<EntryContext> entry() {
			return this.getRuleContexts(EntryContext.class);
		}

		public EntryContext entry(int i) {
			return this.getRuleContext(EntryContext.class, i);
		}

		@Override
		public void exitRule(ParseTreeListener listener) {
			if (listener instanceof KVTParserListener) {
				((KVTParserListener) listener).exitEntryList(this);
			}
		}

		@Override
		public int getRuleIndex() {
			return KVTParser.RULE_entryList;
		}
	}

	@SuppressWarnings("CheckReturnValue")
	public static class KeyContext extends ParserRuleContext {
		public KeyContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}

		@Override
		public void enterRule(ParseTreeListener listener) {
			if (listener instanceof KVTParserListener) {
				((KVTParserListener) listener).enterKey(this);
			}
		}

		@Override
		public void exitRule(ParseTreeListener listener) {
			if (listener instanceof KVTParserListener) {
				((KVTParserListener) listener).exitKey(this);
			}
		}

		@Override
		public int getRuleIndex() {
			return KVTParser.RULE_key;
		}

		public TerminalNode Identifier() {
			return this.getToken(KVTParser.Identifier, 0);
		}

		public TerminalNode StringLiteral() {
			return this.getToken(KVTParser.StringLiteral, 0);
		}
	}

	@SuppressWarnings("CheckReturnValue")
	public static class LiteralContext extends ParserRuleContext {
		public LiteralContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}

		public TerminalNode BooleanLiteral() {
			return this.getToken(KVTParser.BooleanLiteral, 0);
		}

		@Override
		public void enterRule(ParseTreeListener listener) {
			if (listener instanceof KVTParserListener) {
				((KVTParserListener) listener).enterLiteral(this);
			}
		}

		@Override
		public void exitRule(ParseTreeListener listener) {
			if (listener instanceof KVTParserListener) {
				((KVTParserListener) listener).exitLiteral(this);
			}
		}

		public TerminalNode FloatingPointLiteral() {
			return this.getToken(KVTParser.FloatingPointLiteral, 0);
		}

		@Override
		public int getRuleIndex() {
			return KVTParser.RULE_literal;
		}

		public TerminalNode IntegerLiteral() {
			return this.getToken(KVTParser.IntegerLiteral, 0);
		}

		public TerminalNode StringLiteral() {
			return this.getToken(KVTParser.StringLiteral, 0);
		}
	}

	@SuppressWarnings("CheckReturnValue")
	public static class NodeContext extends ParserRuleContext {
		public NodeContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}

		@Override
		public void enterRule(ParseTreeListener listener) {
			if (listener instanceof KVTParserListener) {
				((KVTParserListener) listener).enterNode(this);
			}
		}

		public EntryListContext entryList() {
			return this.getRuleContext(EntryListContext.class, 0);
		}

		@Override
		public void exitRule(ParseTreeListener listener) {
			if (listener instanceof KVTParserListener) {
				((KVTParserListener) listener).exitNode(this);
			}
		}

		@Override
		public int getRuleIndex() {
			return KVTParser.RULE_node;
		}

		public TerminalNode LBRACE() {
			return this.getToken(KVTParser.LBRACE, 0);
		}

		public TerminalNode RBRACE() {
			return this.getToken(KVTParser.RBRACE, 0);
		}
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ValueContext extends ParserRuleContext {
		public ValueContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}

		public ArrayContext array() {
			return this.getRuleContext(ArrayContext.class, 0);
		}

		@Override
		public void enterRule(ParseTreeListener listener) {
			if (listener instanceof KVTParserListener) {
				((KVTParserListener) listener).enterValue(this);
			}
		}

		@Override
		public void exitRule(ParseTreeListener listener) {
			if (listener instanceof KVTParserListener) {
				((KVTParserListener) listener).exitValue(this);
			}
		}

		@Override
		public int getRuleIndex() {
			return KVTParser.RULE_value;
		}

		public LiteralContext literal() {
			return this.getRuleContext(LiteralContext.class, 0);
		}

		public NodeContext node() {
			return this.getRuleContext(NodeContext.class, 0);
		}
	}

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
	public static final int RULE_literal = 0, RULE_compilationUnit = 1,
		RULE_node = 2, RULE_entryList = 3, RULE_entry = 4, RULE_key = 5,
		RULE_value = 6, RULE_array = 7, RULE_arrayElements = 8;

	public static final String[] ruleNames = KVTParser.makeRuleNames();

	private static final String[] _LITERAL_NAMES = KVTParser.makeLiteralNames();

	private static final String[] _SYMBOLIC_NAMES =
		KVTParser.makeSymbolicNames();

	public static final Vocabulary VOCABULARY =
		new VocabularyImpl(KVTParser._LITERAL_NAMES, KVTParser._SYMBOLIC_NAMES);

	/**
	 * @deprecated Use {@link #VOCABULARY} instead.
	 */
	@Deprecated
	public static final String[] tokenNames;

	static {
		tokenNames = new String[KVTParser._SYMBOLIC_NAMES.length];
		for (int i = 0; i < KVTParser.tokenNames.length; i++) {
			KVTParser.tokenNames[i] = KVTParser.VOCABULARY.getLiteralName(i);
			if (KVTParser.tokenNames[i] == null) {
				KVTParser.tokenNames[i] =
					KVTParser.VOCABULARY.getSymbolicName(i);
			}

			if (KVTParser.tokenNames[i] == null) {
				KVTParser.tokenNames[i] = "<INVALID>";
			}
		}
	}

	public static final String _serializedATN =
		"\u0004\u0001\r@\u0002\u0000\u0007\u0000\u0002\u0001\u0007\u0001\u0002"
			+ "\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002\u0004\u0007\u0004\u0002"
			+ "\u0005\u0007\u0005\u0002\u0006\u0007\u0006\u0002\u0007\u0007\u0007\u0002"
			+ "\b\u0007\b\u0001\u0000\u0001\u0000\u0001\u0001\u0001\u0001\u0001\u0001"
			+ "\u0001\u0002\u0001\u0002\u0003\u0002\u001a\b\u0002\u0001\u0002\u0001\u0002"
			+ "\u0001\u0003\u0001\u0003\u0001\u0003\u0005\u0003!\b\u0003\n\u0003\f\u0003"
			+ "$\t\u0003\u0001\u0004\u0001\u0004\u0001\u0004\u0001\u0004\u0001\u0005"
			+ "\u0001\u0005\u0001\u0006\u0001\u0006\u0001\u0006\u0003\u0006/\b\u0006"
			+ "\u0001\u0007\u0001\u0007\u0001\u0007\u0003\u00074\b\u0007\u0001\u0007"
			+ "\u0001\u0007\u0001\b\u0001\b\u0001\b\u0005\b;\b\b\n\b\f\b>\t\b\u0001\b"
			+ "\u0000\u0000\t\u0000\u0002\u0004\u0006\b\n\f\u000e\u0010\u0000\u0002\u0001"
			+ "\u0000\u0001\u0004\u0002\u0000\u0001\u0001\u0005\u0005<\u0000\u0012\u0001"
			+ "\u0000\u0000\u0000\u0002\u0014\u0001\u0000\u0000\u0000\u0004\u0017\u0001"
			+ "\u0000\u0000\u0000\u0006\u001d\u0001\u0000\u0000\u0000\b%\u0001\u0000"
			+ "\u0000\u0000\n)\u0001\u0000\u0000\u0000\f.\u0001\u0000\u0000\u0000\u000e"
			+ "0\u0001\u0000\u0000\u0000\u00107\u0001\u0000\u0000\u0000\u0012\u0013\u0007"
			+ "\u0000\u0000\u0000\u0013\u0001\u0001\u0000\u0000\u0000\u0014\u0015\u0003"
			+ "\u0004\u0002\u0000\u0015\u0016\u0005\u0000\u0000\u0001\u0016\u0003\u0001"
			+ "\u0000\u0000\u0000\u0017\u0019\u0005\u0007\u0000\u0000\u0018\u001a\u0003"
			+ "\u0006\u0003\u0000\u0019\u0018\u0001\u0000\u0000\u0000\u0019\u001a\u0001"
			+ "\u0000\u0000\u0000\u001a\u001b\u0001\u0000\u0000\u0000\u001b\u001c\u0005"
			+ "\b\u0000\u0000\u001c\u0005\u0001\u0000\u0000\u0000\u001d\"\u0003\b\u0004"
			+ "\u0000\u001e\u001f\u0005\u000b\u0000\u0000\u001f!\u0003\b\u0004\u0000"
			+ " \u001e\u0001\u0000\u0000\u0000!$\u0001\u0000\u0000\u0000\" \u0001\u0000"
			+ "\u0000\u0000\"#\u0001\u0000\u0000\u0000#\u0007\u0001\u0000\u0000\u0000"
			+ "$\"\u0001\u0000\u0000\u0000%&\u0003\n\u0005\u0000&\'\u0005\f\u0000\u0000"
			+ "\'(\u0003\f\u0006\u0000(\t\u0001\u0000\u0000\u0000)*\u0007\u0001\u0000"
			+ "\u0000*\u000b\u0001\u0000\u0000\u0000+/\u0003\u0000\u0000\u0000,/\u0003"
			+ "\u000e\u0007\u0000-/\u0003\u0004\u0002\u0000.+\u0001\u0000\u0000\u0000"
			+ ".,\u0001\u0000\u0000\u0000.-\u0001\u0000\u0000\u0000/\r\u0001\u0000\u0000"
			+ "\u000001\u0005\t\u0000\u000013\u0005\u0006\u0000\u000024\u0003\u0010\b"
			+ "\u000032\u0001\u0000\u0000\u000034\u0001\u0000\u0000\u000045\u0001\u0000"
			+ "\u0000\u000056\u0005\n\u0000\u00006\u000f\u0001\u0000\u0000\u00007<\u0003"
			+ "\f\u0006\u000089\u0005\u000b\u0000\u00009;\u0003\f\u0006\u0000:8\u0001"
			+ "\u0000\u0000\u0000;>\u0001\u0000\u0000\u0000<:\u0001\u0000\u0000\u0000"
			+ "<=\u0001\u0000\u0000\u0000=\u0011\u0001\u0000\u0000\u0000><\u0001\u0000"
			+ "\u0000\u0000\u0005\u0019\".3<";

	public static final ATN _ATN = new ATNDeserializer()
		.deserialize(KVTParser._serializedATN.toCharArray());

	static {
		_decisionToDFA = new DFA[KVTParser._ATN.getNumberOfDecisions()];
		for (int i = 0; i < KVTParser._ATN.getNumberOfDecisions(); i++) {
			KVTParser._decisionToDFA[i] =
				new DFA(KVTParser._ATN.getDecisionState(i), i);
		}
	}

	private static String[] makeLiteralNames() {
		return new String[] {null, null, null, null, null, null, null, "'{'",
			"'}'", "'['", "']'", "','", "':'"};
	}

	private static String[] makeRuleNames() {
		return new String[] {"literal", "compilationUnit", "node", "entryList",
			"entry", "key", "value", "array", "arrayElements"};
	}

	private static String[] makeSymbolicNames() {
		return new String[] {null, "StringLiteral", "FloatingPointLiteral",
			"IntegerLiteral", "BooleanLiteral", "Identifier", "ArrayPrefix",
			"LBRACE", "RBRACE", "LBRACK", "RBRACK", "COMMA", "COLON", "WS"};
	}

	public KVTParser(TokenStream input) {
		super(input);
		this._interp = new ParserATNSimulator(this, KVTParser._ATN,
			KVTParser._decisionToDFA, KVTParser._sharedContextCache);
	}

	public final ArrayContext array() throws RecognitionException {
		ArrayContext _localctx = new ArrayContext(this._ctx, this.getState());
		this.enterRule(_localctx, 14, KVTParser.RULE_array);
		int _la;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
				this.setState(48);
				this.match(KVTParser.LBRACK);
				this.setState(49);
				this.match(KVTParser.ArrayPrefix);
				this.setState(51);
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if ((((_la) & ~0x3f) == 0 && ((1L << _la) & 670L) != 0)) {
					{
						this.setState(50);
						this.arrayElements();
					}
				}

				this.setState(53);
				this.match(KVTParser.RBRACK);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			this._errHandler.reportError(this, re);
			this._errHandler.recover(this, re);
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}

	public final ArrayElementsContext arrayElements()
		throws RecognitionException {
		ArrayElementsContext _localctx =
			new ArrayElementsContext(this._ctx, this.getState());
		this.enterRule(_localctx, 16, KVTParser.RULE_arrayElements);
		int _la;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
				this.setState(55);
				this.value();
				this.setState(60);
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				while (_la == KVTParser.COMMA) {
					{
						{
							this.setState(56);
							this.match(KVTParser.COMMA);
							this.setState(57);
							this.value();
						}
					}
					this.setState(62);
					this._errHandler.sync(this);
					_la = this._input.LA(1);
				}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			this._errHandler.reportError(this, re);
			this._errHandler.recover(this, re);
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}

	public final CompilationUnitContext compilationUnit()
		throws RecognitionException {
		CompilationUnitContext _localctx =
			new CompilationUnitContext(this._ctx, this.getState());
		this.enterRule(_localctx, 2, KVTParser.RULE_compilationUnit);
		try {
			this.enterOuterAlt(_localctx, 1);
			{
				this.setState(20);
				this.node();
				this.setState(21);
				this.match(Recognizer.EOF);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			this._errHandler.reportError(this, re);
			this._errHandler.recover(this, re);
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}

	public final EntryContext entry() throws RecognitionException {
		EntryContext _localctx = new EntryContext(this._ctx, this.getState());
		this.enterRule(_localctx, 8, KVTParser.RULE_entry);
		try {
			this.enterOuterAlt(_localctx, 1);
			{
				this.setState(37);
				this.key();
				this.setState(38);
				this.match(KVTParser.COLON);
				this.setState(39);
				this.value();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			this._errHandler.reportError(this, re);
			this._errHandler.recover(this, re);
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}

	public final EntryListContext entryList() throws RecognitionException {
		EntryListContext _localctx =
			new EntryListContext(this._ctx, this.getState());
		this.enterRule(_localctx, 6, KVTParser.RULE_entryList);
		int _la;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
				this.setState(29);
				this.entry();
				this.setState(34);
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				while (_la == KVTParser.COMMA) {
					{
						{
							this.setState(30);
							this.match(KVTParser.COMMA);
							this.setState(31);
							this.entry();
						}
					}
					this.setState(36);
					this._errHandler.sync(this);
					_la = this._input.LA(1);
				}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			this._errHandler.reportError(this, re);
			this._errHandler.recover(this, re);
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}

	@Override
	public ATN getATN() {
		return KVTParser._ATN;
	}

	@Override
	public String getGrammarFileName() {
		return "KVTParser.g4";
	}

	@Override
	public String[] getRuleNames() {
		return KVTParser.ruleNames;
	}

	@Override
	public String getSerializedATN() {
		return KVTParser._serializedATN;
	}

	@Override
	@Deprecated
	public String[] getTokenNames() {
		return KVTParser.tokenNames;
	}

	@Override

	public Vocabulary getVocabulary() {
		return KVTParser.VOCABULARY;
	}

	public final KeyContext key() throws RecognitionException {
		KeyContext _localctx = new KeyContext(this._ctx, this.getState());
		this.enterRule(_localctx, 10, KVTParser.RULE_key);
		int _la;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
				this.setState(41);
				_la = this._input.LA(1);
				if (!(_la == KVTParser.StringLiteral
					|| _la == KVTParser.Identifier)) {
					this._errHandler.recoverInline(this);
				}
				else {
					if (this._input.LA(1) == Token.EOF) {
						this.matchedEOF = true;
					}
					this._errHandler.reportMatch(this);
					this.consume();
				}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			this._errHandler.reportError(this, re);
			this._errHandler.recover(this, re);
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}

	public final LiteralContext literal() throws RecognitionException {
		LiteralContext _localctx =
			new LiteralContext(this._ctx, this.getState());
		this.enterRule(_localctx, 0, KVTParser.RULE_literal);
		int _la;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
				this.setState(18);
				_la = this._input.LA(1);
				if (!((((_la) & ~0x3f) == 0 && ((1L << _la) & 30L) != 0))) {
					this._errHandler.recoverInline(this);
				}
				else {
					if (this._input.LA(1) == Token.EOF) {
						this.matchedEOF = true;
					}
					this._errHandler.reportMatch(this);
					this.consume();
				}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			this._errHandler.reportError(this, re);
			this._errHandler.recover(this, re);
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}

	public final NodeContext node() throws RecognitionException {
		NodeContext _localctx = new NodeContext(this._ctx, this.getState());
		this.enterRule(_localctx, 4, KVTParser.RULE_node);
		int _la;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
				this.setState(23);
				this.match(KVTParser.LBRACE);
				this.setState(25);
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (_la == KVTParser.StringLiteral
					|| _la == KVTParser.Identifier) {
					{
						this.setState(24);
						this.entryList();
					}
				}

				this.setState(27);
				this.match(KVTParser.RBRACE);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			this._errHandler.reportError(this, re);
			this._errHandler.recover(this, re);
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}

	public final ValueContext value() throws RecognitionException {
		ValueContext _localctx = new ValueContext(this._ctx, this.getState());
		this.enterRule(_localctx, 12, KVTParser.RULE_value);
		try {
			this.setState(46);
			this._errHandler.sync(this);
			switch (this._input.LA(1)) {
				case StringLiteral:
				case FloatingPointLiteral:
				case IntegerLiteral:
				case BooleanLiteral:
					this.enterOuterAlt(_localctx, 1); {
					this.setState(43);
					this.literal();
				}
					break;
				case LBRACK:
					this.enterOuterAlt(_localctx, 2); {
					this.setState(44);
					this.array();
				}
					break;
				case LBRACE:
					this.enterOuterAlt(_localctx, 3); {
					this.setState(45);
					this.node();
				}
					break;
				default:
					throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			this._errHandler.reportError(this, re);
			this._errHandler.recover(this, re);
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
}