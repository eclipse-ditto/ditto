/**
 * @author Toru Nagashima
 * See LICENSE file in root directory for full license.
 */

import path from "node:path"
import { isBuiltin } from "node:module"
import { getResolvePaths } from "./get-resolve-paths.js"
import { getResolverConfig } from "./get-resolver-config.js"
import { getTryExtensions } from "./get-try-extensions.js"
import { ImportTarget } from "./import-target.js"
import { stripImportPathParams } from "./strip-import-path-params.js"

/** @typedef {import('@typescript-eslint/typescript-estree').TSESTree.ImportDeclaration} ImportDeclaration */

/**
 * @typedef VisitImportOptions
 * @property {boolean} [includeCore=false] The flag to include core modules.
 * @property {number} [optionIndex=0] The index of rule options.
 * @property {boolean} [ignoreTypeImport=false] The flag to ignore typescript type imports.
 */

/**
 * Gets a list of `import`/`export` declaration targets.
 *
 * Core modules of Node.js (e.g. `fs`, `http`) are excluded.
 *
 * @param {import('eslint').Rule.RuleContext} context - The rule context.
 * @param {VisitImportOptions} options - The flag to include core modules.
 * @param {function(import('./import-target.js').ImportTarget[]):void} callback The callback function to get result.
 * @returns {import('eslint').Rule.RuleListener} A list of found target's information.
 */
export function visitImport(
    context,
    { includeCore = false, optionIndex = 0, ignoreTypeImport = false },
    callback
) {
    /** @type {import('./import-target.js').ImportTarget[]} */
    const targets = []
    const basedir = path.dirname(path.resolve(context.filename))
    const paths = getResolvePaths(context, optionIndex)
    const resolverConfig = getResolverConfig(context, optionIndex)
    const extensions = getTryExtensions(context, optionIndex)
    const options = { basedir, paths, extensions, resolverConfig }

    /**
     * @param {(
     *   | import('estree').ExportAllDeclaration
     *   | import('estree').ExportNamedDeclaration
     *   | import('estree').ImportDeclaration
     *   | import('estree').ImportExpression
     * )} node
     */
    function addTarget(node) {
        if (node.source == null || node.source.type !== "Literal") {
            return
        }

        const name = stripImportPathParams(node.source?.value)
        if (includeCore === true || isBuiltin(name) === false) {
            targets.push(
                new ImportTarget(context, node.source, name, options, "import")
            )
        }
    }

    return {
        ExportAllDeclaration(node) {
            addTarget(node)
        },
        ExportNamedDeclaration(node) {
            addTarget(node)
        },
        ImportDeclaration(node) {
            if (node.source?.value == null) {
                return
            }
            if (
                ignoreTypeImport === true &&
                "importKind" in node &&
                node.importKind === "type"
            ) {
                return
            }

            addTarget(node)
        },
        ImportExpression(node) {
            if (node.source?.type !== "Literal") {
                return
            }

            addTarget(node)
        },

        "Program:exit"() {
            callback(targets)
        },
    }
}
