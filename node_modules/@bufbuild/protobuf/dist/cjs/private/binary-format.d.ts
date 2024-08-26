import type { IBinaryWriter } from "../binary-encoding.js";
import type { BinaryFormat, BinaryWriteOptions } from "../binary-format.js";
import type { FieldInfo } from "../field.js";
export declare function makeBinaryFormat(): BinaryFormat;
export declare function writeMapEntry(writer: IBinaryWriter, options: BinaryWriteOptions, field: FieldInfo & {
    kind: "map";
}, key: string, value: any): void;
