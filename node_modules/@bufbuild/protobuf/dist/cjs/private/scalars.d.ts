import { LongType, ScalarType } from "../scalar.js";
import type { ScalarValue } from "../scalar.js";
/**
 * Returns true if both scalar values are equal.
 */
export declare function scalarEquals(type: ScalarType, a: string | boolean | number | bigint | Uint8Array | undefined, b: string | boolean | number | bigint | Uint8Array | undefined): boolean;
/**
 * Returns the zero value for the given scalar type.
 */
export declare function scalarZeroValue<T extends ScalarType, L extends LongType>(type: T, longType: L): ScalarValue<T, L>;
/**
 * Returns true for a zero-value. For example, an integer has the zero-value `0`,
 * a boolean is `false`, a string is `""`, and bytes is an empty Uint8Array.
 *
 * In proto3, zero-values are not written to the wire, unless the field is
 * optional or repeated.
 */
export declare function isScalarZeroValue(type: ScalarType, value: unknown): boolean;
