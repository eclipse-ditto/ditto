/**
 * @author Toru Nagashima
 * See LICENSE file in root directory for full license.
 */
"use strict"

const { CALL, ReferenceTracker } = require("@eslint-community/eslint-utils")

/** @type {import('@eslint-community/eslint-utils').TraceMap<boolean>} */
const traceMap = {
    fs: {
        access: { [CALL]: true },
        copyFile: { [CALL]: true },
        open: { [CALL]: true },
        rename: { [CALL]: true },
        truncate: { [CALL]: true },
        rmdir: { [CALL]: true },
        mkdir: { [CALL]: true },
        readdir: { [CALL]: true },
        readlink: { [CALL]: true },
        symlink: { [CALL]: true },
        lstat: { [CALL]: true },
        stat: { [CALL]: true },
        link: { [CALL]: true },
        unlink: { [CALL]: true },
        chmod: { [CALL]: true },
        lchmod: { [CALL]: true },
        lchown: { [CALL]: true },
        chown: { [CALL]: true },
        utimes: { [CALL]: true },
        realpath: { [CALL]: true },
        mkdtemp: { [CALL]: true },
        writeFile: { [CALL]: true },
        appendFile: { [CALL]: true },
        readFile: { [CALL]: true },
    },
}
traceMap["node:fs"] = traceMap.fs

/** @type {import('eslint').Rule.RuleModule} */
module.exports = {
    meta: {
        docs: {
            description: 'enforce `require("fs").promises`',
            recommended: false,
            url: "https://github.com/eslint-community/eslint-plugin-n/blob/HEAD/docs/rules/prefer-promises/fs.md",
        },
        fixable: null,
        messages: {
            preferPromises: "Use 'fs.promises.{{name}}()' instead.",
        },
        schema: [],
        type: "suggestion",
    },

    create(context) {
        return {
            "Program:exit"(node) {
                const sourceCode = context.sourceCode ?? context.getSourceCode() // TODO: just use context.sourceCode when dropping eslint < v9
                const scope = sourceCode.getScope?.(node) ?? context.getScope() //TODO: remove context.getScope() when dropping support for ESLint < v9
                const tracker = new ReferenceTracker(scope, { mode: "legacy" })
                const references = [
                    ...tracker.iterateCjsReferences(traceMap),
                    ...tracker.iterateEsmReferences(traceMap),
                ]

                for (const { node, path } of references) {
                    const name = path[path.length - 1]
                    context.report({
                        node,
                        messageId: "preferPromises",
                        data: { name },
                    })
                }
            },
        }
    },
}
