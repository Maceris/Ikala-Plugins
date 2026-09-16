package com.ikalagaming.factory.kvt;

// Generated from KVTParser.g4 by ANTLR 4.13.2
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.tree.*;

import java.util.List;

@SuppressWarnings({
    "all",
    "warnings",
    "unchecked",
    "unused",
    "cast",
    "CheckReturnValue",
    "this-escape"
})
public class KVTParser extends Parser {
    static {
        RuntimeMetaData.checkVersion("4.13.2", RuntimeMetaData.VERSION);
    }

    protected static final DFA[] _decisionToDFA;
    protected static final PredictionContextCache _sharedContextCache =
            new PredictionContextCache();
    public static final int StringLiteral = 1,
            FloatingPointLiteral = 2,
            IntegerLiteral = 3,
            BooleanLiteral = 4,
            Identifier = 5,
            ArrayPrefix = 6,
            LBRACE = 7,
            RBRACE = 8,
            LBRACK = 9,
            RBRACK = 10,
            COMMA = 11,
            COLON = 12,
            WS = 13;
    public static final int RULE_literal = 0,
            RULE_compilationUnit = 1,
            RULE_node = 2,
            RULE_entryList = 3,
            RULE_entry = 4,
            RULE_key = 5,
            RULE_value = 6,
            RULE_array = 7,
            RULE_arrayElements = 8;

    private static String[] makeRuleNames() {
        return new String[] {
            "literal",
            "compilationUnit",
            "node",
            "entryList",
            "entry",
            "key",
            "value",
            "array",
            "arrayElements"
        };
    }

    public static final String[] ruleNames = makeRuleNames();

    private static String[] makeLiteralNames() {
        return new String[] {
            null, null, null, null, null, null, null, "'{'", "'}'", "'['", "']'", "','", "':'"
        };
    }

    private static final String[] _LITERAL_NAMES = makeLiteralNames();

    private static String[] makeSymbolicNames() {
        return new String[] {
            null,
            "StringLiteral",
            "FloatingPointLiteral",
            "IntegerLiteral",
            "BooleanLiteral",
            "Identifier",
            "ArrayPrefix",
            "LBRACE",
            "RBRACE",
            "LBRACK",
            "RBRACK",
            "COMMA",
            "COLON",
            "WS"
        };
    }

    private static final String[] _SYMBOLIC_NAMES = makeSymbolicNames();
    public static final Vocabulary VOCABULARY = new VocabularyImpl(_LITERAL_NAMES, _SYMBOLIC_NAMES);

    /**
     * @deprecated Use {@link #VOCABULARY} instead.
     */
    @Deprecated public static final String[] tokenNames;

    static {
        tokenNames = new String[_SYMBOLIC_NAMES.length];
        for (int i = 0; i < tokenNames.length; i++) {
            tokenNames[i] = VOCABULARY.getLiteralName(i);
            if (tokenNames[i] == null) {
                tokenNames[i] = VOCABULARY.getSymbolicName(i);
            }

            if (tokenNames[i] == null) {
                tokenNames[i] = "<INVALID>";
            }
        }
    }

    @Override
    @Deprecated
    public String[] getTokenNames() {
        return tokenNames;
    }

    @Override
    public Vocabulary getVocabulary() {
        return VOCABULARY;
    }

    @Override
    public String getGrammarFileName() {
        return "KVTParser.g4";
    }

    @Override
    public String[] getRuleNames() {
        return ruleNames;
    }

    @Override
    public String getSerializedATN() {
        return _serializedATN;
    }

    @Override
    public ATN getATN() {
        return _ATN;
    }

    public KVTParser(TokenStream input) {
        super(input);
        _interp = new ParserATNSimulator(this, _ATN, _decisionToDFA, _sharedContextCache);
    }

    @SuppressWarnings("CheckReturnValue")
    public static class LiteralContext extends ParserRuleContext {
        public TerminalNode IntegerLiteral() {
            return getToken(KVTParser.IntegerLiteral, 0);
        }

        public TerminalNode FloatingPointLiteral() {
            return getToken(KVTParser.FloatingPointLiteral, 0);
        }

        public TerminalNode BooleanLiteral() {
            return getToken(KVTParser.BooleanLiteral, 0);
        }

        public TerminalNode StringLiteral() {
            return getToken(KVTParser.StringLiteral, 0);
        }

