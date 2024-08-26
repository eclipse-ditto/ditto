import type { FieldInfo } from "../field.js";
/**
 * Returns true if the field is set.
 */
export declare function isFieldSet(field: FieldInfo, target: Record<string, any>): boolean;
/**
 * Resets the field, so that isFieldSet() will return false.
 */
export declare function clearField(field: FieldInfo, target: Record<string, any>): void;
