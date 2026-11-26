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
exports.toBinary = toBinary;
exports.writeField = writeField;
const binary_encoding_js_1 = require("./wire/binary-encoding.js");
const descriptors_js_1 = require("./descriptors.js");
const error_js_1 = require("./reflect/error.js");
const unsafe_js_1 = require("./reflect/unsafe.js");
const message_js_1 = require("./reflect/message.js");
const proto_int64_js_1 = require("./proto-int64.js");
// bootstrap-inject google.protobuf.FeatureSet.FieldPresence.IMPLICIT: const $name = $number;
const IMPLICIT = 2;
// bootstrap-inject google.protobuf.FeatureSet.FieldPresence.LEGACY_REQUIRED: const $name = $number;
const LEGACY_REQUIRED = 3;
// Default options for serializing binary data.
const writeDefaults = {
    writeUnknownFields: true,
};
function makeWriteOptions(options) {
    return options ? Object.assign(Object.assign({}, writeDefaults), options) : writeDefaults;
}
function toBinary(schema, message, options) {
    const writer = new binary_encoding_js_1.BinaryWriter();
    compiledWriter(schema)(writer, makeWriteOptions(options), message);
    return writer.finish();
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
    const sortedFields = desc.fields.concat().sort((a, b) => a.number - b.number);
    // The field reported in ForeignFieldError.
    const foreignField = sortedFields[0];
    const fieldWriters = [];
    const compiled = (writer, opts, message) => {
        if (message.$typeName !== typeName && foreignField !== undefined) {
            throw new error_js_1.FieldError(foreignField, `cannot use ${foreignField} with message ${message.$typeName}`, "ForeignFieldError");
        }
        for (let i = 0; i < fieldWriters.length; i++) {
            fieldWriters[i](writer, opts, message);
        }
        const unknown = message.$unknown;
        if (unknown !== undefined && opts.writeUnknownFields) {
            for (let i = 0; i < unknown.length; i++) {
                const { no, wireType, data } = unknown[i];
                writer.tag(no, wireType).raw(data);
            }
        }
    };
    // Register before compiling fields, so that recursive message types
    // resolve to this instance instead of compiling endlessly.
    compiledWriters.set(desc, compiled);
    for (const field of sortedFields) {
        fieldWriters.push(compileField(field));
    }
    return compiled;
}
function compileField(field) {
    switch (field.fieldKind) {
        case "message":
        case "scalar":
        case "enum":
            return compileSingularField(field);
        case "list":
            return compileListField(field);
        case "map":
            return compileMapField(field);
    }
}
/**
 * Compile an encoder for a singular field: the presence check, and the
 * value encoder.
 */
function compileSingularField(field) {
    const writeValue = compileSingularValue(field);
    const localName = field.localName;
    if (field.oneof) {
        const oneofLocalName = field.oneof.localName;
        return (writer, opts, message) => {
            const oneof = message[oneofLocalName];
            if (oneof.case === localName) {
                writeValue(writer, opts, oneof.value);
            }
        };
    }
    if (field.presence != IMPLICIT) {
        const requiredError = field.presence == LEGACY_REQUIRED
            ? `cannot encode ${field} to binary: required field not set`
            : undefined;
        return (writer, opts, message) => {
            const value = message[localName];
            // Fields with explicit presence have properties on the prototype
            // chain for default / zero values (except for proto3).
            if (value !== undefined &&
                Object.prototype.hasOwnProperty.call(message, localName)) {
                writeValue(writer, opts, value);
            }
            else if (requiredError !== undefined) {
                throw new Error(requiredError);
            }
        };
    }
    // Implicit presence: the field is set when the value is not the zero
    // value. The check is inlined per type, see isScalarZeroValue.
    if (field.fieldKind == "enum") {
        const zero = field.enum.values[0].number;
        return (writer, opts, message) => {
            const value = message[localName];
            if (value !== zero) {
                writeValue(writer, opts, value);
            }
        };
    }
    switch (field.scalar) {
        case descriptors_js_1.ScalarType.BOOL:
            return (writer, opts, message) => {
                const value = message[localName];
                if (value !== false) {
                    writeValue(writer, opts, value);
                }
            };
        case descriptors_js_1.ScalarType.STRING:
            return (writer, opts, message) => {
                const value = message[localName];
                if (value !== "") {
                    writeValue(writer, opts, value);
                }
            };
        case descriptors_js_1.ScalarType.BYTES:
            return (writer, opts, message) => {
                const value = message[localName];
                if (!(value instanceof Uint8Array) || value.byteLength > 0) {
                    writeValue(writer, opts, value);
                }
            };
        case descriptors_js_1.ScalarType.DOUBLE:
        case descriptors_js_1.ScalarType.FLOAT:
            return (writer, opts, message) => {
                const value = message[localName];
                // Object.is distinguishes -0 from 0.
                if (!Object.is(value, 0)) {
                    writeValue(writer, opts, value);
                }
            };
        default:
            return (writer, opts, message) => {
                const value = message[localName];
                // Loose comparison matches 0n, 0 and "0".
                if (value != 0) {
                    writeValue(writer, opts, value);
                }
            };
    }
}
/**
 * Compile an encoder for the value of a singular field, including the tag.
 */
