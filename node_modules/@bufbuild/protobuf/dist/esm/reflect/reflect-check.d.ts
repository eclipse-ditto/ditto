import { type DescEnum, type DescField, type DescMessage, ScalarType } from "../descriptors.js";
import { FieldError } from "./error.js";
/**
 * Check whether the given field value is valid for the reflect API.
 */
export declare function checkField(field: DescField, value: unknown): FieldError | undefined;
/**
 * Check whether the given list item is valid for the reflect API.
 */
export declare function checkListItem(field: DescField & {
    fieldKind: "list";
}, index: number, value: unknown): FieldError | undefined;
/**
 * Check whether the given map key and value are valid for the reflect API.
 */
export declare function checkMapEntry(field: DescField & {
    fieldKind: "map";
}, key: unknown, value: unknown): FieldError | undefined;
type InvalidScalarValueErr = false | "invalid UTF8" | `${string} out of range`;
/**
 * Return the check for values of the given scalar type.
 *
 * @private
 */
export declare function checkScalarValue(scalar: ScalarType): (value: unknown) => true | InvalidScalarValueErr;
/**
 * Format the reason why a value is invalid for a singular field.
 *
 * @private
 */
export declare function reasonSingular(field: {
    scalar: ScalarType;
    message?: undefined;
    enum?: undefined;
} | {
    scalar?: undefined;
    message: DescMessage;
    enum?: undefined;
} | {
    scalar?: undefined;
    message?: undefined;
    enum: DescEnum;
}, val: unknown, details?: string | false): string;
export declare function formatVal(val: unknown): string;
export {};
