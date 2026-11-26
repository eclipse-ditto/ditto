/**
 * @author Toru Nagashima
 * See LICENSE file in root directory for full license.
 */

import {
    CALL,
    CONSTRUCT,
    ReferenceTracker,
} from "@eslint-community/eslint-utils"
import { iterateProcessGetBuiltinModuleReferences } from "../../util/iterate-process-get-builtin-module-references.js"

/** @type {import('@eslint-community/eslint-utils').TraceMap<boolean>} */
const dns = {
    lookup: { [CALL]: true },
    lookupService: { [CALL]: true },
    Resolver: { [CONSTRUCT]: true },
    getServers: { [CALL]: true },
    resolve: { [CALL]: true },
    resolve4: { [CALL]: true },
    resolve6: { [CALL]: true },
    resolveAny: { [CALL]: true },
    resolveCname: { [CALL]: true },
    resolveMx: { [CALL]: true },
    resolveNaptr: { [CALL]: true },
    resolveNs: { [CALL]: true },
    resolvePtr: { [CALL]: true },
    resolveSoa: { [CALL]: true },
    resolveSrv: { [CALL]: true },
    resolveTxt: { [CALL]: true },
    reverse: { [CALL]: true },
    setServers: { [CALL]: true },
}

/** @type {import('@eslint-community/eslint-utils').TraceMap<boolean>} */
const traceMap = {
    dns: dns,
    "node:dns": dns,
}

/** @type {import('../rule-module.js').RuleModule} */
export default {
    meta: {
        docs: {
            description: 'enforce `require("dns").promises`',
            recommended: false,
            url: "https://github.com/eslint-community/eslint-plugin-n/blob/HEAD/docs/rules/prefer-promises/dns.md",
        },
        fixable: void 0,
        messages: {
            preferPromises: "Use 'dns.promises.{{name}}()' instead.",
            preferPromisesNew: "Use 'new dns.promises.{{name}}()' instead.",
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
                    const name = path[path.length - 1]
                    if (name == null) {
                        continue
                    }
                    const firstLetter = name[0]
                    if (firstLetter == null) {
                        continue
                    }
                    const isClass = firstLetter === firstLetter.toUpperCase()
                    context.report({
                        node,
                        messageId: isClass
                            ? "preferPromisesNew"
                            : "preferPromises",
                        data: { name },
                    })
                }
            },
        }
    },
}
