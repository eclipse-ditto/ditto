/**
 * A position in the Writer's output, used to roll back speculative writes.
 */
interface Mark {
    readonly size: number;
    readonly depth: number;
}
/**
 * A writer for the protobuf text format.
 *
 * The Writer owns layout: indentation, line breaks, and braces. Its output
 * matches the default formatting of txtpbfmt and the multi-line output of
 * protobuf-go: two-space indentation, `name: value` with a single space after
 * the colon, submessages as `name: {` with the body indented and `}` aligned
 * under the field name, and a trailing newline. It never inserts the randomized
 * extra spaces that protobuf-go adds to discourage parsing its output as
 * canonical.
 *
 * Output accumulates into a single buffer of lines, so a deep tree costs no
 * more than the bytes it prints. To decide between `name: {}` and an indented
 * block, the caller renders the body, then rolls back with mark()/reset() if it
 * turned out empty — an O(1) decision that needs no separate child buffer.
 */
export declare class Writer {
    private readonly chunks;
    private depth;
    /**
     * Write a scalar field: `<indent>name: value` followed by a newline.
     */
    scalar(name: string, value: string): void;
    /**
     * Write an empty message field: `<indent>name: {}` followed by a newline.
     */
    emptyMessage(name: string): void;
    /**
     * Open a message field: `<indent>name: {` followed by a newline, then indent
     * the body. Close it with end().
     */
    openMessage(name: string): void;
    /**
     * Close a message opened with openMessage(): outdent and write `<indent>}`
     * followed by a newline.
     */
    end(): void;
    /**
     * Capture the current output position so it can be rolled back with reset().
     */
    mark(): Mark;
    /**
     * Roll back to a position captured with mark().
     */
    reset(mark: Mark): void;
    /**
     * The number of lines written since the given mark.
     */
    writesSince(mark: Mark): number;
    toString(): string;
    private indent;
}
/**
 * Quote and escape a string field value as a double-quoted text format literal.
 *
 * Uses the single escaping decision in escapeCodePoint, so string fields, bytes
 * fields, and unknown length-delimited rendering can never drift apart. Valid
 * non-ASCII passes through as raw UTF-8; surrogates are never escaped.
 */
export declare function quoteString(value: string): string;
/**
 * Quote and escape a bytes field value as a double-quoted text format literal.
 *
 * Valid UTF-8 runs are emitted with escapeCodePoint (so they read identically
 * to a string field); any byte that is not part of a valid UTF-8 sequence is
 * emitted as `\xHH`, keeping the output plain ASCII that round-trips exactly.
 */
export declare function quoteBytes(value: Uint8Array): string;
export {};
