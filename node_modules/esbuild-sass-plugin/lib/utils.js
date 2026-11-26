"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.posixRelative = exports.DEFAULT_FILTER = void 0;
exports.modulesPaths = modulesPaths;
exports.fileSyntax = fileSyntax;
exports.getContext = getContext;
exports.sourceMappingURL = sourceMappingURL;
exports.makeModule = makeModule;
exports.parseNonce = parseNonce;
exports.postcssModules = postcssModules;
exports.createResolver = createResolver;
exports.safeExport = safeExport;
const fs_1 = require("fs");
const path_1 = require("path");
const cwd = process.cwd();
exports.DEFAULT_FILTER = /\.(s[ac]ss|css)$/;
exports.posixRelative = require('path').sep === '/'
    ? (path) => (0, path_1.relative)(cwd, path)
    : (path) => (0, path_1.relative)(cwd, path).replace(/\\/g, '/');
function modulesPaths(absWorkingDir) {
    let path = absWorkingDir || process.cwd();
    let { root } = (0, path_1.parse)(path);
    let found = [];
    while (path !== root) {
        const filename = (0, path_1.resolve)(path, 'node_modules');
        if ((0, fs_1.existsSync)(filename)) {
            found.push(filename);
        }
        path = (0, path_1.resolve)(path, '..');
    }
    return [...found];
}
function fileSyntax(filename) {
    if (filename.endsWith('.scss')) {
        return 'scss';
    }
    else if (filename.endsWith('.css')) {
        return 'css';
    }
    else {
        return 'indented';
    }
}
const SASS_PLUGIN_CONTEXT = Symbol();
function getContext(buildOptions) {
    let descriptor = Object.getOwnPropertyDescriptor(buildOptions, SASS_PLUGIN_CONTEXT);
    if (descriptor === undefined) {
        Object.defineProperty(buildOptions, SASS_PLUGIN_CONTEXT, descriptor = {
            value: {
                instances: 0
            }
        });
    }
    const instance = descriptor.value.instances++;
    return {
        instance,
        namespace: `sass-plugin-${instance}`,
        sourcemap: !!buildOptions.sourcemap,
        watched: {}
    };
}
function sourceMappingURL(sourceMap) {
    const data = Buffer.from(JSON.stringify(sourceMap), 'utf-8').toString('base64');
    return `/*# sourceMappingURL=data:application/json;charset=utf-8;base64,${data} */`;
}
function requireTool(module, basedir) {
    try {
        return require(module);
    }
    catch (ignored) {
    }
    if (basedir)
        try {
            return require(require.resolve(module, { paths: [basedir] }));
        }
        catch (ignored) {
        }
    try {
        return require(require.resolve(module, { paths: [process.cwd()] }));
    }
    catch (e) {
        try {
            return require(module);
        }
        catch (ignored) {
            console.error(`Cannot find module '${module}', make sure it's installed. e.g. yarn add -D ${module}`, e);
            process.exit(1);
        }
    }
}
const cssTextModule = cssText => `\
export default \`${cssText.replace(/([$`\\])/g, '\\$1')}\`;
`;
const cssResultModule = cssText => `\
import {css} from "lit-element/lit-element.js";
export default css\`${cssText.replace(/([$`\\])/g, '\\$1')}\`;
`;
const styleModule = (cssText, nonce) => nonce ? `\
const css = \`${cssText.replace(/([$`\\])/g, '\\$1')}\`;
const style = document.createElement("style");
style.setAttribute("nonce", ${nonce});
style.appendChild(document.createTextNode(css));
document.head.appendChild(style);
export {css};
` : `\
const css = \`${cssText.replace(/([$`\\])/g, '\\$1')}\`;
document.head
    .appendChild(document.createElement("style"))
    .appendChild(document.createTextNode(css));
export {css};
`;
function makeModule(contents, type, nonce) {
    switch (type) {
        case 'style':
            return styleModule(contents, nonce);
        case 'lit-css':
            return cssResultModule(contents);
        case 'css-text':
            return cssTextModule(contents);
        case 'css':
        case 'local-css':
            return contents;
        default:
            return type(contents, nonce);
    }
}
function parseNonce(nonce) {
    if (nonce) {
        if (nonce.startsWith('window.') || nonce.startsWith('process.') || nonce.startsWith('globalThis.')) {
            return nonce;
        }
        else {
            return JSON.stringify(nonce);
        }
    }
    else {
        return nonce;
    }
}
function postcssModules(options, plugins = []) {
    const postcss = requireTool('postcss', options.basedir);
    const postcssModulesPlugin = requireTool('postcss-modules', options.basedir);
    return async function (source, dirname, path) {
        let cssModule;
        const { css } = await postcss([
            postcssModulesPlugin({
                ...options,
                getJSON(cssFilename, json, outputFileName) {
                    var _a;
                    cssModule = JSON.stringify(json, null, 2);
                    (_a = options.getJSON) === null || _a === void 0 ? void 0 : _a.call(options, cssFilename, json, outputFileName);
                }
            }),
            ...plugins
        ]).process(source, { from: path, map: false });
        return {
            contents: css,
            pluginData: { exports: cssModule },
            loader: 'js'
        };
    };
}
function createResolver(options = {}, loadPaths) {
    if (options.prefer) {
        const resolve = require('resolve');
        const cache = {};
        const prefer = options.prefer;
        const opts = {
            paths: ['.', ...loadPaths],
            readPackageSync(readFileSync, pkgfile) {
                let cached = cache[pkgfile];
                if (!cached) {
                    const pkg = JSON.parse(readFileSync(pkgfile));
                    cached = cache[pkgfile] = { main: pkg[prefer] || pkg.main };
                }
                return cached;
            }
        };
        return (id, basedir) => {
            try {
                opts.basedir = basedir;
                return resolve.sync(id, opts);
            }
            catch (ignored) {
                return id;
            }
        };
    }
    else {
        const opts = {
            paths: ['.', ...loadPaths]
        };
        return (id, basedir) => {
            try {
                opts.paths[0] = basedir;
                const resolved = require.resolve(id, opts);
                if (!resolved.endsWith('.js')) {
                    return resolved;
                }
                try {
                    const parts = id.split('/');
                    const pkgName = id.startsWith('@') ? parts[0] + '/' + parts[1] : parts[0];
                    const pkgPath = require.resolve(pkgName + '/package.json', opts);
                    const pkg = JSON.parse((0, fs_1.readFileSync)(pkgPath, 'utf8'));
                    for (const field of ['style', 'sass', 'scss']) {
                        if (typeof pkg[field] === 'string') {
                            const stylePath = (0, path_1.resolve)((0, path_1.dirname)(pkgPath), pkg[field]);
                            if ((0, fs_1.existsSync)(stylePath))
                                return stylePath;
                        }
                    }
                }
                catch (ignored) { }
                const base = resolved.slice(0, -3);
                for (const ext of ['.scss', '.sass', '.css']) {
                    if ((0, fs_1.existsSync)(base + ext))
                        return base + ext;
                }
                return id;
            }
            catch (ignored) {
                return id;
            }
        };
    }
}
const reserved = {
    await: true,
    break: true,
    case: true,
    catch: true,
    class: true,
    const: true,
    continue: true,
    debugger: true,
    default: true,
    delete: true,
    do: true,
    else: true,
    enum: true,
    export: true,
    extends: true,
    false: true,
    finally: true,
    for: true,
    function: true,
    if: true,
    implements: true,
    import: true,
    in: true,
    instanceof: true,
    interface: true,
    let: true,
    new: true,
    null: true,
    package: true,
    private: true,
    protected: true,
    public: true,
    return: true,
    static: true,
    super: true,
    switch: true,
    this: true,
    throw: true,
    true: true,
    try: true,
    typeof: true,
    var: true,
    void: true,
    while: true,
    with: true,
    yield: true
};
function safeExport(identifier) {
    if (reserved[identifier]) {
        return '_' + identifier;
    }
    else {
        return identifier.trim().replace(/\W+([a-z])/g, (esc, c) => esc[0] === '-' ? c.toUpperCase() : '_');
    }
}
//# sourceMappingURL=utils.js.map