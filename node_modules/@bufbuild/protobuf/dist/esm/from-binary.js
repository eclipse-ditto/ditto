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
import { ScalarType } from "./descriptors.js";
import { scalarZeroValue } from "./reflect/scalar.js";
import { FieldError } from "./reflect/error.js";
import { unsafeLocal } from "./reflect/unsafe.js";
import { localMessageMapper } from "./reflect/message.js";
import { create } from "./create.js";
import { BinaryReader, WireType } from "./wire/binary-encoding.js";
import { varint32write } from "./wire/varint.js";
/**
 * @private Only exported for getExtension()
 */
export function makeReadContext(options) {
    return Object.assign(Object.assign({ readUnknownFields: true, recursionLimit: 100 }, options), { depth: 0 });
}
/**
 * Parse serialized binary data.
 */
export function fromBinary(schema, bytes, options) {
    const message = create(schema);
    compiledReader(schema).read(message, new BinaryReader(bytes), makeReadContext(options), bytes.byteLength);
    return message;
}
/**
 * Parse from binary data, merging fields.
 *
 * Repeated fields are appended. Map entries are added, overwriting
 * existing keys.
 *
 * If a message field is already present, it will be merged with the
 * new data.
 */
export function mergeFromBinary(schema, target, bytes, options) {
    if (target.$typeName !== schema.typeName &&
        schema.fields.length > 0) {
        throw new FieldError(schema.fields[0], `cannot use ${schema.fields[0]} with message ${target.$typeName}`, "ForeignFieldError");
    }
    compiledReader(schema).read(target, new BinaryReader(bytes), makeReadContext(options), bytes.byteLength);
    return target;
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
    const fieldReaders = new Map();
    const compiled = {
        read: compileMessageReader(descString, fieldReaders),
        readGroup: compileGroupReader(descString, fieldReaders),
    };
    // Register before compiling fields, so that recursive message types
    // resolve to this instance instead of compiling endlessly.
    compiledReaders.set(desc, compiled);
    for (const field of desc.fields) {
        fieldReaders.set(field.number, compileFieldReader(field));
    }
    return compiled;
}
/**
 * Create a decoder for a length-prefixed message body, dispatching wire
 * records to the compiled field decoders by field number.
 */
function compileMessageReader(descString, fieldReaders) {
    return (message, reader, ctx, length) => {
        var _a;
        if (++ctx.depth > ctx.recursionLimit) {
            throw new Error(`cannot decode ${descString} from binary: maximum recursion depth of ${ctx.recursionLimit} reached`);
        }
        const end = reader.pos + length;
        const unknownFields = (_a = message.$unknown) !== null && _a !== void 0 ? _a : [];
        while (reader.pos < end) {
            const [fieldNo, wireType] = reader.tag();
            const fieldReader = fieldReaders.get(fieldNo);
            if (fieldReader === undefined) {
                // Use remaining recursion budget for skipping nested groups
                const data = reader.skip(wireType, fieldNo, ctx.recursionLimit - ctx.depth);
                if (ctx.readUnknownFields) {
                    unknownFields.push({ no: fieldNo, wireType, data });
                }
                continue;
            }
            fieldReader(message, reader, ctx, wireType);
        }
        if (unknownFields.length > 0) {
            message.$unknown = unknownFields;
        }
        ctx.depth--;
    };
}
/**
 * Create a decoder for a message with the delimited encoding (group),
 * reading until the EndGroup tag, like compileMessageReader.
 */
function compileGroupReader(descString, fieldReaders) {
    return (message, reader, ctx, fieldNo) => {
        var _a;
        if (++ctx.depth > ctx.recursionLimit) {
            throw new Error(`cannot decode ${descString} from binary: maximum recursion depth of ${ctx.recursionLimit} reached`);
        }
        let recordFieldNo;
        let wireType;
        const unknownFields = (_a = message.$unknown) !== null && _a !== void 0 ? _a : [];
        while (reader.pos < reader.len) {
            [recordFieldNo, wireType] = reader.tag();
            if (wireType == WireType.EndGroup) {
                break;
            }
            const fieldReader = fieldReaders.get(recordFieldNo);
            if (fieldReader === undefined) {
                // Use remaining recursion budget for skipping nested groups
                const data = reader.skip(wireType, recordFieldNo, ctx.recursionLimit - ctx.depth);
                if (ctx.readUnknownFields) {
                    unknownFields.push({ no: recordFieldNo, wireType, data });
                }
                continue;
            }
            fieldReader(message, reader, ctx, wireType);
        }
        if (wireType != WireType.EndGroup || recordFieldNo !== fieldNo) {
            throw new Error("invalid end group tag");
        }
        if (unknownFields.length > 0) {
            message.$unknown = unknownFields;
        }
        ctx.depth--;
    };
}
/**
 * @private Only exported for getExtension()
 */
