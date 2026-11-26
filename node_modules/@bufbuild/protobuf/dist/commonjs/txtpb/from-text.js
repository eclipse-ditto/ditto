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
exports.fromText = fromText;
exports.mergeFromText = mergeFromText;
const descriptors_js_1 = require("../descriptors.js");
const proto_int64_js_1 = require("../proto-int64.js");
const reflect_js_1 = require("../reflect/reflect.js");
const error_js_1 = require("../reflect/error.js");
const scalar_js_1 = require("../reflect/scalar.js");
const to_binary_js_1 = require("../to-binary.js");
const extensions_js_1 = require("../extensions.js");
const text_encoding_js_1 = require("../wire/text-encoding.js");
const reader_js_1 = require("./reader.js");
const is_group_like_js_1 = require("./is-group-like.js");
function makeReadContext(reader, options) {
    return Object.assign(Object.assign({ recursionLimit: 100 }, options), { reader, depth: 0 });
}
/**
 * Parse a message from the protobuf text format.
 *
 * Requires BigInt: throws immediately if the environment does not support it.
 */
function fromText(schema, text, options) {
    const msg = (0, reflect_js_1.reflect)(schema);
    parseText(msg, text, options);
    return msg.message;
}
/**
 * Parse a message from the protobuf text format, merging into the target.
 *
 * Repeated fields are appended, singular fields are overwritten (last wins),
 * message fields are merged, and map entries are added (overwriting an existing
 * key).
 *
 * Requires BigInt: throws immediately if the environment does not support it.
 */
function mergeFromText(schema, target, text, options) {
    parseText((0, reflect_js_1.reflect)(schema, target), text, options);
    return target;
}
function parseText(msg, text, options) {
    if (!proto_int64_js_1.protoInt64.supported) {
        throw new Error("the protobuf text format requires BigInt, which is unavailable in this environment");
    }
    const ctx = makeReadContext(new reader_js_1.Reader(text), options);
    try {
        readMessageBody(msg, ctx, "eof");
    }
    catch (e) {
        if ((0, error_js_1.isFieldError)(e)) {
            throw new Error(`cannot decode ${e.field()} from text format: ${e.message}`, 
            // @ts-expect-error we use the ES2022 error CTOR option "cause" for better stack traces
            { cause: e });
        }
        throw e;
    }
}
/**
 * Read a message body (its fields until `close`), guarding nesting depth. The
 * top-level message and every nested "{...}"/"<...>" block go through here, so
 * the recursion limit counts the root too, matching fromJson and fromBinary.
 */
function readMessageBody(msg, ctx, close) {
    if (++ctx.depth > ctx.recursionLimit) {
        throw new Error(`recursion limit of ${ctx.recursionLimit} reached decoding ${msg.desc}`);
    }
    readFields(msg, ctx, close);
    ctx.depth--;
}
/**
 * Read the fields of a message until `close` (the matching close token, or
 * "eof" for the top-level message).
 */
