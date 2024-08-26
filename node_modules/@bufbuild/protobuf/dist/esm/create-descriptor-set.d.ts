import { FeatureSetDefaults, FileDescriptorProto, FileDescriptorSet } from "./google/protobuf/descriptor_pb.js";
import type { DescriptorSet } from "./descriptor-set.js";
import type { BinaryReadOptions, BinaryWriteOptions } from "./binary-format.js";
/**
 * Create a DescriptorSet, a convenient interface for working with a set of
 * google.protobuf.FileDescriptorProto.
 *
 * Note that files must be given in topological order, so each file appears
 * before any file that imports it. Protocol buffer compilers always produce
 * files in topological order.
 */
export declare function createDescriptorSet(input: FileDescriptorProto[] | FileDescriptorSet | Uint8Array, options?: CreateDescriptorSetOptions): DescriptorSet;
/**
 * Options to createDescriptorSet()
 */
interface CreateDescriptorSetOptions {
    /**
     * Editions support language-specific features with extensions to
     * google.protobuf.FeatureSet. They can define defaults, and specify on
     * which targets the features can be set.
     *
     * To create a DescriptorSet that provides your language-specific features,
     * you have to provide a google.protobuf.FeatureSetDefaults message in this
     * option. It can also specify the minimum and maximum supported edition.
     *
     * The defaults can be generated with `protoc` - see the flag
     * `--experimental_edition_defaults_out`.
     */
    featureSetDefaults?: FeatureSetDefaults;
    /**
     * Internally, data is serialized when features are resolved. The
     * serialization options given here will be used for feature resolution.
     */
    serializationOptions?: Partial<BinaryReadOptions & BinaryWriteOptions>;
}
export {};
