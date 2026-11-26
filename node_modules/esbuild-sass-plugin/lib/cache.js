"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.useCache = useCache;
const fs_1 = require("fs");
function createCache({ cache = true }) {
    if (cache === true) {
        return new Map();
    }
    else if (cache instanceof Map) {
        return cache;
    }
}
function useCache(options) {
    const cache = createCache(options);
    if (cache) {
        const mtimeMsCache = new Map();
        const useDateNow = Date.now.bind(Date);
        const maxMtimeMs = async (filenames = []) => {
            for (const filename of filenames) {
                if (!mtimeMsCache.has(filename)) {
                    mtimeMsCache.set(filename, fs_1.promises.stat(filename).then(stats => stats.mtimeMs).catch(useDateNow));
                }
            }
            let max = 0;
            for (const filename of filenames) {
                const mtimeMs = await mtimeMsCache.get(filename);
                if (mtimeMs > max) {
                    max = mtimeMs;
                }
            }
            return max;
        };
        const cached = (loadCallback) => async (args) => {
            let path = args.path;
            try {
                let cached = cache.get(path);
                if (cached) {
                    let mtimeMs = await maxMtimeMs(cached.result.watchFiles);
                    if (mtimeMs > cached.mtimeMs) {
                        cached.result = await loadCallback(args);
                        cached.mtimeMs = mtimeMs;
                    }
                }
                else {
                    let result = await loadCallback(args);
                    cached = {
                        mtimeMs: await maxMtimeMs(result.watchFiles),
                        result
                    };
                    cache.set(path, cached);
                }
                if (cached.result.errors) {
                    cache.delete(path);
                }
                return cached.result;
            }
            catch (error) {
                cache.delete(path);
                throw error;
            }
        };
        return [cached, () => mtimeMsCache.clear()];
    }
    else {
        return [loadCallback => loadCallback, () => { }];
    }
}
//# sourceMappingURL=cache.js.map