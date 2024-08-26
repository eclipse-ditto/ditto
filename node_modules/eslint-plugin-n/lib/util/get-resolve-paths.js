/**
 * @author Toru Nagashima
 * See LICENSE file in root directory for full license.
 */
"use strict"

/** @type {string[]} */
const DEFAULT_VALUE = []

/**
 * Gets `resolvePaths` property from a given option object.
 *
 * @param {{ resolvePaths: unknown[] } | undefined} option - An option object to get.
 * @returns {string[] | undefined} The `allowModules` value, or `null`.
 */
function get(option) {
    if (Array.isArray(option?.resolvePaths)) {
        return option.resolvePaths.map(String)
    }
}

/**
 * Gets "resolvePaths" setting.
 *
 * 1. This checks `options` property, then returns it if exists.
 * 2. This checks `settings.n` | `settings.node` property, then returns it if exists.
 * 3. This returns `[]`.
 *
 * @param {import('eslint').Rule.RuleContext} context - The rule context.
 * @returns {string[]} A list of extensions.
 */
module.exports = function getResolvePaths(context, optionIndex = 0) {
    return (
        get(context.options?.[optionIndex]) ??
        get(context.settings?.n) ??
        get(context.settings?.node) ??
        DEFAULT_VALUE
    )
}

module.exports.schema = {
    type: "array",
    items: { type: "string" },
    uniqueItems: true,
}
