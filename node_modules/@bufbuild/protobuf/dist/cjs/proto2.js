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
exports.proto2 = void 0;
const proto_runtime_js_1 = require("./private/proto-runtime.js");
const field_list_js_1 = require("./private/field-list.js");
const field_normalize_js_1 = require("./private/field-normalize.js");
/**
 * Provides functionality for messages defined with the proto2 syntax.
 */
exports.proto2 = (0, proto_runtime_js_1.makeProtoRuntime)("proto2", (fields) => {
    return new field_list_js_1.InternalFieldList(fields, (source) => (0, field_normalize_js_1.normalizeFieldInfos)(source, false));
}, 
// TODO merge with proto3 and initExtensionField, also see initPartial, equals, clone
(target) => {
    for (const member of target.getType().fields.byMember()) {
        const name = member.localName, t = target;
        if (member.repeated) {
            t[name] = [];
            continue;
        }
        switch (member.kind) {
            case "oneof":
                t[name] = { case: undefined };
                break;
            case "map":
                t[name] = {};
                break;
            case "scalar":
            case "enum":
            case "message":
                // In contrast to proto3, enum and scalar fields have no intrinsic default value,
                // only an optional explicit default value.
                // Unlike proto3 intrinsic default values, proto2 explicit default values are not
                // set on construction, because they are not omitted on the wire. If we did set
                // default values on construction, a deserialize-serialize round-trip would add
                // fields to a message.
                break;
        }
    }
});
