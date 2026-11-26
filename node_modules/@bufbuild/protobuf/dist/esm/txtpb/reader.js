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
import { getTextEncoding } from "../wire/text-encoding.js";
const tokenEof = { type: "eof" };
/**
 * A tokenizer for the protobuf text format.
 *
 * The parser drives it with `peek()` and `next()` (one-token lookahead) and,
 * once it knows it is in a field-name position, asks for the contents of a
 * bracketed name with `readTypeName()` — the `[...]` syntax for extensions and
 * Any type URLs is ambiguous with the list syntax at the lexical level. The
 * structure is modeled on the graphql-js lexer: a single scan position and a
 * per-token reader that returns the decoded value.
 */
export class Reader {
    constructor(input) {
        this.pos = 0;
        // A leading byte-order mark is insignificant; skip it like protobuf-go's
        // tokenizer does.
        this.input = input.charCodeAt(0) === 0xfeff ? input.slice(1) : input;
        this.length = this.input.length;
    }
    /**
     * Return the next token without consuming it.
     */
    peek() {
        if (this.lookahead === undefined) {
            this.lookahead = this.scan();
        }
        return this.lookahead;
    }
    /**
     * Consume and return the next token.
     */
    next() {
        const tok = this.peek();
        this.lookahead = undefined;
        return tok;
    }
    /**
     * Read the contents of a bracketed name, used for extension fields and the
     * expanded form of google.protobuf.Any. The opening `[` must already have
     * been consumed with `next()`. Whitespace and comments inside the brackets
     * are insignificant. Returns the inner name with the brackets removed, e.g.
     * "pkg.Message.field" or "type.googleapis.com/pkg.Message".
     *
     * The text format grammar for this is incomplete, so we follow protobuf-go's
     * parseTypeName: the prefix may contain URL characters, `/` separators, and
     * well-formed percent-escapes, and the type name after the last `/` must be a
     * dotted identifier.
     */
    readTypeName() {
        let name = "";
        for (;;) {
            this.skipSpace();
            const c = this.charAt(this.pos);
            if (c === undefined) {
                throw new Error("unterminated [...] name");
            }
            if (c === "]") {
                this.pos++;
                break;
            }
            if (c === "/") {
                name += "/";
                this.pos++;
            }
            else if (c === "%") {
                if (!isHexDigit(this.charAt(this.pos + 1)) ||
                    !isHexDigit(this.charAt(this.pos + 2))) {
                    throw new Error("invalid percent-escape in [...] name");
                }
                name += this.input.substring(this.pos, this.pos + 3);
                this.pos += 3;
            }
            else if (isUrlChar(c)) {
                name += c;
                this.pos++;
            }
            else {
                throw new Error(`unexpected ${quoteChar(c)} in [...] name`);
            }
        }
        validateTypeName(name);
        return name;
    }
    scan() {
        this.skipSpace();
        const c = this.charAt(this.pos);
        if (c === undefined) {
            return tokenEof;
        }
        switch (c) {
            case "{":
            case "}":
            case "<":
            case ">":
            case "[":
            case "]":
            case ":":
            case ",":
            case ";":
                this.pos++;
                return { type: c };
            case "-":
                // A "-" glued to a letter begins a negative identifier (-inf or
                // -infinity); otherwise it is a sign token. A number may have whitespace
                // between the sign and the digits, so the sign is a separate token the
                // parser reassembles; a float literal may not, matching protobuf-go,
                // where inf/infinity parse through the identifier path with the sign
                // glued (so "- inf" is an error but "- 42" is -42).
                if (isLetter(this.charAt(this.pos + 1))) {
                    return this.scanIdentifier();
                }
                this.pos++;
                return { type: "-" };
            case '"':
            case "'":
                return this.scanString(c);
        }
        if (isDigit(c)) {
            return this.scanNumber();
        }
        if (c === "." && isDigit(this.charAt(this.pos + 1))) {
            return this.scanNumber();
        }
        if (isLetter(c)) {
            return this.scanIdentifier();
        }
        throw new Error(`unexpected ${quoteChar(c)}`);
    }
    skipSpace() {
        for (;;) {
            const c = this.input[this.pos];
            if (c === " " ||
                c === "\t" ||
                c === "\n" ||
                c === "\r" ||
                c === "\v" ||
                c === "\f") {
                this.pos++;
            }
            else if (c === "#") {
                // A comment runs to the end of the line.
                this.pos++;
                while (this.pos < this.length && this.input[this.pos] !== "\n") {
                    this.pos++;
                }
            }
            else {
                return;
            }
        }
    }
    scanIdentifier() {
        const start = this.pos;
        if (this.charAt(this.pos) === "-") {
            this.pos++; // a glued negative identifier such as -inf
        }
        this.pos++; // the first letter (the caller guarantees one is present)
        while (isLetterOrDigit(this.charAt(this.pos))) {
            this.pos++;
        }
        return { type: "identifier", value: this.input.substring(start, this.pos) };
    }
    /**
     * Scan a numeric literal. The sign is a separate token, so a number never
     * starts with `-`. The literal must end at a delimiter, so `10f` is a float
     * but `10bar`, `1.2.3`, `09`, and `0xZ` are errors.
     */
    scanNumber() {
        var _a, _b, _c, _d;
        const start = this.pos;
        if (this.input[this.pos] === "0" &&
            /[xX]/.test((_a = this.charAt(this.pos + 1)) !== null && _a !== void 0 ? _a : "")) {
            // Hexadecimal: `0x` followed by one or more hex digits.
            this.pos += 2;
            const digits = this.pos;
            while (isHexDigit(this.charAt(this.pos))) {
                this.pos++;
            }
            if (this.pos === digits) {
                throw new Error("invalid hexadecimal literal");
            }
            this.expectDelimiter();
            return {
                type: "int",
                text: this.input.substring(start, this.pos),
                base: 16,
            };
        }
        if (this.input[this.pos] === "0" &&
            isOctalDigit(this.charAt(this.pos + 1))) {
            // Octal: a leading `0` followed by octal digits. A subsequent non-octal
            // digit (as in `078`) ends the run, and the delimiter check rejects it.
            this.pos++;
            while (isOctalDigit(this.charAt(this.pos))) {
                this.pos++;
            }
            this.expectDelimiter();
            return {
                type: "int",
                text: this.input.substring(start, this.pos),
                base: 8,
            };
        }
        // A decimal integer or a floating point literal. A leading "0" stands
        // alone (octal and hex were handled above), so the delimiter check below
        // rejects a following digit — `08` and `09` are malformed, not decimal.
        let isFloat = false;
        if (this.charAt(this.pos) === "0") {
            this.pos++;
        }
        else {
            while (isDigit(this.charAt(this.pos))) {
                this.pos++;
            }
        }
        if (this.charAt(this.pos) === ".") {
            isFloat = true;
            this.pos++;
            while (isDigit(this.charAt(this.pos))) {
                this.pos++;
            }
        }
        if (/[eE]/.test((_b = this.charAt(this.pos)) !== null && _b !== void 0 ? _b : "")) {
            isFloat = true;
            this.pos++;
            if (/[+-]/.test((_c = this.charAt(this.pos)) !== null && _c !== void 0 ? _c : "")) {
                this.pos++;
            }
            const digits = this.pos;
            while (isDigit(this.charAt(this.pos))) {
                this.pos++;
            }
            if (this.pos === digits) {
                throw new Error("invalid exponent");
            }
        }
        // A trailing `f`/`F` marks a float and is not part of the value passed to
        // Number(); capture the end before consuming it so it stays out of the text.
        const end = this.pos;
        if (/[fF]/.test((_d = this.charAt(this.pos)) !== null && _d !== void 0 ? _d : "")) {
            isFloat = true;
            this.pos++;
        }
        this.expectDelimiter();
        const text = this.input.substring(start, end);
        return isFloat ? { type: "float", text } : { type: "int", text, base: 10 };
    }
    // A number must be terminated by a delimiter — any character that cannot
    // continue a name or number. This rejects `09`, `0xZ`, `1.2.3`, and `5bar`.
    expectDelimiter() {
        const c = this.charAt(this.pos);
        if (c !== undefined &&
            (isLetterOrDigit(c) || c === "-" || c === "+" || c === ".")) {
            throw new Error("invalid number");
        }
    }
    scanString(quote) {
        this.pos++; // opening quote
        const bytes = [];
        // Literal characters are accumulated as a run and encoded as UTF-8 in one
        // batch when the run ends (at an escape or the closing quote). Escapes
        // contribute their bytes directly.
        let runStart = this.pos;
        for (;;) {
            const c = this.charAt(this.pos);
            if (c === undefined) {
                throw new Error("unterminated string");
            }
            if (c === quote) {
                pushUtf8(bytes, this.input.substring(runStart, this.pos));
                this.pos++; // closing quote
                return { type: "string", value: new Uint8Array(bytes) };
            }
            if (c === "\\") {
                pushUtf8(bytes, this.input.substring(runStart, this.pos));
                this.pos++; // backslash
                this.scanEscape(bytes);
                runStart = this.pos;
                continue;
            }
            // A raw newline or NUL is not allowed in a string, matching protobuf-go.
            if (c === "\n" || c === "\0") {
                throw new Error(`invalid ${quoteChar(c)} in string`);
            }
            this.pos++;
        }
    }
    scanEscape(bytes) {
        const c = this.charAt(this.pos);
        if (c === undefined) {
            throw new Error("unterminated escape sequence");
        }
        switch (c) {
            case '"':
            case "'":
            case "\\":
            case "?":
                bytes.push(c.charCodeAt(0));
                this.pos++;
                return;
            case "a":
                bytes.push(0x07);
                this.pos++;
                return;
            case "b":
                bytes.push(0x08);
                this.pos++;
                return;
            case "f":
                bytes.push(0x0c);
                this.pos++;
                return;
            case "n":
                bytes.push(0x0a);
                this.pos++;
                return;
            case "r":
                bytes.push(0x0d);
                this.pos++;
                return;
            case "t":
                bytes.push(0x09);
                this.pos++;
                return;
            case "v":
                bytes.push(0x0b);
                this.pos++;
                return;
            case "x": {
                this.pos++;
                const hex = this.takeWhile(isHexDigit, 2);
                if (hex.length === 0) {
                    throw new Error("invalid hex escape \\x");
                }
                bytes.push(parseInt(hex, 16));
                return;
            }
            case "u":
            case "U": {
                this.pos++;
                const width = c === "u" ? 4 : 8;
                const hex = this.takeWhile(isHexDigit, width);
                if (hex.length !== width) {
                    throw new Error(`invalid unicode escape \\${c}`);
                }
                const code = parseInt(hex, 16);
                // Reject surrogate code points and values beyond U+10FFFF. We
                // deliberately do NOT combine an adjacent `\u` low surrogate into a
                // pair the way protobuf-go does: the conformance suite (the
                // StringLiteral*Surrogate* cases) requires every surrogate escape, lone
                // or paired, to be a parse error. Do not "fix" this toward Go.
                if (code > 0x10ffff || (code >= 0xd800 && code <= 0xdfff)) {
                    throw new Error(`invalid unicode escape \\${c}${hex}`);
                }
                pushUtf8(bytes, String.fromCodePoint(code));
                return;
            }
            default:
                if (isOctalDigit(c)) {
                    const oct = this.takeWhile(isOctalDigit, 3);
                    const value = parseInt(oct, 8);
                    if (value > 0xff) {
                        throw new Error(`octal escape \\${oct} out of range`);
                    }
                    bytes.push(value);
                    return;
                }
                throw new Error(`invalid escape \\${c}`);
        }
    }
    // Consume up to `max` consecutive characters matching `pred` and return them.
    takeWhile(pred, max) {
        const start = this.pos;
        while (this.pos < start + max && pred(this.charAt(this.pos))) {
            this.pos++;
        }
        return this.input.substring(start, this.pos);
    }
    charAt(index) {
        return index < this.length ? this.input[index] : undefined;
    }
}
/**
 * Validate a type name (and URL prefix) from a bracketed name, mirroring
 * protobuf-go's parseTypeName. The type name is everything after the last `/`;
 * the prefix before it is a URL that may carry extra characters and
 * percent-escapes, but must not begin with `/`.
 */
