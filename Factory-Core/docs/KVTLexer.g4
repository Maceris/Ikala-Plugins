lexer grammar KVTLexer;


fragment Digits
	:	[0-9]+
	;

fragment ExponentPart
	:	ExponentIndicator SignedInteger
	;

fragment Sign
	:	[+-]
	;

fragment ExponentIndicator
	:	[eE]
	;

fragment FloatTypeSuffix
	:	[fFdD]
	;

fragment IntegerTypeSuffix
	:	[bBlLsS]
	;

fragment SignedInteger
	:	Sign? Digits
	;

StringLiteral
	:	'"' StringCharacters? '"'
	;

fragment StringCharacters
	:	StringCharacter+
	;

fragment StringCharacter
	:	~["\\] | EscapeSequence
	;

fragment EscapeSequence
	:	'\\' [btnfr"'\\]
	;

FloatingPointLiteral
    :	SignedInteger '.' Digits? ExponentPart? FloatTypeSuffix?
    |	Sign? '.' Digits ExponentPart? FloatTypeSuffix?
    |	SignedInteger ExponentPart FloatTypeSuffix?
    |	SignedInteger FloatTypeSuffix
	;

IntegerLiteral
	:	SignedInteger IntegerTypeSuffix?
	;

BooleanLiteral
	:	'true'
	|	'false'
	;

Identifier
	:	[a-zA-Z_0-9\-.+]+
	;

fragment ArrayPrefixLetter
	:	[BDFILNSTZ]
	;

ArrayPrefix
	:	ArrayPrefixLetter ';'
	;

LBRACE : '{';
RBRACE : '}';
LBRACK : '[';
RBRACK : ']';
COMMA :	',';
COLON :	':';

WS : [ \t\r\n\u000C]+ -> skip;
