import { ScalarType } from "../descriptors.js";
/**
 * ScalarValue maps from a scalar field type to a TypeScript value type.
 */
export type ScalarValue<T = ScalarType, LongAsString extends boolean = false> = T extends ScalarType.STRING ? string : T extends ScalarType.INT32 ? number : T extends ScalarType.UINT32 ? number : T extends ScalarType.SINT32 ? number : T extends ScalarType.FIXED32 ? number : T extends ScalarType.SFIXED32 ? number : T extends ScalarType.FLOAT ? number : T extends ScalarType.DOUBLE ? number : T extends ScalarType.INT64 ? LongAsString extends true ? string : bigint : T extends ScalarType.SINT64 ? LongAsString extends true ? string : bigint : T extends ScalarType.SFIXED64 ? LongAsString extends true ? string : bigint : T extends ScalarType.UINT64 ? LongAsString extends true ? string : bigint : T extends ScalarType.FIXED64 ? LongAsString extends true ? string : bigint : T extends ScalarType.BOOL ? boolean : T extends ScalarType.BYTES ? Uint8Array : never;
/**
 * Returns true if both scalar values are equal.
 *
 * For float and double, values are compared following IEEE semantics: -0
 * equals 0, and NaN does not equal NaN. This is value equality, not identity.
 *
 * It deliberately differs from isScalarZeroValue, which treats -0 as distinct
 * from 0. A value can equal the zero value without being a zero value
 * (scalarEquals(DOUBLE, -0, 0) is true while isScalarZeroValue(DOUBLE, -0) is
 * false) so this function must not be used to derive implicit presence.
 */
export declare function scalarEquals(type: ScalarType, a: ScalarValue | undefined, b: ScalarValue | undefined): boolean;
/**
 * Returns the zero value for the given scalar type, the value a field of this
 * type has when unset: 0 for numeric types, "" for strings, false for
 * booleans, and an empty Uint8Array for bytes. For 64-bit integer types, the
 * result is "0" when longAsString is true, otherwise 0n.
 *
 * This is the type's zero value, not a proto2 custom field default. For float
 * and double it is +0; isScalarZeroValue treats only +0, not -0, as this value.
 */
export declare function scalarZeroValue<T extends ScalarType, LongAsString extends boolean>(type: T, longAsString: LongAsString): ScalarValue<T, LongAsString>;
/**
 * Returns true if the value is the zero value for the given scalar type: `0`
 * for numeric types, `false` for booleans, `""` for strings, and an empty
 * Uint8Array for bytes.
 *
 * This is the implicit-presence default check. A singular field with implicit
 * presence is treated as unset, and omitted from the wire, when its value is
 * the zero value. With explicit presence, or in repeated and map fields,
 * presence is structural and this function does not apply.
 *
 * Note that -0 is NOT a zero value for float and double: under implicit
 * presence, +0 is omitted from the wire but -0 is written, following the
 * proto3 specification. As a result this can disagree with scalarEquals, which
 * compares by value and treats -0 as equal to 0.
 */
export declare function isScalarZeroValue(type: ScalarType, value: unknown): boolean;
