/**
 * A lexical token of the protobuf text format.
 *
 * This is a discriminated union keyed by `type`: punctuation tokens carry no
 * payload, identifiers carry their text, string tokens carry their decoded
 * bytes (the same bytes back both string and bytes fields, and bytes fields may
 * hold sequences that are not valid UTF-8), and numbers carry their literal
 * text plus enough classification for the parser to accept or reject them per
 * field type.
 *
 * The minus sign before a number is its own token rather than part of the
 * number, which keeps numeric sign handling in one place in the parser and,
 * because whitespace and comments between tokens are insignificant, makes
 * `- 42` mean `-42`, matching protobuf-go (decode_number.go). A minus glued to
 * a letter is instead folded into a negative identifier (`-inf`/`-infinity`),
 * because protobuf-go requires the sign glued for those literals — `- inf` is
 * an error there, not negative infinity.
 */
export type Token = {
    readonly type: Structural | "eof";
} | {
    readonly type: "identifier";
    readonly value: string;
} | {
    readonly type: "string";
    readonly value: Uint8Array;
} | {
    readonly type: "int";
    readonly text: string;
    readonly base: 8 | 10 | 16;
} | {
    readonly type: "float";
    readonly text: string;
};
/**
 * The structural tokens, each the literal source character it represents.
 */
type Structural = "{" | "}" | "<" | ">" | "[" | "]" | ":" | "," | ";" | "-";
/**
 * A tokenizer for the protobuf text format.
 *
 * The parser drives it with `peek()` and `next()` (one-token lookahead) and,
 * once it knows it is in a field-name position, asks for the contents of a
 * bracketed name with `readTypeName()` — the `[...]` syntax for extensions and
 * Any type URLs is ambiguous with the list syntax at the lexical level. The
 * structure is modeled on the graphql-js lexer: a single scan position and a
 * per-token reader that returns the decoded value.
 */
export declare class Reader {
    private readonly input;
    private readonly length;
    private pos;
    private lookahead;
    constructor(input: string);
    /**
     * Return the next token without consuming it.
     */
    peek(): Token;
    /**
     * Consume and return the next token.
     */
    next(): Token;
    /**
     * Read the contents of a bracketed name, used for extension fields and the
     * expanded form of google.protobuf.Any. The opening `[` must already have
     * been consumed with `next()`. Whitespace and comments inside the brackets
     * are insignificant. Returns the inner name with the brackets removed, e.g.
     * "pkg.Message.field" or "type.googleapis.com/pkg.Message".
     *
     * The text format grammar for this is incomplete, so we follow protobuf-go's
     * parseTypeName: the prefix may contain URL characters, `/` separators, and
     * well-formed percent-escapes, and the type name after the last `/` must be a
     * dotted identifier.
     */
    readTypeName(): string;
    private scan;
    private skipSpace;
    private scanIdentifier;
    /**
     * Scan a numeric literal. The sign is a separate token, so a number never
     * starts with `-`. The literal must end at a delimiter, so `10f` is a float
     * but `10bar`, `1.2.3`, `09`, and `0xZ` are errors.
     */
    private scanNumber;
    private expectDelimiter;
    private scanString;
    private scanEscape;
    private takeWhile;
    private charAt;
}
export {};
