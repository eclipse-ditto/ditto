import type { AnyDesc } from "../descriptors.js";
/**
 * Return a fully-qualified name for a Protobuf descriptor.
 * For a file descriptor, return the original file path.
 *
 * See https://protobuf.com/docs/language-spec#fully-qualified-names
 */
export declare function qualifiedName(desc: AnyDesc): string;
/**
 * Converts snake_case to protoCamelCase according to the convention
 * used by protoc to convert a field name to a JSON name.
 *
 * See https://protobuf.com/docs/language-spec#default-json-names
 *
 * The function protoSnakeCase provides the reverse.
 */
export declare function protoCamelCase(snakeCase: string): string;
/**
 * Converts protoCamelCase to snake_case.
 *
 * This function is the reverse of function protoCamelCase. Note that some names
 * are not reversible - for example, "foo__bar" -> "fooBar" -> "foo_bar".
 */
export declare function protoSnakeCase(lowerCamelCase: string): string;
/**
 * Escapes names that are reserved for ECMAScript built-in object properties.
 *
 * Also see safeIdentifier() from @bufbuild/protoplugin.
 */
export declare function safeObjectProperty(name: string): string;
