/**
 * @author Yusuke Iinuma
 * See LICENSE file in root directory for full license.
 */
"use strict"

const { isBuiltin } = require("node:module")
const getConfiguredNodeVersion = require("../util/get-configured-node-version")
const getSemverRange = require("../util/get-semver-range")
const visitImport = require("../util/visit-import")
const visitRequire = require("../util/visit-require")
const mergeVisitorsInPlace = require("../util/merge-visitors-in-place")

const messageId = "preferNodeProtocol"

const supportedRangeForEsm = /** @type {import('semver').Range} */ (
    getSemverRange("^12.20.0 || >= 14.13.1")
)
const supportedRangeForCjs = /** @type {import('semver').Range} */ (
    getSemverRange("^14.18.0 || >= 16.0.0")
)

/** @type {import('eslint').Rule.RuleModule} */
module.exports = {
    meta: {
        docs: {
            description:
                "enforce using the `node:` protocol when importing Node.js builtin modules.",
            recommended: false,
            url: "https://github.com/eslint-community/eslint-plugin-n/blob/HEAD/docs/rules/prefer-node-protocol.md",
        },
        fixable: "code",
        messages: {
            [messageId]: "Prefer `node:{{moduleName}}` over `{{moduleName}}`.",
        },
        schema: [
            {
                type: "object",
                properties: {
                    version: getConfiguredNodeVersion.schema,
                },
                additionalProperties: false,
            },
        ],
        type: "suggestion",
    },
    create(context) {
        /**
         * @param {import('estree').Node} node
         * @param {object} options
         * @param {string} options.name
         * @param {number} options.argumentsLength
         * @returns {node is import('estree').CallExpression}
         */
        function isCallExpression(node, { name, argumentsLength }) {
            if (node?.type !== "CallExpression") {
                return false
            }

            if (node.optional) {
                return false
            }

            if (node.arguments.length !== argumentsLength) {
                return false
            }

            if (
                node.callee.type !== "Identifier" ||
                node.callee.name !== name
            ) {
                return false
            }

            return true
        }

        /**
         * @param {import('estree').Node} node
         * @returns {node is import('estree').Literal}
         */
        function isStringLiteral(node) {
            return node?.type === "Literal" && typeof node.type === "string"
        }

        /**
         * @param {import('estree').Node | undefined} node
         * @returns {node is import('estree').CallExpression}
         */
        function isStaticRequire(node) {
            return (
                node != null &&
                isCallExpression(node, {
                    name: "require",
                    argumentsLength: 1,
                }) &&
                isStringLiteral(node.arguments[0])
            )
        }

        /**
         * @param {import('eslint').Rule.RuleContext} context
         * @param {import('../util/import-target.js').ModuleStyle} moduleStyle
         * @returns {boolean}
         */
        function isEnablingThisRule(context, moduleStyle) {
            const version = getConfiguredNodeVersion(context)

            // Only check Node.js version because this rule is meaningless if configured Node.js version doesn't match semver range.
            if (!version.intersects(supportedRangeForEsm)) {
                return false
            }

            // Only check when using `require`
            if (
                moduleStyle === "require" &&
                !version.intersects(supportedRangeForCjs)
            ) {
                return false
            }

            return true
        }

        /** @type {import('../util/import-target.js')[]} */
        const targets = []
        return [
            visitImport(context, { includeCore: true }, importTargets => {
                targets.push(...importTargets)
            }),
            visitRequire(context, { includeCore: true }, requireTargets => {
                targets.push(
                    ...requireTargets.filter(target =>
                        isStaticRequire(target.node.parent)
                    )
                )
            }),
            {
                "Program:exit"() {
                    for (const { node, moduleStyle } of targets) {
                        if (!isEnablingThisRule(context, moduleStyle)) {
                            continue
                        }

                        if (node.type === "TemplateLiteral") {
                            continue
                        }

                        const { value } = /** @type {{ value: string }}*/ (node)
                        if (
                            typeof value !== "string" ||
                            value.startsWith("node:") ||
                            !isBuiltin(value) ||
                            !isBuiltin(`node:${value}`)
                        ) {
                            continue
                        }

                        context.report({
                            node,
                            messageId,
                            data: {
                                moduleName: value,
                            },
                            fix(fixer) {
                                const firstCharacterIndex =
                                    (node?.range?.[0] ?? 0) + 1
                                return fixer.replaceTextRange(
                                    [firstCharacterIndex, firstCharacterIndex],
                                    "node:"
                                )
                            },
                        })
                    }
                },
            },
        ].reduce(
            (mergedVisitor, thisVisitor) =>
                mergeVisitorsInPlace(mergedVisitor, thisVisitor),
            {}
        )
    },
}
