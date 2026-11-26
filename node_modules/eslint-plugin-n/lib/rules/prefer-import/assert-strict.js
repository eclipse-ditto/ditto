/**
 * @author baevm
 * See LICENSE file in root directory for full license.
 */

const messageId = "preferAssertStrict"

/**
 * @param {import('estree').ImportDeclaration} node
 * @returns {boolean}
 */
function importsOnlyStrict(node) {
    return (
        node.specifiers.length > 0 &&
        node.specifiers.every(
            specifier =>
                specifier.type === "ImportSpecifier" &&
                (specifier.imported.type === "Identifier"
                    ? specifier.imported.name
                    : specifier.imported.value) === "strict"
        )
    )
}

/**
 * @param {import('estree').ExportNamedDeclaration} node
 * @returns {boolean}
 */
function exportsOnlyStrict(node) {
    return (
        node.specifiers.length > 0 &&
        node.specifiers.every(
            specifier =>
                specifier.type === "ExportSpecifier" &&
                (specifier.local.type === "Identifier"
                    ? specifier.local.name
                    : specifier.local.value) === "strict"
        )
    )
}

/**
 * @param {import('estree').Property | import('estree').RestElement} property
 * @returns {boolean}
 */
function isStrictProperty(property) {
    if (property.type !== "Property") {
        return false
    }

    return property.computed
        ? property.key.type === "Literal" && property.key.value === "strict"
        : property.key.type === "Identifier" && property.key.name === "strict"
}

/**
 * @param {import('estree').Pattern | import('estree').MemberExpression} node
 * @returns {boolean}
 */
function accessesOnlyStrict(node) {
    if (node.type === "MemberExpression") {
        return node.computed
            ? node.property.type === "Literal" &&
                  node.property.value === "strict"
            : node.property.type === "Identifier" &&
                  node.property.name === "strict"
    }

    return (
        node.type === "ObjectPattern" &&
        node.properties.length > 0 &&
        node.properties.every(isStrictProperty)
    )
}

/**
 * @param {import('estree').CallExpression & import('eslint').Rule.NodeParentExtension} node
 * @returns {boolean}
 */
function requiresOnlyStrict(node) {
    const { parent } = node

    if (parent.type === "MemberExpression" && parent.object === node) {
        return accessesOnlyStrict(parent)
    }
    if (parent.type === "VariableDeclarator" && parent.init === node) {
        return accessesOnlyStrict(parent.id)
    }
    if (parent.type === "AssignmentExpression" && parent.right === node) {
        return accessesOnlyStrict(parent.left)
    }
    return false
}

/**
 * @param {import('estree').Node | null | undefined} node
 * @param {import('eslint').Rule.RuleContext} context
 */
function checkSource(node, context) {
    if (
        node?.type !== "Literal" ||
        typeof node.value !== "string" ||
        node.value !== "node:assert"
    ) {
        return
    }

    context.report({
        node,
        messageId,
    })
}

/** @type {import('../rule-module.js').RuleModule} */
export default {
    meta: {
        docs: {
            description:
                "enforce using `node:assert/strict` instead of `node:assert`.",
            recommended: false,
            url: "https://github.com/eslint-community/eslint-plugin-n/blob/HEAD/docs/rules/prefer-import/assert-strict.md",
        },
        messages: {
            [messageId]: "Prefer `node:assert/strict` over `node:assert`.",
        },
        schema: [],
        type: "suggestion",
    },
    create(context) {
        return {
            CallExpression(node) {
                if (
                    node.callee.type === "Identifier" &&
                    node.callee.name === "require" &&
                    !requiresOnlyStrict(node)
                ) {
                    checkSource(node.arguments[0], context)
                }
            },
            ExportAllDeclaration(node) {
                checkSource(node.source, context)
            },
            ExportNamedDeclaration(node) {
                if (!exportsOnlyStrict(node)) {
                    checkSource(node.source, context)
                }
            },
            ImportDeclaration(node) {
                if (!importsOnlyStrict(node)) {
                    checkSource(node.source, context)
                }
            },
            ImportExpression(node) {
                checkSource(node.source, context)
            },
        }
    },
}
