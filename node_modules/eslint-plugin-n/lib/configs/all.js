/**
 * @fileoverview config for enabling all rules in the plugin.
 * @author aladdin-add<weiran.zsd@outlook.com>SS
 */

import { allRulesConfig } from "./_commons.js"
import * as recommendeConfig from "./recommended.js"

/**
 * https://eslint.org/docs/latest/use/configure/configuration-files-new
 * @type {import('eslint').Linter.FlatConfig}
 */
export const flat = {
    name: "node/flat/all",
    languageOptions: recommendeConfig.flat.languageOptions ?? {},
    rules: allRulesConfig,
}
