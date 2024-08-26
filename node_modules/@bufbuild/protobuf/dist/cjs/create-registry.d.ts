import type { MessageType } from "./message-type.js";
import type { EnumType } from "./enum.js";
import type { ServiceType } from "./service-type.js";
import type { IEnumTypeRegistry, IExtensionRegistry, IMessageTypeRegistry, IServiceTypeRegistry } from "./type-registry.js";
import type { Extension } from "./extension.js";
/**
 * Create a new registry from the given types.
 */
export declare function createRegistry(...types: Array<MessageType | EnumType | ServiceType | Extension>): IMessageTypeRegistry & IEnumTypeRegistry & IExtensionRegistry & IServiceTypeRegistry;
