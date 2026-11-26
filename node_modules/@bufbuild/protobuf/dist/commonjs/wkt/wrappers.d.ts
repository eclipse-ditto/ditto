import type { Message } from "../types.js";
import type { BoolValue, BytesValue, DoubleValue, FloatValue, Int32Value, Int64Value, StringValue, UInt32Value, UInt64Value } from "./gen/google/protobuf/wrappers_pb.js";
import type { DescField, DescMessage } from "../descriptors.js";
export declare function isWrapper(arg: Message): arg is DoubleValue | FloatValue | Int64Value | UInt64Value | Int32Value | UInt32Value | BoolValue | StringValue | BytesValue;
export type WktWrapperDesc = DescMessage & {
    fields: [
        DescField & {
            fieldKind: "scalar";
            number: 1;
            name: "value";
            oneof: undefined;
        }
    ];
};
export declare function isWrapperDesc(messageDesc: DescMessage): messageDesc is WktWrapperDesc;
/**
 * Returns true if the descriptor is a well-known type with a custom JSON
 * representation per the protobuf JSON spec. Examples: Timestamp as an
 * RFC 3339 string, Duration as "5s", wrappers as the unwrapped scalar.
 *
 * When packed inside `google.protobuf.Any`, these messages are serialized
 * as `{"@type": ..., "value": <custom form>}`; all other messages embed
 * their fields directly.
 */
export declare function hasCustomJsonRepresentation(desc: DescMessage): boolean;
