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
exports.toJson = toJson;
exports.toJsonString = toJsonString;
exports.enumToJson = enumToJson;
const descriptors_js_1 = require("./descriptors.js");
const names_js_1 = require("./reflect/names.js");
const index_js_1 = require("./wkt/index.js");
const wrappers_js_1 = require("./wkt/wrappers.js");
const json_js_1 = require("./wkt/json.js");
const index_js_2 = require("./wire/index.js");
const extensions_js_1 = require("./extensions.js");
const reflect_check_js_1 = require("./reflect/reflect-check.js");
const error_js_1 = require("./reflect/error.js");
const unsafe_js_1 = require("./reflect/unsafe.js");
const scalar_js_1 = require("./reflect/scalar.js");
const message_js_1 = require("./reflect/message.js");
// bootstrap-inject google.protobuf.FeatureSet.FieldPresence.LEGACY_REQUIRED: const $name = $number;
const LEGACY_REQUIRED = 3;
// bootstrap-inject google.protobuf.FeatureSet.FieldPresence.IMPLICIT: const $name = $number;
const IMPLICIT = 2;
// Default options for serializing to JSON.
const jsonWriteDefaults = {
    alwaysEmitImplicit: false,
    enumAsInteger: false,
    useProtoFieldName: false,
};
function makeWriteOptions(options) {
    return options ? Object.assign(Object.assign({}, jsonWriteDefaults), options) : jsonWriteDefaults;
}
/**
 * Serialize the message to a JSON value, a JavaScript value that can be
 * passed to JSON.stringify().
 */
function toJson(schema, message, options) {
    return compiledWriter(schema)(makeWriteOptions(options), message);
}
/**
 * Serialize the message to a JSON string.
 */
function toJsonString(schema, message, options) {
    var _a;
    const jsonValue = toJson(schema, message, options);
    return JSON.stringify(jsonValue, null, (_a = options === null || options === void 0 ? void 0 : options.prettySpaces) !== null && _a !== void 0 ? _a : 0);
}
/**
 * Serialize a single enum value to JSON.
 */
function enumToJson(descEnum, value) {
    var _a;
    if (descEnum.typeName == "google.protobuf.NullValue") {
        return null;
    }
    const name = (_a = descEnum.value[value]) === null || _a === void 0 ? void 0 : _a.name;
    if (name === undefined) {
        throw new Error(`${value} is not a value in ${descEnum}`);
    }
    return name;
}
const compiledWriters = new WeakMap();
/**
 * Return the compiled encoder for a message, compiling it on first use.
 */
function compiledWriter(desc) {
    let compiled = compiledWriters.get(desc);
    if (compiled === undefined) {
        compiled = compileMessage(desc);
    }
    return compiled;
}
function compileMessage(desc) {
    const typeName = desc.typeName;
    const writeWkt = compileWkt(desc);
    if (writeWkt !== undefined) {
        // The field reported in ForeignFieldError. All well-known types with a
        // custom JSON representation have at least one field.
        const foreignField = desc.fields[0];
        const compiledWriter = (opts, message) => {
            if (message.$typeName !== typeName && foreignField !== undefined) {
                throw new error_js_1.FieldError(foreignField, `cannot use ${foreignField} with message ${message.$typeName}`, "ForeignFieldError");
            }
            return writeWkt(opts, message);
        };
        compiledWriters.set(desc, compiledWriter);
        return compiledWriter;
    }
    const sortedFields = desc.fields.concat().sort((a, b) => a.number - b.number);
    // The field reported in ForeignFieldError.
    const foreignField = sortedFields[0];
    const fieldWriters = [];
    const compiledWriter = (opts, message) => {
        if (message.$typeName !== typeName && foreignField !== undefined) {
            throw new error_js_1.FieldError(foreignField, `cannot use ${foreignField} with message ${message.$typeName}`, "ForeignFieldError");
        }
        const json = {};
        for (let i = 0; i < fieldWriters.length; i++) {
            fieldWriters[i](opts, message, json);
        }
        if (opts.registry) {
            writeExtensions(json, opts, opts.registry, message, desc);
        }
        return json;
    };
    // Register before compiling fields, so that recursive message types
    // resolve to this instance instead of compiling endlessly.
    compiledWriters.set(desc, compiledWriter);
    for (const field of sortedFields) {
        fieldWriters.push(compileField(field));
    }
    return compiledWriter;
}
/**
 * Compile an encoder for a well-known type with a custom JSON representation,
 * or return undefined for other messages.
 */
