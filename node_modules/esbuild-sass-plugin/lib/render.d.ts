import { PartialMessage } from 'esbuild';
import { SassPluginOptions } from './index';
import { StringOptions } from 'sass/types/options';
import { CompileResult } from 'sass/types/compile';
export type RenderSass = (path: string) => Promise<RenderResult>;
export type RenderResult = {
    cssText: string;
    watchFiles: string[];
    warnings?: PartialMessage[];
};
export type Compiler = (source: string, options?: StringOptions<any>) => CompileResult | Promise<CompileResult>;
export declare function createRenderer(options: SassPluginOptions | undefined, sourcemap: boolean, onDispose: (callback: () => void) => void): Promise<RenderSass>;
