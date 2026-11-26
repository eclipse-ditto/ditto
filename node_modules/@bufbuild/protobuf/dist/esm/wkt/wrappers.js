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
export function isWrapper(arg) {
    return isWrapperTypeName(arg.$typeName);
}
export function isWrapperDesc(messageDesc) {
    const f = messageDesc.fields[0];
    return (isWrapperTypeName(messageDesc.typeName) &&
        f !== undefined &&
        f.fieldKind == "scalar" &&
        f.name == "value" &&
        f.number == 1);
}
/**
 * Returns true if the descriptor is a well-known type with a custom JSON
 * representation per the protobuf JSON spec. Examples: Timestamp as an
 * RFC 3339 string, Duration as "5s", wrappers as the unwrapped scalar.
 *
 * When packed inside `google.protobuf.Any`, these messages are serialized
 * as `{"@type": ..., "value": <custom form>}`; all other messages embed
 * their fields directly.
 */
export function hasCustomJsonRepresentation(desc) {
    switch (desc.typeName) {
        case "google.protobuf.Any":
        case "google.protobuf.Timestamp":
        case "google.protobuf.Duration":
        case "google.protobuf.FieldMask":
        case "google.protobuf.Struct":
        case "google.protobuf.Value":
        case "google.protobuf.ListValue":
            return true;
        default:
            return isWrapperDesc(desc);
    }
}
const wrapperTypeNames = /*@__PURE__*/ new Set([
    "google.protobuf.DoubleValue",
    "google.protobuf.FloatValue",
    "google.protobuf.Int64Value",
    "google.protobuf.UInt64Value",
    "google.protobuf.Int32Value",
    "google.protobuf.UInt32Value",
    "google.protobuf.BoolValue",
    "google.protobuf.StringValue",
    "google.protobuf.BytesValue",
]);
function isWrapperTypeName(name) {
    return wrapperTypeNames.has(name);
}