function readFields(msg, ctx, close) {
    const seen = { fields: new Set(), oneofs: new Set() };
    for (;;) {
        const tok = ctx.reader.peek();
        if (tok.type === "eof") {
            if (close !== "eof") {
                throw new Error(`unexpected end of input, expected "${close}"`);
            }
            return;
        }
        if (close !== "eof" && tok.type === close) {
            ctx.reader.next();
            return;
        }
        readField(msg, ctx, seen);
        // A single optional "," or ";" may follow a field. Because the next
        // iteration treats a separator as a field name and rejects it, a doubled
        // separator is an error, while a single trailing one is allowed.
        consumeSeparator(ctx);
    }
}
function readField(msg, ctx, seen) {
    var _a;
    const nameTok = ctx.reader.next();
    if (nameTok.type === "identifier") {
        const field = fieldByTextName(msg.desc, nameTok.value);
        if (field !== undefined) {
            checkSeen(field, seen);
            readFieldValue(msg, field, ctx);
            return;
        }
        // Reserved field names are silently skipped; any other unknown name is an
        // error. This matches protobuf-go.
        if (msg.desc.proto.reservedName.includes(nameTok.value)) {
            skipFieldValue(ctx);
            return;
        }
        throw new Error(`unknown field "${nameTok.value}" for ${msg.desc}`);
    }
    if (nameTok.type === "[") {
        const name = ctx.reader.readTypeName();
        // Inside google.protobuf.Any, a bracketed name is always a type URL; in any
        // other message it is an extension name.
        if (msg.desc.typeName === "google.protobuf.Any") {
            readExpandedAny(msg, ctx, name, seen);
            return;
        }
        const ext = (_a = ctx.registry) === null || _a === void 0 ? void 0 : _a.getExtension(name);
        if (ext !== undefined && ext.extendee.typeName === msg.desc.typeName) {
            checkSeen(ext, seen);
            readExtensionField(msg, ext, ctx);
            return;
        }
        throw new Error(`unknown extension "[${name}]" for ${msg.desc}`);
    }
    if (nameTok.type === "int") {
        // Like protobuf-go, a field cannot be addressed by number, so the numbered
        // output of printUnknownFields cannot be read back.
        throw new Error(`cannot specify field by number: ${nameTok.text}`);
    }
    throw new Error(`expected a field name, got ${describe(nameTok)}`);
}
// Rejects a repeated occurrence of a singular field, or a second member of the
// same oneof. Repeated and map fields may appear any number of times.
function checkSeen(field, seen) {
    if (field.fieldKind === "list" || field.fieldKind === "map") {
        return;
    }
    if (field.oneof !== undefined) {
        if (seen.oneofs.has(field.oneof)) {
            throw new Error(`oneof "${field.oneof.name}" is already set`);
        }
        seen.oneofs.add(field.oneof);
    }
    if (seen.fields.has(field.number)) {
        const what = field.kind === "extension"
            ? `extension "[${field.typeName}]"`
            : `field "${field.name}"`;
        throw new Error(`non-repeated ${what} is repeated`);
    }
    seen.fields.add(field.number);
}
function readFieldValue(target, field, ctx) {
    // The ":" separator is optional before a message, group, or map value, but
    // required for scalars, enums, and lists of them.
    const hasColon = consumeColon(ctx);
    if (!colonOptional(field) && !hasColon) {
        throw new Error(`expected ":" before value of field "${field.name}"`);
    }
    switch (field.fieldKind) {
        case "scalar":
            target.set(field, readScalarValue(field, field.scalar, ctx));
            break;
        case "enum":
            target.set(field, readEnumValue(field.enum, ctx));
            break;
        case "message": {
            const sub = target.isSet(field)
                ? target.get(field)
                : (0, reflect_js_1.reflect)(field.message);
            readMessageValue(sub, ctx);
            target.set(field, sub);
            break;
        }
        case "list":
            readListField(field, target.get(field), ctx);
            break;
        case "map":
            readMapField(field, target.get(field), ctx);
            break;
    }
}
function readExtensionField(msg, ext, ctx) {
    // Extensions live in the unknown-field set. We read the new value into a
    // container seeded with any existing value, so a repeated extension appends.
    const existing = (0, extensions_js_1.hasExtension)(msg.message, ext)
        ? (0, extensions_js_1.getExtension)(msg.message, ext)
        : undefined;
    const [container, field, get] = (0, extensions_js_1.createExtensionContainer)(ext, existing);
    readFieldValue(container, field, ctx);
    (0, extensions_js_1.setExtension)(msg.message, ext, get());
}
function colonOptional(field) {
    return (field.fieldKind === "message" ||
        field.fieldKind === "map" ||
        (field.fieldKind === "list" && field.listKind === "message"));
}
/**
 * Read a "{ ... }" or "< ... >" block into the given message.
 */