        public LiteralContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_literal;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof KVTParserListener)
                ((KVTParserListener) listener).enterLiteral(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof KVTParserListener)
                ((KVTParserListener) listener).exitLiteral(this);
        }
    }

    public final LiteralContext literal() throws RecognitionException {
        LiteralContext _localctx = new LiteralContext(_ctx, getState());
        enterRule(_localctx, 0, RULE_literal);
        int _la;
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(18);
                _la = _input.LA(1);
                if (!((((_la) & ~0x3f) == 0 && ((1L << _la) & 30L) != 0))) {
                    _errHandler.recoverInline(this);
                } else {
                    if (_input.LA(1) == Token.EOF) matchedEOF = true;
                    _errHandler.reportMatch(this);
                    consume();
                }
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    @SuppressWarnings("CheckReturnValue")
    public static class CompilationUnitContext extends ParserRuleContext {
        public NodeContext node() {
            return getRuleContext(NodeContext.class, 0);
        }

        public TerminalNode EOF() {
            return getToken(KVTParser.EOF, 0);
        }

        public CompilationUnitContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_compilationUnit;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof KVTParserListener)
                ((KVTParserListener) listener).enterCompilationUnit(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof KVTParserListener)
                ((KVTParserListener) listener).exitCompilationUnit(this);
        }
    }

    public final CompilationUnitContext compilationUnit() throws RecognitionException {
        CompilationUnitContext _localctx = new CompilationUnitContext(_ctx, getState());
        enterRule(_localctx, 2, RULE_compilationUnit);
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(20);
                node();
                setState(21);
                match(EOF);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    @SuppressWarnings("CheckReturnValue")
    public static class NodeContext extends ParserRuleContext {
        public TerminalNode LBRACE() {
            return getToken(KVTParser.LBRACE, 0);
        }

        public TerminalNode RBRACE() {
            return getToken(KVTParser.RBRACE, 0);
        }

        public EntryListContext entryList() {
            return getRuleContext(EntryListContext.class, 0);
        }

        public NodeContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_node;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof KVTParserListener)
                ((KVTParserListener) listener).enterNode(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof KVTParserListener)
                ((KVTParserListener) listener).exitNode(this);
        }
    }

    public final NodeContext node() throws RecognitionException {
        NodeContext _localctx = new NodeContext(_ctx, getState());
        enterRule(_localctx, 4, RULE_node);
        int _la;
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(23);
                match(LBRACE);
                setState(25);
                _errHandler.sync(this);
                _la = _input.LA(1);
                if (_la == StringLiteral || _la == Identifier) {
                    {
                        setState(24);
                        entryList();
                    }
                }

                setState(27);
                match(RBRACE);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    @SuppressWarnings("CheckReturnValue")
    public static class EntryListContext extends ParserRuleContext {
        public List<EntryContext> entry() {
            return getRuleContexts(EntryContext.class);
        }

        public EntryContext entry(int i) {
            return getRuleContext(EntryContext.class, i);
        }

        public List<TerminalNode> COMMA() {
            return getTokens(KVTParser.COMMA);
        }

        public TerminalNode COMMA(int i) {
            return getToken(KVTParser.COMMA, i);
        }

        public EntryListContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_entryList;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof KVTParserListener)
                ((KVTParserListener) listener).enterEntryList(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof KVTParserListener)
                ((KVTParserListener) listener).exitEntryList(this);
        }
    }

    public final EntryListContext entryList() throws RecognitionException {
        EntryListContext _localctx = new EntryListContext(_ctx, getState());
        enterRule(_localctx, 6, RULE_entryList);
        int _la;
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(29);
                entry();
                setState(34);
                _errHandler.sync(this);
                _la = _input.LA(1);
                while (_la == COMMA) {
                    {
                        {
                            setState(30);
                            match(COMMA);
                            setState(31);
                            entry();
                        }
                    }
                    setState(36);
                    _errHandler.sync(this);
                    _la = _input.LA(1);
                }
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    @SuppressWarnings("CheckReturnValue")
    public static class EntryContext extends ParserRuleContext {
        public KeyContext key() {
            return getRuleContext(KeyContext.class, 0);
        }

        public TerminalNode COLON() {
            return getToken(KVTParser.COLON, 0);
        }

        public ValueContext value() {
            return getRuleContext(ValueContext.class, 0);
        }

        public EntryContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_entry;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof KVTParserListener)
                ((KVTParserListener) listener).enterEntry(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof KVTParserListener)
                ((KVTParserListener) listener).exitEntry(this);
        }
    }

    public final EntryContext entry() throws RecognitionException {
        EntryContext _localctx = new EntryContext(_ctx, getState());
        enterRule(_localctx, 8, RULE_entry);
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(37);
                key();
                setState(38);
                match(COLON);
                setState(39);
                value();
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    @SuppressWarnings("CheckReturnValue")
    public static class KeyContext extends ParserRuleContext {
        public TerminalNode Identifier() {
            return getToken(KVTParser.Identifier, 0);
        }

        public TerminalNode StringLiteral() {
            return getToken(KVTParser.StringLiteral, 0);
        }

        public KeyContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_key;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof KVTParserListener)
                ((KVTParserListener) listener).enterKey(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof KVTParserListener) ((KVTParserListener) listener).exitKey(this);
        }
    }

    public final KeyContext key() throws RecognitionException {
        KeyContext _localctx = new KeyContext(_ctx, getState());
        enterRule(_localctx, 10, RULE_key);
        int _la;
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(41);
                _la = _input.LA(1);
                if (!(_la == StringLiteral || _la == Identifier)) {
                    _errHandler.recoverInline(this);
                } else {
                    if (_input.LA(1) == Token.EOF) matchedEOF = true;
                    _errHandler.reportMatch(this);
                    consume();
                }
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    @SuppressWarnings("CheckReturnValue")
    public static class ValueContext extends ParserRuleContext {
        public LiteralContext literal() {
            return getRuleContext(LiteralContext.class, 0);
        }

        public ArrayContext array() {
            return getRuleContext(ArrayContext.class, 0);
        }

        public NodeContext node() {
            return getRuleContext(NodeContext.class, 0);
        }

        public ValueContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_value;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof KVTParserListener)
                ((KVTParserListener) listener).enterValue(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof KVTParserListener)
                ((KVTParserListener) listener).exitValue(this);
        }
    }

    public final ValueContext value() throws RecognitionException {
        ValueContext _localctx = new ValueContext(_ctx, getState());
        enterRule(_localctx, 12, RULE_value);
        try {
            setState(46);
            _errHandler.sync(this);
            switch (_input.LA(1)) {
                case StringLiteral:
                case FloatingPointLiteral:
                case IntegerLiteral:
                case BooleanLiteral:
                    enterOuterAlt(_localctx, 1);
                    {
                        setState(43);
                        literal();
                    }
                    break;
                case LBRACK:
                    enterOuterAlt(_localctx, 2);
                    {
                        setState(44);
                        array();
                    }
                    break;
                case LBRACE:
                    enterOuterAlt(_localctx, 3);
                    {
                        setState(45);
                        node();
                    }
                    break;
                default:
                    throw new NoViableAltException(this);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    @SuppressWarnings("CheckReturnValue")
    public static class ArrayContext extends ParserRuleContext {
        public TerminalNode LBRACK() {
            return getToken(KVTParser.LBRACK, 0);
        }

        public TerminalNode ArrayPrefix() {
            return getToken(KVTParser.ArrayPrefix, 0);
        }

        public TerminalNode RBRACK() {
            return getToken(KVTParser.RBRACK, 0);
        }

        public ArrayElementsContext arrayElements() {
            return getRuleContext(ArrayElementsContext.class, 0);
        }

        public ArrayContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_array;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof KVTParserListener)
                ((KVTParserListener) listener).enterArray(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof KVTParserListener)
                ((KVTParserListener) listener).exitArray(this);
        }
    }

    public final ArrayContext array() throws RecognitionException {
        ArrayContext _localctx = new ArrayContext(_ctx, getState());
        enterRule(_localctx, 14, RULE_array);
        int _la;
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(48);
                match(LBRACK);
                setState(49);
                match(ArrayPrefix);
                setState(51);
                _errHandler.sync(this);
                _la = _input.LA(1);
                if ((((_la) & ~0x3f) == 0 && ((1L << _la) & 670L) != 0)) {
                    {
                        setState(50);
                        arrayElements();
                    }
                }

                setState(53);
                match(RBRACK);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    @SuppressWarnings("CheckReturnValue")
    public static class ArrayElementsContext extends ParserRuleContext {
        public List<ValueContext> value() {
            return getRuleContexts(ValueContext.class);
        }

        public ValueContext value(int i) {
            return getRuleContext(ValueContext.class, i);
        }

        public List<TerminalNode> COMMA() {
            return getTokens(KVTParser.COMMA);
        }

        public TerminalNode COMMA(int i) {
            return getToken(KVTParser.COMMA, i);
        }

        public ArrayElementsContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_arrayElements;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof KVTParserListener)
                ((KVTParserListener) listener).enterArrayElements(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof KVTParserListener)
                ((KVTParserListener) listener).exitArrayElements(this);
        }
    }

    public final ArrayElementsContext arrayElements() throws RecognitionException {
        ArrayElementsContext _localctx = new ArrayElementsContext(_ctx, getState());
        enterRule(_localctx, 16, RULE_arrayElements);
        int _la;
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(55);
                value();
                setState(60);
                _errHandler.sync(this);
                _la = _input.LA(1);
                while (_la == COMMA) {
                    {
                        {
                            setState(56);
                            match(COMMA);
                            setState(57);
                            value();
                        }
                    }
                    setState(62);
                    _errHandler.sync(this);
                    _la = _input.LA(1);
                }
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
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
    public static final ATN _ATN = new ATNDeserializer().deserialize(_serializedATN.toCharArray());

    static {
        _decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
        for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
            _decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
        }
    }
}
