import {SassPluginOptions, Type} from './index'
import {AcceptedPlugin, Postcss} from 'postcss'
import PostcssModulesPlugin from 'postcss-modules'
import {BuildOptions, OnLoadResult} from 'esbuild'
import {Syntax} from 'sass'
import {parse, relative, resolve} from 'path'
import {existsSync} from 'fs'
import {SyncOpts} from 'resolve'
import {identifier} from 'safe-identifier'

const cwd = process.cwd()

export const DEFAULT_FILTER = /\.(s[ac]ss|css)$/

export const posixRelative = require('path').sep === '/'
  ? (path: string) => `css-chunk:${relative(cwd, path)}`
  : (path: string) => `css-chunk:${relative(cwd, path).replace(/\\/g, '/')}`

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

export function makeModule(contents: string, type: Type, nonce?: string):string {
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
        let resolved = require.resolve(id, opts)
        // pretty ugly patch to avoid resolving erroneously to .js files ///////////////////////////////////////////////
        if (resolved.endsWith('.js')) {
          resolved = resolved.slice(0, -3) + '.scss'
          if (!existsSync(resolved)) {
            resolved = resolved.slice(0, -5) + '.sass'
            if (!existsSync(resolved)) {
              resolved = resolved.slice(0, -5) + '.css'
            }
          }
        }
        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        return resolved
      } catch (ignored) {
        return id
      }
    }
  }
}

const escapeClassNameDashes = (name: string) =>
  name.replace(/-+/g, (match) => `$${match.replace(/-/g, "_")}$`)
export const ensureClassName = (name: string) => {
  const escaped = escapeClassNameDashes(name)
  return identifier(escaped)
};