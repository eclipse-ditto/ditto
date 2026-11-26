/**
 * @author Nicholas C. Zakas
 * See LICENSE file in root directory for full license.
 */

import path from "node:path"
import {
    READ,
    ReferenceTracker,
    getStringIfConstant,
} from "@eslint-community/eslint-utils"
import { hasParentNode } from "../util/has-parent-node.js"

/**
 * Get the first char of the specified template element.
 * @param {import('estree').TemplateLiteral} node The `TemplateLiteral` node to get.
 * @param {number} i The number of template elements to get first char.
 * @param {Set<import('estree').Node>} sepNodes The nodes of `path.sep`.
 * @param {import("eslint").Scope.Scope} globalScope The global scope object.
 * @param {string[]} outNextChars The array to collect chars.
 * @returns {void}
 */
function collectFirstCharsOfTemplateElement(
    node,
    i,
    sepNodes,
    globalScope,
    outNextChars
) {
    const element = node.quasis[i]?.value.cooked

    if (element == null) {
        return
    }

    const first = element[0]
    if (first) {
        outNextChars.push(first)
        return
    }

    if (node.expressions.length > i) {
        collectFirstChars(
            node.expressions.at(i),
            sepNodes,
            globalScope,
            outNextChars
        )
    }
}

/**
 * Get the first char of a given node.
 * @param {import('estree').Node | undefined} node The `TemplateLiteral` node to get.
 * @param {Set<import('estree').Node>} sepNodes The nodes of `path.sep`.
 * @param {import("eslint").Scope.Scope} globalScope The global scope object.
 * @param {string[]} outNextChars The array to collect chars.
 * @returns {void}
 */
function collectFirstChars(node, sepNodes, globalScope, outNextChars) {
    switch (node?.type) {
        case "AssignmentExpression":
            collectFirstChars(node.right, sepNodes, globalScope, outNextChars)
            break
        case "BinaryExpression":
            collectFirstChars(node.left, sepNodes, globalScope, outNextChars)
            break
        case "ConditionalExpression":
            collectFirstChars(
                node.consequent,
                sepNodes,
                globalScope,
                outNextChars
            )
            collectFirstChars(
                node.alternate,
                sepNodes,
                globalScope,
                outNextChars
            )
            break
        case "LogicalExpression":
            collectFirstChars(node.left, sepNodes, globalScope, outNextChars)
            collectFirstChars(node.right, sepNodes, globalScope, outNextChars)
            break
        case "SequenceExpression":
            collectFirstChars(
                node.expressions[node.expressions.length - 1],
                sepNodes,
                globalScope,
                outNextChars
            )
            break
        case "TemplateLiteral":
            collectFirstCharsOfTemplateElement(
                node,
                0,
                sepNodes,
                globalScope,
                outNextChars
            )
            break

        case "Identifier":
        case "MemberExpression":
            if (sepNodes.has(node)) {
                outNextChars.push(path.sep)
            } else {
                const str = getStringIfConstant(node, globalScope)?.at(0)
                if (str) {
                    outNextChars.push(str)
                }
            }
            break
        default: {
            if (node) {
                const str = getStringIfConstant(node, globalScope)?.at(0)
                if (str) {
                    outNextChars.push(str)
                }
            }
        }
    }
}

/**
 * Check if a char is a path separator or not.
 * @param {string} c The char to check.
 * @returns {boolean} `true` if the char is a path separator.
 */
function isPathSeparator(c) {
    return c === "/" || c === path.sep
}

/**
 * @param {import('estree').MemberExpression} node
 * @returns {string | undefined}
 */
function getStaticPropertyName(node) {
    if (!node.computed && node.property.type === "Identifier") {
        return node.property.name
    }
    if (
        node.computed &&
        node.property.type === "Literal" &&
        typeof node.property.value === "string"
    ) {
        return node.property.value
    }
    if (
        node.computed &&
        node.property.type === "TemplateLiteral" &&
        node.property.expressions.length === 0
    ) {
        return node.property.quasis[0]?.value.cooked ?? undefined
    }
}

/**
 * @param {import('estree').Node} node
 * @returns {node is import('estree').MemberExpression}
 */
