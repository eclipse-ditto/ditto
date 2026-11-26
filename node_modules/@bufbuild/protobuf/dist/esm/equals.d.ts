import type { MessageShape } from "./types.js";
import { type DescMessage } from "./descriptors.js";
import type { Registry } from "./registry.js";
interface EqualsOptions {
    /**
     * A registry to look up extensions, and messages packed in Any.
     *
     * @private Experimental API, does not follow semantic versioning.
     */
    registry: Registry;
    /**
     * Unpack google.protobuf.Any before comparing.
     * If a type is not in the registry, comparison falls back to comparing the
     * fields of Any.
     *
     * @private Experimental API, does not follow semantic versioning.
     */
    unpackAny?: boolean;
    /**
     * Consider extensions when comparing.
     *
     * @private Experimental API, does not follow semantic versioning.
     */
    extensions?: boolean;
    /**
     * Consider unknown fields when comparing.
     * The registry is used to distinguish between extensions, and unknown fields
     * caused by schema changes.
     *
     * @private Experimental API, does not follow semantic versioning.
     */
    unknown?: boolean;
}
/**
 * Compare two messages of the same type for equality.
 *
 * Fields are compared one by one, but only when set: a field set in one message
 * but not the other makes them unequal, while fields set in both are compared
 * by value. Extensions and unknown fields are disregarded by default.
 *
 * Float and double values are compared with IEEE semantics: NaN does not equal
 * NaN, and -0 equals 0 (with the exception for singular fields with implicit
 * presence: -0 is set, +0 is unset).
 */
export declare function equals<Desc extends DescMessage>(schema: Desc, a: MessageShape<Desc>, b: MessageShape<Desc>, options?: EqualsOptions): boolean;
export {};
