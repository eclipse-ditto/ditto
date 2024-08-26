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
exports.createRegistry = void 0;
/**
 * Create a new registry from the given types.
 */
function createRegistry(...types) {
    const messages = {};
    const enums = {};
    const services = {};
    const extensionsByName = new Map();
    const extensionsByExtendee = new Map();
    const registry = {
        findMessage(typeName) {
            return messages[typeName];
        },
        findEnum(typeName) {
            return enums[typeName];
        },
        findService(typeName) {
            return services[typeName];
        },
        findExtensionFor(typeName, no) {
            var _a, _b;
            return (_b = (_a = extensionsByExtendee.get(typeName)) === null || _a === void 0 ? void 0 : _a.get(no)) !== null && _b !== void 0 ? _b : undefined;
        },
        findExtension(typeName) {
            var _a;
            return (_a = extensionsByName.get(typeName)) !== null && _a !== void 0 ? _a : undefined;
        },
    };
    function addType(type) {
        var _a;
        if ("fields" in type) {
            if (!registry.findMessage(type.typeName)) {
                messages[type.typeName] = type;
                type.fields.list().forEach(addField);
            }
        }
        else if ("methods" in type) {
            if (!registry.findService(type.typeName)) {
                services[type.typeName] = type;
                for (const method of Object.values(type.methods)) {
                    addType(method.I);
                    addType(method.O);
                }
            }
        }
        else if ("extendee" in type) {
            if (!extensionsByName.has(type.typeName)) {
                extensionsByName.set(type.typeName, type);
                const extendeeName = type.extendee.typeName;
                if (!extensionsByExtendee.has(extendeeName)) {
                    extensionsByExtendee.set(extendeeName, new Map());
                }
                (_a = extensionsByExtendee.get(extendeeName)) === null || _a === void 0 ? void 0 : _a.set(type.field.no, type);
                addType(type.extendee);
                addField(type.field);
            }
        }
        else {
            enums[type.typeName] = type;
        }
    }
    function addField(field) {
        if (field.kind == "message") {
            addType(field.T);
        }
        else if (field.kind == "map" && field.V.kind == "message") {
            addType(field.V.T);
        }
        else if (field.kind == "enum") {
            addType(field.T);
        }
    }
    for (const type of types) {
        addType(type);
    }
    return registry;
}
exports.createRegistry = createRegistry;
