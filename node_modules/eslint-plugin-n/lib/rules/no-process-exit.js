/**
 * @author Nicholas C. Zakas
 * See LICENSE file in root directory for full license.
 */

const querySelector = [
    `CallExpression > `,
    `MemberExpression.callee`,
    `[object.name="process"]`,
    `[property.name="exit"]`,
]

/** @type {import('./rule-module.js').RuleModule} */
export default {
    meta: {
        type: "suggestion",
        docs: {
            description: "disallow the use of `process.exit()`",
            recommended: true,
            url: "https://github.com/eslint-community/eslint-plugin-n/blob/HEAD/docs/rules/no-process-exit.md",
        },
        fixable: void 0,
        schema: [],
        messages: {
            noProcessExit: "Don't use process.exit(); throw an error instead.",
        },
    },
    create(context) {
        return {
            /** @param {import('estree').MemberExpression & { parent: import('estree').CallExpression}} node */
            [querySelector.join("")](node) {
                context.report({
                    node: node.parent,
                    messageId: "noProcessExit",
                })
            },
        }
    },
}
