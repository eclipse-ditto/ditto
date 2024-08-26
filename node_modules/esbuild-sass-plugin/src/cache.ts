import {CachedResult, SassPluginOptions} from './index'
import {OnLoadArgs, OnLoadResult} from 'esbuild'
import {promises as fsp, Stats} from 'fs'

type OnLoadCallback = (args: OnLoadArgs) => (OnLoadResult | Promise<OnLoadResult>)
type PluginLoadCallback = (path: string) => (OnLoadResult | Promise<OnLoadResult>)

function collectStats(watchFiles: string[], fsStatCache: Map<string, Stats>): Promise<Stats[]> {
  return Promise.all(watchFiles.map(async filename => {
    if (!fsStatCache.has(filename)) {
      const stats = await fsp.stat(filename)
      fsStatCache.set(filename, stats)
    }
    return fsStatCache.get(filename) as Stats
  }))
}

function maxMtimeMs(stats: Stats[]) {
  return stats.reduce((max, {mtimeMs}) => Math.max(max, mtimeMs), 0)
}

function getCache(options: SassPluginOptions): Map<string, CachedResult> | undefined {
  if (options.cache ?? true) {
    if (typeof options.cache === 'object') {
      return options.cache
    } else {
      return new Map()
    }
  }
}

export function useCache(options: SassPluginOptions = {}, fsStatCache: Map<string, Stats>, loadCallback: PluginLoadCallback): OnLoadCallback {
  const cache = getCache(options)
  if (cache) {
    return async ({path}: OnLoadArgs) => {
      try {
        let cached = cache.get(path)
        if (cached) {
          let watchFiles = cached.result.watchFiles!
          let stats = await collectStats(watchFiles, fsStatCache)
          for (const {mtimeMs} of stats) {
            if (mtimeMs > cached.mtimeMs) {
              cached.result = await loadCallback(watchFiles[0])
              cached.mtimeMs = maxMtimeMs(stats)
              break
            }
          }
        } else {
          let result = await loadCallback(path)
          cached = {
            mtimeMs: maxMtimeMs(await collectStats(result.watchFiles!, fsStatCache)),
            result
          }
          cache.set(path, cached)
        }
        if (cached.result.errors) {
          cache.delete(path)
        }
        return cached.result
      } catch (error) {
        cache.delete(path)
        throw error
      }
    }
  } else {
    return ({path}) => loadCallback(path)
  }
}
