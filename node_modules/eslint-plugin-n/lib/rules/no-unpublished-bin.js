/**
 * @author Toru Nagashima
 * See LICENSE file in root directory for full license.
 */

import path from "node:path"
import { getConvertPath, schema } from "../util/get-convert-path.js"
import { getNpmignore } from "../util/get-npmignore.js"
import { getPackageJson } from "../util/get-package-json.js"
import { isBinFile } from "../util/is-bin-file.js"

/** @type {import('./rule-module.js').RuleModule} */
export default {
    meta: {
        docs: {
            description: "disallow `bin` files that npm ignores",
            url: "https://github.com/eslint-community/eslint-plugin-n/blob/HEAD/docs/rules/no-unpublished-bin.md",
        },
        type: "problem",
        fixable: void 0,
        schema: [
            {
                type: "object",
                properties: {
                    //
                    convertPath: schema,
                },
                additionalProperties: false,
            },
        ],
        messages: {
            invalidIgnored:
                "npm ignores '{{name}}'. Check 'files' field of 'package.json' or '.npmignore'.",
        },
    },
    create(context) {
        return {
            Program(node) {
                // Check file path.
                let rawFilePath = context.filename
                if (rawFilePath === "<input>") {
                    return
                }
                rawFilePath = path.resolve(rawFilePath)

                // Find package.json
                const packageJson = getPackageJson(rawFilePath)
                if (typeof packageJson?.filePath !== "string") {
                    return {}
                }

                // Convert by convertPath option
                const basedir = path.dirname(packageJson.filePath)
                const relativePath = getConvertPath(context)(
                    path.relative(basedir, rawFilePath).replace(/\\/gu, "/")
                )
                const filePath = path.join(basedir, relativePath)

                // Check this file is bin.
                if (!isBinFile(filePath, packageJson.bin, basedir)) {
                    return
                }

                // Check ignored or not
                const npmignore = getNpmignore(filePath)
                if (!npmignore.match(relativePath)) {
                    return
                }

                // Report.
                context.report({
                    node,
                    messageId: "invalidIgnored",
                    data: { name: relativePath },
                })
            },
        }
    },
}
