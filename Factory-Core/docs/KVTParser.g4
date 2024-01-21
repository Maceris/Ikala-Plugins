parser grammar KVTParser;

options {
    tokenVocab=KVTLexer;
}

literal
	:	IntegerLiteral
	|	FloatingPointLiteral
	|	BooleanLiteral
	|	StringLiteral
	;

compilationUnit
	:	node EOF
	;

node
	:	LBRACE entryList? RBRACE
	;

entryList
	:	entry (COMMA entry)*
	;

entry
	:	key COLON value
	;

key
	:	Identifier
	|	StringLiteral
	;

value
	:	literal
	|	array
	|	node
	;

array
	:	LBRACK ArrayPrefix arrayElements? RBRACK
	;

arrayElements
	:	value (COMMA value)*
	;
