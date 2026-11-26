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
exports.fromJsonString = fromJsonString;
exports.mergeFromJsonString = mergeFromJsonString;
exports.fromJson = fromJson;
exports.mergeFromJson = mergeFromJson;
exports.enumFromJson = enumFromJson;
exports.isEnumJson = isEnumJson;
const descriptors_js_1 = require("./descriptors.js");
const proto_int64_js_1 = require("./proto-int64.js");
const create_js_1 = require("./create.js");
const error_js_1 = require("./reflect/error.js");
const reflect_check_js_1 = require("./reflect/reflect-check.js");
const names_js_1 = require("./reflect/names.js");
const scalar_js_1 = require("./reflect/scalar.js");
const unsafe_js_1 = require("./reflect/unsafe.js");
const message_js_1 = require("./reflect/message.js");
const base64_encoding_js_1 = require("./wire/base64-encoding.js");
const index_js_1 = require("./wkt/index.js");
const extensions_js_1 = require("./extensions.js");
const json_js_1 = require("./wkt/json.js");
// bootstrap-inject google.protobuf.FeatureSet.FieldPresence.IMPLICIT: const $name = $number;
const IMPLICIT = 2;
function makeReadContext(options) {
    return Object.assign(Object.assign({ ignoreUnknownFields: false, recursionLimit: 100 }, options), { depth: 0 });
}
/**
 * Parse a message from a JSON string.
 *
 * Duplicate keys are rejected.
 */
function fromJsonString(schema, json, options) {
    return fromJson(schema, parseJsonString(json, schema.typeName), options);
}
/**
 * Parse a message from a JSON string, merging fields into the target.
 *
 * Repeated fields are appended. Map entries are added, overwriting
 * existing keys.
 *
 * If a message field is already present, it will be merged with the
 * new data.
 *
 * Duplicate keys in the JSON are rejected, as in `fromJsonString`.
 */
function mergeFromJsonString(schema, target, json, options) {
    return mergeFromJson(schema, target, parseJsonString(json, schema.typeName), options);
}
/**
 * Parse a message from a JSON value.
 *
 * Duplicate keys are rejected, but a value parsed by JSON.parse has already
 * dropped duplicates (the last one wins). Use `fromJsonString` for strict
 * duplicate-key checking.
 */
function fromJson(schema, json, options) {
    const message = (0, create_js_1.create)(schema);
    readMessage(schema, message, json, options);
    return message;
}
/**
 * Parse a message from a JSON value, merging fields into the target.
 *
 * Repeated fields are appended. Map entries are added, overwriting
 * existing keys.
 *
 * If a message field is already present, it will be merged with the
 * new data.
 *
 * Duplicate keys are rejected as in `fromJson`; use `mergeFromJsonString`
 * for strict checking.
 */
function mergeFromJson(schema, target, json, options) {
    if (target.$typeName !== schema.typeName &&
        schema.fields.length > 0) {
        throw new error_js_1.FieldError(schema.fields[0], `cannot use ${schema.fields[0]} with message ${target.$typeName}`, "ForeignFieldError");
    }
    readMessage(schema, target, json, options);
    return target;
}
/**
 * Run the compiled decoder for the message, wrapping FieldErrors with the
 * standard error message.
 */
function readMessage(schema, message, json, options) {
    try {
        compiledReader(schema)(message, json, makeReadContext(options));
    }
    catch (e) {
        if ((0, error_js_1.isFieldError)(e)) {
            // @ts-expect-error we use the ES2022 error CTOR option "cause" for better stack traces
            throw new Error(`cannot decode ${e.field()} from JSON: ${e.message}`, {
                cause: e,
            });
        }
        throw e;
    }
}
/**
 * Parses an enum value from JSON.
 */
function enumFromJson(descEnum, json) {
    // With ignoreUnknownFields false, the converter never returns the token
    // for ignored unknown enum values.
    return compileEnumConverter(descEnum)(json, false);
}
/**
 * Is the given value a JSON enum value?
 */
function isEnumJson(descEnum, value) {
    return undefined !== descEnum.values.find((v) => v.name === value);
}
const compiledReaders = new WeakMap();
/**
 * Return the compiled decoder for a message, compiling it on first use.
 */
