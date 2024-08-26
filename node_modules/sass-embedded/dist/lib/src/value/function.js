"use strict";
// Copyright 2021 Google LLC. Use of this source code is governed by an
// MIT-style license that can be found in the LICENSE file or at
// https://opensource.org/licenses/MIT.
Object.defineProperty(exports, "__esModule", { value: true });
exports.SassFunction = void 0;
const immutable_1 = require("immutable");
const index_1 = require("./index");
/** A first-class SassScript function. */
class SassFunction extends index_1.Value {
    constructor(idOrSignature, callback) {
        super();
        if (typeof idOrSignature === 'number') {
            this.id = idOrSignature;
        }
        else {
            this.signature = idOrSignature;
            this.callback = callback;
        }
    }
    equals(other) {
        return this.id === undefined
            ? other === this
            : other instanceof SassFunction && other.id === this.id;
    }
    hashCode() {
        return this.id === undefined ? (0, immutable_1.hash)(this.signature) : (0, immutable_1.hash)(this.id);
    }
    toString() {
        return this.signature ? this.signature : `<compiler function ${this.id}>`;
    }
}
exports.SassFunction = SassFunction;
//# sourceMappingURL=function.js.map