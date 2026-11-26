/**
 * @author Toru Nagashima
 * See LICENSE file in root directory for full license.
 */

import { READ } from "@eslint-community/eslint-utils"
import { checkForPreferGlobal } from "../../util/check-prefer-global.js"

const traceMap = {
    globals: {
        TextEncoder: { [READ]: true },
    },
    modules: {
        util: { TextEncoder: { [READ]: true } },
        "node:util": { TextEncoder: { [READ]: true } },
    },
}

/** @type {import('../rule-module.js').RuleModule} */
export default {
    meta: {
        docs: {
            description:
                'enforce either `TextEncoder` or `require("util").TextEncoder`',
            recommended: false,
            url: "https://github.com/eslint-community/eslint-plugin-n/blob/HEAD/docs/rules/prefer-global/text-encoder.md",
        },
        type: "suggestion",
        fixable: void 0,
        schema: [{ enum: ["always", "never"] }],
        messages: {
            preferGlobal:
                "Unexpected use of 'require(\"util\").TextEncoder'. Use the global variable 'TextEncoder' instead.",
            preferModule:
                "Unexpected use of the global variable 'TextEncoder'. Use 'require(\"util\").TextEncoder' instead.",
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