function compileWkt(desc) {
    if (!desc.typeName.startsWith("google.protobuf.")) {
        return undefined;
    }
    switch (desc.typeName) {
        case "google.protobuf.Any":
            return (opts, message) => anyToJson(message, opts);
        case "google.protobuf.Timestamp":
            return (opts, message) => timestampToJson(message);
        case "google.protobuf.Duration":
            return (opts, message) => durationToJson(message);
        case "google.protobuf.FieldMask":
            return (opts, message) => fieldMaskToJson(message);
        case "google.protobuf.Struct":
            return (opts, message) => structToJson(message);
        case "google.protobuf.Value":
            return (opts, message) => valueToJson(message);
        case "google.protobuf.ListValue":
            return (opts, message) => listValueToJson(message);
        default:
            if ((0, wrappers_js_1.isWrapperDesc)(desc)) {
                const valueField = desc.fields[0];
                const localName = valueField.localName;
                const zero = (0, scalar_js_1.scalarZeroValue)(valueField.scalar, false);
                const writeScalar = compileScalarValue(valueField);
                return (opts, message) => {
                    const value = message[localName];
                    return writeScalar(opts, value === undefined ? zero : value);
                };
            }
            return undefined;
    }
}
function compileField(field) {
    switch (field.fieldKind) {
        case "scalar":
        case "enum":
        case "message":
            return compileSingularField(field);
        case "list":
        case "map": {
            const writeValue = field.fieldKind == "list"
                ? compileListValue(field)
                : compileMapValue(field);
            const protoName = field.name;
            const jsonKey = field.jsonName;
            const localName = field.localName;
            return (opts, message, json) => {
                const value = writeValue(opts, message[localName]);
                if (value !== undefined) {
                    json[opts.useProtoFieldName ? protoName : jsonKey] = value;
                }
            };
        }
    }
}
/**
 * Compile an encoder for a singular field: the presence check, and the
 * value encoder.
 */
function compileSingularField(field) {
    const writeValue = compileSingularValue(field);
    const protoName = field.name;
    const jsonKey = field.jsonName;
    const localName = field.localName;
    if (field.oneof) {
        const oneofLocalName = field.oneof.localName;
        return (opts, message, json) => {
            const oneof = message[oneofLocalName];
            if (oneof.case === localName) {
                json[opts.useProtoFieldName ? protoName : jsonKey] = writeValue(opts, oneof.value);
            }
        };
    }
    if (field.presence != IMPLICIT) {
        const requiredError = field.presence == LEGACY_REQUIRED
            ? `cannot encode ${field} to JSON: required field not set`
            : undefined;
        return (opts, message, json) => {
            const value = message[localName];
            // Fields with explicit presence have properties on the prototype
            // chain for default / zero values (except for proto3).
            if (value !== undefined &&
                Object.prototype.hasOwnProperty.call(message, localName)) {
                json[opts.useProtoFieldName ? protoName : jsonKey] = writeValue(opts, value);
            }
            else if (requiredError !== undefined) {
                throw new Error(requiredError);
            }
        };
    }
    // Implicit presence: the field is emitted when the value is not the zero
    // value, or when alwaysEmitImplicit is enabled. The zero check is inlined
    // per type, see isScalarZeroValue.
    if (field.fieldKind == "enum") {
        const zero = field.enum.values[0].number;
        return (opts, message, json) => {
            const value = message[localName];
            if (value !== zero || opts.alwaysEmitImplicit) {
                json[opts.useProtoFieldName ? protoName : jsonKey] = writeValue(opts, value);
            }
        };
    }
    switch (field.scalar) {
        case descriptors_js_1.ScalarType.BOOL:
            return (opts, message, json) => {
                const value = message[localName];
                if (value !== false || opts.alwaysEmitImplicit) {
                    json[opts.useProtoFieldName ? protoName : jsonKey] = writeValue(opts, value);
                }
            };
        case descriptors_js_1.ScalarType.STRING:
            return (opts, message, json) => {
                const value = message[localName];
                if (value !== "" || opts.alwaysEmitImplicit) {
                    json[opts.useProtoFieldName ? protoName : jsonKey] = writeValue(opts, value);
                }
            };
        case descriptors_js_1.ScalarType.BYTES:
            return (opts, message, json) => {
                const value = message[localName];
                if (!(value instanceof Uint8Array) ||
                    value.byteLength > 0 ||
                    opts.alwaysEmitImplicit) {
                    json[opts.useProtoFieldName ? protoName : jsonKey] = writeValue(opts, value);
                }
            };
        case descriptors_js_1.ScalarType.DOUBLE:
        case descriptors_js_1.ScalarType.FLOAT:
            return (opts, message, json) => {
                const value = message[localName];
                // Object.is distinguishes -0 from 0.
                if (!Object.is(value, 0) || opts.alwaysEmitImplicit) {
                    json[opts.useProtoFieldName ? protoName : jsonKey] = writeValue(opts, value);
                }
            };
        default:
            return (opts, message, json) => {
                const value = message[localName];
                // Loose comparison matches 0n, 0 and "0".
                if (value != 0 || opts.alwaysEmitImplicit) {
                    json[opts.useProtoFieldName ? protoName : jsonKey] = writeValue(opts, value);
                }
            };
    }
}
/**
 * Compile an encoder for the value of a field of any kind. Used for
 * extension values.
 */
