/**
 * @author ColumbusLabs
 * See LICENSE file in root directory for full license.
 */

import { isBuiltin } from "node:module"
import {
    findVariable,
    getStringIfConstant,
} from "@eslint-community/eslint-utils"
import { Range, subset } from "semver"
import { getConfiguredNodeVersion } from "../util/get-configured-node-version.js"
import { schema as configuredNodeVersionSchema } from "../util/get-configured-node-version.js"

const supportedRange = new Range("^20.16.0 || >=22.3.0")

/**
 * @param {import("eslint").Rule.RuleContext} context
 * @param {import("estree").Node} node
 * @returns {boolean}
 */
function isProcessShadowed(context, node) {
    const scope = context.sourceCode.getScope(node)
    const variable = findVariable(scope, "process")
    return Boolean(variable?.defs.length)
}

/**
 * @param {import("eslint").Rule.RuleContext} context
 * @param {import("estree").Identifier} node
 * @returns {boolean}
 */
function isCreateRequireImport(context, node) {
    const variable = findVariable(context.sourceCode.getScope(node), node)
    return Boolean(
        variable?.defs.some(
            definition =>
                definition.type === "ImportBinding" &&
                definition.node.type === "ImportSpecifier" &&
                definition.node.imported.type === "Identifier" &&
                definition.node.imported.name === "createRequire" &&
                (definition.parent.source.value === "node:module" ||
                    definition.parent.source.value === "module")
        )
    )
}

/**
 * @param {import("eslint").Rule.RuleContext} context
 * @param {import("estree").Identifier} node
 * @returns {boolean}
 */
function isNodeRequire(context, node) {
    const variable = findVariable(context.sourceCode.getScope(node), node)
    if (variable == null || variable.defs.length === 0) {
        return true
    }

    return variable.defs.some(definition => {
        if (
            definition.type !== "Variable" ||
            definition.node.type !== "VariableDeclarator" ||
            definition.parent.type !== "VariableDeclaration" ||
            definition.parent.kind !== "const" ||
            definition.node.init?.type !== "CallExpression" ||
            definition.node.init.callee.type !== "Identifier"
        ) {
            return false
        }
        return isCreateRequireImport(context, definition.node.init.callee)
    })
}

/**
 * @param {import("estree").Node | null | undefined} node
 * @returns {boolean}
 */
function isBuiltinModuleName(node) {
    if (node == null || node.type === "SpreadElement") {
        return false
    }
    const name = getStringIfConstant(node)
    return typeof name === "string" && isBuiltin(name)
}

/** @type {import("./rule-module.js").RuleModule} */
export default {
    meta: {
        docs: {
            description:
                "enforce using `process.getBuiltinModule()` to load Node.js built-in modules",
            recommended: false,
            url: "https://github.com/eslint-community/eslint-plugin-n/blob/HEAD/docs/rules/prefer-process-get-builtin-module.md",
        },
        messages: {
            preferProcessGetBuiltinModule:
                "Prefer `process.getBuiltinModule()` over `{{method}}()` for Node.js built-in modules.",
        },
        schema: [
            {
                type: "object",
                properties: {
                    version: configuredNodeVersionSchema,
                },
                additionalProperties: false,
            },
        ],
        type: "suggestion",
    },
    create(context) {
        if (!subset(getConfiguredNodeVersion(context), supportedRange)) {
            return {}
        }

        /**
         * @param {import("estree").CallExpression} node
         */
        function reportRequire(node) {
            if (
                node.callee.type !== "Identifier" ||
                node.callee.name !== "require" ||
                !isNodeRequire(context, node.callee) ||
                ("optional" in node && node.optional) ||
                node.arguments.length !== 1 ||
                !isBuiltinModuleName(node.arguments[0]) ||
                isProcessShadowed(context, node)
            ) {
                return
            }

            context.report({
                node,
                messageId: "preferProcessGetBuiltinModule",
                data: { method: "require" },
            })
        }

        /**
         * @param {import("estree").AwaitExpression} node
         */
        function reportImport(node) {
            const importExpression = node.argument
            if (
                importExpression.type !== "ImportExpression" ||
                importExpression.options != null ||
                !isBuiltinModuleName(importExpression.source) ||
                isProcessShadowed(context, node)
            ) {
                return
            }

            context.report({
                node,
                messageId: "preferProcessGetBuiltinModule",
                data: { method: "import" },
            })
        }

        return {
            AwaitExpression: reportImport,
            CallExpression: reportRequire,
        }
    },
}
