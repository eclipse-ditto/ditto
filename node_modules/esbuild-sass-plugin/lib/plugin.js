"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.sassPlugin = void 0;
const path_1 = require("path");
const utils_1 = require("./utils");
const cache_1 = require("./cache");
const render_1 = require("./render");
function sassPlugin(options = {}) {
    var _a;
    if (!options.basedir) {
        options.basedir = process.cwd();
    }
    if (options.includePaths) {
        console.log(`'includePaths' option is deprecated, please use 'loadPaths' instead`);
    }
    const type = (_a = options.type) !== null && _a !== void 0 ? _a : 'css';
    if (options['picomatch'] || options['exclude'] || typeof type !== 'string' && typeof type !== 'function') {
        console.log('The type array, exclude and picomatch options are no longer supported, please refer to the README for alternatives.');
    }
    const nonce = (0, utils_1.parseNonce)(options.nonce);
    return {
        name: 'sass-plugin',
        async setup({ initialOptions, onResolve, onLoad, resolve, onStart, onDispose }) {
            var _a, _b;
            options.loadPaths = Array.from(new Set([
                ...options.loadPaths || (0, utils_1.modulesPaths)(initialOptions.absWorkingDir),
                ...options.includePaths || []
            ]));
            const { sourcemap, watched } = (0, utils_1.getContext)(initialOptions);
            if (options.cssImports) {
                onResolve({ filter: /^~.*\.css$/ }, ({ path, importer, resolveDir }) => {
                    return resolve(path.slice(1), { importer, resolveDir, kind: 'import-rule' });
                });
            }
            const fsStatCache = new Map();
            onStart(() => fsStatCache.clear());
            const transform = options.transform ? options.transform.bind(options) : null;
            const cssChunks = {};
            if (transform) {
                const namespace = 'esbuild-sass-plugin';
                onResolve({ filter: /^css-chunk:/ }, ({ path, resolveDir }) => ({
                    path,
                    namespace,
                    pluginData: { resolveDir }
                }));
                onLoad({ filter: /./, namespace }, ({ path, pluginData: { resolveDir } }) => ({
                    contents: cssChunks[path],
                    resolveDir,
                    loader: 'css'
                }));
            }
            const renderSass = await (0, render_1.createRenderer)(options, (_a = options.sourceMap) !== null && _a !== void 0 ? _a : sourcemap, onDispose);
            onLoad({ filter: (_b = options.filter) !== null && _b !== void 0 ? _b : utils_1.DEFAULT_FILTER }, (0, cache_1.useCache)(options, fsStatCache, async (path) => {
                var _a;
                try {
                    let { cssText, watchFiles, warnings } = await renderSass(path);
                    if (!warnings) {
                        warnings = [];
                    }
                    watched[path] = watchFiles;
                    const resolveDir = (0, path_1.dirname)(path);
                    if (transform) {
                        const out = await transform(cssText, resolveDir, path);
                        if (typeof out !== 'string') {
                            if (out.loader && out.loader !== 'js') {
                                return {
                                    ...out,
                                    resolveDir,
                                    watchFiles: [...watchFiles, ...(out.watchFiles || [])],
                                    watchDirs: out.watchDirs || []
                                };
                            }
                            let { contents, pluginData } = out;
                            if (type === 'css') {
                                let name = (0, utils_1.posixRelative)(path);
                                cssChunks[name] = contents;
                                contents = `import '${name}';`;
                            }
                            else if (type === 'style') {
                                contents = (0, utils_1.makeModule)(String(contents), 'style', nonce);
                            }
                            else {
                                return {
                                    errors: [{ text: `unsupported type '${type}' for postCSS modules` }]
                                };
                            }
                            let exportConstants = "";
                            if (options.namedExports && pluginData.exports) {
                                const json = JSON.parse(pluginData.exports);
                                const getClassName = typeof options.namedExports === "function"
                                    ? options.namedExports
                                    : utils_1.ensureClassName;
                                Object.keys(json).forEach((name) => {
                                    const newName = getClassName(name);
                                    exportConstants += `export const ${newName} = ${JSON.stringify(json[name])};\n`;
                                });
                            }
                            return {
                                contents: `${contents}${exportConstants}export default ${pluginData.exports};`,
                                loader: 'js',
                                resolveDir,
                                watchFiles: [...watchFiles, ...(out.watchFiles || [])],
                                watchDirs: out.watchDirs || []
                            };
                        }
                        else {
                            cssText = out;
                        }
                    }
                    return type === 'css' || type === 'local-css' ? {
                        contents: cssText,
                        loader: type,
                        resolveDir,
                        warnings,
                        watchFiles
                    } : {
                        contents: (0, utils_1.makeModule)(cssText, type, nonce),
                        loader: 'js',
                        resolveDir,
                        warnings,
                        watchFiles
                    };
                }
                catch (err) {
                    return {
                        errors: [{ text: err.message }],
                        watchFiles: (_a = watched[path]) !== null && _a !== void 0 ? _a : [path]
                    };
                }
            }));
        }
    };
}
exports.sassPlugin = sassPlugin;
//# sourceMappingURL=plugin.js.map