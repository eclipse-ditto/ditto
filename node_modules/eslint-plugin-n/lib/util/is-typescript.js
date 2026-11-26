import path from "node:path"

const typescriptExtensions = [".ts", ".tsx", ".cts", ".mts"]

/**
 * Determine if the context source file is typescript.
 *
 * @param {import('eslint').Rule.RuleContext} context - A context
 * @returns {boolean}
 */
export function isTypescript(context) {
    const sourceFileExt = path.extname(
        context.physicalFilename ?? context.filename
    )
    return typescriptExtensions.includes(sourceFileExt)
}
