import {OnLoadArgs, OnLoadResult} from 'esbuild'
import {promises as fsp} from 'fs'

export type LoadCacheEntry = {
    mtimeMs: number
    result: OnLoadResult
}

export type LoadCache = Map<string, LoadCacheEntry>

export type LoadCacheOptions = {
    /**
     * Enable the cache or pass your own Map to recycle its contents although
     * it's advisable to use esbuild incremental or watch for repeated builds
     *
     * @default true
     */
    cache?: boolean | LoadCache
}

type OnLoadCallback = (args: OnLoadArgs) => (OnLoadResult | Promise<OnLoadResult>)

function createCache({cache = true}: LoadCacheOptions) {
    if (cache === true) {
        return new Map()
    } else if (cache instanceof Map) {
        return cache
    }
}

export function useCache(options: LoadCacheOptions): [(loadCallback: OnLoadCallback) => OnLoadCallback, () => void] {
  const cache = createCache(options)
  if (cache) {
      const mtimeMsCache = new Map<string, Promise<number>>()
      const useDateNow = Date.now.bind(Date)

      const maxMtimeMs = async (filenames: string[] = []) => {
          for (const filename of filenames) {
              if (!mtimeMsCache.has(filename)) {
                  mtimeMsCache.set(
                      filename,
                      fsp.stat(filename).then(stats => stats.mtimeMs).catch(useDateNow)
                  )
              }
          }
          let max = 0
          for (const filename of filenames) {
              const mtimeMs = await mtimeMsCache.get(filename)!
              if (mtimeMs > max) {
                  max = mtimeMs
              }
          }
          return max
      }

      const cached = (loadCallback: OnLoadCallback): OnLoadCallback => async args => {
          let path = args.path
          try {
              let cached = cache.get(path)
              if (cached) {
                  let mtimeMs = await maxMtimeMs(cached.result.watchFiles)
                  if (mtimeMs > cached.mtimeMs) {
                      cached.result = await loadCallback(args)
                      cached.mtimeMs = mtimeMs
                  }
              } else {
                  let result = await loadCallback(args)
                  cached = {
                      mtimeMs: await maxMtimeMs(result.watchFiles),
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

      return [cached, () => mtimeMsCache.clear()]
  } else {
      return [loadCallback => loadCallback, () => {}]
  }
}