function compiledReader(desc) {
    let compiled = compiledReaders.get(desc);
    if (compiled === undefined) {
        compiled = compileMessage(desc);
    }
    return compiled;
}
function compileMessage(desc) {
    const descString = String(desc);
    const readWkt = compileWkt(desc);
    if (readWkt !== undefined) {
        // All message decoders count against the recursion limit, including
        // well-known types with a custom JSON representation.
        const compiled = (message, json, ctx) => {
            if (++ctx.depth > ctx.recursionLimit) {
                throw new Error(`cannot decode ${descString} from JSON: maximum recursion depth of ${ctx.recursionLimit} reached`);
            }
            readWkt(message, json, ctx);
            ctx.depth--;
        };
        compiledReaders.set(desc, compiled);
        return compiled;
    }
    const typeName = desc.typeName;
    // Fields are looked up by their proto name and their JSON name.
    const fieldsByJsonKey = new Map();
    const compiled = (message, json, ctx) => {
        var _a;
        if (++ctx.depth > ctx.recursionLimit) {
            throw new Error(`cannot decode ${descString} from JSON: maximum recursion depth of ${ctx.recursionLimit} reached`);
        }
        if (json == null || Array.isArray(json) || typeof json != "object") {
            throw new Error(`cannot decode ${descString} from JSON: ${(0, reflect_check_js_1.formatVal)(json)}`);
        }
        const oneofSeen = new Map();
        const fieldSeen = new Set();
        const jsonKeys = Object.keys(json);
        for (let i = 0; i < jsonKeys.length; i++) {
            const jsonKey = jsonKeys[i];
            const jsonValue = json[jsonKey];
            const entry = fieldsByJsonKey.get(jsonKey);
            if (entry !== undefined) {
                const field = entry.field;
                if (fieldSeen.has(field)) {
                    // The same field may be set by its proto name and its JSON name, or by
                    // a duplicate or unicode-escaped key that JSON.parse already collapsed.
                    // Checked before the null-skip below so that a null entry still counts.
                    throw new error_js_1.FieldError(field, "set multiple times");
                }
                fieldSeen.add(field);
                if (entry.oneofScalarNullSkip && jsonValue === null) {
                    continue;
                }
                if (entry.oneof) {
                    const seen = oneofSeen.get(entry.oneof);
                    if (seen !== undefined) {
                        throw new error_js_1.FieldError(entry.oneof, `oneof set multiple times by ${seen.name} and ${field.name}`);
                    }
                    oneofSeen.set(entry.oneof, field);
                }
                entry.read(message, jsonValue, ctx);
            }
            else {
                const extension = jsonKey.startsWith("[") && jsonKey.endsWith("]")
                    ? (_a = ctx.registry) === null || _a === void 0 ? void 0 : _a.getExtension(jsonKey.substring(1, jsonKey.length - 1))
                    : undefined;
                if ((extension === null || extension === void 0 ? void 0 : extension.extendee.typeName) == typeName) {
                    const [container, field, get] = (0, extensions_js_1.createExtensionContainer)(extension);
                    compileFieldReader(field)(container[unsafe_js_1.unsafeLocal], jsonValue, ctx);
                    (0, extensions_js_1.setExtension)(message, extension, get());
                }
                if (extension === undefined && !ctx.ignoreUnknownFields) {
                    throw new Error(`cannot decode ${descString} from JSON: key "${jsonKey}" is unknown`);
                }
            }
        }
        ctx.depth--;
    };
    // Register before compiling fields, so that recursive message types
    // resolve to this instance instead of compiling endlessly.
    compiledReaders.set(desc, compiled);
    for (const field of desc.fields) {
        const entry = {
            read: compileFieldReader(field),
            field,
            oneof: field.oneof,
            oneofScalarNullSkip: field.oneof !== undefined && field.fieldKind == "scalar",
        };
        fieldsByJsonKey.set(field.name, entry).set(field.jsonName, entry);
    }
    return compiled;
}
/**
 * Compile a decoder for a well-known type with a custom JSON representation,
 * or return undefined for other messages. The recursion limit is enforced by
 * the caller.
 */
