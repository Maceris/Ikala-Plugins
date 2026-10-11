package com.ikalagaming.factory.kvt;

// Generated from KVTLexer.g4 by ANTLR 4.13.2
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.misc.*;

@SuppressWarnings({
    "all",
    "warnings",
    "unchecked",
    "unused",
    "cast",
    "CheckReturnValue",
    "this-escape"
})
public class KVTLexer extends Lexer {
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
            WS = 13,
            BLOCK_COMMENT = 14;
    public static String[] channelNames = {"DEFAULT_TOKEN_CHANNEL", "HIDDEN"};

    public static String[] modeNames = {"DEFAULT_MODE"};

    private static String[] makeRuleNames() {
        return new String[] {
            "Digits",
            "ExponentPart",
            "Sign",
            "ExponentIndicator",
            "FloatTypeSuffix",
            "IntegerTypeSuffix",
            "SignedInteger",
            "StringLiteral",
            "StringCharacters",
            "StringCharacter",
            "EscapeSequence",
            "FloatingPointLiteral",
            "IntegerLiteral",
            "BooleanLiteral",
            "Identifier",
            "ArrayPrefixLetter",
            "ArrayPrefix",
            "LBRACE",
            "RBRACE",
            "LBRACK",
            "RBRACK",
            "COMMA",
            "COLON",
            "WS",
            "BLOCK_COMMENT"
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
            "WS",
            "BLOCK_COMMENT"
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

    public KVTLexer(CharStream input) {
        super(input);
        _interp = new LexerATNSimulator(this, _ATN, _decisionToDFA, _sharedContextCache);
    }

    @Override
    public String getGrammarFileName() {
        return "KVTLexer.g4";
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
    public String[] getChannelNames() {
        return channelNames;
    }

    @Override
    public String[] getModeNames() {
        return modeNames;
    }

    @Override
    public ATN getATN() {
        return _ATN;
    }

    public static final String _serializedATN =
            "\u0004\u0000\u000e\u00b4\u0006\uffff\uffff\u0002\u0000\u0007\u0000\u0002"
                    + "\u0001\u0007\u0001\u0002\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002"
                    + "\u0004\u0007\u0004\u0002\u0005\u0007\u0005\u0002\u0006\u0007\u0006\u0002"
                    + "\u0007\u0007\u0007\u0002\b\u0007\b\u0002\t\u0007\t\u0002\n\u0007\n\u0002"
                    + "\u000b\u0007\u000b\u0002\f\u0007\f\u0002\r\u0007\r\u0002\u000e\u0007\u000e"
                    + "\u0002\u000f\u0007\u000f\u0002\u0010\u0007\u0010\u0002\u0011\u0007\u0011"
                    + "\u0002\u0012\u0007\u0012\u0002\u0013\u0007\u0013\u0002\u0014\u0007\u0014"
                    + "\u0002\u0015\u0007\u0015\u0002\u0016\u0007\u0016\u0002\u0017\u0007\u0017"
                    + "\u0002\u0018\u0007\u0018\u0001\u0000\u0004\u00005\b\u0000\u000b\u0000"
                    + "\f\u00006\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0002\u0001\u0002"
                    + "\u0001\u0003\u0001\u0003\u0001\u0004\u0001\u0004\u0001\u0005\u0001\u0005"
                    + "\u0001\u0006\u0003\u0006E\b\u0006\u0001\u0006\u0001\u0006\u0001\u0007"
                    + "\u0001\u0007\u0003\u0007K\b\u0007\u0001\u0007\u0001\u0007\u0001\b\u0004"
                    + "\bP\b\b\u000b\b\f\bQ\u0001\t\u0001\t\u0003\tV\b\t\u0001\n\u0001\n\u0001"
                    + "\n\u0001\u000b\u0001\u000b\u0001\u000b\u0003\u000b^\b\u000b\u0001\u000b"
                    + "\u0003\u000ba\b\u000b\u0001\u000b\u0003\u000bd\b\u000b\u0001\u000b\u0003"
                    + "\u000bg\b\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0003\u000bl\b\u000b"
                    + "\u0001\u000b\u0003\u000bo\b\u000b\u0001\u000b\u0001\u000b\u0001\u000b"
                    + "\u0003\u000bt\b\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0003\u000b"
                    + "y\b\u000b\u0001\f\u0001\f\u0003\f}\b\f\u0001\r\u0001\r\u0001\r\u0001\r"
                    + "\u0001\r\u0001\r\u0001\r\u0001\r\u0001\r\u0003\r\u0088\b\r\u0001\u000e"
                    + "\u0004\u000e\u008b\b\u000e\u000b\u000e\f\u000e\u008c\u0001\u000f\u0001"
                    + "\u000f\u0001\u0010\u0001\u0010\u0001\u0010\u0001\u0011\u0001\u0011\u0001"
                    + "\u0012\u0001\u0012\u0001\u0013\u0001\u0013\u0001\u0014\u0001\u0014\u0001"
                    + "\u0015\u0001\u0015\u0001\u0016\u0001\u0016\u0001\u0017\u0004\u0017\u00a1"
                    + "\b\u0017\u000b\u0017\f\u0017\u00a2\u0001\u0017\u0001\u0017\u0001\u0018"
                    + "\u0001\u0018\u0001\u0018\u0001\u0018\u0005\u0018\u00ab\b\u0018\n\u0018"
                    + "\f\u0018\u00ae\t\u0018\u0001\u0018\u0001\u0018\u0001\u0018\u0001\u0018"
                    + "\u0001\u0018\u0001\u00ac\u0000\u0019\u0001\u0000\u0003\u0000\u0005\u0000"
                    + "\u0007\u0000\t\u0000\u000b\u0000\r\u0000\u000f\u0001\u0011\u0000\u0013"
                    + "\u0000\u0015\u0000\u0017\u0002\u0019\u0003\u001b\u0004\u001d\u0005\u001f"
                    + "\u0000!\u0006#\u0007%\b\'\t)\n+\u000b-\f/\r1\u000e\u0001\u0000\n\u0001"
                    + "\u000009\u0002\u0000++--\u0002\u0000EEee\u0004\u0000DDFFddff\u0006\u0000"
                    + "BBLLSSbbllss\u0002\u0000\"\"\\\\\b\u0000\"\"\'\'\\\\bbffnnrrtt\u0006\u0000"
                    + "++-.09AZ__az\b\u0000BBDDFFIILLNNSTZZ\u0003\u0000\t\n\f\r  \u00bc\u0000"
                    + "\u000f\u0001\u0000\u0000\u0000\u0000\u0017\u0001\u0000\u0000\u0000\u0000"
                    + "\u0019\u0001\u0000\u0000\u0000\u0000\u001b\u0001\u0000\u0000\u0000\u0000"
                    + "\u001d\u0001\u0000\u0000\u0000\u0000!\u0001\u0000\u0000\u0000\u0000#\u0001"
                    + "\u0000\u0000\u0000\u0000%\u0001\u0000\u0000\u0000\u0000\'\u0001\u0000"
                    + "\u0000\u0000\u0000)\u0001\u0000\u0000\u0000\u0000+\u0001\u0000\u0000\u0000"
                    + "\u0000-\u0001\u0000\u0000\u0000\u0000/\u0001\u0000\u0000\u0000\u00001"
                    + "\u0001\u0000\u0000\u0000\u00014\u0001\u0000\u0000\u0000\u00038\u0001\u0000"
                    + "\u0000\u0000\u0005;\u0001\u0000\u0000\u0000\u0007=\u0001\u0000\u0000\u0000"
                    + "\t?\u0001\u0000\u0000\u0000\u000bA\u0001\u0000\u0000\u0000\rD\u0001\u0000"
                    + "\u0000\u0000\u000fH\u0001\u0000\u0000\u0000\u0011O\u0001\u0000\u0000\u0000"
                    + "\u0013U\u0001\u0000\u0000\u0000\u0015W\u0001\u0000\u0000\u0000\u0017x"
                    + "\u0001\u0000\u0000\u0000\u0019z\u0001\u0000\u0000\u0000\u001b\u0087\u0001"
                    + "\u0000\u0000\u0000\u001d\u008a\u0001\u0000\u0000\u0000\u001f\u008e\u0001"
                    + "\u0000\u0000\u0000!\u0090\u0001\u0000\u0000\u0000#\u0093\u0001\u0000\u0000"
                    + "\u0000%\u0095\u0001\u0000\u0000\u0000\'\u0097\u0001\u0000\u0000\u0000"
                    + ")\u0099\u0001\u0000\u0000\u0000+\u009b\u0001\u0000\u0000\u0000-\u009d"
                    + "\u0001\u0000\u0000\u0000/\u00a0\u0001\u0000\u0000\u00001\u00a6\u0001\u0000"
                    + "\u0000\u000035\u0007\u0000\u0000\u000043\u0001\u0000\u0000\u000056\u0001"
                    + "\u0000\u0000\u000064\u0001\u0000\u0000\u000067\u0001\u0000\u0000\u0000"
                    + "7\u0002\u0001\u0000\u0000\u000089\u0003\u0007\u0003\u00009:\u0003\r\u0006"
                    + "\u0000:\u0004\u0001\u0000\u0000\u0000;<\u0007\u0001\u0000\u0000<\u0006"
                    + "\u0001\u0000\u0000\u0000=>\u0007\u0002\u0000\u0000>\b\u0001\u0000\u0000"
                    + "\u0000?@\u0007\u0003\u0000\u0000@\n\u0001\u0000\u0000\u0000AB\u0007\u0004"
                    + "\u0000\u0000B\f\u0001\u0000\u0000\u0000CE\u0003\u0005\u0002\u0000DC\u0001"
                    + "\u0000\u0000\u0000DE\u0001\u0000\u0000\u0000EF\u0001\u0000\u0000\u0000"
                    + "FG\u0003\u0001\u0000\u0000G\u000e\u0001\u0000\u0000\u0000HJ\u0005\"\u0000"
                    + "\u0000IK\u0003\u0011\b\u0000JI\u0001\u0000\u0000\u0000JK\u0001\u0000\u0000"
                    + "\u0000KL\u0001\u0000\u0000\u0000LM\u0005\"\u0000\u0000M\u0010\u0001\u0000"
                    + "\u0000\u0000NP\u0003\u0013\t\u0000ON\u0001\u0000\u0000\u0000PQ\u0001\u0000"
                    + "\u0000\u0000QO\u0001\u0000\u0000\u0000QR\u0001\u0000\u0000\u0000R\u0012"
                    + "\u0001\u0000\u0000\u0000SV\b\u0005\u0000\u0000TV\u0003\u0015\n\u0000U"
                    + "S\u0001\u0000\u0000\u0000UT\u0001\u0000\u0000\u0000V\u0014\u0001\u0000"
                    + "\u0000\u0000WX\u0005\\\u0000\u0000XY\u0007\u0006\u0000\u0000Y\u0016\u0001"
                    + "\u0000\u0000\u0000Z[\u0003\r\u0006\u0000[]\u0005.\u0000\u0000\\^\u0003"
                    + "\u0001\u0000\u0000]\\\u0001\u0000\u0000\u0000]^\u0001\u0000\u0000\u0000"
                    + "^`\u0001\u0000\u0000\u0000_a\u0003\u0003\u0001\u0000`_\u0001\u0000\u0000"
                    + "\u0000`a\u0001\u0000\u0000\u0000ac\u0001\u0000\u0000\u0000bd\u0003\t\u0004"
                    + "\u0000cb\u0001\u0000\u0000\u0000cd\u0001\u0000\u0000\u0000dy\u0001\u0000"
                    + "\u0000\u0000eg\u0003\u0005\u0002\u0000fe\u0001\u0000\u0000\u0000fg\u0001"
                    + "\u0000\u0000\u0000gh\u0001\u0000\u0000\u0000hi\u0005.\u0000\u0000ik\u0003"
                    + "\u0001\u0000\u0000jl\u0003\u0003\u0001\u0000kj\u0001\u0000\u0000\u0000"
                    + "kl\u0001\u0000\u0000\u0000ln\u0001\u0000\u0000\u0000mo\u0003\t\u0004\u0000"
                    + "nm\u0001\u0000\u0000\u0000no\u0001\u0000\u0000\u0000oy\u0001\u0000\u0000"
                    + "\u0000pq\u0003\r\u0006\u0000qs\u0003\u0003\u0001\u0000rt\u0003\t\u0004"
                    + "\u0000sr\u0001\u0000\u0000\u0000st\u0001\u0000\u0000\u0000ty\u0001\u0000"
                    + "\u0000\u0000uv\u0003\r\u0006\u0000vw\u0003\t\u0004\u0000wy\u0001\u0000"
                    + "\u0000\u0000xZ\u0001\u0000\u0000\u0000xf\u0001\u0000\u0000\u0000xp\u0001"
                    + "\u0000\u0000\u0000xu\u0001\u0000\u0000\u0000y\u0018\u0001\u0000\u0000"
                    + "\u0000z|\u0003\r\u0006\u0000{}\u0003\u000b\u0005\u0000|{\u0001\u0000\u0000"
                    + "\u0000|}\u0001\u0000\u0000\u0000}\u001a\u0001\u0000\u0000\u0000~\u007f"
                    + "\u0005t\u0000\u0000\u007f\u0080\u0005r\u0000\u0000\u0080\u0081\u0005u"
                    + "\u0000\u0000\u0081\u0088\u0005e\u0000\u0000\u0082\u0083\u0005f\u0000\u0000"
                    + "\u0083\u0084\u0005a\u0000\u0000\u0084\u0085\u0005l\u0000\u0000\u0085\u0086"
                    + "\u0005s\u0000\u0000\u0086\u0088\u0005e\u0000\u0000\u0087~\u0001\u0000"
                    + "\u0000\u0000\u0087\u0082\u0001\u0000\u0000\u0000\u0088\u001c\u0001\u0000"
                    + "\u0000\u0000\u0089\u008b\u0007\u0007\u0000\u0000\u008a\u0089\u0001\u0000"
                    + "\u0000\u0000\u008b\u008c\u0001\u0000\u0000\u0000\u008c\u008a\u0001\u0000"
                    + "\u0000\u0000\u008c\u008d\u0001\u0000\u0000\u0000\u008d\u001e\u0001\u0000"
                    + "\u0000\u0000\u008e\u008f\u0007\b\u0000\u0000\u008f \u0001\u0000\u0000"
                    + "\u0000\u0090\u0091\u0003\u001f\u000f\u0000\u0091\u0092\u0005;\u0000\u0000"
                    + "\u0092\"\u0001\u0000\u0000\u0000\u0093\u0094\u0005{\u0000\u0000\u0094"
                    + "$\u0001\u0000\u0000\u0000\u0095\u0096\u0005}\u0000\u0000\u0096&\u0001"
                    + "\u0000\u0000\u0000\u0097\u0098\u0005[\u0000\u0000\u0098(\u0001\u0000\u0000"
                    + "\u0000\u0099\u009a\u0005]\u0000\u0000\u009a*\u0001\u0000\u0000\u0000\u009b"
                    + "\u009c\u0005,\u0000\u0000\u009c,\u0001\u0000\u0000\u0000\u009d\u009e\u0005"
                    + ":\u0000\u0000\u009e.\u0001\u0000\u0000\u0000\u009f\u00a1\u0007\t\u0000"
                    + "\u0000\u00a0\u009f\u0001\u0000\u0000\u0000\u00a1\u00a2\u0001\u0000\u0000"
                    + "\u0000\u00a2\u00a0\u0001\u0000\u0000\u0000\u00a2\u00a3\u0001\u0000\u0000"
                    + "\u0000\u00a3\u00a4\u0001\u0000\u0000\u0000\u00a4\u00a5\u0006\u0017\u0000"
                    + "\u0000\u00a50\u0001\u0000\u0000\u0000\u00a6\u00a7\u0005/\u0000\u0000\u00a7"
                    + "\u00a8\u0005*\u0000\u0000\u00a8\u00ac\u0001\u0000\u0000\u0000\u00a9\u00ab"
                    + "\t\u0000\u0000\u0000\u00aa\u00a9\u0001\u0000\u0000\u0000\u00ab\u00ae\u0001"
                    + "\u0000\u0000\u0000\u00ac\u00ad\u0001\u0000\u0000\u0000\u00ac\u00aa\u0001"
                    + "\u0000\u0000\u0000\u00ad\u00af\u0001\u0000\u0000\u0000\u00ae\u00ac\u0001"
                    + "\u0000\u0000\u0000\u00af\u00b0\u0005*\u0000\u0000\u00b0\u00b1\u0005/\u0000"
                    + "\u0000\u00b1\u00b2\u0001\u0000\u0000\u0000\u00b2\u00b3\u0006\u0018\u0000"
                    + "\u0000\u00b32\u0001\u0000\u0000\u0000\u0013\u00006DJQU]`cfknsx|\u0087"
                    + "\u008c\u00a2\u00ac\u0001\u0006\u0000\u0000";
    public static final ATN _ATN = new ATNDeserializer().deserialize(_serializedATN.toCharArray());

    static {
        _decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
        for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
            _decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
        }
    }
}
