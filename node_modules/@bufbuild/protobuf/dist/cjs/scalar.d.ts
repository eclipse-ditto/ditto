/**
 * Scalar value types. This is a subset of field types declared by protobuf
 * enum google.protobuf.FieldDescriptorProto.Type The types GROUP and MESSAGE
 * are omitted, but the numerical values are identical.
 */
export declare enum ScalarType {
    DOUBLE = 1,
    FLOAT = 2,
    INT64 = 3,
    UINT64 = 4,
    INT32 = 5,
    FIXED64 = 6,
    FIXED32 = 7,
    BOOL = 8,
    STRING = 9,
    BYTES = 12,
    UINT32 = 13,
    SFIXED32 = 15,
    SFIXED64 = 16,
    SINT32 = 17,// Uses ZigZag encoding.
    SINT64 = 18
}
/**
 * JavaScript representation of fields with 64 bit integral types (int64, uint64,
 * sint64, fixed64, sfixed64).
 *
 * This is a subset of google.protobuf.FieldOptions.JSType, which defines JS_NORMAL,
 * JS_STRING, and JS_NUMBER. Protobuf-ES uses BigInt by default, but will use
 * String if `[jstype = JS_STRING]` is specified.
 *
 * ```protobuf
 * uint64 field_a = 1; // BigInt
 * uint64 field_b = 2 [jstype = JS_NORMAL]; // BigInt
 * uint64 field_b = 2 [jstype = JS_NUMBER]; // BigInt
 * uint64 field_b = 2 [jstype = JS_STRING]; // String
 * ```
 */
export declare enum LongType {
    /**
     * Use JavaScript BigInt.
     */
    BIGINT = 0,
    /**
     * Use JavaScript String.
     *
     * Field option `[jstype = JS_STRING]`.
     */
    STRING = 1
}
/**
 * ScalarValue maps from a scalar field type to a TypeScript value type.
 */
export type ScalarValue<T = ScalarType, L extends LongType = LongType.STRING | LongType.BIGINT> = T extends ScalarType.STRING ? string : T extends ScalarType.INT32 ? number : T extends ScalarType.UINT32 ? number : T extends ScalarType.UINT32 ? number : T extends ScalarType.SINT32 ? number : T extends ScalarType.FIXED32 ? number : T extends ScalarType.SFIXED32 ? number : T extends ScalarType.FLOAT ? number : T extends ScalarType.DOUBLE ? number : T extends ScalarType.INT64 ? (L extends LongType.STRING ? string : bigint) : T extends ScalarType.SINT64 ? (L extends LongType.STRING ? string : bigint) : T extends ScalarType.SFIXED64 ? (L extends LongType.STRING ? string : bigint) : T extends ScalarType.UINT64 ? (L extends LongType.STRING ? string : bigint) : T extends ScalarType.FIXED64 ? (L extends LongType.STRING ? string : bigint) : T extends ScalarType.BOOL ? boolean : T extends ScalarType.BYTES ? Uint8Array : never;
