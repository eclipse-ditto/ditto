/// <reference types="node" />
import { SassPluginOptions } from './index';
import { OnLoadArgs, OnLoadResult } from 'esbuild';
import { Stats } from 'fs';
type OnLoadCallback = (args: OnLoadArgs) => (OnLoadResult | Promise<OnLoadResult>);
type PluginLoadCallback = (path: string) => (OnLoadResult | Promise<OnLoadResult>);
export declare function useCache(options: SassPluginOptions | undefined, fsStatCache: Map<string, Stats>, loadCallback: PluginLoadCallback): OnLoadCallback;
export {};
