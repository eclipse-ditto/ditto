/**
 * @author Yosuke Ota
 * See LICENSE file in root directory for full license.
 */
"use strict"

const { READ, ReferenceTracker } = require("eslint-utils")

module.exports = {
    meta: {
        docs: {
            description:
                "disallow `Promise.any` function and `AggregateError` class",
            category: "ES2021",
            recommended: false,
            url:
                "http://mysticatea.github.io/eslint-plugin-es/rules/no-promise-any.html",
        },
        fixable: null,
        messages: {
            forbidden: "ES2021 '{{name}}' is forbidden.",
        },
        schema: [],
        type: "problem",
    },
    create(context) {
        return {
            "Program:exit"() {
                const tracker = new ReferenceTracker(context.getScope())
                for (const { node, path } of tracker.iterateGlobalReferences({
                    AggregateError: { [READ]: true },
                    Promise: {
                        any: { [READ]: true },
                    },
                })) {
                    context.report({
                        node,
                        messageId: "forbidden",
                        data: { name: path.join(".") },
                    })
                }
            },
        }
    },
}