function compileWkt(desc) {
    if (!desc.typeName.startsWith("google.protobuf.")) {
        return undefined;
    }
    switch (desc.typeName) {
        case "google.protobuf.Any":
            return (message, json, ctx) => anyFromJson(message, json, ctx);
        case "google.protobuf.Timestamp":
            return (message, json) => timestampFromJson(message, json);
        case "google.protobuf.Duration":
            return (message, json) => durationFromJson(message, json);
        case "google.protobuf.FieldMask":
            return (message, json) => fieldMaskFromJson(message, json);
        case "google.protobuf.Struct":
            return (message, json, ctx) => structFromJson(message, json, ctx);
        case "google.protobuf.Value":
            return (message, json, ctx) => valueFromJson(message, json, ctx);
        case "google.protobuf.ListValue":
            return (message, json, ctx) => listValueFromJson(message, json, ctx);
        default:
            if ((0, index_js_1.isWrapperDesc)(desc)) {
                const valueField = desc.fields[0];
                const localName = valueField.localName;
                const scalar = valueField.scalar;
                const longAsString = valueField.longAsString;
                const readScalar = compileScalarConverter(valueField);
                return (message, json) => {
                    if (json === null) {
                        message[localName] = (0, scalar_js_1.scalarZeroValue)(scalar, longAsString);
                    }
                    else {
                        message[localName] = readScalar(json);
                    }
                };
            }
            return undefined;
    }
}
function compileFieldReader(field) {
    switch (field.fieldKind) {
        case "scalar":
            return compileScalarFieldReader(field);
        case "enum":
            return compileEnumFieldReader(field);
        case "message":
            return compileMessageFieldReader(field);
        case "list":
            return compileListFieldReader(field);
        case "map":
            return compileMapFieldReader(field);
    }
}
function compileScalarFieldReader(field) {
    const readScalar = compileScalarConverter(field);
    const localName = field.localName;
    if (field.oneof) {
        // JSON null for a oneof scalar member is skipped by the message decoder.
        const oneofLocalName = field.oneof.localName;
        return (message, json) => {
            message[oneofLocalName] = {
                case: localName,
                value: readScalar(json),
            };
        };
    }
    const clear = compileClear(field);
    return (message, json) => {
        if (json === null) {
            clear(message);
        }
        else {
            message[localName] = readScalar(json);
        }
    };
}
/**
 * Compile a function that resets the field to unset, mirroring the clear
 * operation of the reflect API for fields that are not part of a oneof.
 */
function compileClear(field) {
    const localName = field.localName;
    if (field.presence != IMPLICIT) {
        // Fields with explicit presence have properties on the prototype chain
        // for default / zero values (except for proto3). By deleting their own
        // property, the field is reset.
        return (message) => {
            delete message[localName];
        };
    }
    if (field.fieldKind == "enum") {
        const zero = field.enum.values[0].number;
        return (message) => {
            message[localName] = zero;
        };
    }
    const scalar = field.scalar;
    const longAsString = field.longAsString;
    return (message) => {
        message[localName] = (0, scalar_js_1.scalarZeroValue)(scalar, longAsString);
    };
}
function compileEnumFieldReader(field) {
    const readEnumValue = compileEnumConverter(field.enum);
    const checkEnum = compileEnumCheck(field.enum);
    const localName = field.localName;
    // Fields with enum google.protobuf.NullValue permit a Protobuf-serializable
    // null; for all other enums, JSON null resets the field.
    const nullResets = field.enum.typeName != "google.protobuf.NullValue";
    if (field.oneof) {
        const oneofLocalName = field.oneof.localName;
        return (message, json, ctx) => {
            if (json === null && nullResets) {
                const oneof = message[oneofLocalName];
                if (oneof.case === localName) {
                    message[oneofLocalName] = { case: undefined };
                }
                return;
            }
            const value = readEnumValue(json, ctx.ignoreUnknownFields);
            if (value === tokenIgnoredUnknownEnum) {
                return;
            }
            const check = checkEnum(value);
            if (check !== true) {
                throw new error_js_1.FieldError(field, (0, reflect_check_js_1.reasonSingular)(field, value, check));
            }
            message[oneofLocalName] = { case: localName, value };
        };
    }
    const clear = compileClear(field);
    return (message, json, ctx) => {
        if (json === null && nullResets) {
            clear(message);
            return;
        }
        const value = readEnumValue(json, ctx.ignoreUnknownFields);
        if (value === tokenIgnoredUnknownEnum) {
            return;
        }
        const check = checkEnum(value);
        if (check !== true) {
            throw new error_js_1.FieldError(field, (0, reflect_check_js_1.reasonSingular)(field, value, check));
        }
        message[localName] = value;
    };
}
function compileMessageFieldReader(field) {
    const localName = field.localName;
    const { toMessage, toLocal } = (0, message_js_1.localMessageMapper)(field);
    const readChild = compiledReader(field.message);
    // Fields with message google.protobuf.Value permit a Protobuf-serializable
    // null; for all other messages, JSON null resets the field.
    const nullResets = field.message.typeName != "google.protobuf.Value";
    if (field.oneof) {
        const oneofLocalName = field.oneof.localName;
        return (message, json, ctx) => {
            const oneof = message[oneofLocalName];
            if (json === null && nullResets) {
                if (oneof.case === localName) {
                    message[oneofLocalName] = { case: undefined };
                }
                return;
            }
            const child = toMessage(oneof.case === localName ? oneof.value : undefined);
            readChild(child, json, ctx);
            message[oneofLocalName] = { case: localName, value: toLocal(child) };
        };
    }
    return (message, json, ctx) => {
        if (json === null && nullResets) {
            delete message[localName];
            return;
        }
        const child = toMessage(message[localName]);
        readChild(child, json, ctx);
        message[localName] = toLocal(child);
    };
}
function compileListFieldReader(field) {
    const localName = field.localName;
    const readItem = compileListItemReader(field);
    return (message, json, ctx) => {
        if (json === null) {
            return;
        }
        if (!Array.isArray(json)) {
            throw new error_js_1.FieldError(field, "expected Array, got " + (0, reflect_check_js_1.formatVal)(json));
        }
        const items = message[localName];
        for (let i = 0; i < json.length; i++) {
            const value = readItem(json[i], ctx, items.length);
            if (value !== tokenIgnoredUnknownEnum) {
                items.push(value);
            }
        }
    };
}
/**
 * Compile a decoder for a list item. The index is only used in errors, and
 * accounts for previously merged items.
 */