function readMessageValue(msg, ctx) {
    readMessageBody(msg, ctx, readMessageOpen(ctx));
}
function readMessageOpen(ctx) {
    const open = ctx.reader.next();
    if (open.type === "{") {
        return "}";
    }
    if (open.type === "<") {
        return ">";
    }
    throw new Error(`expected "{" or "<", got ${describe(open)}`);
}
/**
 * Read a repeated value: either a single element, or a bracketed list
 * "[ e, e, ... ]". This is the one place the list grammar lives, so list
 * fields, map fields, and the reserved-skip path cannot drift in how they
 * accept (and reject) separators.
 */
function readBracketedList(ctx, readElement) {
    if (ctx.reader.peek().type !== "[") {
        readElement();
        return;
    }
    ctx.reader.next(); // "["
    if (ctx.reader.peek().type === "]") {
        ctx.reader.next();
        return;
    }
    for (;;) {
        readElement();
        const sep = ctx.reader.next();
        if (sep.type === "]") {
            return;
        }
        if (sep.type !== ",") {
            throw new Error(`expected "," or "]" in list, got ${describe(sep)}`);
        }
    }
}
function readListField(field, list, ctx) {
    readBracketedList(ctx, () => list.add(readListItem(field, ctx)));
}
function readListItem(field, ctx) {
    switch (field.listKind) {
        case "scalar":
            return readScalarValue(field, field.scalar, ctx);
        case "enum":
            return readEnumValue(field.enum, ctx);
        case "message": {
            const sub = (0, reflect_js_1.reflect)(field.message);
            readMessageValue(sub, ctx);
            return sub;
        }
    }
}
function readMapField(field, map, ctx) {
    readBracketedList(ctx, () => readMapEntry(field, map, ctx));
}
/**
 * Read a map entry: a "{ key: ... value: ... }" block. A missing key or value
 * defaults to the zero value, like protobuf-go. A duplicate "key" or "value"
 * within one entry is an error; duplicate keys across separate entries are
 * legal, with the last entry winning (handled by the caller's map.set).
 */
function readMapEntry(field, map, ctx) {
    if (++ctx.depth > ctx.recursionLimit) {
        throw new Error(`recursion limit of ${ctx.recursionLimit} reached decoding a map entry`);
    }
    const close = readMessageOpen(ctx);
    let key = (0, scalar_js_1.scalarZeroValue)(field.mapKey, false);
    let value = mapValueZero(field);
    let keySeen = false;
    let valueSeen = false;
    for (;;) {
        const tok = ctx.reader.peek();
        if (tok.type === close) {
            ctx.reader.next();
            break;
        }
        if (tok.type === "eof") {
            throw new Error(`unexpected end of input, expected "${close}"`);
        }
        const nameTok = ctx.reader.next();
        if (nameTok.type !== "identifier") {
            throw new Error(`expected "key" or "value", got ${describe(nameTok)}`);
        }
        if (nameTok.value === "key") {
            if (keySeen) {
                throw new Error('map entry "key" is already set');
            }
            keySeen = true;
            requireColon(ctx);
            key = readScalarValue(field, field.mapKey, ctx);
        }
        else if (nameTok.value === "value") {
            if (valueSeen) {
                throw new Error('map entry "value" is already set');
            }
            valueSeen = true;
            value = readMapValue(field, ctx);
        }
        else {
            throw new Error(`unknown field "${nameTok.value}" in map entry`);
        }
        consumeSeparator(ctx);
    }
    ctx.depth--;
    map.set(key, value);
}
function readMapValue(field, ctx) {
    switch (field.mapKind) {
        case "scalar":
            requireColon(ctx);
            return readScalarValue(field, field.scalar, ctx);
        case "enum":
            requireColon(ctx);
            return readEnumValue(field.enum, ctx);
        case "message": {
            consumeColon(ctx);
            const sub = (0, reflect_js_1.reflect)(field.message);
            readMessageValue(sub, ctx);
            return sub;
        }
    }
}
function mapValueZero(field) {
    switch (field.mapKind) {
        case "scalar":
            return (0, scalar_js_1.scalarZeroValue)(field.scalar, false);
        case "enum":
            return field.enum.values[0].number;
        case "message":
            return (0, reflect_js_1.reflect)(field.message);
    }
}
/**
 * Read `google.protobuf.Any` in its expanded form `[type.url]: { ... }`.
 *
 * The expanded form is mutually exclusive with the raw `type_url` (field 1) and
 * `value` (field 2) fields, and may appear only once. We enforce that through
 * the same seen-set the duplicate-field check uses: the expansion is rejected
 * if either field is already set, and it marks both as set so a following
 * `type_url` or `value` is rejected too.
 */
