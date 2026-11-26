/**
 * @author Toru Nagashima
 * See LICENSE file in root directory for full license.
 */

import path from "node:path"
import {
    CALL,
    ReferenceTracker,
    getStringIfConstant,
} from "@eslint-community/eslint-utils"
import { isBuiltin } from "node:module"
import { getResolvePaths } from "./get-resolve-paths.js"
import { getResolverConfig } from "./get-resolver-config.js"
import { getTryExtensions } from "./get-try-extensions.js"
import { ImportTarget } from "./import-target.js"
import { stripImportPathParams } from "./strip-import-path-params.js"

/**
 * @typedef VisitRequireOptions
 * @property {boolean} [includeCore=false] The flag to include core modules.
 */

/**
 * Gets a list of `require()` targets.
 *
 * Core modules of Node.js (e.g. `fs`, `http`) are excluded.
 *
 * @param {import('eslint').Rule.RuleContext} context - The rule context.
 * @param {VisitRequireOptions} options - The flag to include core modules.
 * @param {function(import('./import-target.js').ImportTarget[]): void} callback The callback function to get result.
 * @returns {import('eslint').Rule.RuleListener} The visitor.
 */
export function visitRequire(context, { includeCore = false }, callback) {
    /** @type {import('./import-target.js').ImportTarget[]} */
    const targets = []
    const basedir = path.dirname(path.resolve(context.filename))
    const paths = getResolvePaths(context)
    const resolverConfig = getResolverConfig(context)
    const extensions = getTryExtensions(context)
    const options = { basedir, paths, extensions, resolverConfig }

    return {
        "Program:exit"() {
            const tracker = new ReferenceTracker(
                context.sourceCode.getScope(context.sourceCode.ast)
            )
            const references = tracker.iterateGlobalReferences({
                require: {
                    [CALL]: true,
                    resolve: { [CALL]: true },
                },
            })

            for (const { node } of references) {
                if (node.type !== "CallExpression") {
                    continue
                }

                const targetNode = node.arguments[0]
                if (targetNode == null) {
                    continue
                }

                const rawName = getStringIfConstant(targetNode)
                if (typeof rawName !== "string") {
                    continue
                }

                const name = stripImportPathParams(rawName)
                if (includeCore || !isBuiltin(name)) {
                    targets.push(
                        new ImportTarget(
                            context,
                            targetNode,
                            name,
                            options,
                            "require"
                        )
                    )
                }
            }

            callback(targets)
        },
    }
}
