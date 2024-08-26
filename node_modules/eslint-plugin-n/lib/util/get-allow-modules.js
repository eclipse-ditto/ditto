/**
 * @author Toru Nagashima
 * See LICENSE file in root directory for full license.
 */
"use strict"

/**
 * @type {string[]}
 */
const DEFAULT_VALUE = []

/**
 * Gets `allowModules` property from a given option object.
 *
 * @param {{allowModules:? string[]}|undefined} option - An option object to get.
 * @returns {string[]|null} The `allowModules` value, or `null`.
 */
function get(option) {
    if (Array.isArray(option?.allowModules)) {
        return option.allowModules.map(String)
    }
    return null
}

/**
 * Gets "allowModules" setting.
 *
 * 1. This checks `options` property, then returns it if exists.
 * 2. This checks `settings.n` | `settings.node` property, then returns it if exists.
 * 3. This returns `[]`.
 *
 * @param {import('eslint').Rule.RuleContext} context - The rule context.
 * @returns {string[]} A list of extensions.
 */
module.exports = function getAllowModules(context) {
    return (
        get(context.options[0]) ??
        get(context.settings?.n) ??
        get(context.settings?.node) ??
        DEFAULT_VALUE
    )
}

module.exports.schema = {
    type: "array",
    items: {
        type: "string",
        pattern: "^(?:@[a-zA-Z0-9_\\-.]+/)?[a-zA-Z0-9_\\-.]+$",
    },
    uniqueItems: true,
}