export function readField(message, reader, field, wireType, ctx) {
    compileFieldReader(field)(message[unsafeLocal], reader, ctx, wireType);
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
    const readScalar = compileScalarReader(field.scalar, field.utf8Validation, field.longAsString);
    const localName = field.localName;
    if (field.oneof) {
        const oneofLocalName = field.oneof.localName;
        return (message, reader) => {
            message[oneofLocalName] = {
                case: localName,
                value: readScalar(reader),
            };
        };
    }
    return (message, reader) => {
        message[localName] = readScalar(reader);
    };
}
function compileEnumFieldReader(field) {
    var _a;
    const localName = field.localName;
    const oneofLocalName = (_a = field.oneof) === null || _a === void 0 ? void 0 : _a.localName;
    if (field.enum.open) {
        if (oneofLocalName !== undefined) {
            return (message, reader) => {
                message[oneofLocalName] = { case: localName, value: reader.int32() };
            };
        }
        return (message, reader) => {
            message[localName] = reader.int32();
        };
    }
    // Closed enums: unknown values are stored as unknown fields.
    const values = field.enum.values;
    const fieldNo = field.number;
    return (message, reader, ctx, wireType) => {
        var _a;
        const val = reader.int32();
        if (values.some((v) => v.number === val)) {
            if (oneofLocalName !== undefined) {
                message[oneofLocalName] = { case: localName, value: val };
            }
            else {
                message[localName] = val;
            }
        }
        else if (ctx.readUnknownFields) {
            const bytes = [];
            varint32write(val, bytes);
            const unknownFields = (_a = message.$unknown) !== null && _a !== void 0 ? _a : [];
            unknownFields.push({
                no: fieldNo,
                wireType,
                data: new Uint8Array(bytes),
            });
            message.$unknown = unknownFields;
        }
    };
}
function compileMessageFieldReader(field) {
    const localName = field.localName;
    const { toMessage, toLocal } = localMessageMapper(field);
    const readChild = compileChildReader(field);
    if (field.oneof) {
        const oneofLocalName = field.oneof.localName;
        return (message, reader, ctx) => {
            const oneof = message[oneofLocalName];
            const child = toMessage(oneof.case === localName ? oneof.value : undefined);
            readChild(child, reader, ctx);
            message[oneofLocalName] = { case: localName, value: toLocal(child) };
        };
    }
    return (message, reader, ctx) => {
        const child = toMessage(message[localName]);
        readChild(child, reader, ctx);
        message[localName] = toLocal(child);
    };
}
/**
 * Compile a decoder for the wire format of a message field, honoring the
 * delimited encoding of the field.
 */
