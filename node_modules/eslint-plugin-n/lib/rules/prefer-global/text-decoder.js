/**
 * @author Toru Nagashima
 * See LICENSE file in root directory for full license.
 */

import { READ } from "@eslint-community/eslint-utils"
import { checkForPreferGlobal } from "../../util/check-prefer-global.js"

const traceMap = {
    globals: {
        TextDecoder: { [READ]: true },
    },
    modules: {
        util: { TextDecoder: { [READ]: true } },
        "node:util": { TextDecoder: { [READ]: true } },
    },
}

/** @type {import('../rule-module.js').RuleModule} */
export default {
    meta: {
        docs: {
            description:
                'enforce either `TextDecoder` or `require("util").TextDecoder`',
            recommended: false,
            url: "https://github.com/eslint-community/eslint-plugin-n/blob/HEAD/docs/rules/prefer-global/text-decoder.md",
        },
        type: "suggestion",
        fixable: void 0,
        schema: [{ enum: ["always", "never"] }],
        messages: {
            preferGlobal:
                "Unexpected use of 'require(\"util\").TextDecoder'. Use the global variable 'TextDecoder' instead.",
            preferModule:
                "Unexpected use of the global variable 'TextDecoder'. Use 'require(\"util\").TextDecoder' instead.",
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
