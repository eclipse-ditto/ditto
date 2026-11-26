/**
 * @author Matt DuVall<http://mattduvall.com/>
 * See LICENSE file in root directory for full license.
 */
import mod from "node:module"
import { fileURLToPath } from "node:url"
import { dirname } from "node:path"

const __filename = fileURLToPath(import.meta.url)
const __dirname = dirname(__filename)

let typeMatchesSpecifier =
    /** @type {import('ts-declaration-location').default | undefined} */
    (undefined)

const require = mod.createRequire(`${__dirname}/no-sync.js`)

import { getTypeOfNode } from "../util/get-type-of-node.js"
import { getParserServices } from "../util/get-parser-services.js"
import { getFullTypeName } from "../util/get-full-type-name.js"

const selectors = [
    // fs.readFileSync()
    // readFileSync.call(null, 'path')
    "CallExpression > MemberExpression.callee Identifier[name=/Sync$/]",
    // readFileSync()
    "CallExpression > Identifier[name=/Sync$/]",
]

/**
 * @typedef {[
 *   {
 *     allowAtRootLevel?: boolean
 *     ignores?: (string | { from: "file"; path?: string; name?: string[]; } | { from: "package"; package?: string; name?: string[]; } | { from: "lib"; name?: string[]; })[]
 *   }?
 * ]} RuleOptions
 */
/** @type {import('./rule-module.js').RuleModule<{RuleOptions: RuleOptions}>} */
export default {
    meta: {
        type: "suggestion",
        docs: {
            description: "disallow synchronous methods",
            recommended: false,
            url: "https://github.com/eslint-community/eslint-plugin-n/blob/HEAD/docs/rules/no-sync.md",
        },
        fixable: void 0,
        schema: [
            {
                type: "object",
                properties: {
                    allowAtRootLevel: {
                        type: "boolean",
                        default: false,
                    },
                    ignores: {
                        type: "array",
                        items: {
                            oneOf: [
                                { type: "string" },
                                {
                                    type: "object",
                                    properties: {
                                        from: { const: "file" },
                                        path: {
                                            type: "string",
                                        },
                                        name: {
                                            type: "array",
                                            items: {
                                                type: "string",
                                            },
                                        },
                                    },
                                    additionalProperties: false,
                                },
                                {
                                    type: "object",
                                    properties: {
                                        from: { const: "lib" },
                                        name: {
                                            type: "array",
                                            items: {
                                                type: "string",
                                            },
                                        },
                                    },
                                    additionalProperties: false,
                                },
                                {
                                    type: "object",
                                    properties: {
                                        from: { const: "package" },
                                        package: {
                                            type: "string",
                                        },
                                        name: {
                                            type: "array",
                                            items: {
                                                type: "string",
                                            },
                                        },
                                    },
                                    additionalProperties: false,
                                },
                            ],
                        },
                        default: [],
                    },
                },
                additionalProperties: false,
            },
        ],
        messages: {
            noSync: "Unexpected sync method: '{{propertyName}}'.",
        },
    },
    create(context) {
        const options = context.options[0] ?? {}
        const ignores = options.ignores ?? []

        const selector = options.allowAtRootLevel
            ? selectors.map(selector => `:function ${selector}`)
            : selectors

        const hasAdvancedIgnores = ignores.some(
            ignore => typeof ignore !== "string"
        )

        // Only require `ts-declaration-location` if needed and not already required.
        if (hasAdvancedIgnores) {
            try {
                typeMatchesSpecifier ||=
                    /** @type {import('ts-declaration-location').default} */ (
                        /** @type {unknown} */ (
                            require("ts-declaration-location")
                        )
                    )
            } catch {
                throw new Error(
                    'ts-declaration-location not available. Rule "n/no-sync" is configured to use "ignores" option with a non-string value. This requires ts-declaration-location to be available.'
                )
            }
        }

        return {
            /**
             * @param {import('estree').Identifier & {parent: import('estree').Node}} node
             * @returns {void}
             */
            [selector.join(",")](node) {
                const parserServices = getParserServices(context)

                /**
                 * @type {import('typescript').Type | undefined | null}
                 */
                let type = undefined

                /**
                 * @type {string | undefined | null}
                 */
                let fullName = undefined

                for (const ignore of ignores) {
                    if (typeof ignore === "string") {
                        if (ignore === node.name) {
                            return
                        }

                        continue
                    }

                    if (
                        parserServices === null ||
                        parserServices.program === null
                    ) {
                        throw new Error(
                            'TypeScript parser services not available. Rule "n/no-sync" is configured to use "ignores" option with a non-string value. This requires TypeScript parser services to be available.'
                        )
                    }

                    type =
                        type === undefined
                            ? getTypeOfNode(node, parserServices)
                            : type

                    fullName =
                        fullName === undefined
                            ? getFullTypeName(type)
                            : fullName

                    if (
                        type !== null &&
                        typeMatchesSpecifier?.(
                            parserServices.program,
                            ignore,
                            type
                        ) &&
                        (ignore.name === undefined ||
                            ignore.name.includes(fullName ?? node.name))
                    ) {
                        return
                    }
                }

                context.report({
                    node: node.parent,
                    messageId: "noSync",
                    data: {
                        propertyName: fullName ?? node.name,
                    },
                })
            },
        }
    },
}
