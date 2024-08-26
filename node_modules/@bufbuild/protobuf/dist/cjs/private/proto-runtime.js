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
exports.makeProtoRuntime = void 0;
const enum_js_1 = require("./enum.js");
const message_type_js_1 = require("./message-type.js");
const extensions_js_1 = require("./extensions.js");
const json_format_js_1 = require("./json-format.js");
const binary_format_js_1 = require("./binary-format.js");
const util_common_js_1 = require("./util-common.js");
function makeProtoRuntime(syntax, newFieldList, initFields) {
    return {
        syntax,
        json: (0, json_format_js_1.makeJsonFormat)(),
        bin: (0, binary_format_js_1.makeBinaryFormat)(),
        util: Object.assign(Object.assign({}, (0, util_common_js_1.makeUtilCommon)()), { newFieldList,
            initFields }),
        makeMessageType(typeName, fields, opt) {
            return (0, message_type_js_1.makeMessageType)(this, typeName, fields, opt);
        },
        makeEnum: enum_js_1.makeEnum,
        makeEnumType: enum_js_1.makeEnumType,
        getEnumType: enum_js_1.getEnumType,
        makeExtension(typeName, extendee, field) {
            return (0, extensions_js_1.makeExtension)(this, typeName, extendee, field);
        },
    };
}
exports.makeProtoRuntime = makeProtoRuntime;
