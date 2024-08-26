"use strict";
// Copyright 2021 Google LLC. Use of this source code is governed by an
// MIT-style license that can be found in the LICENSE file or at
// https://opensource.org/licenses/MIT.
Object.defineProperty(exports, "__esModule", { value: true });
exports.SassMixin = void 0;
const immutable_1 = require("immutable");
const index_1 = require("./index");
/** A first-class SassScript mixin. */
class SassMixin extends index_1.Value {
    constructor(id) {
        super();
        this.id = id;
    }
    equals(other) {
        return other instanceof SassMixin && other.id === this.id;
    }
    hashCode() {
        return (0, immutable_1.hash)(this.id);
    }
    toString() {
        return `<compiler mixin ${this.id}>`;
    }
    assertMixin() {
        return this;
    }
}
exports.SassMixin = SassMixin;
//# sourceMappingURL=mixin.js.map