function compileListItemReader(field) {
    switch (field.listKind) {
        case "scalar": {
            const parseScalar = compileScalarParse(field);
            const checkValue = (0, reflect_check_js_1.checkScalarValue)(field.scalar);
            const toLocal = compileScalarToLocal(field);
            return (json, ctx, index) => {
                if (json === null) {
                    throw new error_js_1.FieldError(field, "list item must not be null");
                }
                const value = parseScalar(json);
                const check = checkValue(value);
                if (check !== true) {
                    throw new error_js_1.FieldError(field, `list item #${index + 1}: ${(0, reflect_check_js_1.reasonSingular)(field, value, check)}`);
                }
                return toLocal(value);
            };
        }
        case "enum": {
            const readEnumValue = compileEnumConverter(field.enum);
            const checkEnum = compileEnumCheck(field.enum);
            const nullResets = field.enum.typeName != "google.protobuf.NullValue";
            return (json, ctx, index) => {
                if (json === null && nullResets) {
                    throw new error_js_1.FieldError(field, "list item must not be null");
                }
                const value = readEnumValue(json, ctx.ignoreUnknownFields);
                if (value === tokenIgnoredUnknownEnum) {
                    return value;
                }
                const check = checkEnum(value);
                if (check !== true) {
                    throw new error_js_1.FieldError(field, `list item #${index + 1}: ${(0, reflect_check_js_1.reasonSingular)(field, value, check)}`);
                }
                return value;
            };
        }
        case "message": {
            const { toMessage, toLocal } = (0, message_js_1.localMessageMapper)(field);
            const readChild = compiledReader(field.message);
            const nullResets = field.message.typeName != "google.protobuf.Value";
            return (json, ctx) => {
                if (json === null && nullResets) {
                    throw new error_js_1.FieldError(field, "list item must not be null");
                }
                const child = toMessage(undefined);
                readChild(child, json, ctx);
                return toLocal(child);
            };
        }
    }
}
function compileMapFieldReader(field) {
    const localName = field.localName;
    const mapKey = field.mapKey;
    const parseMapKey = compileMapKeyParse(mapKey);
    const checkMapKey = (0, reflect_check_js_1.checkScalarValue)(mapKey);
    let parseValue;
    // Additional validation for scalar and enum values, matching the checks
    // of the reflect API. Message values need no validation.
    let checkValue;
    let toLocalValue = (value) => value;
    // Fields with google.protobuf.Value or google.protobuf.NullValue values
    // permit a Protobuf-serializable null.
    let nullResets = true;
    switch (field.mapKind) {
        case "scalar": {
            parseValue = compileScalarParse(field);
            checkValue = (0, reflect_check_js_1.checkScalarValue)(field.scalar);
            toLocalValue = compileScalarToLocal(field);
            break;
        }
        case "enum": {
            const readEnumValue = compileEnumConverter(field.enum);
            parseValue = (json, ctx) => readEnumValue(json, ctx.ignoreUnknownFields);
            checkValue = compileEnumCheck(field.enum);
            nullResets = field.enum.typeName != "google.protobuf.NullValue";
            break;
        }
        case "message": {
            const { toMessage, toLocal } = (0, message_js_1.localMessageMapper)(field);
            const readChild = compiledReader(field.message);
            nullResets = field.message.typeName != "google.protobuf.Value";
            parseValue = (json, ctx) => {
                const child = toMessage(undefined);
                readChild(child, json, ctx);
                return toLocal(child);
            };
            break;
        }
    }
    return (message, json, ctx) => {
        if (json === null) {
            return;
        }
        if (typeof json != "object" || Array.isArray(json)) {
            throw new error_js_1.FieldError(field, "expected object, got " + (0, reflect_check_js_1.formatVal)(json));
        }
        const record = message[localName];
        const seen = new Set();
        const jsonMapKeys = Object.keys(json);
        for (let i = 0; i < jsonMapKeys.length; i++) {
            const jsonMapKey = jsonMapKeys[i];
            const jsonMapValue = json[jsonMapKey];
            const key = parseMapKey(jsonMapKey);
            if (seen.has(key)) {
                throw new error_js_1.FieldError(field, `duplicate map key "${jsonMapKey}"`);
            }
            seen.add(key);
            if (jsonMapValue === null && nullResets) {
                throw new error_js_1.FieldError(field, "map value must not be null");
            }
            const value = parseValue(jsonMapValue, ctx);
            if (value === tokenIgnoredUnknownEnum) {
                continue;
            }
            const checkKey = checkMapKey(key);
            if (checkKey !== true) {
                throw new error_js_1.FieldError(field, `invalid map key: ${(0, reflect_check_js_1.reasonSingular)({ scalar: mapKey }, key, checkKey)}`);
            }
            if (checkValue !== undefined) {
                const check = checkValue(value);
                if (check !== true) {
                    throw new error_js_1.FieldError(field, `map entry ${(0, reflect_check_js_1.formatVal)(key)}: ${(0, reflect_check_js_1.reasonSingular)(field, value, check)}`);
                }
            }
            // Object property keys are always strings or symbols. Assigning with a
            // boolean, number, or bigint key implicitly converts it to a string.
            record[key] = toLocalValue(value);
        }
    };
}
const tokenIgnoredUnknownEnum = Symbol();
/**
 * Compile a converter from a JSON value to an enum value. JSON null returns
 * the enum's first value. With ignoreUnknownFields false, unknown string
 * values raise an error; with true, they return tokenIgnoredUnknownEnum.
 * The value is not checked against the enum's values, see compileEnumCheck.
 */
