import mod from "node:module"
import { fileURLToPath } from "node:url"
import { dirname } from "node:path"

const __filename = fileURLToPath(import.meta.url)
const __dirname = dirname(__filename)

const require = mod.createRequire(`${__dirname}/get-full-type-name.js`)

const ts = (() => {
    try {
        return require("typescript")
    } catch {
        return null
    }
})()

/**
 * @param {import('typescript').Type | null} type
 * @returns {string | null}
 */
export function getFullTypeName(type) {
    if (ts === null || type === null) {
        return null
    }

    /**
     * @type {string[]}
     */
    let nameParts = []
    let currentSymbol = type.getSymbol()
    while (currentSymbol !== undefined) {
        if (
            currentSymbol.valueDeclaration?.kind === ts.SyntaxKind.SourceFile ||
            currentSymbol.valueDeclaration?.kind ===
                ts.SyntaxKind.ModuleDeclaration
        ) {
            break
        }

        nameParts.unshift(currentSymbol.getName())
        currentSymbol =
            /** @type {import('typescript').Symbol & {parent: import('typescript').Symbol | undefined}} */ (
                currentSymbol
            ).parent
    }

    if (nameParts.length === 0) {
        return null
    }

    return nameParts.join(".")
}
