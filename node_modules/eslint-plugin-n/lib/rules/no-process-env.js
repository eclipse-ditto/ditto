/**
 * @author Vignesh Anand
 * See LICENSE file in root directory for full license.
 */
"use strict"

//------------------------------------------------------------------------------
// Rule Definition
//------------------------------------------------------------------------------

const querySelector = [
    `MemberExpression`,
    `[computed!=true]`,
    `[object.name="process"]`,
    `[property.name="env"]`,
]

/** @type {import('eslint').Rule.RuleModule} */
module.exports = {
    meta: {
        type: "suggestion",
        docs: {
            description: "disallow the use of `process.env`",
            recommended: false,
            url: "https://github.com/eslint-community/eslint-plugin-n/blob/HEAD/docs/rules/no-process-env.md",
        },
        fixable: null,
        schema: [],
        messages: {
            unexpectedProcessEnv: "Unexpected use of process.env.",
        },
    },

    create(context) {
        return {
            /** @param {import('estree').MemberExpression} node */
            [querySelector.join("")](node) {
                context.report({ node, messageId: "unexpectedProcessEnv" })
            },
        }
    },
}