function readExpandedAny(msg, ctx, typeUrl, seen) {
    var _a;
    if (seen.fields.has(1) || seen.fields.has(2)) {
        throw new Error("google.protobuf.Any cannot mix the expanded form with type_url/value");
    }
    const slash = typeUrl.lastIndexOf("/");
    const typeName = slash >= 0 ? typeUrl.substring(slash + 1) : typeUrl;
    const desc = (_a = ctx.registry) === null || _a === void 0 ? void 0 : _a.getMessage(typeName);
    if (desc === undefined) {
        throw new Error(`unable to resolve "${typeUrl}" for google.protobuf.Any`);
    }
    consumeColon(ctx);
    const unpacked = (0, reflect_js_1.reflect)(desc);
    readMessageValue(unpacked, ctx);
    const any = msg.message;
    // Preserve the exact type URL, including any custom domain prefix.
    any.typeUrl = typeUrl;
    any.value = (0, to_binary_js_1.toBinary)(desc, unpacked.message);
    seen.fields.add(1);
    seen.fields.add(2);
}
// Consume an optional leading "-" sign and report whether one was present.
// This sees a sign token only before a number (the scanner glues a sign onto an
// identifier as in "-inf"), and whitespace and comments between the sign and the
// number are insignificant, so "- 42" means -42, matching protobuf-go.
function consumeSign(ctx) {
    if (ctx.reader.peek().type === "-") {
        ctx.reader.next();
        return true;
    }
    return false;
}
function readScalarValue(field, type, ctx) {
    const negative = consumeSign(ctx);
    const tok = ctx.reader.next();
    switch (type) {
        case descriptors_js_1.ScalarType.STRING:
        case descriptors_js_1.ScalarType.BYTES: {
            if (negative) {
                throw new Error("a string value cannot have a sign");
            }
            if (tok.type !== "string") {
                throw new Error(`expected a string, got ${describe(tok)}`);
            }
            const bytes = concatStrings(tok, ctx);
            if (type === descriptors_js_1.ScalarType.BYTES) {
                return bytes;
            }
            try {
                return (0, text_encoding_js_1.getTextEncoding)().decodeUtf8(bytes, field.utf8Validation);
            }
            catch (_a) {
                throw new Error("invalid UTF-8 in string");
            }
        }
        case descriptors_js_1.ScalarType.BOOL:
            return readBoolValue(tok, negative);
        case descriptors_js_1.ScalarType.FLOAT:
            // Round to 32-bit precision: an out-of-range value becomes ±inf, which is
            // what the text format requires for float overflow.
            return Math.fround(readFloatValue(tok, negative));
        case descriptors_js_1.ScalarType.DOUBLE:
            return readFloatValue(tok, negative);
        case descriptors_js_1.ScalarType.UINT32:
        case descriptors_js_1.ScalarType.FIXED32:
            return Number(readUnsignedInt(tok, negative));
        case descriptors_js_1.ScalarType.UINT64:
        case descriptors_js_1.ScalarType.FIXED64:
            return readUnsignedInt(tok, negative);
        case descriptors_js_1.ScalarType.INT64:
        case descriptors_js_1.ScalarType.SINT64:
        case descriptors_js_1.ScalarType.SFIXED64:
            return readSignedInt(tok, negative);
        default:
            // INT32, SINT32, SFIXED32: the reflect layer range-checks the number.
            return Number(readSignedInt(tok, negative));
    }
}
function readEnumValue(descEnum, ctx) {
    const negative = consumeSign(ctx);
    const tok = ctx.reader.next();
    if (tok.type === "identifier") {
        if (negative) {
            throw new Error(`invalid enum value "-${tok.value}" for ${descEnum}`);
        }
        const value = descEnum.values.find((v) => v.name === tok.value);
        if (value === undefined) {
            throw new Error(`unknown enum value "${tok.value}" for ${descEnum}`);
        }
        return value.number;
    }
    if (tok.type === "int") {
        // The reflect layer validates the number: any int32 for open enums, a known
        // value for closed (proto2) enums.
        return Number(readSignedInt(tok, negative));
    }
    throw new Error(`expected an enum value for ${descEnum}, got ${describe(tok)}`);
}
function readBoolValue(tok, negative) {
    if (!negative) {
        if (tok.type === "identifier") {
            switch (tok.value) {
                case "true":
                case "True":
                case "t":
                    return true;
                case "false":
                case "False":
                case "f":
                    return false;
            }
        }
        if (tok.type === "int") {
            const value = intTokenToBigInt(tok);
            if (value === BigInt(0)) {
                return false;
            }
            if (value === BigInt(1)) {
                return true;
            }
        }
    }
    throw new Error(`expected a bool, got ${negative ? "-" : ""}${describe(tok)}`);
}
function readFloatValue(tok, negative) {
    if (tok.type === "identifier") {
        // A separate "-" token (negative) before a float literal is invalid; the
        // only signed literals are "-inf"/"-infinity", which the scanner glues into
        // one identifier token. "-nan" is not a literal, so it falls through to the
        // error below. This matches protobuf-go's identifier-path sign handling.
        if (negative) {
            throw new Error(`invalid float value "-${tok.value}"`);
        }
        switch (tok.value.toLowerCase()) {
            case "inf":
            case "infinity":
                return Number.POSITIVE_INFINITY;
            case "-inf":
            case "-infinity":
                return Number.NEGATIVE_INFINITY;
            case "nan":
                return Number.NaN;
        }
        throw new Error(`invalid float value "${tok.value}"`);
    }
    if (tok.type === "float") {
        const n = Number(tok.text);
        return negative ? -n : n;
    }
    if (tok.type === "int") {
        // Octal and hexadecimal literals are not valid for float and double fields.
        if (tok.base !== 10) {
            throw new Error("octal and hexadecimal are not valid for a float field");
        }
        const n = Number(tok.text);
        return negative ? -n : n;
    }
    throw new Error(`expected a float, got ${describe(tok)}`);
}
function readSignedInt(tok, negative) {
    if (tok.type !== "int") {
        throw new Error(`expected an integer, got ${describe(tok)}`);
    }
    const value = intTokenToBigInt(tok);
    return negative ? -value : value;
}
function readUnsignedInt(tok, negative) {
    // Reject any sign for an unsigned field, including "-0": the reflect layer
    // would silently accept it as 0.
    if (negative) {
        throw new Error("an unsigned field does not accept a negative value");
    }
    return readSignedInt(tok, false);
}
function intTokenToBigInt(tok) {
    // Octal text keeps its leading "0" (e.g. "0755"), which BigInt would read as
    // decimal, so it needs the "0o" prefix. Hex ("0x...") and decimal text are
    // accepted by BigInt as-is.
    return tok.base === 8
        ? BigInt("0o" + tok.text.substring(1))
        : BigInt(tok.text);
}
// Concatenate adjacent string literals into a single byte string.
function concatStrings(first, ctx) {
    if (ctx.reader.peek().type !== "string") {
        return first.value;
    }
    const parts = [first.value];
    let length = first.value.length;
    while (ctx.reader.peek().type === "string") {
        const tok = ctx.reader.next();
        parts.push(tok.value);
        length += tok.value.length;
    }
    const bytes = new Uint8Array(length);
    let offset = 0;
    for (const part of parts) {
        bytes.set(part, offset);
        offset += part.length;
    }
    return bytes;
}
/**
 * Skip the value of a reserved field. Like every other nested read, the message
 * case is guarded by the recursion limit.
 */
