/**
 * @author Toru Nagashima
 * See LICENSE file in root directory for full license.
 */

import fs from "node:fs"
import path from "node:path"
import globrex from "globrex"
import ignoreModule from "ignore"
import { Cache } from "./cache.js"
import { exists } from "./exists.js"
import { getPackageJson } from "./get-package-json.js"

/** @type {typeof import('ignore').default} */
const ignore = /** @type {any} */ (ignoreModule)

const cache = new Cache()
const PARENT_RELATIVE_PATH = /^\.\./u
const NEVER_IGNORED =
    /^(?:readme\.[^.]*|(?:licen[cs]e|changes|changelog|history)(?:\.[^.]*)?)$/iu

/**
 * Checks whether or not a given file name is a relative path to a ancestor
 * directory.
 *
 * @param {string} filePath - A file name to check.
 * @returns {boolean} `true` if the file name is a relative path to a ancestor
 *      directory.
 */
function isAncestorFiles(filePath) {
    return PARENT_RELATIVE_PATH.test(filePath)
}

/**
 * @param {(filePath: string) => boolean} f - A function.
 * @param {(filePath: string) => boolean} g - A function.
 * @returns {(filePath: string) => boolean} A logical-and function of `f` and `g`.
 */
function and(f, g) {
    return filePath => f(filePath) && g(filePath)
}

/**
 * @param {(filePath: string) => boolean} f - A function.
 * @param {(filePath: string) => boolean} g - A function.
 * @param {(filePath: string) => boolean} [h] - A function.
 * @returns {(filePath: string) => boolean} A logical-or function of `f`, `g`, and `h`.
 */
function or(f, g, h) {
    if (h == null) {
        return filePath => f(filePath) || g(filePath)
    }

    return filePath => f(filePath) || g(filePath) || h(filePath)
}

/**
 * @param {(filePath: string) => boolean} f - A function.
 * @returns {(filePath: string) => boolean} A logical-not function of `f`.
 */
function not(f) {
    return filePath => !f(filePath)
}

/**
 * Creates a function which checks whether or not a given file is ignoreable.
 *
 * @param {import('type-fest').JsonObject} packageJson - An object of package.json.
 * @returns {(filePath: string) => boolean} A function which checks whether or not a given file is ignoreable.
 */
function filterNeverIgnoredFiles(packageJson) {
    if (typeof packageJson?.filePath !== "string") {
        return () => false
    }

    const basedir = path.dirname(packageJson.filePath)
    const mainFilePath =
        typeof packageJson.main === "string"
            ? path.join(basedir, packageJson.main)
            : null

    return filePath =>
        path.join(basedir, filePath) !== mainFilePath &&
        filePath !== "package.json" &&
        !NEVER_IGNORED.test(path.relative(basedir, filePath))
}

/**
 * Creates a function that checks whether a path is matched by an npm files
 * pattern or a file below a matched directory.
 *
 * @param {string} pattern A pattern from the package.json files field.
 * @returns {(filePath: string) => boolean} A function that checks whether a path matches the pattern.
 */
function createFilesMatcher(pattern, matchBasename = false) {
    const patterns = [pattern, `${pattern}/**`]
    if (matchBasename && !pattern.includes("/")) {
        patterns.push(`**/${pattern}`)
    }

    const matchers = expandSequenceBraces(patterns).map(value => {
        const regex = globrex(value, { extended: true, globstar: true }).regex
        return new RegExp(regex.source, `${regex.flags}i`)
    })

    return filePath => matchers.some(matcher => matcher.test(filePath))
}

/**
 * Expands the numeric and alphabetic brace sequences accepted by npm's files
 * matcher. Other brace expressions are left for globrex to handle.
 *
 * @param {string[]} patterns Patterns to expand.
 * @returns {string[]} Expanded patterns.
 */
function expandSequenceBraces(patterns) {
    const sequence = /\{(-?\d+|[a-z])\.\.(-?\d+|[a-z])(?:\.\.(-?\d+))?\}/iu
    const expanded = []

    for (const pattern of patterns) {
        const match = sequence.exec(pattern)
        if (match == null) {
            expanded.push(pattern)
            continue
        }

        const [, start, end, rawStep] = match
        if (start == null || end == null) {
            expanded.push(pattern)
            continue
        }
        const numeric = /^-?\d+$/u.test(start) && /^-?\d+$/u.test(end)
        const alphabetic = /^[a-z]$/iu.test(start) && /^[a-z]$/iu.test(end)
        const step = Number(rawStep || 1)
        if ((!numeric && !alphabetic) || !Number.isInteger(step) || step <= 0) {
            expanded.push(pattern)
            continue
        }

        const startValue = numeric ? Number(start) : start.codePointAt(0)
        const endValue = numeric ? Number(end) : end.codePointAt(0)
        if (startValue == null || endValue == null) {
            expanded.push(pattern)
            continue
        }
        const direction = startValue <= endValue ? 1 : -1
        const width = numeric && /^0\d/u.test(start) ? start.length : 0
        /**
         * @param {number} value A value in the sequence.
         * @returns {string} The corresponding replacement text.
         */
        const replacement = value => {
            if (!numeric) {
                return String.fromCodePoint(value)
            }
            const text = String(value)
            return width > 0 ? text.padStart(width, "0") : text
        }

        const prefix = pattern.slice(0, match.index)
        const suffix = pattern.slice(match.index + match[0].length)
        const replacements = []
        for (
            let value = startValue;
            direction > 0 ? value <= endValue : value >= endValue;
            value += direction * step
        ) {
            replacements.push(`${prefix}${replacement(value)}${suffix}`)
        }
        expanded.push(...expandSequenceBraces(replacements))
    }

    return expanded
}

