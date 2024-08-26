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
exports.clearField = exports.isFieldSet = void 0;
const scalars_js_1 = require("./scalars.js");
/**
 * Returns true if the field is set.
 */
function isFieldSet(field, target) {
    const localName = field.localName;
    if (field.repeated) {
        return target[localName].length > 0;
    }
    if (field.oneof) {
        return target[field.oneof.localName].case === localName; // eslint-disable-line @typescript-eslint/no-unsafe-member-access
    }
    switch (field.kind) {
        case "enum":
        case "scalar":
            if (field.opt || field.req) {
                // explicit presence
                return target[localName] !== undefined;
            }
            // implicit presence
            if (field.kind == "enum") {
                return target[localName] !== field.T.values[0].no;
            }
            return !(0, scalars_js_1.isScalarZeroValue)(field.T, target[localName]);
        case "message":
            return target[localName] !== undefined;
        case "map":
            return Object.keys(target[localName]).length > 0; // eslint-disable-line @typescript-eslint/no-unsafe-argument
    }
}
exports.isFieldSet = isFieldSet;
/**
 * Resets the field, so that isFieldSet() will return false.
 */
function clearField(field, target) {
    const localName = field.localName;
    const implicitPresence = !field.opt && !field.req;
    if (field.repeated) {
        target[localName] = [];
    }
    else if (field.oneof) {
        target[field.oneof.localName] = { case: undefined };
    }
    else {
        switch (field.kind) {
            case "map":
                target[localName] = {};
                break;
            case "enum":
                target[localName] = implicitPresence ? field.T.values[0].no : undefined;
                break;
            case "scalar":
                target[localName] = implicitPresence
                    ? (0, scalars_js_1.scalarZeroValue)(field.T, field.L)
                    : undefined;
                break;
            case "message":
                target[localName] = undefined;
                break;
        }
    }
}
exports.clearField = clearField;