function compileEnumConverter(desc) {
    const zero = desc.values[0].number;
    const values = desc.values;
    return (json, ignoreUnknownFields) => {
        if (json === null) {
            return zero;
        }
        switch (typeof json) {
            case "number":
                if (Number.isInteger(json)) {
                    return json;
                }
                break;
            case "string": {
                const value = values.find((ev) => ev.name === json);
                if (value !== undefined) {
                    return value.number;
                }
                if (ignoreUnknownFields) {
                    return tokenIgnoredUnknownEnum;
                }
                break;
            }
        }
        throw new Error(`cannot decode ${desc} from JSON: ${(0, reflect_check_js_1.formatVal)(json)}`);
    };
}
/**
 * Compile the check that the reflect API performs for enum values: open
 * enums accept any int32 value, closed enums accept only declared values.
 */
function compileEnumCheck(desc) {
    if (desc.open) {
        return (0, reflect_check_js_1.checkScalarValue)(descriptors_js_1.ScalarType.INT32);
    }
    const values = desc.values;
    return (value) => values.some((v) => v.number === value);
}
/**
 * Compile a converter from a JSON value to the local representation of a
 * scalar, fusing JSON parsing, the validation of the reflect API, and the
 * conversion to the local 64-bit integer representation.
 */
function compileScalarConverter(field) {
    const parseScalar = compileScalarParse(field);
    const checkValue = (0, reflect_check_js_1.checkScalarValue)(field.scalar);
    const toLocal = compileScalarToLocal(field);
    return (json) => {
        const value = parseScalar(json);
        const check = checkValue(value);
        if (check !== true) {
            throw new error_js_1.FieldError(field, (0, reflect_check_js_1.reasonSingular)(field, value, check));
        }
        return toLocal(value);
    };
}
/**
 * Compile the JSON-specific parsing step for a scalar value: the special
 * string values of float and double, string-encoded numbers, and base64
 * bytes. Returns the input unchanged if the JSON value cannot be converted;
 * the validation step raises an error for it.
 */
