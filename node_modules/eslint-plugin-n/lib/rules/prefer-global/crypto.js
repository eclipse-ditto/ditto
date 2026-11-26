/**
 * @author Pixel998
 * See LICENSE file in root directory for full license.
 */

import { READ } from "@eslint-community/eslint-utils"
import { checkForPreferGlobal } from "../../util/check-prefer-global.js"

const traceMap = {
    globals: {
        crypto: { [READ]: true },
    },
    modules: {
        crypto: { webcrypto: { [READ]: true } },
        "node:crypto": { webcrypto: { [READ]: true } },
    },
}

/** @type {import('../rule-module.js').RuleModule} */
export default {
    meta: {
        docs: {
            description:
                'enforce either `crypto` or `require("crypto").webcrypto`',
            recommended: false,
            url: "https://github.com/eslint-community/eslint-plugin-n/blob/HEAD/docs/rules/prefer-global/crypto.md",
        },
        type: "suggestion",
        fixable: void 0,
        schema: [{ enum: ["always", "never"] }],
        messages: {
            preferGlobal:
                "Unexpected use of 'require(\"crypto\").webcrypto'. Use the global variable 'crypto' instead.",
            preferModule:
                "Unexpected use of the global variable 'crypto'. Use 'require(\"crypto\").webcrypto' instead.",
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