/**
 * Checks whether a pattern requires extended glob matching beyond gitignore.
 *
 * @param {string} pattern A pattern from the package.json files field.
 * @returns {boolean} Whether the pattern uses brace expansion or extglob syntax.
 */
function hasExtendedGlob(pattern) {
    return /[{}]|[@+?!*]\(/u.test(pattern)
}

/**
 * Creates a function which checks whether or not a given file should be ignored.
 *
 * @param {unknown} files - File names of whitelist.
 * @returns {((filePath: string) => boolean) | null} A function which checks whether or not a given file should be ignored.
 */
function parseWhiteList(files) {
    if (Array.isArray(files) === false) {
        return null
    }

    const ig = ignore()
    const igN = ignore()
    /** @type {Array<(filePath: string) => boolean>} */
    const include = []
    /** @type {Array<(filePath: string) => boolean>} */
    const exclude = []

    for (const file of files) {
        if (typeof file === "string" && file) {
            const isNegated = file.startsWith("!")
            const body = path.posix
                .normalize(file.replace(/^[!/]/u, ""))
                .replace(/\/+$/u, "")

            if (hasExtendedGlob(body)) {
                if (isNegated) {
                    exclude.push(createFilesMatcher(body, true))
                } else {
                    include.push(createFilesMatcher(body))
                }
            } else if (isNegated) {
                igN.add(`${body}`)
                igN.add(`${body}/**`)
            } else {
                ig.add(`/${body}`)
                ig.add(`/${body}/**`)
            }
        }
    }

    const includeFilter = ig.createFilter()
    const excludeFilter = igN.createFilter()
    return filePath => {
        const matchesInclude = include.some(matcher => matcher(filePath))
        const matchesExclude = exclude.some(matcher => matcher(filePath))

        return (
            (includeFilter(filePath) && !matchesInclude) ||
            !excludeFilter(filePath) ||
            matchesExclude
        )
    }
}

/**
 * Creates a function which checks whether or not a given file should be ignored.
 *
 * @param {string} basedir - The directory path "package.json" exists.
 * @param {boolean} filesFieldExists - `true` if `files` field of `package.json` exists.
 * @returns {((filePath: string) => boolean)|null} A function which checks whether or not a given file should be ignored.
 */
function parseNpmignore(basedir, filesFieldExists) {
    let filePath = path.join(basedir, ".npmignore")
    if (!exists(filePath)) {
        if (filesFieldExists) {
            return null
        }

        filePath = path.join(basedir, ".gitignore")
        if (!exists(filePath)) {
            return null
        }
    }

    const ig = ignore()
    ig.add(fs.readFileSync(filePath, "utf8"))
    return not(ig.createFilter())
}

/**
 * Gets an object to check whether a given path should be ignored or not.
 * The object is created from:
 *
 * - `files` field of `package.json`
 * - `.npmignore`
 *
 * @param {string} startPath - A file path to lookup.
 * @returns {{ match: (filePath: string) => boolean }}
 *      An object to check whther or not a given path should be ignored.
 *      The object has a method `match`.
 *      `match` returns `true` if a given file path should be ignored.
 */
export function getNpmignore(startPath) {
    const retv = { match: isAncestorFiles }

    const packageJson = getPackageJson(startPath)
    if (typeof packageJson?.filePath !== "string") {
        return retv
    }

    const data = cache.get(packageJson.filePath)
    if (data) {
        return data
    }

    const filesIgnore = parseWhiteList(packageJson.files)

    const npmignoreIgnore = parseNpmignore(
        path.dirname(packageJson.filePath),
        Boolean(filesIgnore)
    )

    if (filesIgnore && npmignoreIgnore) {
        retv.match = and(
            filterNeverIgnoredFiles(packageJson),
            or(isAncestorFiles, filesIgnore, npmignoreIgnore)
        )
    } else if (filesIgnore) {
        retv.match = and(
            filterNeverIgnoredFiles(packageJson),
            or(isAncestorFiles, filesIgnore)
        )
    } else if (npmignoreIgnore) {
        retv.match = and(
            filterNeverIgnoredFiles(packageJson),
            or(isAncestorFiles, npmignoreIgnore)
        )
    }

    cache.set(packageJson.filePath, retv)

    return retv
}
