/**
 * @author Toru Nagashima
 * See LICENSE file in root directory for full license.
 */

import { READ } from "@eslint-community/eslint-utils"
import { checkForPreferGlobal } from "../../util/check-prefer-global.js"

const traceMap = {
    globals: {
        process: { [READ]: true },
    },
    modules: {
        process: { [READ]: true },
        "node:process": { [READ]: true },
    },
}

/** @type {import('../rule-module.js').RuleModule} */
export default {
    meta: {
        docs: {
            description: 'enforce either `process` or `require("process")`',
            recommended: false,
            url: "https://github.com/eslint-community/eslint-plugin-n/blob/HEAD/docs/rules/prefer-global/process.md",
        },
        type: "suggestion",
        fixable: void 0,
        schema: [{ enum: ["always", "never"] }],
        messages: {
            preferGlobal:
                "Unexpected use of 'require(\"process\")'. Use the global variable 'process' instead.",
            preferModule:
                "Unexpected use of the global variable 'process'. Use 'require(\"process\")' instead.",
        },
    },
    create(context) {
        return {
            "Program:exit"() {
                checkForPreferGlobal(context, traceMap)
            },
        }
    },
}
