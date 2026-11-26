import { type DescMessage } from "../descriptors.js";
import type { Registry } from "../registry.js";
import type { MessageShape } from "../types.js";
/**
 * Options for parsing the protobuf text format.
 */
export interface TextReadOptions {
    /**
     * The registry to resolve `google.protobuf.Any` and extensions. Parsing an
     * Any in its expanded form, or an extension field, requires it.
     */
    registry?: Registry | undefined;
    /**
     * The maximum depth of nested messages to parse. A message nesting deeper
     * than this fails with an error instead of exhausting the call stack.
     * Defaults to 100.
     */
    recursionLimit: number;
}
/**
 * Parse a message from the protobuf text format.
 *
 * Requires BigInt: throws immediately if the environment does not support it.
 */
export declare function fromText<Desc extends DescMessage>(schema: Desc, text: string, options?: Partial<TextReadOptions>): MessageShape<Desc>;
/**
 * Parse a message from the protobuf text format, merging into the target.
 *
 * Repeated fields are appended, singular fields are overwritten (last wins),
 * message fields are merged, and map entries are added (overwriting an existing
 * key).
 *
 * Requires BigInt: throws immediately if the environment does not support it.
 */
export declare function mergeFromText<Desc extends DescMessage>(schema: Desc, target: MessageShape<Desc>, text: string, options?: Partial<TextReadOptions>): MessageShape<Desc>;
