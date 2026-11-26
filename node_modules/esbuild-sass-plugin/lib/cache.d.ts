import { OnLoadArgs, OnLoadResult } from 'esbuild';
export type LoadCacheEntry = {
    mtimeMs: number;
    result: OnLoadResult;
};
export type LoadCache = Map<string, LoadCacheEntry>;
export type LoadCacheOptions = {
    cache?: boolean | LoadCache;
};
type OnLoadCallback = (args: OnLoadArgs) => (OnLoadResult | Promise<OnLoadResult>);
export declare function useCache(options: LoadCacheOptions): [(loadCallback: OnLoadCallback) => OnLoadCallback, () => void];
export {};
