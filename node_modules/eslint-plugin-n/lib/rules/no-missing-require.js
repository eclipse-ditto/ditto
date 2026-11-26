/**
 * @author Toru Nagashima
 * See LICENSE file in root directory for full license.
 */

import { checkExistence, messages } from "../util/check-existence.js"
import { schema as allowModulesSchema } from "../util/get-allow-modules.js"
import { schema as resolvePathsSchema } from "../util/get-resolve-paths.js"
import { schema as resolverConfigSchema } from "../util/get-resolver-config.js"
import { schema as tsconfigPathSchema } from "../util/get-tsconfig.js"
import { schema as tryExtensionsSchema } from "../util/get-try-extensions.js"
import { schema as typescriptExtensionMapSchema } from "../util/get-typescript-extension-map.js"
import { visitRequire } from "../util/visit-require.js"

/** @type {import('./rule-module.js').RuleModule} */
export default {
    meta: {
        docs: {
            description:
                "disallow `require()` expressions which import missing modules",
            recommended: true,
            url: "https://github.com/eslint-community/eslint-plugin-n/blob/HEAD/docs/rules/no-missing-require.md",
        },
        type: "problem",
        fixable: void 0,
        schema: [
            {
                type: "object",
                properties: {
                    allowModules: allowModulesSchema,
                    tryExtensions: tryExtensionsSchema,
                    resolvePaths: resolvePathsSchema,
                    resolverConfig: resolverConfigSchema,
                    typescriptExtensionMap: typescriptExtensionMapSchema,
                    tsconfigPath: tsconfigPathSchema,
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
            checkExistence(context, targets)
        })
    },
}
