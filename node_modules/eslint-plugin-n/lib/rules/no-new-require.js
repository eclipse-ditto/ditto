/**
 * @author Wil Moore III
 * See LICENSE file in root directory for full license.
 */

/** @type {import('./rule-module.js').RuleModule} */
export default {
    meta: {
        type: "suggestion",
        docs: {
            description: "disallow `new` operators with calls to `require`",
            recommended: false,
            url: "https://github.com/eslint-community/eslint-plugin-n/blob/HEAD/docs/rules/no-new-require.md",
        },
        fixable: void 0,
        schema: [],
        messages: {
            noNewRequire: "Unexpected use of new with require.",
        },
    },
    create(context) {
        return {
            NewExpression(node) {
                if (
                    node.callee.type === "Identifier" &&
                    node.callee.name === "require"
                ) {
                    context.report({
                        node,
                        messageId: "noNewRequire",
                    })
                }
            },
        }
    },
}
