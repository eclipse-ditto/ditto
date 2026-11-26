import pkg from "../package.json" with { type: "json" }
import * as esmConfig from "./configs/recommended-module.js"
import * as cjsConfig from "./configs/recommended-script.js"
import * as recommendedConfig from "./configs/recommended.js"
import { flat as allRulesFlatConfig } from "./configs/all.js"
import allRules from "./all-rules.js"

/** @import { ESLint, Linter } from 'eslint' */

/** @type {ESLint.Plugin} */
const base = {
    meta: {
        name: pkg.name,
        version: pkg.version,
    },
    rules: allRules,
}
/**
 * @typedef {{
 *     'recommended-module': ESLint.ConfigData;
 *     'recommended-script': ESLint.ConfigData;
 *     'recommended': ESLint.ConfigData;
 *     'flat/recommended-module': Linter.Config;
 *     'flat/recommended-script': Linter.Config;
 *     'flat/recommended': Linter.Config;
 *     'flat/mixed-esm-and-cjs': Linter.Config[];
 *     'flat/all': Linter.Config;
 * }} Configs
 */

/** @type {Configs} */
const configs = {
    "recommended-module": { plugins: ["n"], ...esmConfig.eslintrc },
    "recommended-script": { plugins: ["n"], ...cjsConfig.eslintrc },
    recommended: { plugins: ["n"], ...recommendedConfig.eslintrc },
    "flat/recommended-module": { plugins: { n: base }, ...esmConfig.flat },
    "flat/recommended-script": { plugins: { n: base }, ...cjsConfig.flat },
    "flat/recommended": { plugins: { n: base }, ...recommendedConfig.flat },
    "flat/mixed-esm-and-cjs": [
        { files: ["**/*.js"], plugins: { n: base }, ...recommendedConfig.flat },
        { files: ["**/*.mjs"], plugins: { n: base }, ...esmConfig.flat },
        { files: ["**/*.cjs"], plugins: { n: base }, ...cjsConfig.flat },
    ],
    "flat/all": { plugins: { n: base }, ...allRulesFlatConfig },
}

// Name the variable `n` for the default export so that it has the same name
// when the plugin is imported.
/** @type {ESLint.Plugin & { configs: Configs }} */
const n = Object.assign(base, { configs })

export {
    n as default,
    n as 'module.exports',
}

