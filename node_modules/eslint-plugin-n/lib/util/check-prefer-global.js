/**
 * @author Toru Nagashima
 * See LICENSE file in root directory for full license.
 */
"use strict"

const { ReferenceTracker } = require("@eslint-community/eslint-utils")

/**
 * @typedef TraceMap
 * @property {import('@eslint-community/eslint-utils').TraceMap<boolean>} globals
 * @property {import('@eslint-community/eslint-utils').TraceMap<boolean>} modules
 */

/**
 * Verifier for `prefer-global/*` rules.
 */
class Verifier {
    /**
     * Initialize this instance.
     * @param {import('eslint').Rule.RuleContext} context The rule context to report.
     * @param {TraceMap} traceMap The track map.
     */
    constructor(context, traceMap) {
        this.context = context
        this.traceMap = traceMap
        this.verify =
            context.options[0] === "never"
                ? this.verifyToPreferModules
                : this.verifyToPreferGlobals
    }

    /**
     * Verify the code to suggest the use of globals.
     * @returns {void}
     */
    verifyToPreferGlobals() {
        const { context, traceMap } = this
        const sourceCode = context.sourceCode ?? context.getSourceCode() // TODO: just use context.sourceCode when dropping eslint < v9
        const scope =
            sourceCode.getScope?.(sourceCode.ast) ?? context.getScope() //TODO: remove context.getScope() when dropping support for ESLint < v9
        const tracker = new ReferenceTracker(scope, {
            mode: "legacy",
        })

        for (const { node } of [
            ...tracker.iterateCjsReferences(traceMap.modules),
            ...tracker.iterateEsmReferences(traceMap.modules),
        ]) {
            context.report({ node, messageId: "preferGlobal" })
        }
    }

    /**
     * Verify the code to suggest the use of modules.
     * @returns {void}
     */
    verifyToPreferModules() {
        const { context, traceMap } = this
        const sourceCode = context.sourceCode ?? context.getSourceCode() // TODO: just use context.sourceCode when dropping eslint < v9
        const scope =
            sourceCode.getScope?.(sourceCode.ast) ?? context.getScope() //TODO: remove context.getScope() when dropping support for ESLint < v9
        const tracker = new ReferenceTracker(scope)

        for (const { node } of tracker.iterateGlobalReferences(
            traceMap.globals
        )) {
            context.report({ node, messageId: "preferModule" })
        }
    }
}

/**
 * @param {import('eslint').Rule.RuleContext} context
 * @param {TraceMap} traceMap
 * @returns {void}
 */
module.exports = function checkForPreferGlobal(context, traceMap) {
    new Verifier(context, traceMap).verify()
}
