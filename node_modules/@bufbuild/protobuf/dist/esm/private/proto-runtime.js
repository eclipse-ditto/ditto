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
import { getEnumType, makeEnum, makeEnumType } from "./enum.js";
import { makeMessageType } from "./message-type.js";
import { makeExtension } from "./extensions.js";
import { makeJsonFormat } from "./json-format.js";
import { makeBinaryFormat } from "./binary-format.js";
import { makeUtilCommon } from "./util-common.js";
export function makeProtoRuntime(syntax, newFieldList, initFields) {
    return {
        syntax,
        json: makeJsonFormat(),
        bin: makeBinaryFormat(),
        util: Object.assign(Object.assign({}, makeUtilCommon()), { newFieldList,
            initFields }),
        makeMessageType(typeName, fields, opt) {
            return makeMessageType(this, typeName, fields, opt);
        },
        makeEnum,
        makeEnumType,
        getEnumType,
        makeExtension(typeName, extendee, field) {
            return makeExtension(this, typeName, extendee, field);
        },
    };
}
