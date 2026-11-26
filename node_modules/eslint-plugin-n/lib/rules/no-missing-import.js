/**
 * @author Toru Nagashima
 * See LICENSE file in root directory for full license.
 */

import { checkExistence, messages } from "../util/check-existence.js"
import { schema as allowModulesSchema } from "../util/get-allow-modules.js"
import { schema as resolvePathsSchema } from "../util/get-resolve-paths.js"
import { schema as resolverConfigSchema } from "../util/get-resolver-config.js"
import { schema as tryExtensionsSchema } from "../util/get-try-extensions.js"
import { schema as tsconfigPathSchema } from "../util/get-tsconfig.js"
import { schema as typescriptExtensionMapSchema } from "../util/get-typescript-extension-map.js"
import { visitImport } from "../util/visit-import.js"

/**
 * @typedef {[
 *   {
 *     allowModules?: import('../util/get-allow-modules.js').AllowModules;
 *     resolvePaths?: import('../util/get-resolve-paths.js').ResolvePaths;
 *     resolverConfig?: import('../util/get-resolver-config.js').ResolverConfig;
 *     tryExtensions?: import('../util/get-try-extensions.js').TryExtensions;
 *     ignoreTypeImport?: boolean;
 *     tsconfigPath?: import('../util/get-tsconfig.js').TSConfigPath;
 *     typescriptExtensionMap?: import('../util/get-typescript-extension-map.js').TypescriptExtensionMap;
 *   }?
 * ]} RuleOptions
 */
/** @type {import('./rule-module.js').RuleModule<{RuleOptions: RuleOptions}>} */
export default {
    meta: {
        docs: {
            description:
                "disallow `import` declarations which import missing modules",
            recommended: true,
            url: "https://github.com/eslint-community/eslint-plugin-n/blob/HEAD/docs/rules/no-missing-import.md",
        },
        type: "problem",
        fixable: void 0,
        schema: [
            {
                type: "object",
                properties: {
                    allowModules: allowModulesSchema,
                    resolvePaths: resolvePathsSchema,
                    resolverConfig: resolverConfigSchema,
                    tryExtensions: tryExtensionsSchema,
                    ignoreTypeImport: { type: "boolean", default: false },
                    tsconfigPath: tsconfigPathSchema,
                    typescriptExtensionMap: typescriptExtensionMapSchema,
                },
                additionalProperties: false,
            },
        ],
        messages,
    },
    create(context) {
        const options = context.options[0] ?? {}
        const ignoreTypeImport = options.ignoreTypeImport ?? false

        const filePath = context.filename
        if (filePath === "<input>") {
            return {}
        }

        return visitImport(context, { ignoreTypeImport }, targets => {
            checkExistence(context, targets)
        })
    },
}
