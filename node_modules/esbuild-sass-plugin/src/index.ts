import {OnLoadResult} from "esbuild";
import {LoadCacheOptions} from "./cache";
import {StringOptions} from "sass";
import {sassPlugin} from "./plugin";

export type Type = 'css' | 'local-css' | 'style' | 'css-text' | 'lit-css' | ((cssText: string, nonce?: string) => string)
export type SassPluginOptions = StringOptions<'sync'|'async'> & LoadCacheOptions & {

  /**
   * Careful: this RegExp has to respect Go limitations!!!
   */
  filter?: RegExp

  /**
   * Function to transform import path. Not just paths by @import
   * directive, but also paths imported by ts code.
   */
  importMapper?: (url: string) => string

  /**
   * An array of paths that should be looked in to attempt to resolve your @import declarations.
   * When using `data`, it is recommended that you use this.
   *
   * @deprecated please use the new loadPaths option, this is just an alias for it
   * https://sass-lang.com/documentation/js-api/interfaces/StringOptionsWithoutImporter#loadPaths
   *
   * @default []
   */
  includePaths?: string[]

  /**
   * Directory that paths will be relative to.
   *
   * @default process.cwd()
   */
  basedir?: string

  /**
   * Type of module wrapper to use
   *
   * @default css files will be passed to css loader
   */
  type?: Type

  /**
   * A function which will post process the css file before wrapping it in a module
   *
   * @default undefined
   */
  transform?: (this: SassPluginOptions, css: string, resolveDir: string, filePath: string) => string | OnLoadResult | Promise<string | OnLoadResult>

  /**
   *
   */
  precompile?: (source: string, path: string, isRoot?: boolean) => string

  /**
   * Should rewrite leftover css imports starting with ~ so that esbuild can resolve them?
   */
  cssImports?: boolean

  /**
   *
   */
  nonce?: string

  /**
   *
   */
  prefer?: 'sass' | 'style' | 'main'

  /**
   * To enable the sass-embedded compiler
   */
  embedded?: boolean

  /**
   * Use named exports alongside default export.
   */
  namedExports?: boolean | "safe" | ((name: string) => string | null | undefined | false)
}

export default sassPlugin
export {sassPlugin}
export {makeModule, postcssModules} from './utils'
