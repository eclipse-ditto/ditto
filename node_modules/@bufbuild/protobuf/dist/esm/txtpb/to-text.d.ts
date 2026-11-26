import { type DescMessage } from "../descriptors.js";
import type { Registry } from "../registry.js";
import type { MessageShape } from "../types.js";
/**
 * Options for serializing to the protobuf text format.
 *
 * The text format represents 64-bit integral types with BigInt and has no
 * string fall-back: toText throws immediately when BigInt is unavailable.
 */
export interface TextWriteOptions {
    /**
     * Print unknown fields?
     *
     * Disabled by default. This is a debugging aid only: unknown fields are
     * printed by field number, and fromText rejects fields named by number, so
     * output that includes them cannot be parsed back.
     */
    printUnknownFields: boolean;
    /**
     * The registry to resolve `google.protobuf.Any` and extensions. Without it,
     * an Any is written as its raw `type_url`/`value` fields and extensions are
     * omitted.
     */
    registry?: Registry | undefined;
}
/**
 * Serialize a message to the protobuf text format.
 *
 * The output matches the default formatting of txtpbfmt: two-space indentation,
 * one field per line, and a trailing newline.
 *
 * Requires BigInt: throws immediately if the environment does not support it.
 */
export declare function toText<Desc extends DescMessage>(schema: Desc, message: MessageShape<Desc>, options?: Partial<TextWriteOptions>): string;