function isImportMetaPath(node) {
    const propertyName =
        node.type === "MemberExpression"
            ? getStaticPropertyName(node)
            : undefined

    return (
        node.type === "MemberExpression" &&
        node.object.type === "MetaProperty" &&
        node.object.meta.type === "Identifier" &&
        node.object.meta.name === "import" &&
        node.object.property.type === "Identifier" &&
        node.object.property.name === "meta" &&
        (propertyName === "dirname" ||
            propertyName === "filename" ||
            propertyName === "url")
    )
}

/**
 * Check if the given Identifier node is followed by string concatenation with a
 * path separator.
 * @param {import('estree').Identifier | import('estree').MemberExpression} node The path-like node to check.
 * @param {Set<import('estree').Node>} sepNodes The nodes of `path.sep`.
 * @param {import("eslint").Scope.Scope} globalScope The global scope object.
 * @returns {boolean} `true` if the given Identifier node is followed by string
 * concatenation with a path separator.
 */
function isConcat(node, sepNodes, globalScope) {
    if (!hasParentNode(node)) {
        return false
    }

    /** @type {string[]} */
    const nextChars = []

    if (
        node.parent.type === "BinaryExpression" &&
        node.parent.operator === "+" &&
        node.parent.left === node
    ) {
        collectFirstChars(
            node.parent.right,
            sepNodes,
            globalScope,
            /* out */ nextChars
        )
    } else if (node.parent.type === "TemplateLiteral") {
        collectFirstCharsOfTemplateElement(
            node.parent,
            node.parent.expressions.indexOf(node) + 1,
            sepNodes,
            globalScope,
            /* out */ nextChars
        )
    }

    return nextChars.some(isPathSeparator)
}

/** @type {import('./rule-module.js').RuleModule} */
export default {
    meta: {
        type: "suggestion",
        docs: {
            description:
                "disallow string concatenation with `__dirname`, `__filename`, and `import.meta` paths",
            recommended: false,
            url: "https://github.com/eslint-community/eslint-plugin-n/blob/HEAD/docs/rules/no-path-concat.md",
        },
        fixable: void 0,
        schema: [],
        messages: {
            usePathFunctions:
                "Use path.join() or path.resolve() instead of string concatenation.",
            useUrl: "Use new URL() instead of string concatenation.",
        },
    },
    create(context) {
        const sourceCode = context.sourceCode
        /** @type {import("eslint").Scope.Scope | undefined} */
        let globalScope
        /** @type {Set<import('estree').Node> | undefined} */
        let sepNodes

        function getAnalysisState() {
            globalScope ??= sourceCode.getScope(sourceCode.ast)

            if (sepNodes === undefined) {
                const tracker = new ReferenceTracker(globalScope)
                sepNodes = new Set()

                for (const { node } of tracker.iterateCjsReferences({
                    path: { sep: { [READ]: true } },
                })) {
                    sepNodes.add(node)
                }
                for (const { node } of tracker.iterateEsmReferences({
                    path: { sep: { [READ]: true } },
                })) {
                    sepNodes.add(node)
                }
            }

            return { globalScope, sepNodes }
        }

        return {
            MemberExpression(node) {
                if (!isImportMetaPath(node)) {
                    return
                }

                const { globalScope, sepNodes } = getAnalysisState()
                if (isConcat(node, sepNodes, globalScope)) {
                    context.report({
                        node: node.parent,
                        messageId:
                            getStaticPropertyName(node) === "url"
                                ? "useUrl"
                                : "usePathFunctions",
                    })
                }
            },
            "Program:exit"() {
                const { globalScope, sepNodes } = getAnalysisState()
                const tracker = new ReferenceTracker(globalScope)

                // Verify `__dirname` and `__filename`
                for (const { node } of tracker.iterateGlobalReferences({
                    __dirname: { [READ]: true },
                    __filename: { [READ]: true },
                })) {
                    if (
                        node.type === "Identifier" &&
                        hasParentNode(node) &&
                        isConcat(node, sepNodes, globalScope)
                    ) {
                        context.report({
                            node: node.parent,
                            messageId: "usePathFunctions",
                        })
                    }
                }
            },
        }
    },
}
