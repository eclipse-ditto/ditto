/**
 * @author Toru Nagashima
 * See LICENSE file in root directory for full license.
 */

import { READ } from "@eslint-community/eslint-utils"
import { checkForPreferGlobal } from "../../util/check-prefer-global.js"

const traceMap = {
    globals: {
        URL: { [READ]: true },
    },
    modules: {
        url: { URL: { [READ]: true } },
        "node:url": { URL: { [READ]: true } },
    },
}

/** @type {import('../rule-module.js').RuleModule} */
export default {
    meta: {
        docs: {
            description: 'enforce either `URL` or `require("url").URL`',
            recommended: false,
            url: "https://github.com/eslint-community/eslint-plugin-n/blob/HEAD/docs/rules/prefer-global/url.md",
        },
        type: "suggestion",
        fixable: void 0,
        schema: [{ enum: ["always", "never"] }],
        messages: {
            preferGlobal:
                "Unexpected use of 'require(\"url\").URL'. Use the global variable 'URL' instead.",
            preferModule:
                "Unexpected use of the global variable 'URL'. Use 'require(\"url\").URL' instead.",
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
