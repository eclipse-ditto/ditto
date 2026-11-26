/**
 * @author Toru Nagashima
 * See LICENSE file in root directory for full license.
 */

import { READ } from "@eslint-community/eslint-utils"
import { checkForPreferGlobal } from "../../util/check-prefer-global.js"

const traceMap = {
    globals: {
        Buffer: { [READ]: true },
    },
    modules: {
        buffer: { Buffer: { [READ]: true } },
        "node:buffer": { Buffer: { [READ]: true } },
    },
}

/** @type {import('../rule-module.js').RuleModule} */
export default {
    meta: {
        docs: {
            description:
                'enforce either `Buffer` or `require("buffer").Buffer`',
            recommended: false,
            url: "https://github.com/eslint-community/eslint-plugin-n/blob/HEAD/docs/rules/prefer-global/buffer.md",
        },
        type: "suggestion",
        fixable: void 0,
        schema: [{ enum: ["always", "never"] }],
        messages: {
            preferGlobal:
                "Unexpected use of 'require(\"buffer\").Buffer'. Use the global variable 'Buffer' instead.",
            preferModule:
                "Unexpected use of the global variable 'Buffer'. Use 'require(\"buffer\").Buffer' instead.",
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
