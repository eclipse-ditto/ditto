/**
 * @author Toru Nagashima <https://github.com/mysticatea>
 * See LICENSE file in root directory for full license.
 */

import { Range } from "semver"
import { getPackageJson } from "./get-package-json.js"
import { getSemverRange } from "./get-semver-range.js"

const fallbackRange = new Range(">=16.0.0")

/**
 * @typedef {{ version:? string } | undefined} VersionOption
 */

/**
 * Gets `version` property from a given option object.
 *
 * @param {VersionOption} option - An option object to get.
 * @returns {import("semver").Range|undefined} The `allowModules` value, or `null`.
 */
function getVersionRange(option) {
    if (option?.version) {
        return getSemverRange(option.version)
    }
}
/**
 * @typedef {{ [EngineName in 'npm' | 'node' | string]?: string }} Engines
 */
/**
 * @typedef {{ name?: string, version?: string, onFail?: string }} DevEngineEntry
 */
/**
 * Get the `engines.node` field of package.json.
 * @param {import('eslint').Rule.RuleContext} context The path to the current linting file.
 * @returns {import("semver").Range | undefined} The range object of the `engines.node` field.
 */
function getEnginesNode(context) {
    const filename = context.filename
    const info = getPackageJson(filename)
    if (
        info?.engines != null &&
        typeof info?.engines === "object" &&
        "node" in info.engines &&
        typeof info?.engines?.node === "string"
    ) {
        return getSemverRange(info.engines.node)
    }
}

/**
 * Get the `devEngines.runtime` (`name: "node"`) version from package.json.
 * `devEngines.runtime` may be a single object or an array of objects.
 * See https://docs.npmjs.com/cli/v11/configuring-npm/package-json#devengines
 * @param {import('eslint').Rule.RuleContext} context The path to the current linting file.
 * @returns {import("semver").Range | undefined} The range object of the matching `devEngines.runtime.version`.
 */
function getDevEnginesNode(context) {
    const filename = context.filename
    const info = getPackageJson(filename)
    if (
        info?.devEngines == null ||
        typeof info.devEngines !== "object" ||
        Array.isArray(info.devEngines) ||
        !("runtime" in info.devEngines) ||
        info.devEngines.runtime == null
    ) {
        return
    }
    const runtime = info.devEngines.runtime
    const entries = /** @type {DevEngineEntry[]} */ (
        Array.isArray(runtime) ? runtime : [runtime]
    )
    for (const entry of entries) {
        if (
            entry != null &&
            typeof entry === "object" &&
            entry.name === "node" &&
            typeof entry.version === "string"
        ) {
            return getSemverRange(entry.version)
        }
    }
}

/**
 * Gets version configuration.
 *
 * 1. Parse a given version then return it if it's valid.
 * 2. Look package.json up and parse `engines.node` then return it if it's valid.
 * 3. Look package.json up and parse `devEngines.runtime` (`name: "node"`) then return it if it's valid.
 * 4. Return `>=16.0.0`.
 *
 * @param {import('eslint').Rule.RuleContext} context The version range text.
 * This will be used to look package.json up if `version` is not a valid version range.
 * @returns {import("semver").Range} The configured version range.
 */
export function getConfiguredNodeVersion(context) {
    return (
        getVersionRange(/** @type {VersionOption} */ (context.options?.[0])) ??
        getVersionRange(/** @type {VersionOption} */ (context.settings?.n)) ??
        getVersionRange(
            /** @type {VersionOption} */ (context.settings?.node)
        ) ??
        getEnginesNode(context) ??
        getDevEnginesNode(context) ??
        fallbackRange
    )
}

export const schema = {
    type: "string",
}
