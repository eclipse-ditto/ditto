import type { Message } from "./message.js";
import type { BinaryReadOptions, BinaryWriteOptions } from "./binary-format.js";
import type { Extension } from "./extension.js";
/**
 * Retrieve an extension value from a message.
 *
 * The function never returns undefined. Use hasExtension() to check whether an
 * extension is set. If the extension is not set, this function returns the
 * default value (if one was specified in the protobuf source), or the zero value
 * (for example `0` for numeric types, `[]` for repeated extension fields, and
 * an empty message instance for message fields).
 *
 * Extensions are stored as unknown fields on a message. To mutate an extension
 * value, make sure to store the new value with setExtension() after mutating.
 *
 * If the extension does not extend the given message, an error is raised.
 */
export declare function getExtension<E extends Message<E>, V>(message: E, extension: Extension<E, V>, options?: Partial<BinaryReadOptions>): V;
/**
 * Set an extension value on a message. If the message already has a value for
 * this extension, the value is replaced.
 *
 * If the extension does not extend the given message, an error is raised.
 */
export declare function setExtension<E extends Message<E>, V>(message: E, extension: Extension<E, V>, value: V, options?: Partial<BinaryReadOptions & BinaryWriteOptions>): void;
/**
 * Remove an extension value from a message.
 *
 * If the extension does not extend the given message, an error is raised.
 */
export declare function clearExtension<E extends Message<E>, V>(message: E, extension: Extension<E, V>): void;
/**
 * Check whether an extension is set on a message.
 */
export declare function hasExtension<E extends Message<E>, V>(message: E, extension: Extension<E, V>): boolean;
