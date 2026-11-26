import { Logger } from 'bs-logger';
import { CompilerOptions, CustomTransformers, Program, TranspileOutput } from 'typescript';
import type { StringMap, TsCompilerInstance, TsJestAstTransformer, TsJestCompileOptions, TTypeScript, CompiledOutput } from '../../types';
import type { ConfigSet } from '../config/config-set';
export declare class TsCompiler implements TsCompilerInstance {
    readonly configSet: ConfigSet;
    readonly runtimeCacheFS: StringMap;
    protected readonly _logger: Logger;
    protected readonly _ts: TTypeScript;
    protected readonly _initialCompilerOptions: CompilerOptions;
    protected _compilerOptions: CompilerOptions;
    /**
     * @private
     */
    private _runtimeCacheFS;
    /**
     * @private
     */
    private _fileContentCache;
    program: Program | undefined;
    constructor(configSet: ConfigSet, runtimeCacheFS: StringMap);
    getResolvedModules(fileContent: string, fileName: string, runtimeCacheFS: StringMap): string[];
    /**
     * Apply ts-jest's runtime fixups on top of the user's compiler options before
     * handing them to the language service or transpiler.
     *
     * Two compiler-options now flow through dedicated helpers so the produced
     * options are always a TypeScript-valid combination:
     *
     *   - `moduleResolution` is delegated to `resolveCompatibleModuleResolution`,
     *     which preserves the user's value when it is valid alongside the
     *     `module` ts-jest forces at runtime (CommonJS on the CJS path, or
     *     ESNext / the user's original `module` on the ESM path) and otherwise
     *     substitutes a TypeScript-valid alternative. When the user has not set
     *     a value the historical Node10 default is kept, so unchanged tsconfigs
     *     see the exact same resolved options as before.
     *
     *   - `customConditions` is delegated to `preserveCustomConditionsIfCompatible`,
     *     which keeps the user's value only when the resolved
     *     `moduleResolution` supports it (`Bundler` / `Node16` / `NodeNext`)
     *     and clears it otherwise. The pre-#4198 code unconditionally cleared
     *     this option because the hardcoded `Node10` override always made it
     *     incompatible; that is no longer true.
     *
     * @see https://github.com/kulshekhar/ts-jest/issues/4198
     */
    private fixupCompilerOptionsForModuleKind;
    /**
     * Pick a `moduleResolution` value that is valid alongside the `module` ts-jest
     * forces at runtime. Closes #4198: previously this was hardcoded to Node10 and
     * silently overrode whatever the user set in tsconfig, even when the user value
     * would have been valid (e.g. Bundler with module: ESNext, Classic with CommonJS).
     *
     * Substitution rules — each tied to a specific TypeScript diagnostic that the
     * resulting combination would otherwise raise. The "Bundler-compatible" set is
     * the one defined by `isBundlerCompatibleModuleKind` (ES2015 / ES2020 / ES2022
     * / ESNext / Preserve); everything else (CommonJS / AMD / UMD / System / None)
     * is treated as Bundler-incompatible across the full supported TS range.
     *
     *   - Node16 / NodeNext require `module: Node16` or `module: NodeNext` (TS5110).
     *     ts-jest never emits those module kinds, so these user-supplied values are
     *     substituted: to Bundler when the forced module is in the
     *     Bundler-compatible set, or to Node10 otherwise. (Pairing Bundler with a
     *     non-ES module raises TS5095, and Node10 is the only kind that has been
     *     valid with non-ES modules across every TypeScript version ts-jest
     *     supports.)
     *
     *   - User-supplied Bundler with a non-Bundler-compatible forced module is
     *     TS5095, substitute Node10. (TypeScript 6 relaxed this for
     *     `module: CommonJS` specifically; that relaxation is encoded inside
     *     `isBundlerCompatibleModuleKind` via a runtime version check, so on
     *     TS ≥ 6 user-supplied Bundler passes through unchanged on the CJS
     *     path.)
     *
     *   - Anything else (Node10 / Classic / unset) passes through or falls back
     *     to Node10. These pairings are valid with every forced module kind.
     *
     * Compatibility: `ModuleResolutionKind.Bundler` was introduced in
     * TypeScript 5.0. ts-jest declares `peerDependencies: { typescript: ">=4.3 <7" }`,
     * so the Bundler member is `undefined` at runtime on TypeScript 4.3 - 4.9.
     * The Node16/NodeNext substitution falls back to Node10 in that case
     * (`bundlerResolution` below) to keep the function deterministic across the
     * full supported range. Users on TypeScript < 5 can never have set Bundler in
     * tsconfig (the parser rejects it), so the user-supplied-Bundler branch is
     * unreachable there and needs no separate guard.
     */
    private resolveCompatibleModuleResolution;
    /**
     * TypeScript pairs `moduleResolution: bundler` only with ES-module module
     * kinds (`ES2015` / `ES2020` / `ES2022` / `ESNext`) or `Preserve` (added in
     * TypeScript 5.4). Pairing `bundler` with `CommonJS`, `AMD`, `UMD`, `System`,
     * or `None` raises TS5095. Used by `resolveCompatibleModuleResolution` to
     * gate the `Node16` / `NodeNext` → `Bundler` substitution and the
     * user-supplied-`Bundler` pass-through, so neither path emits an invalid
     * pair when the user has selected a non-ES `module`.
     *
     * TypeScript 6.0 relaxed TS5095 for `module: CommonJS` specifically
     * (`CommonJS` + `Bundler` is a valid pair on TS ≥ 6); the other non-ES
     * module kinds (`AMD` / `UMD` / `System` / `None`) remain Bundler-incompatible
     * on every TypeScript version. The version is detected at runtime from
     * `this._ts.version` so the function stays correct across the full
     * peerDependency range (`>=4.3 <7`).
     *
     * @see https://www.typescriptlang.org/tsconfig/#moduleResolution
     */
    private isBundlerCompatibleModuleKind;
    /**
     * Pass `customConditions` through unchanged when the resolved
     * `moduleResolution` is one of the kinds that supports it (`Bundler`,
     * `Node16`, `NodeNext`); strip it otherwise. TypeScript raises TS5098
     * when `customConditions` is paired with any other resolution kind
     * (verified empirically against TypeScript 5.9.3 with `tsc -p`).
     *
     * Before #4198 the surrounding `fixupCompilerOptionsForModuleKind`
     * unconditionally cleared `customConditions` because the hardcoded
     * `Node10` override always made it incompatible. After #4198 the
     * resolved `moduleResolution` can be `Bundler` (e.g. when the user
     * has `Node16`/`NodeNext` paired with an ES-family `module`), so we
     * need to preserve the user's `customConditions` in that case rather
     * than silently dropping it.
     *
     * @see https://www.typescriptlang.org/tsconfig/#customConditions
     */
    private preserveCustomConditionsIfCompatible;
    getCompiledOutput(fileContent: string, fileName: string, options: TsJestCompileOptions): CompiledOutput;
    protected _transpileOutput(fileContent: string, fileName: string): TranspileOutput;
    /**
     * TypeScript 6 reports TS5107 when ts-jest's historical default of
     * `moduleResolution: Node10` is passed to `transpileModule`. The diagnostic
     * is an implementation detail when ts-jest injected that value, but remains
     * actionable when the user selected Node10 themselves.
     */
    private _filterDiagnosticsFromTsJestDefaults;
    protected _makeTransformers(customTransformers: TsJestAstTransformer): CustomTransformers;
}
