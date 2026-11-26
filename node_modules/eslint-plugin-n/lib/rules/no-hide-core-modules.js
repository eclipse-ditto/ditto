/**
 * @author Toru Nagashima
 * See LICENSE file in root directory for full license.
 *
 * @deprecated since v4.2.0
 * This rule was based on an invalid assumption.
 * No meaning.
 */

import path from "node:path"
import { getPackageJson } from "../util/get-package-json.js"
import { mergeVisitorsInPlace } from "../util/merge-visitors-in-place.js"
import { visitImport } from "../util/visit-import.js"
import { visitRequire } from "../util/visit-require.js"

/** @type {Set<string|undefined>} */
const CORE_MODULES = new Set([
    "assert",
    "buffer",
    "child_process",
    "cluster",
    "console",
    "constants",
    "crypto",
    "dgram",
    "dns",
    /* "domain", */
    "events",
    "fs",
    "http",
    "https",
    "module",
    "net",
    "os",
    "path",
    /* "punycode", */
    "querystring",
    "readline",
    "repl",
    "stream",
    "string_decoder",
    "timers",
    "tls",
    "tty",
    "url",
    "util",
    "vm",
    "zlib",
])
const BACK_SLASH = /\\/gu

/**
 * @typedef {[
 *   {
 *     allow?: string[];
 *     ignoreDirectDependencies?: boolean;
 *     ignoreIndirectDependencies?: boolean;
 *   }?
 * ]} RuleOptions
 */
/** @type {import('./rule-module.js').RuleModule<{RuleOptions: RuleOptions}>} */
export default {
    meta: {
        docs: {
            description:
                "disallow third-party modules which are hiding core modules",
            recommended: false,
            url: "https://github.com/eslint-community/eslint-plugin-n/blob/HEAD/docs/rules/no-hide-core-modules.md",
        },
        type: "problem",
        deprecated: {
            deprecatedSince: "4.2.0",
            message: "This rule was based on an invalid assumption.",
            url: "https://github.com/mysticatea/eslint-plugin-node/issues/69",
        },
        fixable: void 0,
        schema: [
            {
                type: "object",
                properties: {
                    allow: {
                        type: "array",
                        items: { enum: Array.from(CORE_MODULES) },
                        additionalItems: false,
                        uniqueItems: true,
                    },
                    ignoreDirectDependencies: { type: "boolean" },
                    ignoreIndirectDependencies: { type: "boolean" },
                },
                additionalProperties: false,
            },
        ],
        messages: {
            unexpectedImport:
                "Unexpected import of third-party module '{{name}}'.",
        },
    },
    create(context) {
        const filename = context.filename
        if (filename === "<input>") {
            return {}
        }
        const filePath = path.resolve(filename)
        const dirPath = path.dirname(filePath)
        const packageJson = getPackageJson(filePath)
        /** @type {Set<string|undefined>} */
        const deps = new Set([
            ...Object.keys(packageJson?.dependencies ?? {}),
            ...Object.keys(packageJson?.devDependencies ?? {}),
        ])
        const options = context.options[0] || {}
        const allow = /** @type {(string|undefined)[]} */ (options.allow || [])
        const ignoreDirectDependencies = Boolean(
            options.ignoreDirectDependencies
        )
        const ignoreIndirectDependencies = Boolean(
            options.ignoreIndirectDependencies
        )
        /** @type {import('../util/import-target.js').ImportTarget[]} */
        const targets = []

        return [
            visitImport(context, { includeCore: true }, importTargets =>
                targets.push(...importTargets)
            ),
            visitRequire(context, { includeCore: true }, requireTargets =>
                targets.push(...requireTargets)
            ),
            {
                "Program:exit"() {
                    for (const target of targets.filter(
                        t =>
                            CORE_MODULES.has(t.moduleName) &&
                            t.moduleName === t.name
                    )) {
                        const name = target.moduleName
                        const allowed =
                            allow.indexOf(name) !== -1 ||
                            (ignoreDirectDependencies && deps.has(name)) ||
                            (ignoreIndirectDependencies && !deps.has(name))

                        if (allowed) {
                            continue
                        }

                        if (target.filePath == null) {
                            continue
                        }

                        context.report({
                            node: target.node,
                            messageId: "unexpectedImport",
                            data: {
                                name: path
                                    .relative(dirPath, target.filePath)
                                    .replace(BACK_SLASH, "/"),
                            },
                        })
                    }
                },
            },
        ].reduce(mergeVisitorsInPlace, {})
    },
}
