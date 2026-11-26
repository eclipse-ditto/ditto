/**
 * @author Toru Nagashima
 * See LICENSE file in root directory for full license.
 */

import { checkExtraneous, messages } from "../util/check-extraneous.js"
import { schema as allowedModuleSchema } from "../util/get-allow-modules.js"
import { schema as convertPathSchema } from "../util/get-convert-path.js"
import { schema as resolvePathSchema } from "../util/get-resolve-paths.js"
import { schema as resolverConfigSchema } from "../util/get-resolver-config.js"
import { visitImport } from "../util/visit-import.js"

/** @type {import('./rule-module.js').RuleModule} */
export default {
    meta: {
        docs: {
            description:
                "disallow `import` declarations which import extraneous modules",
            recommended: true,
            url: "https://github.com/eslint-community/eslint-plugin-n/blob/HEAD/docs/rules/no-extraneous-import.md",
        },
        type: "problem",
        fixable: void 0,
        schema: [
            {
                type: "object",
                properties: {
                    allowModules: allowedModuleSchema,
                    convertPath: convertPathSchema,
                    resolvePaths: resolvePathSchema,
                    resolverConfig: resolverConfigSchema,
                },
                additionalProperties: false,
            },
        ],
        messages,
    },
    create(context) {
        const filePath = context.filename
        if (filePath === "<input>") {
            return {}
        }

        return visitImport(context, {}, targets => {
            checkExtraneous(context, filePath, targets)
        })
    },
}
