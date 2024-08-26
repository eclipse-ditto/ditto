import type { FieldInfo } from "./field.js";
import type { AnyMessage, Message } from "./message.js";
import type { MessageType } from "./message-type.js";
import type { ProtoRuntime } from "./private/proto-runtime.js";
export interface Extension<E extends Message<E> = AnyMessage, V = unknown> {
    /**
     * The fully qualified name of the extension.
     */
    readonly typeName: string;
    /**
     * The message extended by this extension.
     */
    readonly extendee: MessageType<E>;
    /**
     * Field information for this extension. Note that required fields, maps,
     * oneof are not allowed in extensions. Behavior of "localName" property is
     * undefined and must not be relied upon.
     */
    readonly field: FieldInfo;
    /**
     * Provides serialization and other functionality.
     */
    readonly runtime: ProtoRuntime;
}