function compileFieldValue(field) {
    switch (field.fieldKind) {
        case "scalar":
        case "enum":
        case "message":
            return compileSingularValue(field);
        case "list":
            return compileListValue(field);
        case "map":
            return compileMapValue(field);
    }
}
/**
 * Compile an encoder for the value of a singular field.
 */
function compileSingularValue(field) {
    switch (field.fieldKind) {
        case "scalar":
            return compileScalarValue(field);
        case "enum":
            return compileEnumValue(field);
        case "message":
            return compileMessageValue(field);
    }
}
/**
 * Compile an encoder for the value of a message field.
 */
function compileMessageValue(field) {
    const { toMessage } = (0, message_js_1.localMessageMapper)(field);
    const writeMessage = compiledWriter(field.message);
    return (opts, value) => writeMessage(opts, toMessage(value));
}
/**
 * Compile an encoder for a list field value. Returns undefined for an empty
 * list, unless alwaysEmitImplicit is enabled.
 */
function compileListValue(field) {
    const writeItem = compileListItemValue(field);
    return (opts, value) => {
        const items = value;
        if (items.length == 0 && !opts.alwaysEmitImplicit) {
            return undefined;
        }
        const jsonArray = [];
        for (let i = 0; i < items.length; i++) {
            jsonArray.push(writeItem(opts, items[i]));
        }
        return jsonArray;
    };
}
function compileListItemValue(field) {
    switch (field.listKind) {
        case "scalar":
            return compileScalarValue(field);
        case "enum":
            return compileEnumValue(field);
        case "message":
            return compileMessageValue(field);
    }
}
/**
 * Compile an encoder for a map field value. Returns undefined for an empty
 * map, unless alwaysEmitImplicit is enabled. Map keys are stored as object
 * keys and are used as JSON keys as-is.
 */
function compileMapValue(field) {
    const writeMapValue = compileMapEntryValue(field);
    return (opts, value) => {
        const record = value;
        const keys = Object.keys(record);
        if (keys.length == 0 && !opts.alwaysEmitImplicit) {
            return undefined;
        }
        const jsonObject = {};
        for (let i = 0; i < keys.length; i++) {
            const key = keys[i];
            jsonObject[key] = writeMapValue(opts, record[key]);
        }
        return jsonObject;
    };
}
function compileMapEntryValue(field) {
    switch (field.mapKind) {
        case "scalar":
            return compileScalarValue(field);
        case "enum":
            return compileEnumValue(field);
        case "message":
            return compileMessageValue(field);
    }
}
/**
 * Compile an encoder for an enum value.
 */
function compileEnumValue(field) {
    const desc = field.enum;
    if (desc.typeName == "google.protobuf.NullValue") {
        return (opts, value) => {
            if (typeof value != "number") {
                throw errorEnumValue(desc, value);
            }
            return null;
        };
    }
    return (opts, value) => {
        var _a, _b;
        if (typeof value != "number") {
            throw errorEnumValue(desc, value);
        }
        if (opts.enumAsInteger) {
            return value;
        }
        // If we don't know the enum value, just return the number.
        return (_b = (_a = desc.value[value]) === null || _a === void 0 ? void 0 : _a.name) !== null && _b !== void 0 ? _b : value;
    };
}
function errorEnumValue(desc, value) {
    return new Error(`cannot encode ${desc} to JSON: expected number, got ${(0, reflect_check_js_1.formatVal)(value)}`);
}
/**
 * Compile an encoder for a scalar value. Errors report the original field
 * descriptor, which may be a list or map field for items of those fields.
 */
