import { Edition, FeatureSet, FeatureSetDefaults } from "../google/protobuf/descriptor_pb.js";
import type { BinaryReadOptions, BinaryWriteOptions } from "../binary-format.js";
/**
 * A merged google.protobuf.FeaturesSet, with all fields guaranteed to be set.
 */
export type MergedFeatureSet = FeatureSet & Required<FeatureSet>;
/**
 * A function that resolves features.
 *
 * If no feature set is provided, the default feature set for the edition is
 * returned. If features are provided, they are merged into the edition default
 * features.
 */
export type FeatureResolverFn = (a?: FeatureSet, b?: FeatureSet) => MergedFeatureSet;
/**
 * Create an edition feature resolver with the given feature set defaults, or
 * the feature set defaults supported by @bufbuild/protobuf.
 */
export declare function createFeatureResolver(edition: Edition, compiledFeatureSetDefaults?: FeatureSetDefaults, serializationOptions?: Partial<BinaryReadOptions & BinaryWriteOptions>): FeatureResolverFn;
