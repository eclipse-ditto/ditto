"use strict";
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
Object.defineProperty(exports, "__esModule", { value: true });
exports.localMessageMapper = localMessageMapper;
exports.wktStructToReflect = wktStructToReflect;
exports.wktStructToLocal = wktStructToLocal;
const create_js_1 = require("../create.js");
const guard_js_1 = require("./guard.js");
const wrappers_js_1 = require("../wkt/wrappers.js");
// google.protobuf.NullValue.NULL_VALUE;
const NULL_VALUE = 0;
/**
 * Return the conversions between the local representation of the field
 * value and the message it represents.
 *
 * @private
 */
function localMessageMapper(field) {
    // google.protobuf.Struct fields are stored as JsonObject.
    if (usesJsonRepresentation(field)) {
        return {
            toMessage: (local) => wktStructToReflect(local),
            toLocal: (message) => wktStructToLocal(message),
        };
    }
    // Singular wrapper fields outside a oneof are unwrapped to the scalar value.
    if (field.fieldKind == "message" &&
        !field.oneof &&
        (0, wrappers_js_1.isWrapperDesc)(field.message)) {
        const wrapperDesc = field.message;
        const valueLocalName = wrapperDesc.fields[0].localName;
        return {
            toMessage: (local) => {
                const message = (0, create_js_1.create)(wrapperDesc);
                if (local !== undefined) {
                    message[valueLocalName] = local;
                }
                return message;
            },
            toLocal: (message) => message[valueLocalName],
        };
    }
    // For all other fields, the local value is the message itself.
    const childDesc = field.message;
    return {
        toMessage: (local) => (local === undefined ? (0, create_js_1.create)(childDesc) : local),
        toLocal: (message) => message,
    };
}
/**
 * Returns true if values of this field are stored as JsonValue instead of
 * a message: google.protobuf.Struct is represented with JsonObject when
 * used in a field, except when used in google.protobuf.Value.
 */
function usesJsonRepresentation(field) {
    return (field.message.typeName == "google.protobuf.Struct" &&
        field.parent.typeName != "google.protobuf.Value");
}
/**
 * Convert the JsonValue representation of a google.protobuf.Struct to the
 * message representation.
 *
 * @private
 */
function wktStructToReflect(json) {
    const struct = {
        $typeName: "google.protobuf.Struct",
        fields: {},
    };
    if ((0, guard_js_1.isObject)(json)) {
        for (const k of Object.keys(json)) {
            struct.fields[k] = wktValueToReflect(json[k]);
        }
    }
    return struct;
}
/**
 * Convert a google.protobuf.Struct message to its JsonValue representation.
 *
 * @private
 */
function wktStructToLocal(val) {
    const json = {};
    for (const k of Object.keys(val.fields)) {
        json[k] = wktValueToLocal(val.fields[k]);
    }
    return json;
}
function wktValueToLocal(val) {
    switch (val.kind.case) {
        case "structValue":
            return wktStructToLocal(val.kind.value);
        case "listValue":
            return val.kind.value.values.map(wktValueToLocal);
        case "nullValue":
        case undefined:
            return null;
        default:
            return val.kind.value;
    }
}
function wktValueToReflect(json) {
    const value = {
        $typeName: "google.protobuf.Value",
        kind: { case: undefined },
    };
    switch (typeof json) {
        case "number":
            value.kind = { case: "numberValue", value: json };
            break;
        case "string":
            value.kind = { case: "stringValue", value: json };
            break;
        case "boolean":
            value.kind = { case: "boolValue", value: json };
            break;
        case "object":
            if (json === null) {
                value.kind = { case: "nullValue", value: NULL_VALUE };
            }
            else if (Array.isArray(json)) {
                const listValue = {
                    $typeName: "google.protobuf.ListValue",
                    values: [],
                };
                if (Array.isArray(json)) {
                    for (const e of json) {
                        listValue.values.push(wktValueToReflect(e));
                    }
                }
                value.kind = {
                    case: "listValue",
                    value: listValue,
                };
            }
            else {
                value.kind = {
                    case: "structValue",
                    value: wktStructToReflect(json),
                };
            }
            break;
    }
    return value;
}