function validateTypeName(name) {
    const lastSlash = name.lastIndexOf("/");
    if (lastSlash >= 0 && name[0] === "/") {
        throw new Error("invalid type name: empty URL host");
    }
    const typeName = name.substring(lastSlash + 1);
    if (typeName.length === 0) {
        throw new Error("invalid type name: empty");
    }
    for (const part of typeName.split(".")) {
        if (part.length === 0) {
            throw new Error("invalid type name: empty component");
        }
    }
    for (const c of typeName) {
        if (!(isLetterOrDigit(c) || c === "." || c === "-")) {
            throw new Error(`unexpected ${quoteChar(c)} in type name`);
        }
    }
}
function isDigit(c) {
    return c !== undefined && c >= "0" && c <= "9";
}
function isOctalDigit(c) {
    return c !== undefined && c >= "0" && c <= "7";
}
function isHexDigit(c) {
    return (c !== undefined &&
        ((c >= "0" && c <= "9") || (c >= "a" && c <= "f") || (c >= "A" && c <= "F")));
}
function isLetter(c) {
    return (c !== undefined &&
        ((c >= "a" && c <= "z") || (c >= "A" && c <= "Z") || c === "_"));
}
function isLetterOrDigit(c) {
    return isLetter(c) || isDigit(c);
}
/**
 * A character permitted in the URL prefix of an Any type name, matching
 * protobuf-go's isUrlChar plus the type-name characters.
 */
function isUrlChar(c) {
    return (isLetterOrDigit(c) ||
        c === "-" ||
        c === "." ||
        c === "~" ||
        c === "!" ||
        c === "$" ||
        c === "&" ||
        c === "(" ||
        c === ")" ||
        c === "*" ||
        c === "+" ||
        c === "," ||
        c === ";" ||
        c === "=");
}
function quoteChar(c) {
    var _a;
    const code = (_a = c.codePointAt(0)) !== null && _a !== void 0 ? _a : 0;
    return code >= 0x20 && code <= 0x7e
        ? `"${c}"`
        : `U+${code.toString(16).toUpperCase().padStart(4, "0")}`;
}
// Append the UTF-8 encoding of `text` to `out`, using the same Text Encoding
// API as the rest of the library. Unpaired surrogates become U+FFFD (the
// standard TextEncoder behavior), matching binary serialization.
function pushUtf8(out, text) {
    if (text.length === 0) {
        return;
    }
    for (const byte of getTextEncoding().encodeUtf8(text)) {
        out.push(byte);
    }
}
