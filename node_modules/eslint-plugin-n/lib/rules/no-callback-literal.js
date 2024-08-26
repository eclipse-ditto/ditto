/**
 * @author Jamund Ferguson
 * See LICENSE file in root directory for full license.
 */
"use strict"

const callbackNames = ["callback", "cb"]

/** @type {import('eslint').Rule.RuleModule} */
module.exports = {
    meta: {
        docs: {
            description:
                "enforce Node.js-style error-first callback pattern is followed",
            recommended: false,
            url: "https://github.com/eslint-community/eslint-plugin-n/blob/HEAD/docs/rules/no-callback-literal.md",
        },
        type: "problem",
        fixable: null,
        schema: [],
        messages: {
            unexpectedLiteral:
                "Unexpected literal in error position of callback.",
        },
    },

    create(context) {
        return {
            CallExpression(node) {
                const errorArg = node.arguments[0]
                const calleeName = /** @type {import('estree').Identifier} */ (
                    node.callee
                ).name

                if (
                    errorArg &&
                    !couldBeError(errorArg) &&
                    callbackNames.includes(calleeName)
                ) {
                    context.report({
                        node,
                        messageId: "unexpectedLiteral",
                    })
                }
            },
        }
    },
}

/**
 * Determine if a node has a possiblity to be an Error object
 * @param  {import('estree').Node}  node  ASTNode to check
 * @returns {boolean}       True if there is a chance it contains an Error obj
 */
function couldBeError(node) {
    switch (node.type) {
        case "Identifier":
        case "CallExpression":
        case "NewExpression":
        case "MemberExpression":
        case "TaggedTemplateExpression":
        case "YieldExpression":
            return true // possibly an error object.
        case "Literal":
            return node.value == null
        case "AssignmentExpression":
            return couldBeError(node.right)

        case "SequenceExpression": {
            const exprs = node.expressions
            return exprs.length !== 0 && couldBeError(exprs[exprs.length - 1])
        }

        case "LogicalExpression":
            return couldBeError(node.left) || couldBeError(node.right)

        case "ConditionalExpression":
            return couldBeError(node.consequent) || couldBeError(node.alternate)
        default:
            return true // assuming unknown nodes can be error objects.
    }
}
