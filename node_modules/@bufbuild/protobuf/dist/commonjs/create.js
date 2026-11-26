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
exports.create = create;
const is_message_js_1 = require("./is-message.js");
const descriptors_js_1 = require("./descriptors.js");
const scalar_js_1 = require("./reflect/scalar.js");
const guard_js_1 = require("./reflect/guard.js");
const wrappers_js_1 = require("./wkt/wrappers.js");
// bootstrap-inject google.protobuf.Edition.EDITION_PROTO3: const $name = $number;
const EDITION_PROTO3 = 999;
// bootstrap-inject google.protobuf.Edition.EDITION_PROTO2: const $name = $number;
const EDITION_PROTO2 = 998;
// bootstrap-inject google.protobuf.FeatureSet.FieldPresence.IMPLICIT: const $name = $number;
const IMPLICIT = 2;
/**
 * Create a new message instance.
 *
 * The second argument is an optional initializer object, where all fields are
 * optional.
 */
function create(schema, init) {
    if ((0, is_message_js_1.isMessage)(init, schema)) {
        return init;
    }
    return compiledCreate(schema)(init);
}
const compiledCreates = new WeakMap();
/**
 * Return the compiled create function for a message, compiling it on first use. */
function compiledCreate(desc) {
    let compiled = compiledCreates.get(desc);
    if (compiled === undefined) {
        compiled = compileCreate(desc);
        compiledCreates.set(desc, compiled);
    }
    return compiled;
}
/** Singular field: scalar, enum, or message. */
const INIT_SINGULAR = 0;
/** List field: a zero message has a fresh empty array. */
const INIT_LIST = 1;
/** Map field: a zero message has a fresh empty object. */
const INIT_MAP = 2;
/** Oneof group: the ADT is always stored, cases convert by case name. */
const INIT_ONEOF = 3;
/* Compile the create function for this message type. */
function compileCreate(desc) {
    const typeName = desc.typeName;
    const { properties, prototype } = compileInitMessage(desc);
    return (init) => {
        let message;
        if (prototype !== undefined) {
            message = Object.create(prototype);
            message.$typeName = typeName;
        }
        else {
            message = { $typeName: typeName };
        }
        for (let i = 0; i < properties.length; i++) {
            const property = properties[i];
            const name = property.name;
            const initValue = init === null || init === void 0 ? void 0 : init[name];
            switch (property.kind) {
                case INIT_SINGULAR:
                    if (initValue != null) {
                        message[name] =
                            property.convert !== undefined
                                ? property.convert(initValue)
                                : initValue;
                    }
                    else if (property.constant !== undefined) {
                        message[name] = property.constant;
                    }
                    break;
                case INIT_LIST:
                    message[name] =
                        property.convert !== undefined && Array.isArray(initValue)
                            ? initValue.map(property.convert)
                            : (initValue !== null && initValue !== void 0 ? initValue : []);
                    break;
                case INIT_MAP:
                    // Object.create(null) would be desirable for the fresh map, but is
                    // unsupported by React:
                    // https://react.dev/reference/react/use-server#serializable-parameters-and-return-values
                    if (property.convert === undefined || !(0, guard_js_1.isObject)(initValue)) {
                        message[name] = initValue !== null && initValue !== void 0 ? initValue : {};
                    }
                    else {
                        const converted = {};
                        const keys = Object.keys(initValue);
                        for (let k = 0; k < keys.length; k++) {
                            converted[keys[k]] = property.convert(initValue[keys[k]]);
                        }
                        message[name] = converted;
                    }
                    break;
                case INIT_ONEOF: {
                    const oneofValue = initValue;
                    if ((oneofValue === null || oneofValue === void 0 ? void 0 : oneofValue.case) != null) {
                        const convert = property.convert.get(oneofValue.case);
                        if (convert !== undefined) {
                            message[name] = {
                                case: oneofValue.case,
                                value: convert(oneofValue.value),
                            };
                            break;
                        }
                    }
                    message[name] = { case: undefined };
                    break;
                }
            }
        }
        return message;
    };
}
/**
 * Classify every member once, so that creating a message is a walk over a
 * compact list instead of a walk over the descriptor.
 */
