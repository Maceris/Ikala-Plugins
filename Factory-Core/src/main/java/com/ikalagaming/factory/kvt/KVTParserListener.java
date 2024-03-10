package com.ikalagaming.factory.kvt;

// Generated from KVTParser.g4 by ANTLR 4.12.0
import org.antlr.v4.runtime.tree.ParseTreeListener;

/** This interface defines a complete listener for a parse tree produced by {@link KVTParser}. */
public interface KVTParserListener extends ParseTreeListener {
    /**
     * Enter a parse tree produced by {@link KVTParser#array}.
     *
     * @param ctx the parse tree
     */
    void enterArray(KVTParser.ArrayContext ctx);

    /**
     * Enter a parse tree produced by {@link KVTParser#arrayElements}.
     *
     * @param ctx the parse tree
     */
    void enterArrayElements(KVTParser.ArrayElementsContext ctx);

    /**
     * Enter a parse tree produced by {@link KVTParser#compilationUnit}.
     *
     * @param ctx the parse tree
     */
    void enterCompilationUnit(KVTParser.CompilationUnitContext ctx);

    /**
     * Enter a parse tree produced by {@link KVTParser#entry}.
     *
     * @param ctx the parse tree
     */
    void enterEntry(KVTParser.EntryContext ctx);

    /**
     * Enter a parse tree produced by {@link KVTParser#entryList}.
     *
     * @param ctx the parse tree
     */
    void enterEntryList(KVTParser.EntryListContext ctx);

    /**
     * Enter a parse tree produced by {@link KVTParser#key}.
     *
     * @param ctx the parse tree
     */
    void enterKey(KVTParser.KeyContext ctx);

    /**
     * Enter a parse tree produced by {@link KVTParser#literal}.
     *
     * @param ctx the parse tree
     */
    void enterLiteral(KVTParser.LiteralContext ctx);

    /**
     * Enter a parse tree produced by {@link KVTParser#node}.
     *
     * @param ctx the parse tree
     */
    void enterNode(KVTParser.NodeContext ctx);

    /**
     * Enter a parse tree produced by {@link KVTParser#value}.
     *
     * @param ctx the parse tree
     */
    void enterValue(KVTParser.ValueContext ctx);

    /**
     * Exit a parse tree produced by {@link KVTParser#array}.
     *
     * @param ctx the parse tree
     */
    void exitArray(KVTParser.ArrayContext ctx);

    /**
     * Exit a parse tree produced by {@link KVTParser#arrayElements}.
     *
     * @param ctx the parse tree
     */
    void exitArrayElements(KVTParser.ArrayElementsContext ctx);

    /**
     * Exit a parse tree produced by {@link KVTParser#compilationUnit}.
     *
     * @param ctx the parse tree
     */
    void exitCompilationUnit(KVTParser.CompilationUnitContext ctx);

    /**
     * Exit a parse tree produced by {@link KVTParser#entry}.
     *
     * @param ctx the parse tree
     */
    void exitEntry(KVTParser.EntryContext ctx);

    /**
     * Exit a parse tree produced by {@link KVTParser#entryList}.
     *
     * @param ctx the parse tree
     */
    void exitEntryList(KVTParser.EntryListContext ctx);

    /**
     * Exit a parse tree produced by {@link KVTParser#key}.
     *
     * @param ctx the parse tree
     */
    void exitKey(KVTParser.KeyContext ctx);

    /**
     * Exit a parse tree produced by {@link KVTParser#literal}.
     *
     * @param ctx the parse tree
     */
    void exitLiteral(KVTParser.LiteralContext ctx);

    /**
     * Exit a parse tree produced by {@link KVTParser#node}.
     *
     * @param ctx the parse tree
     */
    void exitNode(KVTParser.NodeContext ctx);

    /**
     * Exit a parse tree produced by {@link KVTParser#value}.
     *
     * @param ctx the parse tree
     */
    void exitValue(KVTParser.ValueContext ctx);
}
