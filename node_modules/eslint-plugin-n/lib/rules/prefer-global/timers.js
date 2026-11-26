/**
 * @author Pixel998
 * See LICENSE file in root directory for full license.
 */

import { READ } from "@eslint-community/eslint-utils"
import { checkForPreferGlobal } from "../../util/check-prefer-global.js"

const timers = {
    clearImmediate: { [READ]: true },
    clearInterval: { [READ]: true },
    clearTimeout: { [READ]: true },
    setImmediate: { [READ]: true },
    setInterval: { [READ]: true },
    setTimeout: { [READ]: true },
}

const traceMap = {
    globals: timers,
    modules: {
        timers,
        "node:timers": timers,
    },
}

/** @type {import('../rule-module.js').RuleModule} */
export default {
    meta: {
        docs: {
            description:
                'enforce either global timer functions or `require("timers")`',
            recommended: false,
            url: "https://github.com/eslint-community/eslint-plugin-n/blob/HEAD/docs/rules/prefer-global/timers.md",
        },
        type: "suggestion",
        fixable: void 0,
        schema: [{ enum: ["always", "never"] }],
        messages: {
            preferGlobal:
                "Unexpected use of 'require(\"timers\").*'. Use the global variable instead.",
            preferModule:
                "Unexpected use of the global variable. Use 'require(\"timers\").*' instead.",
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
