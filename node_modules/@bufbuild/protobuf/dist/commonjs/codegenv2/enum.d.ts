import type { DescEnum, DescFile } from "../descriptors.js";
import type { GenEnum } from "./types.js";
import type { JsonValue } from "../json-value.js";
/**
 * Hydrate an enum descriptor.
 *
 * @private
 */
export declare function enumDesc<Shape extends number, JsonType extends JsonValue = JsonValue>(file: DescFile, path: number, ...paths: number[]): GenEnum<Shape, JsonType>;
/**
 * Construct a TypeScript enum object at runtime from a descriptor.
 *
 * The returned object is identical to a transpiled TS enum and includes the
 * reverse mapping, see https://www.typescriptlang.org/docs/handbook/enums.html#reverse-mappings
 */
export declare function tsEnum(desc: DescEnum): {
    [key: number]: string;
    [k: string]: string | number;
};
/**
 * Construct an object enum at runtime from a descriptor.
 *
 * The returned object is a record of enum value name to integer value. It's
 * a subset of transpiled TS enums - it does not include the reverse mapping,
 * and only supports lookup by value name.
 */
export declare function objEnum(desc: DescEnum): {
    [key: string]: number;
};
