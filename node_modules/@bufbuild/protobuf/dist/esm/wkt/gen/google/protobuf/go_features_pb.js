// Copyright 2021-2026 Buf Technologies, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
import { fileDesc } from "../../../../codegenv2/file.js";
import { file_google_protobuf_descriptor } from "./descriptor_pb.js";
import { messageDesc } from "../../../../codegenv2/message.js";
import { enumDesc } from "../../../../codegenv2/enum.js";
import { extDesc } from "../../../../codegenv2/extension.js";
/**
 * Describes the file google/protobuf/go_features.proto.
 */
export const file_google_protobuf_go_features = /*@__PURE__*/ fileDesc("CiFnb29nbGUvcHJvdG9idWYvZ29fZmVhdHVyZXMucHJvdG8SAnBiItEGCgpHb0ZlYXR1cmVzEqUBChpsZWdhY3lfdW5tYXJzaGFsX2pzb25fZW51bRgBIAEoCEKAAYgBAZgBBpgBAaIBCRIEdHJ1ZRiEB6IBChIFZmFsc2UY5weyAVsI6AcQ6AcaU1RoZSBsZWdhY3kgVW5tYXJzaGFsSlNPTiBBUEkgaXMgZGVwcmVjYXRlZCBhbmQgd2lsbCBiZSByZW1vdmVkIGluIGEgZnV0dXJlIGVkaXRpb24uEmoKCWFwaV9sZXZlbBgCIAEoDjIXLnBiLkdvRmVhdHVyZXMuQVBJTGV2ZWxCPogBAZgBA5gBAaIBGhIVQVBJX0xFVkVMX1VOU1BFQ0lGSUVEGIQHogEPEgpBUElfT1BBUVVFGOkHsgEDCOgHEmsKEXN0cmlwX2VudW1fcHJlZml4GAMgASgOMh4ucGIuR29GZWF0dXJlcy5TdHJpcEVudW1QcmVmaXhCMIgBAZgBBpgBB5gBAaIBGxIWU1RSSVBfRU5VTV9QUkVGSVhfS0VFUBiEB7IBAwjpBxJ4Cg1vcHRpbWl6ZV9tb2RlGAQgASgOMi8ucGIuR29GZWF0dXJlcy5PcHRpbWl6ZU1vZGVGZWF0dXJlLk9wdGltaXplTW9kZUIwiAEBmAEDmAEBogEeEhlPUFRJTUlaRV9NT0RFX1VOU1BFQ0lGSUVEGIQHsgEDCOkHGl4KE09wdGltaXplTW9kZUZlYXR1cmUiRwoMT3B0aW1pemVNb2RlEh0KGU9QVElNSVpFX01PREVfVU5TUEVDSUZJRUQQABIJCgVTUEVFRBABEg0KCUNPREVfU0laRRACIlMKCEFQSUxldmVsEhkKFUFQSV9MRVZFTF9VTlNQRUNJRklFRBAAEgwKCEFQSV9PUEVOEAESDgoKQVBJX0hZQlJJRBACEg4KCkFQSV9PUEFRVUUQAyKSAQoPU3RyaXBFbnVtUHJlZml4EiEKHVNUUklQX0VOVU1fUFJFRklYX1VOU1BFQ0lGSUVEEAASGgoWU1RSSVBfRU5VTV9QUkVGSVhfS0VFUBABEiMKH1NUUklQX0VOVU1fUFJFRklYX0dFTkVSQVRFX0JPVEgQAhIbChdTVFJJUF9FTlVNX1BSRUZJWF9TVFJJUBADOjwKAmdvEhsuZ29vZ2xlLnByb3RvYnVmLkZlYXR1cmVTZXQY6gcgASgLMg4ucGIuR29GZWF0dXJlc1ICZ29CL1otZ29vZ2xlLmdvbGFuZy5vcmcvcHJvdG9idWYvdHlwZXMvZ29mZWF0dXJlc3Bi", [file_google_protobuf_descriptor]);
/**
 * Describes the message pb.GoFeatures.
 * Use `create(GoFeaturesSchema)` to create a new message.
 */
export const GoFeaturesSchema = /*@__PURE__*/ messageDesc(file_google_protobuf_go_features, 0);
/**
 * Describes the message pb.GoFeatures.OptimizeModeFeature.
 * Use `create(GoFeatures_OptimizeModeFeatureSchema)` to create a new message.
 */
export const GoFeatures_OptimizeModeFeatureSchema = /*@__PURE__*/ messageDesc(file_google_protobuf_go_features, 0, 0);
/**
 * The name of this enum matches OptimizeMode in descriptor.proto.
 *
 * @generated from enum pb.GoFeatures.OptimizeModeFeature.OptimizeMode
 */
