"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.sassPlugin = sassPlugin;
const cache_1 = require("./cache");
const path_1 = require("path");
const render_1 = require("./render");
const utils_1 = require("./utils");
let nextId = 0;
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
            const transform = options.transform ? options.transform.bind(options) : null;
            const namedExports = options.namedExports === 'safe' ? utils_1.safeExport : options.namedExports;
            const cssChunks = {};
            const pluginSuffix = nextId ? `-${nextId++}` : (nextId = 1) && '';
            const cssChunkPrefix = 'css-chunk' + pluginSuffix;
            if (transform) {
                const namespace = 'esbuild-sass-plugin' + pluginSuffix;
                onResolve({ filter: new RegExp(`^${cssChunkPrefix}`) }, ({ path, resolveDir }) => ({
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
            const [cached, resetCache] = (0, cache_1.useCache)(options);
            onStart(resetCache);
            onLoad({ filter: (_b = options.filter) !== null && _b !== void 0 ? _b : utils_1.DEFAULT_FILTER }, cached(async ({ path }) => {
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
                                let name = cssChunkPrefix + (0, utils_1.posixRelative)(path);
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
                            if (namedExports) {
                                const json = JSON.parse(pluginData.exports);
                                const keys = Object.keys(json);
                                if (typeof namedExports === 'function') {
                                    const vars = [];
                                    for (const name of keys) {
                                        const safe = namedExports(name);
                                        if (safe) {
                                            if (safe !== name) {
                                                if (json[safe]) {
                                                    return { errors: [{ text: `clash detected in safe named export '${safe}'` }] };
                                                }
                                                console.log(`exported '${name}' as '${safe}' in '${path}'`);
                                            }
                                            vars.push([safe, name]);
                                        }
                                    }
                                    if (vars.length > 0) {
                                        contents += `export const ${vars.map(([name, key]) => {
                                            const assignment = `${name}=${JSON.stringify(json[key])}`;
                                            json[key] = name;
                                            return assignment;
                                        }).join(',')};`;
                                    }
                                }
                                else {
                                    contents += `export const ${keys.map(key => {
                                        const assignment = `${key}=${JSON.stringify(json[key])}`;
                                        json[key] = key;
                                        return assignment;
                                    }).join(',')};`;
                                }
                                contents += `export default {${keys.map(key => `${JSON.stringify(key)}:${json[key]}`).join(',')}};`;
                            }
                            else {
                                contents += `export default ${pluginData.exports};`;
                            }
                            return {
                                contents,
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
//# sourceMappingURL=plugin.js.map