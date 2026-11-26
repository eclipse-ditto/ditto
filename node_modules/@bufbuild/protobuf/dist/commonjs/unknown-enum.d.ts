import type { DescEnum } from "./descriptors.js";
import type { UnknownEnum } from "./types.js";
/**
 * Open enums can contain numeric values that are not in the set of values
 * defined by the enum.
 *
 * This function returns true for those values, and narrows the type to
 * `UnknownEnum`.
 */
export declare function isUnknownEnum(desc: DescEnum, value: number): value is UnknownEnum;