function compileSingularValue(field) {
    switch (field.fieldKind) {
        case "message": {
            const { toMessage } = (0, message_js_1.localMessageMapper)(field);
            const writeChild = compileChildWriter(field);
            return (writer, opts, value) => {
                writeChild(writer, opts, toMessage(value));
            };
        }
        case "scalar":
        case "enum": {
            const scalarType = field.fieldKind == "enum" ? descriptors_js_1.ScalarType.INT32 : field.scalar;
            const fieldNo = field.number;
            const wireType = writeTypeOfScalar(scalarType);
            const writeScalar = compileScalarValue(scalarType, field.parent.typeName, field.name);
            return (writer, opts, value) => {
                writer.tag(fieldNo, wireType);
                writeScalar(writer, value);
            };
        }
    }
}
function compileListField(field) {
    const localName = field.localName;
    const fieldNo = field.number;
    switch (field.listKind) {
        case "message": {
            const { toMessage } = (0, message_js_1.localMessageMapper)(field);
            const writeChild = compileChildWriter(field);
            return (writer, opts, message) => {
                const items = message[localName];
                for (let i = 0; i < items.length; i++) {
                    writeChild(writer, opts, toMessage(items[i]));
                }
            };
        }
        case "scalar":
        case "enum": {
            const scalarType = field.listKind == "enum" ? descriptors_js_1.ScalarType.INT32 : field.scalar;
            const writeScalar = compileScalarValue(scalarType, field.parent.typeName, field.name);
            if (field.packed) {
                return (writer, opts, message) => {
                    const items = message[localName];
                    if (items.length == 0) {
                        return;
                    }
                    writer.tag(fieldNo, binary_encoding_js_1.WireType.LengthDelimited).fork();
                    for (let i = 0; i < items.length; i++) {
                        writeScalar(writer, items[i]);
                    }
                    writer.join();
                };
            }
            const wireType = writeTypeOfScalar(scalarType);
            return (writer, opts, message) => {
                const items = message[localName];
                for (let i = 0; i < items.length; i++) {
                    writer.tag(fieldNo, wireType);
                    writeScalar(writer, items[i]);
                }
            };
        }
    }
}
function compileMapField(field) {
    const localName = field.localName;
    const fieldNo = field.number;
    const writeKey = compileMapKey(field);
    if (field.mapKind == "message") {
        const { toMessage } = (0, message_js_1.localMessageMapper)(field);
        const writeMessage = compiledWriter(field.message);
        return (writer, opts, message) => {
            const record = message[localName];
            const keys = Object.keys(record);
            for (let i = 0; i < keys.length; i++) {
                const key = keys[i];
                writer.tag(fieldNo, binary_encoding_js_1.WireType.LengthDelimited).fork();
                writeKey(writer, key);
                // The value of a map entry is always field number 2.
                writer.tag(2, binary_encoding_js_1.WireType.LengthDelimited).fork();
                writeMessage(writer, opts, toMessage(record[key]));
                writer.join();
                writer.join();
            }
        };
    }
    const scalarType = field.mapKind == "enum" ? descriptors_js_1.ScalarType.INT32 : field.scalar;
    const valueWireType = writeTypeOfScalar(scalarType);
    const writeScalar = compileScalarValue(scalarType, field.parent.typeName, field.name);
    return (writer, opts, message) => {
        const record = message[localName];
        const keys = Object.keys(record);
        for (let i = 0; i < keys.length; i++) {
            const key = keys[i];
            writer.tag(fieldNo, binary_encoding_js_1.WireType.LengthDelimited).fork();
            writeKey(writer, key);
            // The value of a map entry is always field number 2.
            writer.tag(2, valueWireType);
            writeScalar(writer, record[key]);
            writer.join();
        }
    };
}
/**
 * Compile an encoder for a map key. Map keys are stored as object keys and
 * are always strings locally. Convert them to their scalar type before
 * writing, like the reflect API does when iterating map entries.
 */
function compileMapKey(field) {
    const wireType = writeTypeOfScalar(field.mapKey);
    const writeScalar = compileScalarValue(field.mapKey, field.parent.typeName, field.name);
    const convertKey = compileMapKeyConverter(field.mapKey);
    return (writer, key) => {
        // The key of a map entry is always field number 1.
        writer.tag(1, wireType);
        writeScalar(writer, convertKey(key));
    };
}
/**
 * Returns a converter from an object key (always a string) to the closest
 * possible type for the map key type. Invalid keys are passed through to
 * the scalar writer, which raises an error for them.
 */
