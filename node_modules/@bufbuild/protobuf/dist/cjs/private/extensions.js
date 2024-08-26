"use strict";
// Copyright 2021-2024 Buf Technologies, Inc.
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
exports.filterUnknownFields = exports.createExtensionContainer = exports.makeExtension = void 0;
const scalars_js_1 = require("./scalars.js");
/**
 * Create a new extension using the given runtime.
 */
function makeExtension(runtime, typeName, extendee, field) {
    let fi;
    return {
        typeName,
        extendee,
        get field() {
            if (!fi) {
                const i = (typeof field == "function" ? field() : field);
                i.name = typeName.split(".").pop();
                i.jsonName = `[${typeName}]`;
                fi = runtime.util.newFieldList([i]).list()[0];
            }
            return fi;
        },
        runtime,
    };
}
exports.makeExtension = makeExtension;
/**
 * Create a container that allows us to read extension fields into it with the
 * same logic as regular fields.
 */
function createExtensionContainer(extension) {
    const localName = extension.field.localName;
    const container = Object.create(null);
    container[localName] = initExtensionField(extension);
    return [container, () => container[localName]];
}
exports.createExtensionContainer = createExtensionContainer;
function initExtensionField(ext) {
    const field = ext.field;
    if (field.repeated) {
        return [];
    }
    if (field.default !== undefined) {
        return field.default;
    }
    switch (field.kind) {
        case "enum":
            return field.T.values[0].no;
        case "scalar":
            return (0, scalars_js_1.scalarZeroValue)(field.T, field.L);
        case "message":
            // eslint-disable-next-line no-case-declarations
            const T = field.T, value = new T();
            return T.fieldWrapper ? T.fieldWrapper.unwrapField(value) : value;
        case "map":
            throw "map fields are not allowed to be extensions";
    }
}
/**
 * Helper to filter unknown fields, optimized based on field type.
 */
function filterUnknownFields(unknownFields, field) {
    if (!field.repeated && (field.kind == "enum" || field.kind == "scalar")) {
        // singular scalar fields do not merge, we pick the last
        for (let i = unknownFields.length - 1; i >= 0; --i) {
            if (unknownFields[i].no == field.no) {
                return [unknownFields[i]];
            }
        }
        return [];
    }
    return unknownFields.filter((uf) => uf.no === field.no);
}
exports.filterUnknownFields = filterUnknownFields;
