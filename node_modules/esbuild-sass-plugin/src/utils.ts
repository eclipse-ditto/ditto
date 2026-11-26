import {BuildOptions, OnLoadResult} from 'esbuild'
import {existsSync, readFileSync} from 'fs'
import {dirname, parse, relative, resolve} from 'path'
import {AcceptedPlugin, Postcss} from 'postcss'
import PostcssModulesPlugin from 'postcss-modules'
import {SyncOpts} from 'resolve'
import {Syntax} from 'sass'
import {SassPluginOptions, Type} from './index'

const cwd = process.cwd()

export const DEFAULT_FILTER = /\.(s[ac]ss|css)$/

export const posixRelative = require('path').sep === '/'
  ? (path: string) => relative(cwd, path)
  : (path: string) => relative(cwd, path).replace(/\\/g, '/')

export function modulesPaths(absWorkingDir?: string): string[] {
  let path = absWorkingDir || process.cwd()
  let {root} = parse(path)
  let found: string[] = []
  while (path !== root) {
    const filename = resolve(path, 'node_modules')
    if (existsSync(filename)) {
      found.push(filename)
    }
    path = resolve(path, '..')
  }
  return [...found]
}

export function fileSyntax(filename: string): Syntax {
  if (filename.endsWith('.scss')) {
    return 'scss'
  } else if (filename.endsWith('.css')) {
    return 'css'
  } else {
    return 'indented'
  }
}

export type PluginContext = {
  instance: number
  namespace: string
  sourcemap: boolean
  watched: { [path: string]: string[] }
}

const SASS_PLUGIN_CONTEXT = Symbol()

export function getContext(buildOptions: BuildOptions): PluginContext {
  let descriptor = Object.getOwnPropertyDescriptor(buildOptions, SASS_PLUGIN_CONTEXT)
  if (descriptor === undefined) {
    Object.defineProperty(buildOptions, SASS_PLUGIN_CONTEXT, descriptor = {
      value: {
        instances: 0
      }
    })
  }
  const instance = descriptor.value.instances++
  return {
    instance,
    namespace: `sass-plugin-${instance}`,
    sourcemap: !!buildOptions.sourcemap,
    watched: {}
  }
}

export function sourceMappingURL(sourceMap: any): string {
  const data = Buffer.from(JSON.stringify(sourceMap), 'utf-8').toString('base64')
  return `/*# sourceMappingURL=data:application/json;charset=utf-8;base64,${data} */`
}

function requireTool(module: string, basedir?: string) {
  try {
    return require(module)
  } catch (ignored) {
  }
  if (basedir) try {
    return require(require.resolve(module, {paths: [basedir]}))
  } catch (ignored) {
  }
  try {
    return require(require.resolve(module, {paths: [process.cwd()]}))
  } catch (e) {
    try {
      return require(module) // extra attempt at finding a co-located tool
    } catch (ignored) {
      console.error(`Cannot find module '${module}', make sure it's installed. e.g. yarn add -D ${module}`, e)
      process.exit(1)
    }
  }
}

const cssTextModule = cssText => `\
export default \`${cssText.replace(/([$`\\])/g, '\\$1')}\`;
`

const cssResultModule = cssText => `\
import {css} from "lit-element/lit-element.js";
export default css\`${cssText.replace(/([$`\\])/g, '\\$1')}\`;
`

const styleModule = (cssText: string, nonce?: string) => nonce ? `\
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
`

export function makeModule(contents: string, type: Type, nonce?: string): string {
  switch (type) {
    case 'style':
      return styleModule(contents, nonce)
    case 'lit-css':
      return cssResultModule(contents)
    case 'css-text':
      return cssTextModule(contents)
    case 'css':
    case 'local-css':
      return contents
    default:
      return type(contents, nonce)
  }
}

export function parseNonce(nonce: string | undefined): string | undefined {
  if (nonce) {
    if (nonce.startsWith('window.') || nonce.startsWith('process.') || nonce.startsWith('globalThis.')) {
      return nonce
    } else {
      return JSON.stringify(nonce)
    }
  } else {
    return nonce
  }
}

export type PostcssModulesParams = Parameters<PostcssModulesPlugin>[0] & {
  basedir?: string
};

export function postcssModules(options: PostcssModulesParams, plugins: AcceptedPlugin[] = []) {

  const postcss: Postcss = requireTool('postcss', options.basedir)
  const postcssModulesPlugin: PostcssModulesPlugin = requireTool('postcss-modules', options.basedir)

  return async function (source: string, dirname: string, path: string): Promise<OnLoadResult> {

    let cssModule

    const {css} = await postcss([
      postcssModulesPlugin({
        ...(options as Parameters<PostcssModulesPlugin>[0]),
        getJSON(cssFilename: string, json: { [name: string]: string }, outputFileName?: string): void {
          cssModule = JSON.stringify(json, null, 2)
          options.getJSON?.(cssFilename, json, outputFileName)
        }
      }),
      ...plugins
    ]).process(source, {from: path, map: false})

    return {
      contents: css,
      pluginData: {exports: cssModule},
      loader: 'js'
    }
  }
}

export function createResolver(options: SassPluginOptions = {}, loadPaths: string[]) {
  if (options.prefer) {
    const resolve = require('resolve')
    const cache = {} as Record<string, { main: string }>
    const prefer = options.prefer
    const opts: SyncOpts = {
      paths: ['.', ...loadPaths],
      readPackageSync(readFileSync, pkgfile) {
        let cached = cache[pkgfile]
        if (!cached) {
          const pkg = JSON.parse(readFileSync(pkgfile) as string)
          cached = cache[pkgfile] = {main: pkg[prefer] || pkg.main}
        }
        return cached
      }
    }
    return (id: string, basedir: string) => {
      try {
        opts.basedir = basedir
        return resolve.sync!(id, opts)
      } catch (ignored) {
        return id
      }
    }
  } else {
    const opts = {
      paths: ['.', ...loadPaths]
    }
    return (id: string, basedir: string) => {
      try {
        opts.paths[0] = basedir
        const resolved = require.resolve(id, opts)
        if (!resolved.endsWith('.js')) {
          return resolved
        }
        // require.resolve found a JS entry point — look for a stylesheet instead.
        // First check package.json style/sass/scss fields, then try adjacent files.
        try {
          const parts = id.split('/')
          const pkgName = id.startsWith('@') ? parts[0] + '/' + parts[1] : parts[0]
          const pkgPath = require.resolve(pkgName + '/package.json', opts)
          const pkg = JSON.parse(readFileSync(pkgPath, 'utf8'))
          for (const field of ['style', 'sass', 'scss']) {
            if (typeof pkg[field] === 'string') {
              const stylePath = resolve(dirname(pkgPath), pkg[field])
              if (existsSync(stylePath)) return stylePath
            }
          }
        } catch (ignored) {}
        const base = resolved.slice(0, -3)
        for (const ext of ['.scss', '.sass', '.css']) {
          if (existsSync(base + ext)) return base + ext
        }
        return id
      } catch (ignored) {
        return id
      }
    }
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
}

export function safeExport(identifier: string): string {
  if (reserved[identifier]) {
    return '_' + identifier
  } else {
    return identifier.trim().replace(/\W+([a-z])/g, (esc, c) => esc[0] === '-' ? c.toUpperCase() : '_')
  }
}