function skipFieldValue(ctx) {
    consumeColon(ctx);
    skipValue(ctx);
}
function skipValue(ctx) {
    readBracketedList(ctx, () => skipSingleValue(ctx));
}
function skipSingleValue(ctx) {
    const tok = ctx.reader.peek();
    if (tok.type === "{" || tok.type === "<") {
        skipMessageBlock(ctx);
        return;
    }
    // A leading sign is consumed leniently here: skipping a reserved value should
    // tolerate "- 5".
    consumeSign(ctx);
    const value = ctx.reader.next();
    if (value.type === "string") {
        while (ctx.reader.peek().type === "string") {
            ctx.reader.next();
        }
        return;
    }
    if (value.type !== "identifier" &&
        value.type !== "int" &&
        value.type !== "float") {
        throw new Error(`expected a value, got ${describe(value)}`);
    }
}
function skipMessageBlock(ctx) {
    if (++ctx.depth > ctx.recursionLimit) {
        throw new Error(`recursion limit of ${ctx.recursionLimit} reached skipping a reserved field`);
    }
    const close = readMessageOpen(ctx);
    for (;;) {
        const tok = ctx.reader.peek();
        if (tok.type === close) {
            ctx.reader.next();
            ctx.depth--;
            return;
        }
        if (tok.type === "eof") {
            throw new Error(`unexpected end of input, expected "${close}"`);
        }
        const nameTok = ctx.reader.next();
        if (nameTok.type === "[") {
            ctx.reader.readTypeName();
        }
        else if (nameTok.type !== "identifier" && nameTok.type !== "int") {
            throw new Error(`expected a field name, got ${describe(nameTok)}`);
        }
        skipFieldValue(ctx);
        consumeSeparator(ctx);
    }
}
// Consume an optional "," or ";" that separates fields or list/map elements.
function consumeSeparator(ctx) {
    const sep = ctx.reader.peek();
    if (sep.type === "," || sep.type === ";") {
        ctx.reader.next();
    }
}
function consumeColon(ctx) {
    if (ctx.reader.peek().type === ":") {
        ctx.reader.next();
        return true;
    }
    return false;
}
function requireColon(ctx) {
    if (!consumeColon(ctx)) {
        throw new Error(`expected ":", got ${describe(ctx.reader.peek())}`);
    }
}
const textFieldCache = new WeakMap();
/**
 * Resolve a field by its text format name, mirroring protobuf-go's ByTextName:
 * group-like fields are addressed by their message type name, with the
 * lowercase form as an alias; JSON names are not in this table.
 */
function fieldByTextName(desc, name) {
    let byText = textFieldCache.get(desc);
    if (byText === undefined) {
        byText = new Map();
        for (const field of desc.fields) {
            if ((0, is_group_like_js_1.isGroupLike)(field)) {
                setOnce(byText, field.message.name, field);
                setOnce(byText, field.message.name.toLowerCase(), field);
            }
            else {
                setOnce(byText, field.name, field);
            }
        }
        textFieldCache.set(desc, byText);
    }
    return byText.get(name);
}
function setOnce(map, key, field) {
    if (!map.has(key)) {
        map.set(key, field);
    }
}
function describe(tok) {
    switch (tok.type) {
        case "identifier":
            return `"${tok.value}"`;
        case "int":
        case "float":
            return `"${tok.text}"`;
        case "string":
            return "a string";
        case "eof":
            return "end of input";
        default:
            return `"${tok.type}"`;
    }
}
