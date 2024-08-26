/**
 * @author Toru Nagashima
 * See LICENSE file in root directory for full license.
 */
"use strict"

const { resolve } = require("path")
const { isBuiltin } = require("node:module")
const resolver = require("enhanced-resolve")

const isTypescript = require("./is-typescript")
const { getTSConfigForContext } = require("./get-tsconfig.js")
const getTypescriptExtensionMap = require("./get-typescript-extension-map")

/**
 * @overload
 * @param {string[]} input
 * @returns {string[]}
 */
/**
 * @overload
 * @param {string} input
 * @returns {string}
 */
/**
 * @param {string | string[]} input
 * @returns {string | string[]}
 */
function removeTrailWildcard(input) {
    if (typeof input === "string") {
        return input.replace(/[/\\*]+$/, "")
    }

    return input.map(removeTrailWildcard)
}

/**
 * Initialize this instance.
 * @param {import('eslint').Rule.RuleContext} context - The context for the import origin.
 * @returns {import('enhanced-resolve').ResolveOptions['alias'] | undefined}
 */
function getTSConfigAliases(context) {
    const tsConfig = getTSConfigForContext(context)

    const paths = tsConfig?.config?.compilerOptions?.paths

    if (paths == null) {
        return
    }

    return Object.entries(paths).map(([name, alias]) => ({
        name: removeTrailWildcard(name),
        alias: removeTrailWildcard(alias),
    }))
}

/**
 * @typedef Options
 * @property {string[]} [extensions]
 * @property {string[]} [paths]
 * @property {string} basedir
 */
/** @typedef { 'unknown' | 'relative' | 'absolute' | 'node' | 'npm' | 'http' } ModuleType */
/** @typedef { 'import' | 'require' | 'type' } ModuleStyle */

/**
 * @param {string} string The string to manipulate
 * @param {string} matcher The character to use as a segmenter
 * @param {Number} [count=1] How many segments to keep
 * @returns {string}
 */
function trimAfter(string, matcher, count = 1) {
    return string.split(matcher).slice(0, count).join(matcher)
}

/** @typedef {import('estree').Node & { parent?: Node }} Node */

/**
 * Information of an import target.
 */
module.exports = class ImportTarget {
    /**
     * Initialize this instance.
     * @param {import('eslint').Rule.RuleContext} context - The context for the import origin.
     * @param {Node} node - The node of a `require()` or a module declaraiton.
     * @param {string} name - The name of an import target.
     * @param {Options} options - The options of `enhanced-resolve` module.
     * @param {'import' | 'require'} moduleType - whether the target was require-ed or imported
     */
    constructor(context, node, name, options, moduleType) {
        /**
         * The context for the import origin
         * @type {import('eslint').Rule.RuleContext}
         */
        this.context = context

        /**
         * The node of a `require()` or a module declaraiton.
         * @type {Node}
         */
        this.node = node

        /**
         * The name of this import target.
         * @type {string}
         */
        this.name = name

        /**
         * The import target options.
         * @type {Options}
         */
        this.options = options

        /**
         * What type of module are we looking for?
         * @type {ModuleType}
         */
        this.moduleType = this.getModuleType()

        /**
         * What import style are we using
         * @type {ModuleStyle}
         */
        this.moduleStyle = this.getModuleStyle(moduleType)

        /**
         * The module name of this import target.
         * If the target is a relative path then this is `null`.
         * @type {string | undefined}
         */
        this.moduleName = this.getModuleName()

        /**
         * This is the full resolution failure reasons
         * @type {string | null}
         */
        this.resolveError = null

        /**
         * The full path of this import target.
         * If the target is a module and it does not exist then this is `null`.
         * @type {string | null}
         */
        this.filePath = this.getFilePath()
    }

    /**
     * What type of module is this
     * @returns {ModuleType}
     */
    getModuleType() {
        if (/^\.{1,2}([\\/]|$)/.test(this.name)) {
            return "relative"
        }

        if (/^[\\/]/.test(this.name)) {
            return "absolute"
        }

        if (isBuiltin(this.name)) {
            return "node"
        }

        if (/^(@[\w~-][\w.~-]*\/)?[\w~-][\w.~-]*/.test(this.name)) {
            return "npm"
        }

        if (/^https?:\/\//.test(this.name)) {
            return "http"
        }

        return "unknown"
    }

    /**
     * What module import style is used
     * @param {'import' | 'require'} fallback
     * @returns {ModuleStyle}
     */
    getModuleStyle(fallback) {
        let node = this.node

        do {
            if (node.parent == null) {
                break
            }

            // `const {} = require('')`
            if (
                node.parent.type === "CallExpression" &&
                node.parent.callee.type === "Identifier" &&
                node.parent.callee.name === "require"
            ) {
                return "require"
            }

            // `import {} from '';`
            if (node.parent.type === "ImportDeclaration") {
                // `import type {} from '';`
                return "importKind" in node.parent &&
                    node.parent.importKind === "type"
                    ? "type"
                    : "import"
            }

            node = node.parent
        } while (node.parent)

        return fallback
    }

    /**
     * Get the node or npm module name
     * @returns {string | undefined}
     */
    getModuleName() {
        if (this.moduleType === "relative") return

        if (this.moduleType === "npm") {
            if (this.name.startsWith("@")) {
                return trimAfter(this.name, "/", 2)
            }

            return trimAfter(this.name, "/")
        }

        if (this.moduleType === "node") {
            if (this.name.startsWith("node:")) {
                return trimAfter(this.name.slice(5), "/")
            }

            return trimAfter(this.name, "/")
        }
    }

    /**
     * @returns {string[]}
     */
    getPaths() {
        if (Array.isArray(this.options.paths)) {
            return [...this.options.paths, this.options.basedir]
        }

        return [this.options.basedir]
    }

    /**
     * @param {string} baseDir
     * @param {unknown} error
     * @returns {void}
     */
    handleResolutionError(baseDir, error) {
        if (error instanceof Error === false) {
            throw error
        }

        this.resolveError = error.message
    }

    /**
     * Resolve the given id to file paths.
     * @returns {string | null} The resolved path.
     */
    getFilePath() {
        const conditionNames = ["node", "require"]
        const { extensions } = this.options
        const mainFields = []
        const mainFiles = []

        if (this.moduleStyle === "import") {
            conditionNames.push("import")
        }

        if (this.moduleStyle === "type") {
            conditionNames.push("import", "types")
        }

        if (
            this.moduleStyle === "require" ||
            this.moduleType === "npm" ||
            this.moduleType === "node"
        ) {
            mainFields.push("main")
            mainFiles.push("index")
        }

        let alias = undefined
        let extensionAlias = undefined

        if (isTypescript(this.context)) {
            alias = getTSConfigAliases(this.context)
            extensionAlias = getTypescriptExtensionMap(this.context).backward
        }

        /** @type {import('enhanced-resolve').ResolveOptionsOptionalFS} */
        this.resolverConfig = {
            conditionNames,
            extensions,
            mainFields,
            mainFiles,

            extensionAlias,
            alias,
        }

        const requireResolve = resolver.create.sync(this.resolverConfig)

        const cwd = this.context.settings?.cwd ?? process.cwd()
        for (const directory of this.getPaths()) {
            const baseDir = resolve(cwd, directory)

            try {
                const resolved = requireResolve(baseDir, this.name)
                if (typeof resolved === "string") return resolved
            } catch (error) {
                this.handleResolutionError(baseDir, error)
            }
        }

        if (this.moduleType === "absolute" || this.moduleType === "relative") {
            return resolve(this.options.basedir, this.name)
        }

        return null
    }
}
