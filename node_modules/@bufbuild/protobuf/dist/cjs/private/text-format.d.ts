import type { DescEnum } from "../descriptor-set.js";
import { ScalarType } from "../scalar.js";
export declare function parseTextFormatEnumValue(descEnum: DescEnum, value: string): number;
export declare function parseTextFormatScalarValue(type: ScalarType, value: string): number | boolean | string | bigint | Uint8Array;
