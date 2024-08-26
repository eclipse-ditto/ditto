import type { FieldListSource } from "./field-list.js";
import type { FieldInfo } from "../field.js";
/**
 * Convert a collection of field info to an array of normalized FieldInfo.
 *
 * The argument `packedByDefault` specifies whether fields that do not specify
 * `packed` should be packed (proto3) or unpacked (proto2).
 */
export declare function normalizeFieldInfos(fieldInfos: FieldListSource, packedByDefault: boolean): FieldInfo[];
