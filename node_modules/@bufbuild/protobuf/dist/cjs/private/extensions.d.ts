import type { Extension } from "../extension.js";
import type { AnyMessage, Message } from "../message.js";
import type { FieldInfo, OneofInfo, PartialFieldInfo } from "../field.js";
import { WireType } from "../binary-encoding.js";
import type { ProtoRuntime } from "./proto-runtime.js";
import type { MessageType } from "../message-type.js";
export type ExtensionFieldSource = extensionFieldRules<FieldInfo> | extensionFieldRules<PartialFieldInfo> | (() => extensionFieldRules<FieldInfo>) | (() => extensionFieldRules<PartialFieldInfo>);
type extensionFieldRules<T extends FieldInfo | PartialFieldInfo> = T extends {
    kind: "map";
} ? never : T extends {
    oneof: string;
} ? never : T extends {
    oneof: OneofInfo;
} ? never : Omit<T, "name"> & Partial<Pick<T, "name">>;
/**
 * Create a new extension using the given runtime.
 */
export declare function makeExtension<E extends Message<E> = AnyMessage, V = unknown>(runtime: ProtoRuntime, typeName: string, extendee: MessageType<E>, field: ExtensionFieldSource): Extension<E, V>;
/**
 * Create a container that allows us to read extension fields into it with the
 * same logic as regular fields.
 */
export declare function createExtensionContainer<E extends Message<E> = AnyMessage, V = unknown>(extension: Extension<E, V>): [Record<string, V>, () => V];
type UnknownField = {
    no: number;
    wireType: WireType;
    data: Uint8Array;
};
type UnknownFields = ReadonlyArray<UnknownField>;
/**
 * Helper to filter unknown fields, optimized based on field type.
 */
export declare function filterUnknownFields(unknownFields: UnknownFields, field: Pick<FieldInfo, "no" | "kind" | "repeated">): UnknownField[];
export {};
