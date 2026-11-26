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
exports.configureTextEncoding = configureTextEncoding;
exports.getTextEncoding = getTextEncoding;
exports.emulateEncodeInto = emulateEncodeInto;
let te;
/**
 * Protobuf-ES requires the Text Encoding API to convert UTF-8 from and to
 * binary. This WHATWG API is widely available, but it is not part of the
 * ECMAScript standard. This function used to be the way to supply an
 * implementation on runtimes that do not provide one.
 *
 * It only configures the copy of the library it is imported from. To provide
 * the encoding API for every copy of Protobuf-ES in an isolate, install
 * `TextEncoder` (with the methods `encode` and optionally `encodeInto`) and
 * `TextDecoder` (with the `fatal: true` constructor argument and the `decode`
 * method) on `globalThis`.
 *
 * Providing `encodeUtf8Into` is optional for backwards compatibility. If it
 * is omitted, we emulate it with a wrapper that calls `encodeUtf8`.
 *
 * Note that the Text Encoding API does not provide a way to validate UTF-8.
 * Our implementation uses String.prototype.isWellFormed, and falls back
 * to use encodeURIComponent().
 *
 * @deprecated Install `TextEncoder` and `TextDecoder` on `globalThis` instead.
 */
function configureTextEncoding(textEncoding) {
    var _a;
    te = Object.assign(Object.assign({}, textEncoding), { encodeUtf8Into: (_a = textEncoding.encodeUtf8Into) !== null && _a !== void 0 ? _a : emulateEncodeInto(textEncoding.encodeUtf8.bind(textEncoding)) });
}
function getTextEncoding() {
    if (!te) {
        const globals = globalThis;
        if (!globals.TextEncoder || !globals.TextDecoder) {
            throw new Error("encoding API missing: install TextEncoder and TextDecoder on globalThis");
        }
        const textEncoder = new globals.TextEncoder();
        const textDecoder = new globals.TextDecoder();
        let textDecoderStrict;
        const config = {
            encodeUtf8(text) {
                return textEncoder.encode(text);
            },
            decodeUtf8(bytes, strict) {
                if (strict) {
                    if (!textDecoderStrict) {
                        textDecoderStrict = new globals.TextDecoder("utf-8", {
                            fatal: true,
                        });
                    }
                    return textDecoderStrict.decode(bytes);
                }
                return textDecoder.decode(bytes);
            },
            checkUtf8(text) {
                try {
                    encodeURIComponent(text);
                    return true;
                }
                catch (_) {
                    return false;
                }
            },
        };
        // If encodeInto is available, use it. Otherwise, configureTextEncoding
        // fills in a slower fallback that uses encodeUtf8.
        if (textEncoder.encodeInto) {
            config.encodeUtf8Into = textEncoder.encodeInto.bind(textEncoder);
        }
        // Native String.prototype.isWellFormed, if the runtime provides it.
        const nativeStringIsWellFormed = String.prototype.isWellFormed;
        if (nativeStringIsWellFormed) {
            config.checkUtf8 = (text) => {
                return nativeStringIsWellFormed.call(text);
            };
        }
        configureTextEncoding(config);
    }
    return te;
}
/**
 * Simplistic polyfill for encodeUtf8Into.
 *
 * @private
 */
function emulateEncodeInto(encodeUtf8) {
    return (text, dest) => {
        const bytes = encodeUtf8(text);
        dest.set(bytes);
        return { written: bytes.byteLength };
    };
}