function compileMapKeyConverter(type) {
    switch (type) {
        case descriptors_js_1.ScalarType.STRING:
            return (key) => key;
        case descriptors_js_1.ScalarType.BOOL:
            return (key) => (key === "true" ? true : key === "false" ? false : key);
        case descriptors_js_1.ScalarType.UINT64:
        case descriptors_js_1.ScalarType.FIXED64:
            return (key) => {
                try {
                    return proto_int64_js_1.protoInt64.uParse(key);
                }
                catch (_a) {
                    return key;
                }
            };
        case descriptors_js_1.ScalarType.INT64:
        case descriptors_js_1.ScalarType.SFIXED64:
        case descriptors_js_1.ScalarType.SINT64:
            return (key) => {
                try {
                    return proto_int64_js_1.protoInt64.parse(key);
                }
                catch (_a) {
                    return key;
                }
            };
        default:
            // Handles INT32, UINT32, SINT32, FIXED32, SFIXED32.
            // We do not use individual cases to save a few bytes code size.
            return (key) => {
                const n = Number.parseInt(key);
                return Number.isFinite(n) ? n : key;
            };
    }
}
/**
 * Compile an encoder for a bare scalar value (no tag), wrapping errors from
 * the writer with the message and field name.
 */
function compileScalarValue(type, messageName, fieldName) {
    const writeScalar = compileScalarWrite(type);
    return (writer, value) => {
        try {
            writeScalar(writer, value);
        }
        catch (e) {
            if (e instanceof Error) {
                throw new Error(`cannot encode field ${messageName}.${fieldName} to binary: ${e.message}`);
            }
            throw e;
        }
    };
}
function compileScalarWrite(type) {
    switch (type) {
        case descriptors_js_1.ScalarType.STRING:
            return (writer, value) => writer.string(value);
        case descriptors_js_1.ScalarType.BOOL:
            return (writer, value) => writer.bool(value);
        case descriptors_js_1.ScalarType.DOUBLE:
            return (writer, value) => writer.double(value);
        case descriptors_js_1.ScalarType.FLOAT:
            return (writer, value) => writer.float(value);
        case descriptors_js_1.ScalarType.INT32:
            return (writer, value) => writer.int32(value);
        case descriptors_js_1.ScalarType.INT64:
            return (writer, value) => writer.int64(value);
        case descriptors_js_1.ScalarType.UINT64:
            return (writer, value) => writer.uint64(value);
        case descriptors_js_1.ScalarType.FIXED64:
            return (writer, value) => writer.fixed64(value);
        case descriptors_js_1.ScalarType.BYTES:
            return (writer, value) => writer.bytes(value);
        case descriptors_js_1.ScalarType.FIXED32:
            return (writer, value) => writer.fixed32(value);
        case descriptors_js_1.ScalarType.SFIXED32:
            return (writer, value) => writer.sfixed32(value);
        case descriptors_js_1.ScalarType.SFIXED64:
            return (writer, value) => writer.sfixed64(value);
        case descriptors_js_1.ScalarType.SINT64:
            return (writer, value) => writer.sint64(value);
        case descriptors_js_1.ScalarType.UINT32:
            return (writer, value) => writer.uint32(value);
        case descriptors_js_1.ScalarType.SINT32:
            return (writer, value) => writer.sint32(value);
    }
}
/**
 * Write a single field to binary format, if it is set. Used to serialize
 * extensions: extensions always have explicit presence, so an extension
 * value that was just set on the container is always written.
 *
 * @private
 */
function writeField(writer, opts, msg, field) {
    compileField(field)(writer, opts, msg[unsafe_js_1.unsafeLocal]);
}
/**
 * Compile an encoder for the wire format of a message field, honoring the
 * delimited encoding of the field. The tag is written by the encoder.
 */
function compileChildWriter(field) {
    const fieldNo = field.number;
    const writeMessage = compiledWriter(field.message);
    if (field.delimitedEncoding) {
        return (writer, opts, child) => {
            writer.tag(fieldNo, binary_encoding_js_1.WireType.StartGroup);
            writeMessage(writer, opts, child);
            writer.tag(fieldNo, binary_encoding_js_1.WireType.EndGroup);
        };
    }
    return (writer, opts, child) => {
        writer.tag(fieldNo, binary_encoding_js_1.WireType.LengthDelimited).fork();
        writeMessage(writer, opts, child);
        writer.join();
    };
}
function writeTypeOfScalar(type) {
    switch (type) {
        case descriptors_js_1.ScalarType.BYTES:
        case descriptors_js_1.ScalarType.STRING:
            return binary_encoding_js_1.WireType.LengthDelimited;
        case descriptors_js_1.ScalarType.DOUBLE:
        case descriptors_js_1.ScalarType.FIXED64:
        case descriptors_js_1.ScalarType.SFIXED64:
            return binary_encoding_js_1.WireType.Bit64;
        case descriptors_js_1.ScalarType.FIXED32:
        case descriptors_js_1.ScalarType.SFIXED32:
        case descriptors_js_1.ScalarType.FLOAT:
            return binary_encoding_js_1.WireType.Bit32;
        default:
            return binary_encoding_js_1.WireType.Varint;
    }
}