export var GoFeatures_OptimizeModeFeature_OptimizeMode;
(function (GoFeatures_OptimizeModeFeature_OptimizeMode) {
    /**
     * OPTIMIZE_MODE_UNSPECIFIED results in falling back to the default
     * (optimize for code size), but needs to be a separate value to distinguish
     * between an explicitly set optimize mode or a missing optimize mode.
     *
     * @generated from enum value: OPTIMIZE_MODE_UNSPECIFIED = 0;
     */
    GoFeatures_OptimizeModeFeature_OptimizeMode[GoFeatures_OptimizeModeFeature_OptimizeMode["OPTIMIZE_MODE_UNSPECIFIED"] = 0] = "OPTIMIZE_MODE_UNSPECIFIED";
    /**
     * @generated from enum value: SPEED = 1;
     */
    GoFeatures_OptimizeModeFeature_OptimizeMode[GoFeatures_OptimizeModeFeature_OptimizeMode["SPEED"] = 1] = "SPEED";
    /**
     * There is no enum entry for LITE_RUNTIME (descriptor.proto),
     * because Go Protobuf does not have the concept of a lite runtime.
     *
     * @generated from enum value: CODE_SIZE = 2;
     */
    GoFeatures_OptimizeModeFeature_OptimizeMode[GoFeatures_OptimizeModeFeature_OptimizeMode["CODE_SIZE"] = 2] = "CODE_SIZE";
})(GoFeatures_OptimizeModeFeature_OptimizeMode || (GoFeatures_OptimizeModeFeature_OptimizeMode = {}));
/**
 * Describes the enum pb.GoFeatures.OptimizeModeFeature.OptimizeMode.
 */
export const GoFeatures_OptimizeModeFeature_OptimizeModeSchema = /*@__PURE__*/ enumDesc(file_google_protobuf_go_features, 0, 0, 0);
/**
 * @generated from enum pb.GoFeatures.APILevel
 */
export var GoFeatures_APILevel;
(function (GoFeatures_APILevel) {
    /**
     * API_LEVEL_UNSPECIFIED results in selecting the OPEN API,
     * but needs to be a separate value to distinguish between
     * an explicitly set api level or a missing api level.
     *
     * @generated from enum value: API_LEVEL_UNSPECIFIED = 0;
     */
    GoFeatures_APILevel[GoFeatures_APILevel["API_LEVEL_UNSPECIFIED"] = 0] = "API_LEVEL_UNSPECIFIED";
    /**
     * @generated from enum value: API_OPEN = 1;
     */
    GoFeatures_APILevel[GoFeatures_APILevel["API_OPEN"] = 1] = "API_OPEN";
    /**
     * @generated from enum value: API_HYBRID = 2;
     */
    GoFeatures_APILevel[GoFeatures_APILevel["API_HYBRID"] = 2] = "API_HYBRID";
    /**
     * @generated from enum value: API_OPAQUE = 3;
     */
    GoFeatures_APILevel[GoFeatures_APILevel["API_OPAQUE"] = 3] = "API_OPAQUE";
})(GoFeatures_APILevel || (GoFeatures_APILevel = {}));
/**
 * Describes the enum pb.GoFeatures.APILevel.
 */
export const GoFeatures_APILevelSchema = /*@__PURE__*/ enumDesc(file_google_protobuf_go_features, 0, 0);
/**
 * @generated from enum pb.GoFeatures.StripEnumPrefix
 */
export var GoFeatures_StripEnumPrefix;
(function (GoFeatures_StripEnumPrefix) {
    /**
     * @generated from enum value: STRIP_ENUM_PREFIX_UNSPECIFIED = 0;
     */
    GoFeatures_StripEnumPrefix[GoFeatures_StripEnumPrefix["UNSPECIFIED"] = 0] = "UNSPECIFIED";
    /**
     * @generated from enum value: STRIP_ENUM_PREFIX_KEEP = 1;
     */
    GoFeatures_StripEnumPrefix[GoFeatures_StripEnumPrefix["KEEP"] = 1] = "KEEP";
    /**
     * @generated from enum value: STRIP_ENUM_PREFIX_GENERATE_BOTH = 2;
     */
    GoFeatures_StripEnumPrefix[GoFeatures_StripEnumPrefix["GENERATE_BOTH"] = 2] = "GENERATE_BOTH";
    /**
     * @generated from enum value: STRIP_ENUM_PREFIX_STRIP = 3;
     */
    GoFeatures_StripEnumPrefix[GoFeatures_StripEnumPrefix["STRIP"] = 3] = "STRIP";
})(GoFeatures_StripEnumPrefix || (GoFeatures_StripEnumPrefix = {}));
/**
 * Describes the enum pb.GoFeatures.StripEnumPrefix.
 */
export const GoFeatures_StripEnumPrefixSchema = /*@__PURE__*/ enumDesc(file_google_protobuf_go_features, 0, 1);
/**
 * @generated from extension: optional pb.GoFeatures go = 1002;
 */
export const go = /*@__PURE__*/ extDesc(file_google_protobuf_go_features, 0);
