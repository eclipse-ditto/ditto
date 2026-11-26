/**
 * @author Toru Nagashima
 * See LICENSE file in root directory for full license.
 */

import { checkForRestriction, messages } from "../util/check-restricted.js"
import { visitImport as visit } from "../util/visit-import.js"

/** @type {import('./rule-module.js').RuleModule} */
export default {
    meta: {
        type: "suggestion",
        docs: {
            description:
                "disallow specified modules when loaded by `import` declarations",
            recommended: false,
            url: "https://github.com/eslint-community/eslint-plugin-n/blob/HEAD/docs/rules/no-restricted-import.md",
        },
        fixable: void 0,
        schema: [
            {
                type: "array",
                items: {
                    anyOf: [
                        { type: "string" },
                        {
                            type: "object",
                            properties: {
                                name: {
                                    anyOf: [
                                        { type: "string" },
                                        {
                                            type: "array",
                                            items: { type: "string" },
                                            additionalItems: false,
                                        },
                                    ],
                                },
                                message: { type: "string" },
                            },
                            additionalProperties: false,
                            required: ["name"],
                        },
                    ],
                },
                additionalItems: false,
            },
        ],
        messages,
    },
    create(context) {
        const opts = { includeCore: true }
        return visit(context, opts, targets =>
            checkForRestriction(context, targets)
        )
    },
}