function compileScalarParse(field) {
    switch (field.scalar) {
        // float, double: JSON value will be a number or one of the special string values "NaN", "Infinity", and "-Infinity".
        // Either numbers or strings are accepted. Exponent notation is also accepted.
        case descriptors_js_1.ScalarType.DOUBLE:
        case descriptors_js_1.ScalarType.FLOAT:
            return (json) => {
                if (json === "NaN")
                    return NaN;
                if (json === "Infinity")
                    return Number.POSITIVE_INFINITY;
                if (json === "-Infinity")
                    return Number.NEGATIVE_INFINITY;
                if (typeof json == "number") {
                    if (Number.isNaN(json)) {
                        // NaN must be encoded with string constants
                        throw new error_js_1.FieldError(field, "unexpected NaN number");
                    }
                    if (!Number.isFinite(json)) {
                        // Infinity must be encoded with string constants
                        throw new error_js_1.FieldError(field, "unexpected infinite number");
                    }
                    return json;
                }
                if (typeof json == "string") {
                    if (json === "") {
                        // empty string is not a number
                        return json;
                    }
                    if (json.trim().length !== json.length) {
                        // extra whitespace
                        return json;
                    }
                    const float = Number(json);
                    if (!Number.isFinite(float)) {
                        // Infinity and NaN must be encoded with string constants
                        return json;
                    }
                    return float;
                }
                return json;
            };
        // int32, fixed32, uint32: JSON value will be a decimal number. Either numbers or strings are accepted.
        case descriptors_js_1.ScalarType.INT32:
        case descriptors_js_1.ScalarType.FIXED32:
        case descriptors_js_1.ScalarType.SFIXED32:
        case descriptors_js_1.ScalarType.SINT32:
        case descriptors_js_1.ScalarType.UINT32:
            return int32FromJson;
        // bytes: JSON value will be the data encoded as a string using standard base64 encoding with paddings.
        // Either standard or URL-safe base64 encoding with/without paddings are accepted.
        case descriptors_js_1.ScalarType.BYTES:
            return (json) => {
                if (typeof json == "string") {
                    if (json === "") {
                        return new Uint8Array(0);
                    }
                    try {
                        return (0, base64_encoding_js_1.base64Decode)(json);
                    }
                    catch (e) {
                        const message = e instanceof Error ? e.message : String(e);
                        throw new error_js_1.FieldError(field, message);
                    }
                }
                return json;
            };
        // int64, sfixed64, sint64, fixed64, uint64: The validation step accepts
        // string and number. string, bool: no conversion.
        default:
            return (json) => json;
    }
}
/**
 * Compile the conversion of a validated scalar value to its local
 * representation: 64-bit integers become bigint, or string with the
 * longAsString option.
 */
function compileScalarToLocal(field) {
    const longAsString = field.fieldKind !== "map" && field.longAsString;
    switch (field.scalar) {
        case descriptors_js_1.ScalarType.INT64:
        case descriptors_js_1.ScalarType.SFIXED64:
        case descriptors_js_1.ScalarType.SINT64:
            if (longAsString) {
                return (value) => String(value);
            }
            return (value) => typeof value == "string" || typeof value == "number"
                ? proto_int64_js_1.protoInt64.parse(value)
                : value;
        case descriptors_js_1.ScalarType.FIXED64:
        case descriptors_js_1.ScalarType.UINT64:
            if (longAsString) {
                return (value) => String(value);
            }
            return (value) => typeof value == "string" || typeof value == "number"
                ? proto_int64_js_1.protoInt64.uParse(value)
                : value;
        default:
            return (value) => value;
    }
}
/**
 * Return a parser from a JSON value to a map key for the given key type.
 * Canonicalizes 64-bit integers given as string, so that "01" and "1" are
 * one key, and duplicates can raise an error.
 * The parser returns the input if the JSON value cannot be converted.
 */
function compileMapKeyParse(type) {
    switch (type) {
        case descriptors_js_1.ScalarType.BOOL:
            return (jsonString) => {
                switch (jsonString) {
                    case "true":
                        return true;
                    case "false":
                        return false;
                }
                return jsonString;
            };
        case descriptors_js_1.ScalarType.INT32:
        case descriptors_js_1.ScalarType.FIXED32:
        case descriptors_js_1.ScalarType.UINT32:
        case descriptors_js_1.ScalarType.SFIXED32:
        case descriptors_js_1.ScalarType.SINT32:
            return int32FromJson;
        case descriptors_js_1.ScalarType.INT64:
        case descriptors_js_1.ScalarType.SINT64:
        case descriptors_js_1.ScalarType.SFIXED64:
        case descriptors_js_1.ScalarType.UINT64:
        case descriptors_js_1.ScalarType.FIXED64:
            return (jsonString) => /^-?0+$/.test(jsonString)
                ? "0"
                : jsonString.replace(/^(-?)0+(?=\d)/, "$1");
        default:
            // ScalarType.STRING
            return (jsonString) => jsonString;
    }
}
/**
 * Try to parse a JSON value to a 32-bit integer for the reflect API.
 *
 * Returns the input if the JSON value cannot be converted.
 */
function int32FromJson(json) {
    if (typeof json == "string") {
        if (json === "") {
            // empty string is not a number
            return json;
        }
        if (json.trim().length !== json.length) {
            // extra whitespace
            return json;
        }
        const num = Number(json);
        if (Number.isNaN(num)) {
            // not a number
            return json;
        }
        return num;
    }
    return json;
}
/**
 * Parse a JSON string, rejecting duplicate object keys (which JSON.parse would
 * otherwise silently merge).
 */
