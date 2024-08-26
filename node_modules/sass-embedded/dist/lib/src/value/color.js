"use strict";
// Copyright 2021 Google Inc. Use of this source code is governed by an
// MIT-style license that can be found in the LICENSE file or at
// https://opensource.org/licenses/MIT.
Object.defineProperty(exports, "__esModule", { value: true });
exports.SassColor = void 0;
const index_1 = require("./index");
const utils_1 = require("./utils");
const immutable_1 = require("immutable");
/** A SassScript color. */
class SassColor extends index_1.Value {
    constructor(color) {
        super();
        if ('red' in color) {
            this.redInternal = (0, utils_1.fuzzyAssertInRange)(Math.round(color.red), 0, 255, 'red');
            this.greenInternal = (0, utils_1.fuzzyAssertInRange)(Math.round(color.green), 0, 255, 'green');
            this.blueInternal = (0, utils_1.fuzzyAssertInRange)(Math.round(color.blue), 0, 255, 'blue');
        }
        else if ('saturation' in color) {
            this.hueInternal = (0, utils_1.positiveMod)(color.hue, 360);
            this.saturationInternal = (0, utils_1.fuzzyAssertInRange)(color.saturation, 0, 100, 'saturation');
            this.lightnessInternal = (0, utils_1.fuzzyAssertInRange)(color.lightness, 0, 100, 'lightness');
        }
        else {
            // From https://www.w3.org/TR/css-color-4/#hwb-to-rgb
            const scaledHue = (0, utils_1.positiveMod)(color.hue, 360) / 360;
            let scaledWhiteness = (0, utils_1.fuzzyAssertInRange)(color.whiteness, 0, 100, 'whiteness') / 100;
            let scaledBlackness = (0, utils_1.fuzzyAssertInRange)(color.blackness, 0, 100, 'blackness') / 100;
            const sum = scaledWhiteness + scaledBlackness;
            if (sum > 1) {
                scaledWhiteness /= sum;
                scaledBlackness /= sum;
            }
            // Because HWB is (currently) used much less frequently than HSL or RGB, we
            // don't cache its values because we expect the memory overhead of doing so
            // to outweigh the cost of recalculating it on access. Instead, we eagerly
            // convert it to RGB and then convert back if necessary.
            this.redInternal = hwbToRgb(scaledHue + 1 / 3, scaledWhiteness, scaledBlackness);
            this.greenInternal = hwbToRgb(scaledHue, scaledWhiteness, scaledBlackness);
            this.blueInternal = hwbToRgb(scaledHue - 1 / 3, scaledWhiteness, scaledBlackness);
        }
        this.alphaInternal =
            color.alpha === undefined
                ? 1
                : (0, utils_1.fuzzyAssertInRange)(color.alpha, 0, 1, 'alpha');
    }
    /** `this`'s red channel. */
    get red() {
        if (this.redInternal === undefined) {
            this.hslToRgb();
        }
        return this.redInternal;
    }
    /** `this`'s blue channel. */
    get blue() {
        if (this.blueInternal === undefined) {
            this.hslToRgb();
        }
        return this.blueInternal;
    }
    /** `this`'s green channel. */
    get green() {
        if (this.greenInternal === undefined) {
            this.hslToRgb();
        }
        return this.greenInternal;
    }
    /** `this`'s hue value. */
    get hue() {
        if (this.hueInternal === undefined) {
            this.rgbToHsl();
        }
        return this.hueInternal;
    }
    /** `this`'s saturation value. */
    get saturation() {
        if (this.saturationInternal === undefined) {
            this.rgbToHsl();
        }
        return this.saturationInternal;
    }
    /** `this`'s hue value. */
    get lightness() {
        if (this.lightnessInternal === undefined) {
            this.rgbToHsl();
        }
        return this.lightnessInternal;
    }
    /** `this`'s whiteness value. */
    get whiteness() {
        // Because HWB is (currently) used much less frequently than HSL or RGB, we
        // don't cache its values because we expect the memory overhead of doing so
        // to outweigh the cost of recalculating it on access.
        return (Math.min(this.red, this.green, this.blue) / 255) * 100;
    }
    /** `this`'s blackness value. */
    get blackness() {
        // Because HWB is (currently) used much less frequently than HSL or RGB, we
        // don't cache its values because we expect the memory overhead of doing so
        // to outweigh the cost of recalculating it on access.
        return 100 - (Math.max(this.red, this.green, this.blue) / 255) * 100;
    }
    /** `this`'s alpha channel. */
    get alpha() {
        return this.alphaInternal;
    }
    /**
     * Whether `this` has already calculated the HSL components for the color.
     *
     * This is an internal property that's not an official part of Sass's JS API,
     * and may be broken at any time.
     */
    get hasCalculatedHsl() {
        return !!this.hueInternal;
    }
    assertColor() {
        return this;
    }
    change(color) {
        var _a, _b, _c, _d, _e, _f, _g, _h, _j, _k, _l, _m, _o;
        if ('whiteness' in color || 'blackness' in color) {
            return new SassColor({
                hue: (_a = color.hue) !== null && _a !== void 0 ? _a : this.hue,
                whiteness: (_b = color.whiteness) !== null && _b !== void 0 ? _b : this.whiteness,
                blackness: (_c = color.blackness) !== null && _c !== void 0 ? _c : this.blackness,
                alpha: (_d = color.alpha) !== null && _d !== void 0 ? _d : this.alpha,
            });
        }
        else if ('hue' in color ||
            'saturation' in color ||
            'lightness' in color) {
            // Tell TypeScript this isn't a Partial<HwbColor>.
            const hsl = color;
            return new SassColor({
                hue: (_e = hsl.hue) !== null && _e !== void 0 ? _e : this.hue,
                saturation: (_f = hsl.saturation) !== null && _f !== void 0 ? _f : this.saturation,
                lightness: (_g = hsl.lightness) !== null && _g !== void 0 ? _g : this.lightness,
                alpha: (_h = hsl.alpha) !== null && _h !== void 0 ? _h : this.alpha,
            });
        }
        else if ('red' in color ||
            'green' in color ||
            'blue' in color ||
            this.redInternal) {
            const rgb = color;
            return new SassColor({
                red: (_j = rgb.red) !== null && _j !== void 0 ? _j : this.red,
                green: (_k = rgb.green) !== null && _k !== void 0 ? _k : this.green,
                blue: (_l = rgb.blue) !== null && _l !== void 0 ? _l : this.blue,
                alpha: (_m = rgb.alpha) !== null && _m !== void 0 ? _m : this.alpha,
            });
        }
        else {
            return new SassColor({
                hue: this.hue,
                saturation: this.saturation,
                lightness: this.lightness,
                alpha: (_o = color.alpha) !== null && _o !== void 0 ? _o : this.alpha,
            });
        }
    }
    equals(other) {
        return (other instanceof SassColor &&
            (0, utils_1.fuzzyEquals)(this.red, other.red) &&
            (0, utils_1.fuzzyEquals)(this.green, other.green) &&
            (0, utils_1.fuzzyEquals)(this.blue, other.blue) &&
            (0, utils_1.fuzzyEquals)(this.alpha, other.alpha));
    }
    hashCode() {
        return (0, immutable_1.hash)(this.red ^ this.green ^ this.blue ^ this.alpha);
    }
    toString() {
        const isOpaque = (0, utils_1.fuzzyEquals)(this.alpha, 1);
        let string = isOpaque ? 'rgb(' : 'rgba(';
        string += `${this.red}, ${this.green}, ${this.blue}`;
        string += isOpaque ? ')' : `, ${this.alpha})`;
        return string;
    }
    // Computes `this`'s `hue`, `saturation`, and `lightness` values based on
    // `red`, `green`, and `blue`.
    //
    // Algorithm from https://en.wikipedia.org/wiki/HSL_and_HSV#RGB_to_HSL_and_HSV
    rgbToHsl() {
        const scaledRed = this.red / 255;
        const scaledGreen = this.green / 255;
        const scaledBlue = this.blue / 255;
        const max = Math.max(scaledRed, scaledGreen, scaledBlue);
        const min = Math.min(scaledRed, scaledGreen, scaledBlue);
        const delta = max - min;
        if (max === min) {
            this.hueInternal = 0;
        }
        else if (max === scaledRed) {
            this.hueInternal = (0, utils_1.positiveMod)((60 * (scaledGreen - scaledBlue)) / delta, 360);
        }
        else if (max === scaledGreen) {
            this.hueInternal = (0, utils_1.positiveMod)(120 + (60 * (scaledBlue - scaledRed)) / delta, 360);
        }
        else if (max === scaledBlue) {
            this.hueInternal = (0, utils_1.positiveMod)(240 + (60 * (scaledRed - scaledGreen)) / delta, 360);
        }
        this.lightnessInternal = 50 * (max + min);
        if (max === min) {
            this.saturationInternal = 0;
        }
        else if (this.lightnessInternal < 50) {
            this.saturationInternal = (100 * delta) / (max + min);
        }
        else {
            this.saturationInternal = (100 * delta) / (2 - max - min);
        }
    }
    // Computes `this`'s red`, `green`, and `blue` channels based on `hue`,
    // `saturation`, and `value`.
    //
    // Algorithm from the CSS3 spec: https://www.w3.org/TR/css3-color/#hsl-color.
    hslToRgb() {
        const scaledHue = this.hue / 360;
        const scaledSaturation = this.saturation / 100;
        const scaledLightness = this.lightness / 100;
        const m2 = scaledLightness <= 0.5
            ? scaledLightness * (scaledSaturation + 1)
            : scaledLightness +
                scaledSaturation -
                scaledLightness * scaledSaturation;
        const m1 = scaledLightness * 2 - m2;
        this.redInternal = (0, utils_1.fuzzyRound)(hueToRgb(m1, m2, scaledHue + 1 / 3) * 255);
        this.greenInternal = (0, utils_1.fuzzyRound)(hueToRgb(m1, m2, scaledHue) * 255);
        this.blueInternal = (0, utils_1.fuzzyRound)(hueToRgb(m1, m2, scaledHue - 1 / 3) * 255);
    }
}
exports.SassColor = SassColor;
// A helper for converting HWB colors to RGB.
function hwbToRgb(hue, scaledWhiteness, scaledBlackness) {
    const factor = 1 - scaledWhiteness - scaledBlackness;
    const channel = hueToRgb(0, 1, hue) * factor + scaledWhiteness;
    return (0, utils_1.fuzzyRound)(channel * 255);
}
// An algorithm from the CSS3 spec: http://www.w3.org/TR/css3-color/#hsl-color.
function hueToRgb(m1, m2, hue) {
    if (hue < 0)
        hue += 1;
    if (hue > 1)
        hue -= 1;
    if (hue < 1 / 6) {
        return m1 + (m2 - m1) * hue * 6;
    }
    else if (hue < 1 / 2) {
        return m2;
    }
    else if (hue < 2 / 3) {
        return m1 + (m2 - m1) * (2 / 3 - hue) * 6;
    }
    else {
        return m1;
    }
}
//# sourceMappingURL=color.js.map