/**
 * @author Toru Nagashima
 * See LICENSE file in root directory for full license.
 */

import path from "node:path"
import globrex from "globrex"
import { Cache } from "./cache.js"
import { getAllowModules } from "./get-allow-modules.js"
import { getPackageJson } from "./get-package-json.js"

const workspacePackageJsonsCache = new Cache()
const workspacePatternMatcherCache = new Cache()

/**
 * @param {unknown} workspaces - The package.json workspaces value.
 * @returns {string[]} Workspace package patterns.
 */
function getWorkspacePatterns(workspaces) {
    if (Array.isArray(workspaces)) {
        return workspaces.map(String)
    }

    if (
        workspaces != null &&
        typeof workspaces === "object" &&
        "packages" in workspaces &&
        Array.isArray(workspaces.packages)
    ) {
        return workspaces.packages.map(String)
    }

    return []
}

/**
 * @param {string} pattern - A package.json workspace pattern.
 * @returns {string} A pattern comparable to path.posix-style relative paths.
 */
function normalizeWorkspacePattern(pattern) {
    return pattern
        .replace(/\\/gu, "/")
        .replace(/^\.\//u, "")
        .replace(/\/+$/u, "")
}

/**
 * @param {string} relativePath - A relative package directory path.
 * @param {string[]} patterns - Workspace package patterns.
 * @returns {boolean} Whether the path is included by the patterns.
 */
function matchesWorkspacePattern(relativePath, patterns) {
    let matched = false

    for (const rawPattern of patterns) {
        const negated = rawPattern.startsWith("!")
        const pattern = normalizeWorkspacePattern(
            negated ? rawPattern.slice(1) : rawPattern
        )

        if (pattern === "") {
            continue
        }

        if (getWorkspacePatternMatcher(pattern).test(relativePath)) {
            if (negated) {
                return false
            }

            matched = true
        }
    }

    return matched
}

/**
 * @param {string} pattern - A normalized package.json workspace pattern.
 * @returns {RegExp} A matcher for package paths.
 */
function getWorkspacePatternMatcher(pattern) {
    let matcher = workspacePatternMatcherCache.get(pattern)

    if (matcher == null) {
        matcher = globrex(pattern, {
            extended: true,
            globstar: true,
        }).regex
        workspacePatternMatcherCache.set(pattern, matcher)
    }

    return matcher
}

/**
 * @param {string} dir - A directory path.
 * @returns {import('type-fest').JsonObject|null} The package.json in the directory, if present.
 */
function getPackageJsonInDirectory(dir) {
    const packageInfo = getPackageJson(path.join(dir, "__placeholder__.js"))
    const filePath = path.join(dir, "package.json")

    return packageInfo != null && packageInfo.filePath === filePath
        ? packageInfo
        : null
}

/**
 * @param {import('type-fest').JsonObject} packageInfo - A package.json object.
 * @param {import('type-fest').JsonObject} workspacePackageInfo - An ancestor package.json object.
 * @returns {boolean} Whether the package is in the ancestor's workspace.
 */
function isWorkspacePackage(packageInfo, workspacePackageInfo) {
    if (
        typeof packageInfo.filePath !== "string" ||
        typeof workspacePackageInfo.filePath !== "string"
    ) {
        return false
    }

    const packageDir = path.dirname(packageInfo.filePath)
    const workspaceDir = path.dirname(workspacePackageInfo.filePath)
    const relativePath = path
        .relative(workspaceDir, packageDir)
        .replace(/\\/gu, "/")

    if (
        relativePath === "" ||
        relativePath === ".." ||
        relativePath.startsWith("../") ||
        path.isAbsolute(relativePath)
    ) {
        return false
    }

    return matchesWorkspacePattern(
        relativePath,
        getWorkspacePatterns(workspacePackageInfo.workspaces)
    )
}

/**
 * @param {import('type-fest').JsonObject} packageInfo - A package.json object.
 * @returns {import('type-fest').JsonObject[]} Matching workspace root package.json objects.
 */
function getWorkspacePackageJsons(packageInfo) {
    if (typeof packageInfo.filePath !== "string") {
        return []
    }

    const cached = workspacePackageJsonsCache.get(packageInfo.filePath)
    if (cached != null) {
        return cached
    }

    const workspacePackageJsons = []
    let dir = path.resolve(path.dirname(packageInfo.filePath), "..")
    let prevDir = ""

    do {
        const workspacePackageInfo = getPackageJsonInDirectory(dir)

        if (
            workspacePackageInfo != null &&
            isWorkspacePackage(packageInfo, workspacePackageInfo)
        ) {
            workspacePackageJsons.push(workspacePackageInfo)
            break
        }

        prevDir = dir
        dir = path.resolve(dir, "..")
    } while (dir !== prevDir)

    workspacePackageJsonsCache.set(packageInfo.filePath, workspacePackageJsons)
    return workspacePackageJsons
}

/**
 * @param {import('type-fest').JsonObject} packageInfo - A package.json object.
 * @returns {string[]} Package and dependency names.
 */
function getPackageNames(packageInfo) {
    return (
        typeof packageInfo.name === "string" ? [packageInfo.name] : []
    ).concat(
        getDependencyNames(packageInfo.dependencies),
        getDependencyNames(packageInfo.devDependencies),
        getDependencyNames(packageInfo.peerDependencies),
        getDependencyNames(packageInfo.optionalDependencies)
    )
}

/**
 * @param {unknown} dependencies - A package.json dependency object.
 * @returns {string[]} Dependency names.
 */
function getDependencyNames(dependencies) {
    return dependencies != null &&
        typeof dependencies === "object" &&
        Array.isArray(dependencies) === false
        ? Object.keys(dependencies)
        : []
}

/**
 * Get the matching DefinitelyTyped package name for a module.
 * @param {string} moduleName - An npm module name.
 * @returns {string}
 */
function getTypesPackageName(moduleName) {
    if (moduleName.startsWith("@")) {
        const [scope, name] = moduleName.slice(1).split("/")
        return `@types/${scope}__${name}`
    }

    return `@types/${moduleName}`
}

/**
 * Checks whether or not each requirement target is published via package.json.
 *
 * It reads package.json and checks the target exists in `dependencies`.
 *
 * @param {import('eslint').Rule.RuleContext} context - A context to report.
 * @param {string} filePath - The current file path.
 * @param {import('./import-target.js').ImportTarget[]} targets - A list of target information to check.
 * @returns {void}
 */
export function checkExtraneous(context, filePath, targets) {
    const packageInfo = getPackageJson(filePath)
    if (!packageInfo) {
        return
    }

    const allowed = new Set(getAllowModules(context))
    const dependencies = new Set(
        getPackageNames(packageInfo).concat(
            getWorkspacePackageJsons(packageInfo).flatMap(getPackageNames)
        )
    )

    for (const target of targets) {
        if (
            target.moduleName != null &&
            target.filePath != null &&
            !dependencies.has(target.moduleName) &&
            !(
                target.moduleStyle === "type" &&
                dependencies.has(getTypesPackageName(target.moduleName))
            ) &&
            !allowed.has(target.moduleName) &&
            // https://github.com/eslint-community/eslint-plugin-n/issues/379
            !target.hasTSAlias()
        ) {
            context.report({
                node: target.node,
                messageId: "extraneous",
                data: {
                    moduleName: target.moduleName,
                },
            })
        }
    }
}

export const messages = {
    extraneous: '"{{moduleName}}" is extraneous.',
}
