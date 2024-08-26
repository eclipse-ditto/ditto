/**
 * @author Toru Nagashima
 * See LICENSE file in root directory for full license.
 */
"use strict"

const path = require("path")
const fs = require("fs")
const { convertTsExtensionToJs } = require("../util/map-typescript-extension")
const visitImport = require("../util/visit-import")

/**
 * Get all file extensions of the files which have the same basename.
 * @param {string} filePath The path to the original file to check.
 * @returns {string[]} File extensions.
 */
function getExistingExtensions(filePath) {
    const directory = path.dirname(filePath)
    const extension = path.extname(filePath)
    const basename = path.basename(filePath, extension)

    try {
        return fs
            .readdirSync(directory)
            .filter(filename => filename.startsWith(`${basename}.`))
            .map(filename => path.extname(filename))
    } catch {
        return []
    }
}

/** @type {import('eslint').Rule.RuleModule} */
module.exports = {
    meta: {
        docs: {
            description:
                "enforce the style of file extensions in `import` declarations",
            recommended: false,
            url: "https://github.com/eslint-community/eslint-plugin-n/blob/HEAD/docs/rules/file-extension-in-import.md",
        },
        fixable: "code",
        messages: {
            requireExt: "require file extension '{{ext}}'.",
            forbidExt: "forbid file extension '{{ext}}'.",
        },
        schema: [
            {
                enum: ["always", "never"],
            },
            {
                type: "object",
                properties: {},
                additionalProperties: {
                    enum: ["always", "never"],
                },
            },
        ],
        type: "suggestion",
    },
    create(context) {
        if ((context.filename ?? context.getFilename()).startsWith("<")) {
            return {}
        }
        const defaultStyle = context.options[0] || "always"
        const overrideStyle = context.options[1] || {}

        /**
         * @param {import("../util/import-target.js")} target
         * @returns {void}
         */
        function verify({ filePath, name, node, moduleType }) {
            // Ignore if it's not resolved to a file or it's a bare module.
            if (
                (moduleType !== "relative" && moduleType !== "absolute") ||
                filePath == null
            ) {
                return
            }

            // Get extension.
            const currentExt = path.extname(name)
            const actualExt = path.extname(filePath)
            const style = overrideStyle[actualExt] || defaultStyle

            const expectedExt = convertTsExtensionToJs(
                context,
                filePath,
                actualExt
            )

            // Verify.
            if (style === "always" && currentExt !== expectedExt) {
                context.report({
                    node,
                    messageId: "requireExt",
                    data: { ext: expectedExt },
                    fix(fixer) {
                        const index =
                            /** @type {[number, number]} */ (node.range)[1] - 1
                        return fixer.insertTextBeforeRange(
                            [index, index],
                            expectedExt
                        )
                    },
                })
            }

            if (
                style === "never" &&
                currentExt !== "" &&
                expectedExt !== "" &&
                currentExt === expectedExt
            ) {
                const otherExtensions = getExistingExtensions(filePath)

                context.report({
                    node,
                    messageId: "forbidExt",
                    data: { ext: currentExt },
                    fix:
                        otherExtensions.length > 1
                            ? undefined
                            : fixer => {
                                  const index = name.lastIndexOf(currentExt)
                                  const start =
                                      /** @type {[number, number]} */ (
                                          node.range
                                      )[0] +
                                      1 +
                                      index
                                  const end = start + currentExt.length
                                  return fixer.removeRange([start, end])
                              },
                })
            }
        }

        return visitImport(context, { optionIndex: 1 }, targets => {
            targets.forEach(verify)
        })
    },
}