function compileScalarValue(field) {
    switch (field.scalar) {
        // int32, fixed32, uint32: JSON value will be a decimal number. Either numbers or strings are accepted.
        case descriptors_js_1.ScalarType.INT32:
        case descriptors_js_1.ScalarType.SFIXED32:
        case descriptors_js_1.ScalarType.SINT32:
        case descriptors_js_1.ScalarType.FIXED32:
        case descriptors_js_1.ScalarType.UINT32:
            return (opts, value) => {
                if (typeof value != "number") {
                    throw errorScalarValue(field, value);
                }
                return value;
            };
        // float, double: JSON value will be a number or one of the special string values "NaN", "Infinity", and "-Infinity".
        // Either numbers or strings are accepted. Exponent notation is also accepted.
        case descriptors_js_1.ScalarType.FLOAT:
        case descriptors_js_1.ScalarType.DOUBLE:
            return (opts, value) => {
                if (typeof value != "number") {
                    throw errorScalarValue(field, value);
                }
                if (Number.isNaN(value))
                    return "NaN";
                if (value === Number.POSITIVE_INFINITY)
                    return "Infinity";
                if (value === Number.NEGATIVE_INFINITY)
                    return "-Infinity";
                return value;
            };
        // string:
        case descriptors_js_1.ScalarType.STRING:
            return (opts, value) => {
                if (typeof value != "string") {
                    throw errorScalarValue(field, value);
                }
                return value;
            };
        // bool:
        case descriptors_js_1.ScalarType.BOOL:
            return (opts, value) => {
                if (typeof value != "boolean") {
                    throw errorScalarValue(field, value);
                }
                return value;
            };
        // JSON value will be a decimal string. Either numbers or strings are accepted.
        case descriptors_js_1.ScalarType.UINT64:
        case descriptors_js_1.ScalarType.FIXED64:
        case descriptors_js_1.ScalarType.INT64:
        case descriptors_js_1.ScalarType.SFIXED64:
        case descriptors_js_1.ScalarType.SINT64:
            return (opts, value) => {
                if (typeof value == "bigint" ||
                    typeof value == "string" ||
                    (typeof value == "number" && Number.isInteger(value))) {
                    return value.toString();
                }
                throw errorScalarValue(field, value);
            };
        // bytes: JSON value will be the data encoded as a string using standard base64 encoding with paddings.
        // Either standard or URL-safe base64 encoding with/without paddings are accepted.
        case descriptors_js_1.ScalarType.BYTES:
            return (opts, value) => {
                if (value instanceof Uint8Array) {
                    return (0, index_js_2.base64Encode)(value);
                }
                throw errorScalarValue(field, value);
            };
    }
}
function errorScalarValue(field, value) {
    var _a;
    return new Error(`cannot encode ${field} to JSON: ${(_a = (0, reflect_check_js_1.checkField)(field, value)) === null || _a === void 0 ? void 0 : _a.message}`);
}
/**
 * Write extensions for unknown fields that are found in the registry.
 */
