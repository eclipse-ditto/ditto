import type { DescField, DescMessage } from "../descriptors.js";
import type { JsonObject, JsonValue } from "../json-value.js";
import type { Struct } from "../wkt/gen/google/protobuf/struct_pb.js";
/**
 * Mapper between the local representation of a message field value
 * and the message it represents. For most fields, the local value is the
 * message itself. Types from google/protobuf/wrappers.proto are unwrapped
 * to the wrapped scalar value when used in a singular field that is not
 * part of a oneof group, and google.protobuf.Struct is represented with
 * JsonObject when used in a field, except when used in
 * google.protobuf.Value.
 *
 * @private
 */
export interface LocalMessageMapper {
    /**
     * Wrap a local value in the message it represents. For undefined - an
     * unset field - a new message is created. Like the reflect API, wrapping
     * an existing Struct field value creates a normalized copy, so that
     * merging does not mutate the previous value in place.
     */
    toMessage(local: unknown): Record<string, unknown>;
    /**
     * Convert a message to the local representation of the field value.
     */
    toLocal(message: Record<string, unknown>): unknown;
}
/**
 * Return the conversions between the local representation of the field
 * value and the message it represents.
 *
 * @private
 */
export declare function localMessageMapper(field: DescField & {
    message: DescMessage;
}): LocalMessageMapper;
/**
 * Convert the JsonValue representation of a google.protobuf.Struct to the
 * message representation.
 *
 * @private
 */
export declare function wktStructToReflect(json: JsonValue): Struct;
/**
 * Convert a google.protobuf.Struct message to its JsonValue representation.
 *
 * @private
 */
export declare function wktStructToLocal(val: Struct): JsonObject;