function compileInitMessage(desc) {
    var _a, _b;
    const properties = [];
    const prototype = {};
    const usePrototype = needsPrototypeChain(desc);
    for (const member of desc.members) {
        const name = member.localName;
        if (member.kind == "oneof") {
            properties.push({
                name,
                kind: INIT_ONEOF,
                constant: undefined,
                convert: compileConvertOneof(member),
            });
            continue;
        }
        switch (member.fieldKind) {
            case "message": {
                // Singular message fields are absent from a zero message.
                properties.push({
                    name,
                    kind: INIT_SINGULAR,
                    constant: undefined,
                    convert: compileConvertMessage(member),
                });
                break;
            }
            case "list": {
                properties.push({
                    name,
                    kind: INIT_LIST,
                    constant: undefined,
                    convert: member.listKind == "message"
                        ? ((_a = compileConvertMessage(member)) !== null && _a !== void 0 ? _a : ((value) => value))
                        : member.scalar == descriptors_js_1.ScalarType.BYTES
                            ? toU8Arr
                            : undefined,
                });
                break;
            }
            case "map": {
                properties.push({
                    name,
                    kind: INIT_MAP,
                    constant: undefined,
                    convert: member.mapKind == "message"
                        ? ((_b = compileConvertMessage(member)) !== null && _b !== void 0 ? _b : ((value) => value))
                        : member.scalar == descriptors_js_1.ScalarType.BYTES
                            ? toU8Arr
                            : undefined,
                });
                break;
            }
            default: {
                const zeroValue = createZeroValue(member);
                properties.push({
                    name,
                    kind: INIT_SINGULAR,
                    constant: member.presence == IMPLICIT ? zeroValue : undefined,
                    convert: member.fieldKind == "scalar" && member.scalar == descriptors_js_1.ScalarType.BYTES
                        ? toU8Arr
                        : undefined,
                });
                if (usePrototype) {
                    prototype[name] = zeroValue;
                }
                break;
            }
        }
    }
    return {
        properties,
        prototype: usePrototype ? prototype : undefined,
    };
}
/**
 * Compile the conversion of each case of a oneof group, keyed by case name.
 */
function compileConvertOneof(oneof) {
    const converters = new Map();
    for (const field of oneof.fields) {
        let convert;
        if (field.fieldKind == "message") {
            convert = compileConvertMessage(field);
        }
        else if (field.fieldKind == "scalar" &&
            field.scalar == descriptors_js_1.ScalarType.BYTES) {
            convert = toU8Arr;
        }
        converters.set(field.localName, convert !== null && convert !== void 0 ? convert : ((value) => value));
    }
    return converters;
}
/**
 * Compile the conversion of an init value for a message field, a message
 * list item, or a message map value. Returns undefined if values are used
 * as-is.
 */
function compileConvertMessage(field) {
    if (field.fieldKind == "message" &&
        !field.oneof &&
        (0, wrappers_js_1.isWrapperDesc)(field.message)) {
        // Types from google/protobuf/wrappers.proto are unwrapped when used in
        // a singular field that is not part of a oneof group.
        return field.message.fields[0].scalar == descriptors_js_1.ScalarType.BYTES
            ? toU8Arr
            : undefined;
    }
    if (field.message.typeName == "google.protobuf.Struct" &&
        field.parent.typeName !== "google.protobuf.Value") {
        // google.protobuf.Struct is represented with JsonObject when used in a
        // field, except when used in google.protobuf.Value.
        return undefined;
    }
    const messageDesc = field.message;
    // Resolved on first use, not here: the message type can be this very field's
    // parent, whose create function is still being compiled.
    let compiled;
    return (value) => {
        if (!(0, guard_js_1.isObject)(value) || (0, is_message_js_1.isMessage)(value, messageDesc)) {
            return value;
        }
        compiled !== null && compiled !== void 0 ? compiled : (compiled = compiledCreate(messageDesc));
        return compiled(value);
    };
}
// converts any ArrayLike<number> to Uint8Array if necessary.
function toU8Arr(value) {
    return Array.isArray(value) ? new Uint8Array(value) : value;
}
/**
 * Do we need the prototype chain to track field presence?
 */
function needsPrototypeChain(desc) {
    switch (desc.file.edition) {
        case EDITION_PROTO3:
            // proto3 always uses implicit presence, we never need the prototype chain.
            return false;
        case EDITION_PROTO2:
            // proto2 never uses implicit presence, we always need the prototype chain.
            return true;
        default:
            // If a message uses scalar or enum fields with explicit presence, we need
            // the prototype chain to track presence. This rule does not apply to fields
            // in a oneof group - they use a different mechanism to track presence.
            return desc.fields.some((f) => f.presence != IMPLICIT && f.fieldKind != "message" && !f.oneof);
    }
}
/**
 * Returns the zero value for a scalar or enum field. Scalar and enum fields
 * can have default values.
 */
function createZeroValue(field) {
    const defaultValue = field.getDefaultValue();
    if (defaultValue !== undefined) {
        return field.fieldKind == "scalar" && field.longAsString
            ? defaultValue.toString()
            : defaultValue;
    }
    return field.fieldKind == "scalar"
        ? (0, scalar_js_1.scalarZeroValue)(field.scalar, field.longAsString)
        : field.enum.values[0].number;
}
