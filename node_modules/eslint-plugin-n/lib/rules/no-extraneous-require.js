/**
 * @author Toru Nagashima
 * See LICENSE file in root directory for full license.
 */

import { checkExtraneous, messages } from "../util/check-extraneous.js"
import { schema as allowedModuleSchema } from "../util/get-allow-modules.js"
import { schema as convertPathSchema } from "../util/get-convert-path.js"
import { schema as resolvePathSchema } from "../util/get-resolve-paths.js"
import { schema as resolverConfigSchema } from "../util/get-resolver-config.js"
import { schema as tryExtensionsSchema } from "../util/get-try-extensions.js"
import { visitRequire } from "../util/visit-require.js"

/** @type {import('./rule-module.js').RuleModule} */
export default {
    meta: {
        docs: {
            description:
                "disallow `require()` expressions which import extraneous modules",
            recommended: true,
            url: "https://github.com/eslint-community/eslint-plugin-n/blob/HEAD/docs/rules/no-extraneous-require.md",
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
                    tryExtensions: tryExtensionsSchema,
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

        return visitRequire(context, {}, targets => {
            checkExtraneous(context, filePath, targets)
        })
    },
}
