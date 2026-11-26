/**
 * @author Toru Nagashima
 * See LICENSE file in root directory for full license.
 */

import { checkPublish, messages } from "../util/check-publish.js"
import { schema as allowModulesSchema } from "../util/get-allow-modules.js"
import { schema as convertPathSchema } from "../util/get-convert-path.js"
import { schema as resolvePathsSchema } from "../util/get-resolve-paths.js"
import { schema as resolverConfigSchema } from "../util/get-resolver-config.js"
import { schema as tryExtensionsSchema } from "../util/get-try-extensions.js"
import { visitRequire } from "../util/visit-require.js"

/**
 * @typedef {[
 *   {
 *     allowModules?: import('../util/get-allow-modules.js').AllowModules;
 *     convertPath?: import('../util/get-convert-path.js').ConvertPath;
 *     resolvePaths?: import('../util/get-resolve-paths.js').ResolvePaths;
 *     resolverConfig?: import('../util/get-resolver-config.js').ResolverConfig;
 *     tryExtensions?: import('../util/get-try-extensions.js').TryExtensions;
 *     ignorePrivate?: boolean;
 *   }?
 * ]} RuleOptions
 */
/** @type {import('./rule-module.js').RuleModule<{RuleOptions: RuleOptions}>} */
export default {
    meta: {
        docs: {
            description:
                "disallow `require()` expressions which import private modules",
            recommended: true,
            url: "https://github.com/eslint-community/eslint-plugin-n/blob/HEAD/docs/rules/no-unpublished-require.md",
        },
        type: "problem",
        fixable: void 0,
        schema: [
            {
                type: "object",
                properties: {
                    allowModules: allowModulesSchema,
                    convertPath: convertPathSchema,
                    resolvePaths: resolvePathsSchema,
                    resolverConfig: resolverConfigSchema,
                    tryExtensions: tryExtensionsSchema,
                    ignorePrivate: { type: "boolean", default: true },
                },
                additionalProperties: false,
            },
        ],
        messages,
    },
    create(context) {
        const filePath = context.filename
        const options = context.options[0] || {}
        const ignorePrivate = options.ignorePrivate ?? true

        if (filePath === "<input>") {
            return {}
        }

        return visitRequire(context, {}, targets => {
            checkPublish(context, filePath, targets, { ignorePrivate })
        })
    },
}
