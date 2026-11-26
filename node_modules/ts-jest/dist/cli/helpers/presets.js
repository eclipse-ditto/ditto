"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.jsWIthBabel = exports.jsWithTs = exports.defaults = exports.allPresets = exports.JestPresetNames = void 0;
/** @internal */
var JestPresetNames;
(function (JestPresetNames) {
    JestPresetNames["default"] = "ts-jest/presets/default";
    JestPresetNames["jsWithTs"] = "ts-jest/presets/js-with-ts";
    JestPresetNames["jsWIthBabel"] = "ts-jest/presets/js-with-babel";
})(JestPresetNames || (exports.JestPresetNames = JestPresetNames = {}));
const definePreset = (fullName) => ({
    fullName,
    get name() {
        return this.isDefault ? 'ts-jest' : fullName;
    },
    get label() {
        // eslint-disable-next-line @typescript-eslint/no-non-null-assertion
        return fullName.split('/').pop();
    },
    get jsVarName() {
        return this.isDefault
            ? 'defaults'
            : // eslint-disable-next-line @typescript-eslint/no-non-null-assertion
                fullName
                    .split('/')
                    .pop()
                    // eslint-disable-next-line no-useless-escape
                    .replace(/\-([a-z])/g, (_, l) => l.toUpperCase());
    },
    get value() {
        return require(`../../../${fullName.replace(/^ts-jest\//, '')}/jest-preset`);
    },
    jsImport(varName = 'tsjPreset') {
        return `const { ${this.jsVarName}: ${varName} } = require('ts-jest/presets')`;
    },
    get isDefault() {
        return fullName === JestPresetNames.default;
    },
});
/** @internal */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
exports.allPresets = {};
/** @internal */
exports.defaults = (exports.allPresets[JestPresetNames.default] = definePreset(JestPresetNames.default));
/** @internal */
exports.jsWithTs = (exports.allPresets[JestPresetNames.jsWithTs] = definePreset(JestPresetNames.jsWithTs));
/** @internal */
exports.jsWIthBabel = (exports.allPresets[JestPresetNames.jsWIthBabel] = definePreset(JestPresetNames.jsWIthBabel));