function parseJsonString(jsonString, typeName) {
    let json;
    try {
        json = JSON.parse(jsonString);
    }
    catch (e) {
        const message = e instanceof Error ? e.message : String(e);
        throw new Error(`cannot decode message ${typeName} from JSON: ${message}`, 
        // @ts-expect-error we use the ES2022 error CTOR option "cause" for better stack traces
        { cause: e });
    }
    checkDuplicateKeys(jsonString, typeName);
    return json;
}
/**
 * Scan a JSON string for duplicate object member names at any depth, throwing
 * if any are found. JSON.parse() silently keeps the last of duplicate keys, so
 * this raw-string scan is the only way to reject them. It must only be called
 * with a string that JSON.parse() has already accepted, so it can assume the
 * input is well-formed.
 */
function checkDuplicateKeys(jsonString, typeName) {
    // One Set of seen member names for each open object; arrays push null.
    const stack = [];
    // Whether the next string token is an object member name.
    let expectKey = false;
    let i = 0;
    while (i < jsonString.length) {
        switch (jsonString[i]) {
            case "{":
                stack.push(new Set());
                expectKey = true;
                i++;
                break;
            case "[":
                stack.push(null);
                expectKey = false;
                i++;
                break;
            case "}":
            case "]":
                stack.pop();
                expectKey = false;
                i++;
                break;
            case ",":
                expectKey = stack[stack.length - 1] != null;
                i++;
                break;
            case ":":
                expectKey = false;
                i++;
                break;
            case '"': {
                const open = i++;
                let escaped = false;
                while (i < jsonString.length) {
                    if (jsonString[i] == "\\") {
                        escaped = true;
                        i += 2; // skip the backslash and the character it escapes
                        continue;
                    }
                    if (jsonString[i] == '"') {
                        break;
                    }
                    i++;
                }
                const close = i++;
                const seen = stack[stack.length - 1];
                if (expectKey && seen) {
                    // Decode escapes (rare) so that, for example, a key written with a
                    // unicode escape collides with the same key written literally.
                    const name = escaped
                        ? JSON.parse(jsonString.substring(open, close + 1))
                        : jsonString.substring(open + 1, close);
                    if (seen.has(name)) {
                        throw new Error(`cannot decode message ${typeName} from JSON: duplicate object key "${name}"`);
                    }
                    seen.add(name);
                }
                expectKey = false;
                break;
            }
            default:
                i++;
                break;
        }
    }
}
function anyFromJson(any, json, ctx) {
    var _a;
    if (json === null || Array.isArray(json) || typeof json != "object") {
        throw new Error(`cannot decode message ${any.$typeName} from JSON: expected object but got ${(0, reflect_check_js_1.formatVal)(json)}`);
    }
    if (Object.keys(json).length == 0) {
        return;
    }
    const typeUrl = json["@type"];
    if (typeof typeUrl != "string" || typeUrl == "") {
        throw new Error(`cannot decode message ${any.$typeName} from JSON: "@type" is empty`);
    }
    const typeName = typeUrl.includes("/")
        ? typeUrl.substring(typeUrl.lastIndexOf("/") + 1)
        : typeUrl;
    if (!typeName.length) {
        throw new Error(`cannot decode message ${any.$typeName} from JSON: "@type" is invalid`);
    }
    const desc = (_a = ctx.registry) === null || _a === void 0 ? void 0 : _a.getMessage(typeName);
    if (!desc) {
        throw new Error(`cannot decode message ${any.$typeName} from JSON: ${typeUrl} is not in the type registry`);
    }
    const message = (0, create_js_1.create)(desc);
    if ((0, index_js_1.hasCustomJsonRepresentation)(desc) &&
        Object.prototype.hasOwnProperty.call(json, "value")) {
        compiledReader(desc)(message, json.value, ctx);
    }
    else {
        const copy = Object.assign({}, json);
        // biome-ignore lint/performance/noDelete: <explanation>
        delete copy["@type"];
        compiledReader(desc)(message, copy, ctx);
    }
    (0, index_js_1.anyPack)(desc, message, any);
}
function timestampFromJson(timestamp, json) {
    if (typeof json !== "string") {
        throw new Error(`cannot decode message ${timestamp.$typeName} from JSON: ${(0, reflect_check_js_1.formatVal)(json)}`);
    }
    const matches = json.match(/^([0-9]{4})-([0-9]{2})-([0-9]{2})T([0-9]{2}):([0-9]{2}):([0-9]{2})(?:\.([0-9]{1,9}))?(?:Z|([+-][0-9][0-9]:[0-9][0-9]))$/);
    if (!matches) {
        throw new Error(`cannot decode message ${timestamp.$typeName} from JSON: invalid RFC 3339 string`);
    }
    const ms = Date.parse(
    // biome-ignore format: want this to read well
    matches[1] + "-" + matches[2] + "-" + matches[3] + "T" + matches[4] + ":" + matches[5] + ":" + matches[6] + (matches[8] ? matches[8] : "Z"));
    if (Number.isNaN(ms)) {
        throw new Error(`cannot decode message ${timestamp.$typeName} from JSON: invalid RFC 3339 string`);
    }
    if (ms < json_js_1.timestampMsMin || ms > json_js_1.timestampMsMax) {
        throw new Error(`cannot decode message ${timestamp.$typeName} from JSON: must be from 0001-01-01T00:00:00Z to 9999-12-31T23:59:59Z inclusive`);
    }
    timestamp.seconds = proto_int64_js_1.protoInt64.parse(ms / 1000);
    timestamp.nanos = 0;
    if (matches[7]) {
        timestamp.nanos =
            parseInt("1" + matches[7] + "0".repeat(9 - matches[7].length)) -
                1000000000;
    }
}
function durationFromJson(duration, json) {
    if (typeof json !== "string") {
        throw new Error(`cannot decode message ${duration.$typeName} from JSON: ${(0, reflect_check_js_1.formatVal)(json)}`);
    }
    const match = json.match(/^(-?[0-9]+)(?:\.([0-9]+))?s/);
    if (match === null) {
        throw new Error(`cannot decode message ${duration.$typeName} from JSON: ${(0, reflect_check_js_1.formatVal)(json)}`);
    }
    const longSeconds = Number(match[1]);
    if (longSeconds > json_js_1.durationSecondsMax || longSeconds < json_js_1.durationSecondsMin) {
        throw new Error(`cannot decode message ${duration.$typeName} from JSON: ${(0, reflect_check_js_1.formatVal)(json)}`);
    }
    duration.seconds = proto_int64_js_1.protoInt64.parse(longSeconds);
    if (typeof match[2] !== "string") {
        return;
    }
    const nanosStr = match[2] + "0".repeat(9 - match[2].length);
    duration.nanos = parseInt(nanosStr);
    if (longSeconds < 0 || Object.is(longSeconds, -0)) {
        duration.nanos = -duration.nanos;
    }
}
function fieldMaskFromJson(fieldMask, json) {
    if (typeof json !== "string") {
        throw new Error(`cannot decode message ${fieldMask.$typeName} from JSON: ${(0, reflect_check_js_1.formatVal)(json)}`);
    }
    if (json === "") {
        return;
    }
    fieldMask.paths = json.split(",").map((path) => {
        if (path.includes("_")) {
            throw new Error(`cannot decode message ${fieldMask.$typeName} from JSON: path names must be lowerCamelCase`);
        }
        return (0, names_js_1.protoSnakeCase)(path);
    });
}
function structFromJson(struct, json, ctx) {
    if (typeof json != "object" || json == null || Array.isArray(json)) {
        throw new Error(`cannot decode message ${struct.$typeName} from JSON ${(0, reflect_check_js_1.formatVal)(json)}`);
    }
    const keys = Object.keys(json);
    for (let i = 0; i < keys.length; i++) {
        const key = keys[i];
        const parsedValue = (0, create_js_1.create)(index_js_1.ValueSchema);
        valueFromJson(parsedValue, json[key], ctx);
        struct.fields[key] = parsedValue;
    }
}
function valueFromJson(value, json, ctx) {
    if (++ctx.depth > ctx.recursionLimit) {
        throw new Error(`cannot decode ${value.$typeName} from JSON: maximum recursion depth of ${ctx.recursionLimit} reached`);
    }
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
                value.kind = { case: "nullValue", value: index_js_1.NullValue.NULL_VALUE };
            }
            else if (Array.isArray(json)) {
                const listValue = (0, create_js_1.create)(index_js_1.ListValueSchema);
                listValueFromJson(listValue, json, ctx);
                value.kind = { case: "listValue", value: listValue };
            }
            else {
                const struct = (0, create_js_1.create)(index_js_1.StructSchema);
                structFromJson(struct, json, ctx);
                value.kind = { case: "structValue", value: struct };
            }
            break;
        default:
            throw new Error(`cannot decode message ${value.$typeName} from JSON ${(0, reflect_check_js_1.formatVal)(json)}`);
    }
    ctx.depth--;
    return value;
}
function listValueFromJson(listValue, json, ctx) {
    if (!Array.isArray(json)) {
        throw new Error(`cannot decode message ${listValue.$typeName} from JSON ${(0, reflect_check_js_1.formatVal)(json)}`);
    }
    for (let i = 0; i < json.length; i++) {
        const value = (0, create_js_1.create)(index_js_1.ValueSchema);
        valueFromJson(value, json[i], ctx);
        listValue.values.push(value);
    }
}
