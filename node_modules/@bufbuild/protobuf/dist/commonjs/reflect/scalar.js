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
exports.scalarEquals = scalarEquals;
exports.scalarZeroValue = scalarZeroValue;
exports.isScalarZeroValue = isScalarZeroValue;
const proto_int64_js_1 = require("../proto-int64.js");
const descriptors_js_1 = require("../descriptors.js");
/**
 * Returns true if both scalar values are equal.
 *
 * For float and double, values are compared following IEEE semantics: -0
 * equals 0, and NaN does not equal NaN. This is value equality, not identity.
 *
 * It deliberately differs from isScalarZeroValue, which treats -0 as distinct
 * from 0. A value can equal the zero value without being a zero value
 * (scalarEquals(DOUBLE, -0, 0) is true while isScalarZeroValue(DOUBLE, -0) is
 * false) so this function must not be used to derive implicit presence.
 */
function scalarEquals(type, a, b) {
    if (a === b) {
        // This correctly matches equal values except BYTES and (possibly) 64-bit integers.
        return true;
    }
    // Special case BYTES - we need to compare each byte individually
    if (type == descriptors_js_1.ScalarType.BYTES) {
        if (!(a instanceof Uint8Array) || !(b instanceof Uint8Array)) {
            return false;
        }
        if (a.length !== b.length) {
            return false;
        }
        for (let i = 0; i < a.length; i++) {
            if (a[i] !== b[i]) {
                return false;
            }
        }
        return true;
    }
    // Special case 64-bit integers - we support number, string and bigint representation.
    switch (type) {
        case descriptors_js_1.ScalarType.UINT64:
        case descriptors_js_1.ScalarType.FIXED64:
        case descriptors_js_1.ScalarType.INT64:
        case descriptors_js_1.ScalarType.SFIXED64:
        case descriptors_js_1.ScalarType.SINT64:
            // Loose comparison will match between 0n, 0 and "0".
            return a == b;
    }
    // Anything that hasn't been caught by strict comparison or special cased
    // BYTES and 64-bit integers is not equal.
    return false;
}
/**
 * Returns the zero value for the given scalar type, the value a field of this
 * type has when unset: 0 for numeric types, "" for strings, false for
 * booleans, and an empty Uint8Array for bytes. For 64-bit integer types, the
 * result is "0" when longAsString is true, otherwise 0n.
 *
 * This is the type's zero value, not a proto2 custom field default. For float
 * and double it is +0; isScalarZeroValue treats only +0, not -0, as this value.
 */
function scalarZeroValue(type, longAsString) {
    switch (type) {
        case descriptors_js_1.ScalarType.STRING:
            return "";
        case descriptors_js_1.ScalarType.BOOL:
            return false;
        case descriptors_js_1.ScalarType.DOUBLE:
        case descriptors_js_1.ScalarType.FLOAT:
            return 0.0;
        case descriptors_js_1.ScalarType.INT64:
        case descriptors_js_1.ScalarType.UINT64:
        case descriptors_js_1.ScalarType.SFIXED64:
        case descriptors_js_1.ScalarType.FIXED64:
        case descriptors_js_1.ScalarType.SINT64:
            return (longAsString ? "0" : proto_int64_js_1.protoInt64.zero);
        case descriptors_js_1.ScalarType.BYTES:
            return new Uint8Array(0);
        default:
            // Handles INT32, UINT32, SINT32, FIXED32, SFIXED32.
            // We do not use individual cases to save a few bytes code size.
            return 0;
    }
}
/**
 * Returns true if the value is the zero value for the given scalar type: `0`
 * for numeric types, `false` for booleans, `""` for strings, and an empty
 * Uint8Array for bytes.
 *
 * This is the implicit-presence default check. A singular field with implicit
 * presence is treated as unset, and omitted from the wire, when its value is
 * the zero value. With explicit presence, or in repeated and map fields,
 * presence is structural and this function does not apply.
 *
 * Note that -0 is NOT a zero value for float and double: under implicit
 * presence, +0 is omitted from the wire but -0 is written, following the
 * proto3 specification. As a result this can disagree with scalarEquals, which
 * compares by value and treats -0 as equal to 0.
 */
function isScalarZeroValue(type, value) {
    switch (type) {
        case descriptors_js_1.ScalarType.BOOL:
            return value === false;
        case descriptors_js_1.ScalarType.STRING:
            return value === "";
        case descriptors_js_1.ScalarType.BYTES:
            return value instanceof Uint8Array && !value.byteLength;
        case descriptors_js_1.ScalarType.DOUBLE:
        case descriptors_js_1.ScalarType.FLOAT:
            // Object.is distinguishes -0 from 0.
            return Object.is(value, 0);
        default:
            // Loose comparison matches 0n, 0 and "0".
            return value == 0;
    }
}