function writeExtensions(json, opts, registry, message, desc) {
    const unknown = message.$unknown;
    if (unknown === undefined) {
        return;
    }
    const tagSeen = new Set();
    for (let i = 0; i < unknown.length; i++) {
        const { no } = unknown[i];
        // Same tag can appear multiple times, so we
        // keep track and skip identical ones.
        if (!tagSeen.has(no)) {
            tagSeen.add(no);
            const extension = registry.getExtensionFor(desc, no);
            if (!extension) {
                continue;
            }
            const value = (0, extensions_js_1.getExtension)(message, extension);
            const [container, field] = (0, extensions_js_1.createExtensionContainer)(extension, value);
            const local = container[unsafe_js_1.unsafeLocal];
            const jsonValue = compileFieldValue(field)(opts, local[field.localName]);
            if (jsonValue !== undefined) {
                json[extension.jsonName] = jsonValue;
            }
        }
    }
}
function anyToJson(val, opts) {
    if (val.typeUrl === "") {
        return {};
    }
    const { registry } = opts;
    let message;
    let desc;
    if (registry) {
        message = (0, index_js_1.anyUnpack)(val, registry);
        if (message) {
            desc = registry.getMessage(message.$typeName);
        }
    }
    if (!desc || !message) {
        throw new Error(`cannot encode message ${val.$typeName} to JSON: "${val.typeUrl}" is not in the type registry`);
    }
    const json = (0, wrappers_js_1.hasCustomJsonRepresentation)(desc)
        ? {
            value: compiledWriter(desc)(opts, message),
        }
        : compiledWriter(desc)(opts, message);
    json["@type"] = val.typeUrl;
    return json;
}
function durationToJson(val) {
    const seconds = Number(val.seconds);
    const nanos = val.nanos;
    if (seconds > json_js_1.durationSecondsMax || seconds < json_js_1.durationSecondsMin) {
        throw new Error(`cannot encode message ${val.$typeName} to JSON: value out of range`);
    }
    if ((seconds > 0 && nanos < 0) || (seconds < 0 && nanos > 0)) {
        throw new Error(`cannot encode message ${val.$typeName} to JSON: nanos sign must match seconds sign`);
    }
    let text = val.seconds.toString();
    if (nanos !== 0) {
        let nanosStr = Math.abs(nanos).toString();
        nanosStr = "0".repeat(9 - nanosStr.length) + nanosStr;
        if (nanosStr.substring(3) === "000000") {
            nanosStr = nanosStr.substring(0, 3);
        }
        else if (nanosStr.substring(6) === "000") {
            nanosStr = nanosStr.substring(0, 6);
        }
        text += "." + nanosStr;
        if (nanos < 0 && seconds == 0) {
            text = "-" + text;
        }
    }
    return text + "s";
}
function fieldMaskToJson(val) {
    return val.paths
        .map((p) => {
        if ((0, names_js_1.protoSnakeCase)((0, names_js_1.protoCamelCase)(p)) !== p) {
            throw new Error(`cannot encode message ${val.$typeName} to JSON: lowerCamelCase of path name "${p}" is irreversible`);
        }
        return (0, names_js_1.protoCamelCase)(p);
    })
        .join(",");
}
function structToJson(val) {
    const json = {};
    const keys = Object.keys(val.fields);
    for (let i = 0; i < keys.length; i++) {
        const key = keys[i];
        json[key] = valueToJson(val.fields[key]);
    }
    return json;
}
function valueToJson(val) {
    switch (val.kind.case) {
        case "nullValue":
            return null;
        case "numberValue":
            if (!Number.isFinite(val.kind.value)) {
                throw new Error(`${val.$typeName} cannot be NaN or Infinity`);
            }
            return val.kind.value;
        case "boolValue":
            return val.kind.value;
        case "stringValue":
            return val.kind.value;
        case "structValue":
            return structToJson(val.kind.value);
        case "listValue":
            return listValueToJson(val.kind.value);
        default:
            throw new Error(`${val.$typeName} must have a value`);
    }
}
function listValueToJson(val) {
    return val.values.map(valueToJson);
}
function timestampToJson(val) {
    const ms = Number(val.seconds) * 1000;
    if (ms < json_js_1.timestampMsMin || ms > json_js_1.timestampMsMax) {
        throw new Error(`cannot encode message ${val.$typeName} to JSON: must be from 0001-01-01T00:00:00Z to 9999-12-31T23:59:59Z inclusive`);
    }
    if (val.nanos < 0) {
        throw new Error(`cannot encode message ${val.$typeName} to JSON: nanos must not be negative`);
    }
    if (val.nanos > 999999999) {
        throw new Error(`cannot encode message ${val.$typeName} to JSON: nanos must not be greater than 99999999`);
    }
    let z = "Z";
    if (val.nanos > 0) {
        const nanosStr = (val.nanos + 1000000000).toString().substring(1);
        if (nanosStr.substring(3) === "000000") {
            z = "." + nanosStr.substring(0, 3) + "Z";
        }
        else if (nanosStr.substring(6) === "000") {
            z = "." + nanosStr.substring(0, 6) + "Z";
        }
        else {
            z = "." + nanosStr + "Z";
        }
    }
    return new Date(ms).toISOString().replace(".000Z", z);
}
