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
exports.enumDesc = enumDesc;
exports.tsEnum = tsEnum;
exports.objEnum = objEnum;
/**
 * Hydrate an enum descriptor.
 *
 * @private
 */
function enumDesc(file, path, ...paths) {
    if (paths.length == 0) {
        return file.enums[path];
    }
    const e = paths.pop(); // we checked length above
    return paths.reduce((acc, cur) => acc.nestedMessages[cur], file.messages[path]).nestedEnums[e];
}
/**
 * Construct a TypeScript enum object at runtime from a descriptor.
 *
 * The returned object is identical to a transpiled TS enum and includes the
 * reverse mapping, see https://www.typescriptlang.org/docs/handbook/enums.html#reverse-mappings
 */
function tsEnum(desc) {
    const enumObject = {};
    for (const value of desc.values) {
        enumObject[value.localName] = value.number;
        enumObject[value.number] = value.localName;
    }
    return enumObject;
}
/**
 * Construct an object enum at runtime from a descriptor.
 *
 * The returned object is a record of enum value name to integer value. It's
 * a subset of transpiled TS enums - it does not include the reverse mapping,
 * and only supports lookup by value name.
 */
function objEnum(desc) {
    const enumObject = {};
    for (const value of desc.values) {
        enumObject[value.localName] = value.number;
    }
    return enumObject;
}
