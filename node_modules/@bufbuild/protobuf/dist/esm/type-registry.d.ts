import type { MessageType } from "./message-type.js";
import type { EnumType } from "./enum.js";
import type { ServiceType } from "./service-type.js";
import type { Extension } from "./extension.js";
/**
 * IMessageTypeRegistry provides look-up for message types.
 *
 * You can conveniently create a registry using the createRegistry()
 * function:
 *
 * ```ts
 * import { createRegistry } from "@bufbuild/protobuf";
 * import { MyMessage, MyOtherMessage } from "./gen/my_message_pb.js";
 *
 * const reg: IMessageTypeRegistry = createRegistry(
 *   MyMessage,
 *   MyOtherMessage,
 * );
 * ```
 */
export interface IMessageTypeRegistry {
    /**
     * Find a message type by its protobuf type name.
     */
    findMessage(typeName: string): MessageType | undefined;
}
/**
 * IEnumTypeRegistry provides look-up for enum types.
 */
export interface IEnumTypeRegistry {
    /**
     * Find an enum type by its protobuf type name.
     */
    findEnum(typeName: string): EnumType | undefined;
}
/**
 * IServiceTypeRegistry provides look-up for service types.
 */
export interface IServiceTypeRegistry {
    /**
     * Find a service type by its protobuf type name.
     */
    findService(typeName: string): ServiceType | undefined;
}
/**
 * IExtensionRegistry provides look-up for extensions.
 */
export interface IExtensionRegistry {
    /**
     * Find an extension by the extendee type name and the extension number.
     */
    findExtensionFor(extendee: string, no: number): Extension | undefined;
    /**
     * Find an extension type by its protobuf type name.
     */
    findExtension(typeName: string): Extension | undefined;
}
