/**
 * @author Toru Nagashima
 * See LICENSE file in root directory for full license.
 */

import { CALL, ReferenceTracker } from "@eslint-community/eslint-utils"
import { iterateProcessGetBuiltinModuleReferences } from "../../util/iterate-process-get-builtin-module-references.js"

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
        cp: { [CALL]: true },
        glob: { [CALL]: true },
        lutimes: { [CALL]: true },
        opendir: { [CALL]: true },
        rm: { [CALL]: true },
        statfs: { [CALL]: true },
    },
}
traceMap["node:fs"] = traceMap.fs

/** @type {import('../rule-module.js').RuleModule} */
export default {
    meta: {
        docs: {
            description: 'enforce `require("fs").promises`',
            recommended: false,
            url: "https://github.com/eslint-community/eslint-plugin-n/blob/HEAD/docs/rules/prefer-promises/fs.md",
        },
        fixable: void 0,
        messages: {
            preferPromises: "Use 'fs.promises.{{name}}()' instead.",
        },
        schema: [],
        type: "suggestion",
    },
    create(context) {
        return {
            "Program:exit"() {
                const scope = context.sourceCode.getScope(
                    context.sourceCode.ast
                )
                const tracker = new ReferenceTracker(scope, { mode: "legacy" })
                const references = [
                    ...tracker.iterateCjsReferences(traceMap),
                    ...iterateProcessGetBuiltinModuleReferences(
                        tracker,
                        traceMap
                    ),
                    ...tracker.iterateEsmReferences(traceMap),
                ]

                for (const { node, path } of references) {
                    const name = path.at(-1)
                    if (name == null) {
                        continue
                    }

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
