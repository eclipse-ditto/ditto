/**
 * @author Jamund Ferguson
 * See LICENSE file in root directory for full license.
 */
"use strict"

const ACCEPTABLE_PARENTS = [
    "AssignmentExpression",
    "VariableDeclarator",
    "MemberExpression",
    "ExpressionStatement",
    "CallExpression",
    "ConditionalExpression",
    "Program",
    "VariableDeclaration",
]

/**
 * Finds the eslint-scope reference in the given scope.
 * @param {import('eslint').Scope.Scope} scope The scope to search.
 * @param {import('estree').Node} node The identifier node.
 * @returns {import('eslint').Scope.Reference|undefined} Returns the found reference or null if none were found.
 */
function findReference(scope, node) {
    return scope.references.find(
        reference =>
            reference.identifier.range?.[0] === node.range?.[0] &&
            reference.identifier.range?.[1] === node.range?.[1]
    )
}

/**
 * Checks if the given identifier node is shadowed in the given scope.
 * @param {import('eslint').Scope.Scope} scope The current scope.
 * @param {import('estree').Node} node The identifier node to check.
 * @returns {boolean} Whether or not the name is shadowed.
 */
function isShadowed(scope, node) {
    const reference = findReference(scope, node)

    return Boolean(reference?.resolved?.defs?.length)
}

/** @type {import('eslint').Rule.RuleModule} */
module.exports = {
    meta: {
        type: "suggestion",
        docs: {
            description:
                "require `require()` calls to be placed at top-level module scope",
            recommended: false,
            url: "https://github.com/eslint-community/eslint-plugin-n/blob/HEAD/docs/rules/global-require.md",
        },
        fixable: null,
        schema: [],
        messages: {
            unexpected: "Unexpected require().",
        },
    },

    create(context) {
        const sourceCode = context.sourceCode ?? context.getSourceCode() // TODO: just use context.sourceCode when dropping eslint < v9

        return {
            CallExpression(node) {
                const currentScope =
                    sourceCode.getScope?.(node) ?? context.getScope() //TODO: remove context.getScope() when dropping support for ESLint < v9

                if (
                    /** @type {import('estree').Identifier} */ (node.callee)
                        .name === "require" &&
                    !isShadowed(currentScope, node.callee)
                ) {
                    const isGoodRequire = (
                        sourceCode.getAncestors?.(node) ??
                        context.getAncestors()
                    ) // TODO: remove context.getAncestors() when dropping support for ESLint < v9
                        .every(
                            parent =>
                                ACCEPTABLE_PARENTS.indexOf(parent.type) > -1
                        )

                    if (!isGoodRequire) {
                        context.report({ node, messageId: "unexpected" })
                    }
                }
            },
        }
    },
}
