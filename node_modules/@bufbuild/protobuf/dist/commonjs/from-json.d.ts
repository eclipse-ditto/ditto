import { type DescEnum, type DescMessage } from "./descriptors.js";
import type { JsonValue } from "./json-value.js";
import type { Registry } from "./registry.js";
import type { EnumJsonType, EnumShape, MessageShape } from "./types.js";
/**
 * Options for parsing JSON data.
 */
export interface JsonReadOptions {
    /**
     * Ignore unknown fields: Proto3 JSON parser should reject unknown fields
     * by default. This option ignores unknown fields in parsing, as well as
     * unrecognized enum string representations.
     */
    ignoreUnknownFields: boolean;
    /**
     * This option is required to read `google.protobuf.Any` and extensions
     * from JSON format.
     */
    registry?: Registry | undefined;
    /**
     * The maximum depth of nested messages to parse. If a message nests deeper
     * than this limit, parsing fails with an error instead of exhausting the
     * call stack. Defaults to 100.
     */
    recursionLimit: number;
}
/**
 * Parse a message from a JSON string.
 *
 * Duplicate keys are rejected.
 */
export declare function fromJsonString<Desc extends DescMessage>(schema: Desc, json: string, options?: Partial<JsonReadOptions>): MessageShape<Desc>;
/**
 * Parse a message from a JSON string, merging fields into the target.
 *
 * Repeated fields are appended. Map entries are added, overwriting
 * existing keys.
 *
 * If a message field is already present, it will be merged with the
 * new data.
 *
 * Duplicate keys in the JSON are rejected, as in `fromJsonString`.
 */
export declare function mergeFromJsonString<Desc extends DescMessage>(schema: Desc, target: MessageShape<Desc>, json: string, options?: Partial<JsonReadOptions>): MessageShape<Desc>;
/**
 * Parse a message from a JSON value.
 *
 * Duplicate keys are rejected, but a value parsed by JSON.parse has already
 * dropped duplicates (the last one wins). Use `fromJsonString` for strict
 * duplicate-key checking.
 */
export declare function fromJson<Desc extends DescMessage>(schema: Desc, json: JsonValue, options?: Partial<JsonReadOptions>): MessageShape<Desc>;
/**
 * Parse a message from a JSON value, merging fields into the target.
 *
 * Repeated fields are appended. Map entries are added, overwriting
 * existing keys.
 *
 * If a message field is already present, it will be merged with the
 * new data.
 *
 * Duplicate keys are rejected as in `fromJson`; use `mergeFromJsonString`
 * for strict checking.
 */
export declare function mergeFromJson<Desc extends DescMessage>(schema: Desc, target: MessageShape<Desc>, json: JsonValue, options?: Partial<JsonReadOptions>): MessageShape<Desc>;
/**
 * Parses an enum value from JSON.
 */
export declare function enumFromJson<Desc extends DescEnum>(descEnum: Desc, json: EnumJsonType<Desc>): EnumShape<Desc>;
/**
 * Is the given value a JSON enum value?
 */
export declare function isEnumJson<Desc extends DescEnum>(descEnum: Desc, value: unknown): value is EnumJsonType<Desc>;
