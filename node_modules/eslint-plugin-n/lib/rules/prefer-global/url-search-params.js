/**
 * @author Toru Nagashima
 * See LICENSE file in root directory for full license.
 */

import { READ } from "@eslint-community/eslint-utils"
import { checkForPreferGlobal } from "../../util/check-prefer-global.js"

const traceMap = {
    globals: {
        URLSearchParams: { [READ]: true },
    },
    modules: {
        url: { URLSearchParams: { [READ]: true } },
        "node:url": { URLSearchParams: { [READ]: true } },
    },
}

/** @type {import('../rule-module.js').RuleModule} */
export default {
    meta: {
        docs: {
            description:
                'enforce either `URLSearchParams` or `require("url").URLSearchParams`',
            recommended: false,
            url: "https://github.com/eslint-community/eslint-plugin-n/blob/HEAD/docs/rules/prefer-global/url-search-params.md",
        },
        type: "suggestion",
        fixable: void 0,
        schema: [{ enum: ["always", "never"] }],
        messages: {
            preferGlobal:
                "Unexpected use of 'require(\"url\").URLSearchParams'. Use the global variable 'URLSearchParams' instead.",
            preferModule:
                "Unexpected use of the global variable 'URLSearchParams'. Use 'require(\"url\").URLSearchParams' instead.",
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