function compileChildReader(field) {
    const compiledChild = compiledReader(field.message);
    if (field.delimitedEncoding) {
        const fieldNo = field.number;
        return (child, reader, ctx) => compiledChild.readGroup(child, reader, ctx, fieldNo);
    }
    return (child, reader, ctx) => compiledChild.read(child, reader, ctx, reader.uint32());
}
function compileListFieldReader(field) {
    const localName = field.localName;
    if (field.listKind == "message") {
        const { toMessage, toLocal } = localMessageMapper(field);
        const readChild = compileChildReader(field);
        return (message, reader, ctx) => {
            const child = toMessage(undefined);
            readChild(child, reader, ctx);
            message[localName].push(toLocal(child));
        };
    }
    const scalarType = field.listKind == "enum" ? ScalarType.INT32 : field.scalar;
    const longAsString = field.listKind == "scalar" ? field.longAsString : false;
    const readScalar = compileScalarReader(scalarType, field.utf8Validation, longAsString);
    const packedPossible = scalarType != ScalarType.STRING && scalarType != ScalarType.BYTES;
    return (message, reader, ctx, wireType) => {
        const items = message[localName];
        if (wireType == WireType.LengthDelimited && packedPossible) {
            const end = reader.uint32() + reader.pos;
            while (reader.pos < end) {
                items.push(readScalar(reader));
            }
        }
        else {
            items.push(readScalar(reader));
        }
    };
}
function compileMapFieldReader(field) {
    const localName = field.localName;
    const readKey = compileScalarReader(field.mapKey, field.utf8Validation, false);
    const keyZero = scalarZeroValue(field.mapKey, false);
    let readValue;
    let valueDefault;
    switch (field.mapKind) {
        case "scalar": {
            const scalar = field.scalar;
            const readScalar = compileScalarReader(scalar, field.utf8Validation, false);
            readValue = (reader) => readScalar(reader);
            // Bytes zero values are created per entry, so that entries do not share
            // one instance.
            if (scalar == ScalarType.BYTES) {
                valueDefault = () => new Uint8Array(0);
            }
            else {
                const zero = scalarZeroValue(scalar, false);
                valueDefault = () => zero;
            }
            break;
        }
        case "enum": {
            const zero = field.enum.values[0].number;
            readValue = (reader) => reader.int32();
            valueDefault = () => zero;
            break;
        }
        case "message": {
            const { toMessage, toLocal } = localMessageMapper(field);
            const readChild = compiledReader(field.message).read;
            readValue = (reader, ctx) => {
                const child = toMessage(undefined);
                readChild(child, reader, ctx, reader.uint32());
                return toLocal(child);
            };
            valueDefault = () => toLocal(toMessage(undefined));
            break;
        }
    }
    return (message, reader, ctx) => {
        const record = message[localName];
        let key;
        let val;
        // Read the length of the map entry, which is a varint.
        const len = reader.uint32();
        // Calculate end AFTER advancing reader.pos (above), so that reader.pos is
        // at the start of the map entry.
        const end = reader.pos + len;
        while (reader.pos < end) {
            // Map entries have the key in field 1, and the value in field 2.
            const [fieldNo] = reader.tag();
            switch (fieldNo) {
                case 1:
                    key = readKey(reader);
                    break;
                case 2:
                    val = readValue(reader, ctx);
                    break;
            }
        }
        if (key === undefined) {
            key = keyZero;
        }
        if (val === undefined) {
            val = valueDefault();
        }
        // Object property keys are always strings or symbols. Assigning with a
        // boolean, number, or bigint key implicitly converts it to a string.
        record[key] = val;
    };
}
/**
 * Returns a reader for a scalar value. For 64-bit integers, BinaryReader
 * already returns the local representation (bigint or string), so, unlike in
 * the reflection layer, no validation is needed here.
 */
function compileScalarReader(type, utf8Validation, longAsString) {
    switch (type) {
        case ScalarType.STRING:
            return (reader) => reader.string(utf8Validation);
        case ScalarType.BOOL:
            return (reader) => reader.bool();
        case ScalarType.DOUBLE:
            return (reader) => reader.double();
        case ScalarType.FLOAT:
            return (reader) => reader.float();
        case ScalarType.INT32:
            return (reader) => reader.int32();
        case ScalarType.INT64:
            if (longAsString) {
                return (reader) => String(reader.int64());
            }
            return (reader) => reader.int64();
        case ScalarType.UINT64:
            if (longAsString) {
                return (reader) => String(reader.uint64());
            }
            return (reader) => reader.uint64();
        case ScalarType.FIXED64:
            if (longAsString) {
                return (reader) => String(reader.fixed64());
            }
            return (reader) => reader.fixed64();
        case ScalarType.BYTES:
            return (reader) => reader.bytes();
        case ScalarType.FIXED32:
            return (reader) => reader.fixed32();
        case ScalarType.SFIXED32:
            return (reader) => reader.sfixed32();
        case ScalarType.SFIXED64:
            if (longAsString) {
                return (reader) => String(reader.sfixed64());
            }
            return (reader) => reader.sfixed64();
        case ScalarType.SINT64:
            if (longAsString) {
                return (reader) => String(reader.sint64());
            }
            return (reader) => reader.sint64();
        case ScalarType.UINT32:
            return (reader) => reader.uint32();
        case ScalarType.SINT32:
            return (reader) => reader.sint32();
    }
}
