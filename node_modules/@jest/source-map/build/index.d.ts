/**
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

import callsites from 'callsites';

/**
 * One remapped `CallSite`, `level` frames above the caller.
 *
 * @deprecated Use `SourceMapSupport#getCallsite` instead.
 */
export declare function getCallsite(
  level: number,
  sourceMaps?: SourceMapRegistry | null,
): callsites.CallSite;

/** Transformed file path to the map file written for it. */
export declare type SourceMapRegistry = ReadonlyMap<string, string>;

export declare class SourceMapSupport {
  private activeCache;
  private nullCache;
  private readonly cachesByRegistry;
  private suppressWarnings;
  private readonly reportedMapPaths;
  private readonly boundFormatStackTrace;
  constructor();
  /**
   * Replaces `Error.prepareStackTrace` in the current realm, so `error.stack`
   * renders frames against the original sources.
   *
   * Stays installed for the lifetime of the worker — each call swaps in its
   * own cache. There is deliberately no `uninstall`: restoring V8's formatter
   * at teardown would leave an error thrown after the environment is torn
   * down pointing into the transformed file. Holding the cache does not
   * retain the environment: it only ever references path strings and parsed
   * source maps.
   */
  install(
    sourceMaps?: SourceMapRegistry | null,
    options?: SourceMapSupportInstallOptions,
  ): void;
  /** One remapped `CallSite`, `level` frames above the caller. */
  getCallsite(
    level: number,
    sourceMaps?: SourceMapRegistry | null,
  ): callsites.CallSite;
  private cacheFor;
  private reportUnparsable;
  private formatStackTrace;
}

export declare interface SourceMapSupportInstallOptions {
  /** Turns off the once-per-map warning about maps that cannot be parsed. */
  suppressWarnings?: boolean;
}
