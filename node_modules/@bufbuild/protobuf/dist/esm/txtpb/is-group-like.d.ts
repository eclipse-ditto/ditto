import type { DescField, DescMessage } from "../descriptors.js";
/**
 * Returns true if the field is structured like a proto2 group: a delimited
 * message field whose name is the lowercase of its message type name, declared
 * in the same scope as that message.
 *
 * The text format addresses such fields by their message type name (e.g.
 * `MyGroup`) rather than their field name. This is a faithful port of
 * protobuf-go's isGroupLike (internal/filedesc/desc.go), so editions delimited
 * fields are treated exactly like proto2 groups.
 *
 * Testing `field.message` first narrows the DescField union to its three
 * message-bearing variants (singular, list, and map value) — all of which carry
 * `delimitedEncoding` — so it is in scope below without a cast. Maps are
 * excluded automatically, because their `delimitedEncoding` is always false.
 */
export declare function isGroupLike(field: DescField): field is DescField & {
    message: DescMessage;
};
