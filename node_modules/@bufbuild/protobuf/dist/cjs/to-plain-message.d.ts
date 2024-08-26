import { Message } from "./message.js";
import type { PlainMessage } from "./message.js";
/**
 * toPlainMessage returns a new object by stripping
 * all methods from a message, leaving only fields and
 * oneof groups. It is recursive, meaning it applies this
 * same logic to all nested message fields as well.
 *
 * If the argument is already a plain message, it is
 * returned as-is.
 */
export declare function toPlainMessage<T extends Message<T>>(message: T | PlainMessage<T>): PlainMessage<T>;
