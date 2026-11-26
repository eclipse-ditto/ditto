import {OnLoadResult, Plugin} from 'esbuild'
import {useCache} from './cache'
import {dirname} from 'path'
import {SassPluginOptions} from './index'
import {createRenderer} from './render'
import {DEFAULT_FILTER, getContext, makeModule, modulesPaths, parseNonce, posixRelative, safeExport} from './utils'

let nextId = 0

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

      const transform = options.transform ? options.transform.bind(options) : null
      const namedExports = options.namedExports === 'safe' ? safeExport : options.namedExports

      const cssChunks: Record<string, string | Uint8Array | undefined> = {}
      const pluginSuffix = nextId ? `-${nextId++}` : (nextId = 1) && ''
      const cssChunkPrefix = 'css-chunk' + pluginSuffix

      if (transform) {
        const namespace = 'esbuild-sass-plugin' + pluginSuffix

        onResolve({filter: new RegExp(`^${cssChunkPrefix}`)}, ({path, resolveDir}) => ({
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

      const [cached, resetCache] = useCache(options)

      onStart(resetCache)

      onLoad({filter: options.filter ?? DEFAULT_FILTER}, cached(async ({path}) => {
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
                let name = cssChunkPrefix + posixRelative(path)
                cssChunks[name] = contents
                contents = `import '${name}';`
              } else if (type === 'style') {
                contents = makeModule(String(contents), 'style', nonce)
              } else {
                return {
                  errors: [{text: `unsupported type '${type}' for postCSS modules`}]
                }
              }

              if (namedExports) {
                const json = JSON.parse(pluginData.exports)
                const keys = Object.keys(json)
                if (typeof namedExports === 'function') {
                  const vars: Array<[string, string]> = []
                  for (const name of keys) {
                    const safe = namedExports(name)
                    if (safe) {
                      if (safe !== name) {
                        if (json[safe]) {
                          return {errors: [{text: `clash detected in safe named export '${safe}'`}]}
                        }
                        console.log(`exported '${name}' as '${safe}' in '${path}'`)
                      }
                      vars.push([safe, name])
                    }
                  }
                  if (vars.length > 0) {
                    contents += `export const ${
                      vars.map(([name, key]) => {
                        const assignment = `${name}=${JSON.stringify(json[key])}`
                        json[key] = name
                        return assignment
                      }).join(',')};`
                  }
                } else {
                  contents += `export const ${
                    keys.map(key => {
                      const assignment = `${key}=${JSON.stringify(json[key])}`
                      json[key] = key
                      return assignment
                    }).join(',')};`
                }
                contents += `export default {${
                  keys.map(key => `${JSON.stringify(key)}:${json[key]}`).join(',')
                }};`
              } else {
                contents += `export default ${pluginData.exports};`
              }

              return {
                contents,
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
