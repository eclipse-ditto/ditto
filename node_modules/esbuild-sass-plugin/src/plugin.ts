import {OnLoadResult, Plugin} from 'esbuild'
import {dirname} from 'path'
import {SassPluginOptions} from './index'
import {getContext, makeModule, modulesPaths, parseNonce, posixRelative, DEFAULT_FILTER, ensureClassName} from './utils'
import {useCache} from './cache'
import {createRenderer} from './render'

/**
 *
 * @param options
 */
export function sassPlugin(options: SassPluginOptions = {}): Plugin {

  if (!options.basedir) {
    options.basedir = process.cwd()
  }

  if (options.includePaths) {
    console.log(`'includePaths' option is deprecated, please use 'loadPaths' instead`)
  }

  const type = options.type ?? 'css'

  if (options['picomatch'] || options['exclude'] || typeof type !== 'string' && typeof type !== 'function') {
    console.log('The type array, exclude and picomatch options are no longer supported, please refer to the README for alternatives.')
  }

  const nonce = parseNonce(options.nonce)

  return {
    name: 'sass-plugin',
    async setup({initialOptions, onResolve, onLoad, resolve, onStart, onDispose}) {

      options.loadPaths = Array.from(new Set([
        ...options.loadPaths || modulesPaths(initialOptions.absWorkingDir),
        ...options.includePaths || []
      ]))

      const {
        sourcemap,
        watched
      } = getContext(initialOptions)

      if (options.cssImports) {
        onResolve({filter: /^~.*\.css$/}, ({path, importer, resolveDir}) => {
          return resolve(path.slice(1), {importer, resolveDir, kind: 'import-rule'})
        })
      }

      const fsStatCache = new Map()
      onStart(() => fsStatCache.clear())

      const transform = options.transform ? options.transform.bind(options) : null

      const cssChunks: Record<string, string | Uint8Array | undefined> = {}

      if (transform) {
        const namespace = 'esbuild-sass-plugin'

        onResolve({filter: /^css-chunk:/}, ({path, resolveDir}) => ({
          path,
          namespace,
          pluginData: {resolveDir}
        }))

        onLoad({filter: /./, namespace}, ({path, pluginData: {resolveDir}}) => ({
          contents: cssChunks[path],
          resolveDir,
          loader: 'css'
        }))
      }

      const renderSass = await createRenderer(options, options.sourceMap ?? sourcemap, onDispose)

      onLoad({filter: options.filter ?? DEFAULT_FILTER}, useCache(options, fsStatCache, async path => {
        try {
          let {cssText, watchFiles, warnings} = await renderSass(path)
          if (!warnings) {
            warnings = []
          }

          watched[path] = watchFiles

          const resolveDir = dirname(path)

          if (transform) {
            const out: string | OnLoadResult = await transform(cssText, resolveDir, path)
            if (typeof out !== 'string') {
              if (out.loader && out.loader !== 'js') {
                return {
                  ...out,
                  resolveDir,
                  watchFiles: [...watchFiles, ...(out.watchFiles || [])],
                  watchDirs: out.watchDirs || []
                }
              }
              let {contents, pluginData} = out
              if (type === 'css') {
                let name = posixRelative(path)
                cssChunks[name] = contents
                contents = `import '${name}';`
              } else if (type === 'style') {
                contents = makeModule(String(contents), 'style', nonce)
              } else {
                return {
                  errors: [{text: `unsupported type '${type}' for postCSS modules`}]
                }
              }

              let exportConstants = "";
              if (options.namedExports && pluginData.exports) {
                const json = JSON.parse(pluginData.exports)
                const getClassName =
                  typeof options.namedExports === "function"
                    ? options.namedExports
                    : ensureClassName
                Object.keys(json).forEach((name) => {
                  const newName = getClassName(name);
                  exportConstants += `export const ${newName} = ${JSON.stringify(
                    json[name]
                  )};\n`
                })
              }

              return {
                contents: `${contents}${exportConstants}export default ${pluginData.exports};`,
                loader: 'js',
                resolveDir,
                watchFiles: [...watchFiles, ...(out.watchFiles || [])],
                watchDirs: out.watchDirs || []
              }
            } else {
              cssText = out
            }
          }

          return type === 'css' || type === 'local-css' ? {
            contents: cssText,
            loader: type,
            resolveDir,
            warnings,
            watchFiles
          } : {
            contents: makeModule(cssText, type, nonce),
            loader: 'js',
            resolveDir,
            warnings,
            watchFiles
          }

        } catch (err: any) {
          return {
            errors: [{text: err.message}],
            watchFiles: watched[path] ?? [path]
          }
        }
      }))
    }
  }
}
