/*!
 * /**
 *  * Copyright (c) Meta Platforms, Inc. and affiliates.
 *  *
 *  * This source code is licensed under the MIT license found in the
 *  * LICENSE file in the root directory of this source tree.
 *  * /
 */
/******/ (() => { // webpackBootstrap
/******/ 	"use strict";
/******/ 	var __webpack_modules__ = ({

/***/ "./src/helpers.ts"
(__unused_webpack_module, exports) {



Object.defineProperty(exports, "__esModule", ({
  value: true
}));
exports.findSiblingsWithFileExtension = exports.decodePossibleOutsideJestVmPath = exports.createOutsideJestVmPath = void 0;
exports.noop = noop;
function path() {
  const data = _interopRequireWildcard(require("node:path"));
  path = function () {
    return data;
  };
  return data;
}
function _glob() {
  const data = require("glob");
  _glob = function () {
    return data;
  };
  return data;
}
function _slash() {
  const data = _interopRequireDefault(require("slash"));
  _slash = function () {
    return data;
  };
  return data;
}
function _interopRequireDefault(e) { return e && e.__esModule ? e : { default: e }; }
function _interopRequireWildcard(e, t) { if ("function" == typeof WeakMap) var r = new WeakMap(), n = new WeakMap(); return (_interopRequireWildcard = function (e, t) { if (!t && e && e.__esModule) return e; var o, i, f = { __proto__: null, default: e }; if (null === e || "object" != typeof e && "function" != typeof e) return f; if (o = t ? n : r) { if (o.has(e)) return o.get(e); o.set(e, f); } for (const t in e) "default" !== t && {}.hasOwnProperty.call(e, t) && ((i = (o = Object.defineProperty) && Object.getOwnPropertyDescriptor(e, t)) && (i.get || i.set) ? o(f, t, i) : f[t] = e[t]); return f; })(e, t); }
/**
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

const OUTSIDE_JEST_VM_PROTOCOL = 'jest-main:';
// String manipulation is easier here, fileURLToPath is only in newer Nodes,
// plus setting non-standard protocols on URL objects is difficult.
const createOutsideJestVmPath = path => `${OUTSIDE_JEST_VM_PROTOCOL}//${encodeURIComponent(path)}`;
exports.createOutsideJestVmPath = createOutsideJestVmPath;
const decodePossibleOutsideJestVmPath = outsideJestVmPath => {
  if (outsideJestVmPath.startsWith(`${OUTSIDE_JEST_VM_PROTOCOL}//`)) {
    return decodeURIComponent(outsideJestVmPath.slice(OUTSIDE_JEST_VM_PROTOCOL.length + 2));
  }
  return undefined;
};
exports.decodePossibleOutsideJestVmPath = decodePossibleOutsideJestVmPath;
const findSiblingsWithFileExtension = (moduleFileExtensions, from, moduleName) => {
  if (!path().isAbsolute(moduleName) && path().extname(moduleName) === '') {
    const dirname = path().dirname(from);
    const pathToModule = path().resolve(dirname, moduleName);
    try {
      const slashedDirname = (0, _slash().default)(dirname);
      const matches = _glob().glob.sync(`${pathToModule}.*`, {
        windowsPathsNoEscape: true
      }).map(match => {
        const slashedMap = (0, _slash().default)(match);
        const relativePath = path().posix.relative(slashedDirname, slashedMap);
        const slashedPath = path().posix.dirname(slashedMap) === slashedDirname ? `./${relativePath}` : relativePath;
        return `\t'${slashedPath}'`;
      }).join('\n');
      if (matches) {
        const foundMessage = `\n\nHowever, Jest was able to find:\n${matches}`;
        const mappedModuleFileExtensions = moduleFileExtensions.map(ext => `'${ext}'`).join(', ');
        return `${foundMessage}\n\nYou might want to include a file extension in your import, or update your 'moduleFileExtensions', which is currently ` + `[${mappedModuleFileExtensions}].\n\nSee https://jestjs.io/docs/configuration#modulefileextensions-arraystring`;
      }
    } catch {}
  }
  return '';
};
exports.findSiblingsWithFileExtension = findSiblingsWithFileExtension;
function noop() {
  // empty
}

/***/ },

/***/ "./src/internals/CjsExportsCache.ts"
(__unused_webpack_module, exports, __webpack_require__) {



Object.defineProperty(exports, "__esModule", ({
  value: true
}));
exports.CjsExportsCache = void 0;
function path() {
  const data = _interopRequireWildcard(require("node:path"));
  path = function () {
    return data;
  };
  return data;
}
function _cjsModuleLexer() {
  const data = require("cjs-module-lexer");
  _cjsModuleLexer = function () {
    return data;
  };
  return data;
}
var _ModuleExecutor = __webpack_require__("./src/internals/ModuleExecutor.ts");
var _esmLexer = __webpack_require__("./src/internals/esmLexer.ts");
function _interopRequireWildcard(e, t) { if ("function" == typeof WeakMap) var r = new WeakMap(), n = new WeakMap(); return (_interopRequireWildcard = function (e, t) { if (!t && e && e.__esModule) return e; var o, i, f = { __proto__: null, default: e }; if (null === e || "object" != typeof e && "function" != typeof e) return f; if (o = t ? n : r) { if (o.has(e)) return o.get(e); o.set(e, f); } for (const t in e) "default" !== t && {}.hasOwnProperty.call(e, t) && ((i = (o = Object.defineProperty) && Object.getOwnPropertyDescriptor(e, t)) && (i.get || i.set) ? o(f, t, i) : f[t] = e[t]); return f; })(e, t); }
/**
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

// Computes (and caches) the named exports of a CJS module by static analysis
// with cjs-module-lexer, recursively walking `module.exports = require(...)`
// re-exports. Native (`.node`) addons and core-module re-exports can't be
// statically analysed, so they are loaded via the injected callbacks and the
// real export keys are read off the resulting object.
class CjsExportsCache {
  cache = new Map();
  resolution;
  fileCache;
  transformCache;
  loadNativeAddon;
  loadCoreReexport;
  constructor(options) {
    this.resolution = options.resolution;
    this.fileCache = options.fileCache;
    this.transformCache = options.transformCache;
    this.loadNativeAddon = options.loadNativeAddon;
    this.loadCoreReexport = options.loadCoreReexport;
  }

  // `from` is the module asking for the exports - propagated to the load
  // callbacks so user mocks (`jest.mock('./addon.node', factory)`) dispatch
  // against the real importer rather than an empty placeholder. The cache is
  // keyed by `modulePath` only (export keys don't depend on the importer);
  // `from` matters only for the cache-miss load.
  getExportsOf(from, modulePath) {
    const cached = this.cache.get(modulePath);
    if (cached) return cached;
    if (path().extname(modulePath) === '.node') {
      const nativeModule = this.loadNativeAddon(from, modulePath);
      const namedExports = new Set(Object.keys(nativeModule));
      this.cache.set(modulePath, namedExports);
      return namedExports;
    }
    const transformedCode = this.transformCache.getCachedSource(modulePath) ?? this.fileCache.readFile(modulePath);
    let exports;
    let reexports;
    try {
      ({
        exports,
        reexports
      } = (0, _cjsModuleLexer().parse)(transformedCode));
    } catch (error) {
      if (error instanceof Error && (0, _esmLexer.hasEsmSyntax)(transformedCode)) {
        throw new _ModuleExecutor.CjsParseError(error);
      }
      throw error;
    }
    const namedExports = new Set(exports);
    // Cache before walking re-exports so a cycle terminates: a re-entrant call
    // gets the set built so far instead of recursing forever. The set is
    // mutated in place, so the cached reference stays complete afterwards.
    this.cache.set(modulePath, namedExports);
    try {
      for (const reexport of reexports) {
        if (this.resolution.isCoreModule(reexport)) {
          const coreExports = this.loadCoreReexport(modulePath, reexport);
          if (coreExports !== null && typeof coreExports === 'object') {
            for (const key of Object.keys(coreExports)) namedExports.add(key);
          }
        } else {
          const resolved = this.resolution.resolveCjs(modulePath, reexport);
          // A re-exported ES module has no CJS export list. Letting the parse
          // error escape would make the caller treat the *re-exporting* module
          // as ESM; its names still reach importers off the runtime exports
          // object.
          let reexportedNames;
          try {
            reexportedNames = this.getExportsOf(modulePath, resolved);
          } catch (error) {
            if (error instanceof _ModuleExecutor.CjsParseError) continue;
            throw error;
          }
          for (const key of reexportedNames) namedExports.add(key);
        }
      }
    } catch (error) {
      // Drop the entry seeded above: a walk that failed part-way must not
      // leave its partial export list memoized, or the next call returns it
      // as success and swallows this error.
      //
      // The rollback is deliberately shallow. Inside a re-export cycle, a
      // dependency that finished against this module's in-progress set keeps
      // its own entry, so looking that dependency up later yields a set built
      // from partial data instead of retrying. Reaching that needs a cycle
      // *and* a sibling re-export that fails to resolve, and callers execute
      // the module before asking for its exports - an unresolvable `require`
      // throws there first. Rolling back the whole traversal would mean
      // tracking every entry it seeded, which is more re-entrant bookkeeping
      // than the case warrants.
      this.cache.delete(modulePath);
      throw error;
    }
    return namedExports;
  }
  clear() {
    this.cache.clear();
  }
}
exports.CjsExportsCache = CjsExportsCache;

/***/ },

/***/ "./src/internals/CjsLoader.ts"
(__unused_webpack_module, exports, __webpack_require__) {



Object.defineProperty(exports, "__esModule", ({
  value: true
}));
exports.CjsLoader = void 0;
function path() {
  const data = _interopRequireWildcard(require("node:path"));
  path = function () {
    return data;
  };
  return data;
}
function _jestUtil() {
  const data = require("jest-util");
  _jestUtil = function () {
    return data;
  };
  return data;
}
var _ModuleExecutor = __webpack_require__("./src/internals/ModuleExecutor.ts");
var _esmLexer = __webpack_require__("./src/internals/esmLexer.ts");
var _moduleTypes = __webpack_require__("./src/internals/moduleTypes.ts");
var _nodeCapabilities = __webpack_require__("./src/internals/nodeCapabilities.ts");
function _interopRequireWildcard(e, t) { if ("function" == typeof WeakMap) var r = new WeakMap(), n = new WeakMap(); return (_interopRequireWildcard = function (e, t) { if (!t && e && e.__esModule) return e; var o, i, f = { __proto__: null, default: e }; if (null === e || "object" != typeof e && "function" != typeof e) return f; if (o = t ? n : r) { if (o.has(e)) return o.get(e); o.set(e, f); } for (const t in e) "default" !== t && {}.hasOwnProperty.call(e, t) && ((i = (o = Object.defineProperty) && Object.getOwnPropertyDescriptor(e, t)) && (i.get || i.set) ? o(f, t, i) : f[t] = e[t]); return f; })(e, t); }
/**
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

class CjsLoader {
  resolution;
  registries;
  mockState;
  transformCache;
  environment;
  coreModule;
  executor;
  requireEsm;
  testState;
  logFormattedReferenceError;
  constructor(options) {
    this.resolution = options.resolution;
    this.registries = options.registries;
    this.mockState = options.mockState;
    this.transformCache = options.transformCache;
    this.environment = options.environment;
    this.coreModule = options.coreModule;
    this.executor = options.executor;
    this.requireEsm = options.requireEsm;
    this.testState = options.testState;
    this.logFormattedReferenceError = options.logFormattedReferenceError;
  }
  requireModule(from, moduleName, options, isRequireActual = false) {
    if (moduleName && this.resolution.isCoreModule(moduleName)) {
      return this.coreModule.require(moduleName);
    }
    const isInternal = options?.isInternalModule ?? false;
    let modulePath;

    // Some old tests rely on this mocking behavior. Ideally we'll change this
    // to be more explicit.
    if (moduleName && !isInternal && !isRequireActual && !this.resolution.getModule(moduleName)) {
      const manualMock = this.resolution.getCjsMockModule(from, moduleName);
      if (manualMock && manualMock !== this.executor.getCurrentlyExecutingManualMock() && !this.mockState.isExplicitlyUnmocked(this.mockState.getCjsModuleId(from, moduleName))) {
        modulePath = manualMock;
      }
    }
    if (!modulePath) {
      modulePath = this.resolution.resolveCjs(from, moduleName);
    }

    // On Node 24.9+ we can require() ESM natively. On older Node, fall
    // through to the CJS path so a configured transform can convert it.
    if (_nodeCapabilities.supportsSyncEvaluate && this.resolution.shouldLoadAsEsm(modulePath)) {
      const exports = this.requireEsm(modulePath, from, isRequireActual, moduleName);
      if (!isInternal) {
        this.recordEsmChildModule(from, modulePath);
      }
      return exports;
    }
    const moduleRegistry = isInternal ? this.registries.getInternalCjsRegistry() : this.registries.getActiveCjsRegistry();
    const module = moduleRegistry.get(modulePath);
    if (module) {
      if (!isInternal) {
        this.recordChildModule(from, module, moduleRegistry);
      }
      return module.exports;
    }

    // We must register the pre-allocated module object first so that any
    // circular dependencies that may arise while evaluating the module can
    // be satisfied.
    const localModule = (0, _moduleTypes.createInitialModule)(modulePath);
    moduleRegistry.set(modulePath, localModule);
    if (!isInternal) {
      this.recordChildModule(from, localModule, moduleRegistry);
    }
    try {
      this.loadModule(localModule, from, moduleName, modulePath, options, moduleRegistry);
    } catch (error) {
      moduleRegistry.delete(modulePath);
      this.removeChildModule(from, localModule, moduleRegistry);
      if (error instanceof _ModuleExecutor.CjsParseError) {
        return this.handleCjsParseError(modulePath, from, error, isRequireActual, moduleName);
      }
      // Without --experimental-vm-modules, CjsParseError is never thrown.
      // Detect untransformed ESM syntax and surface an actionable error.
      if (!_nodeCapabilities.supportsSyncEvaluate && (0, _jestUtil().isError)(error) && error.name === 'SyntaxError' && (0, _esmLexer.hasEsmSyntax)(this.transformCache.getCachedSource(modulePath) ?? '') && !this.resolution.isExplicitlyCommonjs(modulePath)) {
        throw createRequireEsmError(modulePath);
      }
      throw error;
    }
    return localModule.exports;
  }

  // Runs after evaluation, unlike Node, which registers the child before the
  // ESM body executes: the wrapper's `exports` is the require() result,
  // which exists only once the module is `evaluated` - registering earlier
  // would break the evaluated-only invariant the require.cache surface
  // relies on. The end state matches Node either way (present on success,
  // absent on failure), and a require() cycle back into the parent throws
  // ERR_REQUIRE_CYCLE_MODULE before it could observe the difference.
  //
  // The parent lookup targets the active CJS registry, so an internal
  // requiring module (registered elsewhere) never gains children.
  recordEsmChildModule(from, modulePath) {
    const wrapper = this.registries.getEsmRequireCacheEntry(modulePath);
    if (wrapper) {
      this.recordChildModule(from, wrapper, this.registries.getActiveCjsRegistry());
    }
  }

  // Node records every loaded module on its requiring parent - fresh loads
  // before evaluation and registry hits alike, deduplicated. A load that
  // throws is removed again.
  recordChildModule(from, child, moduleRegistry) {
    const parent = moduleRegistry.get(from);
    if (!parent || !('children' in parent) || parent.children.includes(child)) {
      return;
    }
    parent.children.push(child);
  }
  removeChildModule(from, child, moduleRegistry) {
    const parent = moduleRegistry.get(from);
    if (!parent || !('children' in parent)) {
      return;
    }
    const index = parent.children.indexOf(child);
    if (index !== -1) {
      parent.children.splice(index, 1);
    }
  }

  /**
   * The CJS compiler rejected the file (ESM syntax in a non-ESM context).
   * On Node 24.9+ retry as native ESM; on older Node either surface an
   * actionable error (for explicitly ESM-marked files) or re-throw so the
   * ESM loader's own CjsParseError catch can trigger its fallback path.
   */
  handleCjsParseError(modulePath, from, parseError, isRequireActual, moduleName) {
    // A package that declares `"type": "commonjs"` opted out of ESM for its
    // `.js` files - Node throws the parse error instead of retrying.
    if (this.resolution.isExplicitlyCommonjs(modulePath)) {
      throw parseError.cause;
    }
    if (_nodeCapabilities.supportsSyncEvaluate) {
      let exports;
      try {
        exports = this.requireEsm(modulePath, from, isRequireActual, moduleName);
      } catch (esmError) {
        // Both CJS and ESM parsers rejected it — surface the original CJS error.
        if (esmError instanceof Error && esmError.name === 'SyntaxError') {
          throw parseError.cause;
        }
        throw esmError;
      }
      this.recordEsmChildModule(from, modulePath);
      return exports;
    }
    // Explicitly ESM-marked files (.mjs / "type":"module") can't be retried
    // by the ESM loader — give the user an actionable error.
    if (this.resolution.shouldLoadAsEsm(modulePath)) {
      throw createRequireEsmError(modulePath);
    }
    // Unmarked file with ESM syntax — re-throw so the ESM loader can retry.
    throw parseError;
  }
  loadModule(localModule, from, moduleName, modulePath, options, moduleRegistry) {
    if (path().extname(modulePath) === '.json') {
      const transformed = this.transformCache.transformJson(modulePath, options);
      localModule.exports = this.environment.global.JSON.parse(transformed);
    } else if (path().extname(modulePath) === '.node') {
      localModule.exports = require(modulePath);
    } else {
      // testState gates apply only to executing JS bodies - JSON/.node go
      // through pure data parsing and don't run user code in the VM.
      if (this.testState.bailIfTornDown('You are trying to `require` a file after the Jest environment has been torn down.')) {
        return;
      }
      if (!_nodeCapabilities.runtimeSupportsVmModules) {
        this.testState.throwIfBetweenTests('You are trying to `require` a file outside of the scope of the test code.');
      }
      const fromPath = moduleName ? from : null;
      const result = this.executor.exec(localModule, options, moduleRegistry, fromPath, moduleName);
      if (result === 'env-disposed') {
        this.logFormattedReferenceError('You are trying to `require` a file after the Jest environment has been torn down.');
        process.exitCode = 1;
        return;
      }
    }
    localModule.loaded = true;
  }
}
exports.CjsLoader = CjsLoader;
function createRequireEsmError(modulePath) {
  const error = new Error(`Must use import to load ES Module: ${modulePath}\n\n` + 'The file contains ESM syntax (import/export) that could not be ' + 'executed as CommonJS. Either:\n' + '  - Configure a transform (e.g. babel-jest) that compiles this ' + 'file to CommonJS (see https://jestjs.io/docs/code-transformation)\n' + '  - If the file is in "node_modules", allow it to be transformed by ' + 'adjusting "transformIgnorePatterns" (see ' + 'https://jestjs.io/docs/configuration#transformignorepatterns-arraystring)\n' + '  - Use Node v24.9+ where Jest supports require(esm) natively ' + '(see https://jestjs.io/docs/ecmascript-modules#require-of-esm)');
  error.code = 'ERR_REQUIRE_ESM';
  return error;
}

/***/ },

/***/ "./src/internals/EsmLoader.ts"
(__unused_webpack_module, exports, __webpack_require__) {



Object.defineProperty(exports, "__esModule", ({
  value: true
}));
exports.LOAD_ASYNC = exports.EsmLoader = void 0;
function path() {
  const data = _interopRequireWildcard(require("node:path"));
  path = function () {
    return data;
  };
  return data;
}
function _nodeUrl() {
  const data = require("node:url");
  _nodeUrl = function () {
    return data;
  };
  return data;
}
function _nodeVm() {
  const data = require("node:vm");
  _nodeVm = function () {
    return data;
  };
  return data;
}
function _stripBom() {
  const data = _interopRequireDefault(require("strip-bom"));
  _stripBom = function () {
    return data;
  };
  return data;
}
function _jestUtil() {
  const data = require("jest-util");
  _jestUtil = function () {
    return data;
  };
  return data;
}
var _helpers = __webpack_require__("./src/helpers.ts");
var _ModuleExecutor = __webpack_require__("./src/internals/ModuleExecutor.ts");
var _Resolution = __webpack_require__("./src/internals/Resolution.ts");
var _dataUri = __webpack_require__("./src/internals/dataUri.ts");
var _esmLexer = __webpack_require__("./src/internals/esmLexer.ts");
var _importAttributes = __webpack_require__("./src/internals/importAttributes.ts");
var _nodeCapabilities = __webpack_require__("./src/internals/nodeCapabilities.ts");
var _syntheticBuilders = __webpack_require__("./src/internals/syntheticBuilders.ts");
function _interopRequireDefault(e) { return e && e.__esModule ? e : { default: e }; }
function _interopRequireWildcard(e, t) { if ("function" == typeof WeakMap) var r = new WeakMap(), n = new WeakMap(); return (_interopRequireWildcard = function (e, t) { if (!t && e && e.__esModule) return e; var o, i, f = { __proto__: null, default: e }; if (null === e || "object" != typeof e && "function" != typeof e) return f; if (o = t ? n : r) { if (o.has(e)) return o.get(e); o.set(e, f); } for (const t in e) "default" !== t && {}.hasOwnProperty.call(e, t) && ((i = (o = Object.defineProperty) && Object.getOwnPropertyDescriptor(e, t)) && (i.get || i.set) ? o(f, t, i) : f[t] = e[t]); return f; })(e, t); }
/**
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

// `'sync-required'` is `require(esm)` (must be loaded synchronously, throw a
// typed error on edges that would normally bail). `'sync-preferred'` is the
// fast path for `await import()` (try sync; fall back to the legacy async
// loader on any unsupported edge).

// Returned by sync-graph methods when a dependency or condition prevents
// synchronous loading. Callers propagate it upward; the top-level
// `tryLoadGraphSync` caller falls back to the legacy async path.
const LOAD_ASYNC = exports.LOAD_ASYNC = 'load-async';

// Work discovered during resolution whose build executes user code, deferred
// until the whole graph has resolved - Node reports resolution and
// import-attribute errors before executing anything. A CJS dep executes its
// body when built (the export-name merge needs the runtime exports object);
// a factory-less mock decision (automock or manual __mocks__) executes the
// real module to collect metadata.

// Shape of the third arg Node passes to the `module.link` callback. TC39 final
// is `{attributes}`; legacy was `{assert}`. `@types/node@18` only types the
// legacy field, so we declare both ourselves.
// TODO(jest next major): drop `assert` once we require Node 22+.

// Source-text entries carry their dep cacheKeys (used for `linkRequests`).
// `'prelinked'` covers everything that arrives already linked - mocks, core,
// JSON, wasm, @jest/globals, plus modules adopted from the registry, which
// may be `SourceTextModule`s rather than `SyntheticModule`s - so it never
// appears in the link-requests pass.

// `SourceTextModule#hasAsyncGraph()` lets us prove a graph is sync-evaluable.
// `SyntheticModule` does not expose it but is by definition sync (the user
// callback is sync), so treat its absence as "not async".
function moduleHasAsyncGraph(module) {
  return typeof module.hasAsyncGraph === 'function' ? module.hasAsyncGraph() : false;
}

// Mirrors Node's `require(esm)` error code so user catches work uniformly.
function makeRequireAsyncError(modulePath, detail) {
  const error = new Error(`require() cannot be used to load ES Module ${modulePath}: ${detail}`);
  error.code = 'ERR_REQUIRE_ASYNC_MODULE';
  return error;
}

// Node satisfies a source-phase request (`import source` / `import.source()`,
// used for WebAssembly) by attaching the compiled `WebAssembly.Module` to the
// target module via the internal `ModuleWrap#setModuleSourceObject`. `node:vm`
// exposes no equivalent - no option on `SyntheticModule`, no method on
// `vm.Module` - so the phase cannot be implemented here until it does.
// Without this check V8 fails at `instantiate()` with a bare "Source phase
// import object is not defined" SyntaxError pointing at Jest internals.
function makeSourcePhaseUnsupportedError(specifier, referencingIdentifier) {
  const error = new SyntaxError(`Jest cannot load '${specifier}' (imported from ${referencingIdentifier}): source phase imports ('import source' / 'import.source()') are not supported, because node:vm has no way to provide a module source object.`);
  error.code = 'ERR_SOURCE_PHASE_NOT_DEFINED';
  return error;
}
function makeRequireCycleError(modulePath, requiredFrom) {
  const fromPart = requiredFrom === undefined ? '' : ` (from ${requiredFrom})`;
  const error = new Error(`Cannot require() ES Module ${modulePath} in a cycle.${fromPart} A cycle involving require(esm) is not allowed to maintain invariants mandated by the ECMAScript specification. Try making at least part of the dependency in the graph lazily loaded.`);
  error.code = 'ERR_REQUIRE_CYCLE_MODULE';
  return error;
}
const urlSchemeRegex = /^[A-Za-z][A-Za-z0-9+.-]*:/;
const ESM_TRANSFORM_OPTIONS = {
  isInternalModule: false,
  supportsDynamicImport: true,
  supportsExportNamespaceFrom: true,
  supportsStaticESM: true,
  supportsTopLevelAwait: true
};
// The scheme is case-insensitive and the authority is optional, so
// `file:/tmp/a.mjs` and `FILE:///tmp/a.mjs` are the same URL as
// `file:///tmp/a.mjs` - all three have to reach `fileURLToPath` rather than
// the package resolver.
const fileSchemeRegex = /^file:/i;

// A `?` or `#` in an import specifier is a query/fragment delimiter, never a
// filename character: the fragment starts at the first `#`, the query at the
// first `?` before it. `data:` URIs pass through whole - their `?`/`#` belong
// to the URI payload. A leading `#` opens a package-imports specifier rather
// than a fragment, so the scan starts past it and the rest of the string
// splits as usual - `#lib/a.mjs?v=2` resolves `#lib/a.mjs` and carries `?v=2`
// onto the target, the same instance Node's wildcard substitution produces.
function splitQueryAndFragment(specifier) {
  if (specifier.startsWith('data:')) {
    return {
      pathOrSpecifier: specifier,
      suffix: ''
    };
  }
  if (fileSchemeRegex.test(specifier)) {
    const url = new URL(specifier);
    return {
      pathOrSpecifier: (0, _nodeUrl().fileURLToPath)(url),
      suffix: url.search + url.hash
    };
  }
  const scanFrom = specifier.startsWith('#') ? 1 : 0;
  const hashIndex = specifier.indexOf('#', scanFrom);
  const beforeFragment = hashIndex === -1 ? specifier : specifier.slice(0, hashIndex);
  const queryIndex = beforeFragment.indexOf('?', scanFrom);
  const splitIndex = queryIndex === -1 ? hashIndex : queryIndex;
  if (splitIndex === -1) {
    return {
      pathOrSpecifier: specifier,
      suffix: ''
    };
  }
  return {
    pathOrSpecifier: specifier.slice(0, splitIndex),
    suffix: specifier.slice(splitIndex)
  };
}

// ESM registry keys are serialized URLs, matching Node's per-URL module
// instancing: `?a`/`?a` share an instance while `?b`, `#frag` and the plain
// form are all distinct. Reading the suffix back off `search`/`hash` applies
// the normalization Node's resolver applies: non-ASCII and spaces
// percent-encode, and an empty `?` or `#` drops out, so `./a.mjs?` names the
// same instance as `./a.mjs`.
function fileCacheKey(modulePath, suffix) {
  const baseUrl = (0, _nodeUrl().pathToFileURL)(modulePath);
  if (suffix === '') return baseUrl.href;
  const url = new URL(suffix, baseUrl);
  return baseUrl.href + url.search + url.hash;
}
function makeUnknownBuiltinError(specifier) {
  const error = new Error(`No such built-in module: ${specifier}`);
  error.code = 'ERR_UNKNOWN_BUILTIN_MODULE';
  return error;
}

// Every builtin has a `node:`-prefixed spelling, but not every builtin has a
// bare one - `node:sea`, `node:sqlite`, `node:test` and `node:test/reporters`
// exist only with the prefix. Prefixing is therefore the total direction.
function canonicalCoreSpecifier(specifier) {
  return specifier.startsWith('node:') ? specifier : `node:${specifier}`;
}
class EsmLoader {
  resolution;
  fileCache;
  transformCache;
  registries;
  mockState;
  environment;
  cjsExportsCache;
  coreModule;
  jestGlobals;
  shouldLoadAsEsm;
  requireModule;
  requireModuleOrMock;
  testState;
  testPath;
  requireFacades = new WeakMap();
  importAttributeValidator = new _importAttributes.ImportAttributeValidator();
  // Depth of automock-metadata loads in progress. Generation loads the real
  // module, whose graph must not recurse into generating its own deps: a
  // cycle back into an in-progress generation would link against an empty
  // placeholder mock. See `mockDecisionServable`.
  generatingMockDepth = 0;
  // Every cacheKey popped by a sync walk that is still on the stack. Walks
  // nest (a CJS body executing mid-walk can require() an unrelated ESM root),
  // so this is a stack of one entry per walk rather than a single Set. Each
  // walk records the registry it ran against: an isolation overlay or an
  // automock scratch registry is a separate instance space, so a key match
  // across registries is a fresh build, not a cycle.
  activeSyncWalks = [];
  // Used only by the legacy async path; deletable when min-Node ≥ v24.9
  // (delete the block at the bottom of this file too - eslint/tsc will
  // surface anything else that becomes unused).
  linkingMap = new WeakMap();
  evaluatingMap = new WeakMap();
  constructor(options) {
    this.resolution = options.resolution;
    this.fileCache = options.fileCache;
    this.transformCache = options.transformCache;
    this.registries = options.registries;
    this.mockState = options.mockState;
    this.environment = options.environment;
    this.cjsExportsCache = options.cjsExportsCache;
    this.coreModule = options.coreModule;
    this.jestGlobals = options.jestGlobals;
    this.shouldLoadAsEsm = options.shouldLoadAsEsm;
    this.requireModule = options.requireModule;
    this.requireModuleOrMock = options.requireModuleOrMock;
    this.testState = options.testState;
    this.testPath = options.testPath;
  }

  // `'load-async'` means the sync graph could not be completed — a concurrent
  // `await import()` is mid-flight, a dependency is async-only, etc. Surface
  // as ERR_REQUIRE_ESM with actionable context.
  requireEsmModule(modulePath, requiredFrom, isRequireActual = false, moduleName) {
    // The graph walk (and the mock decision below) resolves synchronously.
    // With an async-only configured resolver that silently falls back to the
    // default resolver, missing every user mapping - throw the same typed
    // error an async-only transformer gets.
    //
    // The guard sits here, after `CjsLoader` has already resolved the entry:
    // sync require() resolution falls back to the default resolver for CJS
    // targets too, and gating all of require() would break configs where
    // that fallback resolves correctly. A name only the async resolver can
    // map therefore still fails as unresolvable before ESM handling starts.
    if (!this.resolution.canResolveSync()) {
      throw makeRequireAsyncError(modulePath, 'the configured resolver is async-only');
    }
    if (!isRequireActual) {
      const from = requiredFrom ?? modulePath;
      const {
        shouldMock,
        moduleID
      } = this.mockState.shouldMockEsmSync(from, modulePath);
      if (shouldMock && this.mockDecisionServable(moduleID) && !this.cjsUnmockBlocksGeneratedMock(from, moduleName ?? modulePath, modulePath, moduleID)) {
        return this.requireResultFromModule(this.requireMockedEsmModule(from, modulePath, moduleID));
      }
    }
    const module = this.tryLoadGraphSync(modulePath, '', 'sync-required', requiredFrom);
    if (module === LOAD_ASYNC) {
      const error = new Error(`Cannot require() ES Module ${modulePath} synchronously: it is currently being loaded by a concurrent \`import()\`. Await that import before calling require(), or import this module instead of requiring it.`);
      error.code = 'ERR_REQUIRE_ESM';
      throw error;
    }
    return this.requireResultFromModule(module);
  }

  // Mirrors Node's `populateCJSExportsFromESM`: a `'module.exports'` named
  // export wins outright; a module without a `default` export - or one that
  // defines its own `__esModule`, which users may set to override tooling
  // detection - hands back the raw namespace. Everything else gets a facade
  // namespace carrying `__esModule: true`, so transpiled consumers that pick
  // `mod.__esModule ? mod.default : mod` see require()d real ESM as ESM.
  requireResultFromModule(module) {
    const namespace = module.namespace;
    if ('module.exports' in namespace) {
      return namespace['module.exports'];
    }
    // The facade needs `linkRequests`/`instantiate` and a synchronously
    // settling evaluate - the capabilities `require(esm)` itself is gated on.
    // Below that gate this is reached only through `require.cache` reads,
    // which keep handing out the raw namespace.
    if (!_nodeCapabilities.supportsSyncEvaluate || module.status !== 'evaluated' || !('default' in namespace) || '__esModule' in namespace) {
      return namespace;
    }
    return this.requireFacadeFor(module).namespace;
  }

  // The require(esm) counterpart of `importMockSync`: produce the mock as an
  // evaluated module outside any walk. `importMockSync` handles instance
  // reuse and factory invocation; a fresh synthetic arrives `'linked'`
  // (evaluation normally belongs to the walk's cascade), so evaluate it here.
  // Entry for Runtime's CJS mock machinery when the decision to mock was
  // already made on the CJS side (explicit `jest.mock` or automock): serve
  // the mock without re-consulting the ESM decision maps, which know nothing
  // about CJS registrations.
  requireEsmMock(from, moduleName, modulePath) {
    // Prefer the ID an `import` of the same name produces - it also carries
    // any root manual-mock path, so require() and import share one mock
    // instance. The path-derived ID stays for conditional packages whose
    // condition sets diverge: those genuinely reference different files.
    let moduleID;
    try {
      moduleID = this.resolution.resolveEsm(from, moduleName) === modulePath ? this.mockState.getEsmModuleId(from, moduleName) : this.mockState.getEsmModuleId(from, modulePath);
    } catch {
      moduleID = this.mockState.getEsmModuleId(from, modulePath);
    }
    return this.requireResultFromModule(this.requireMockedEsmModule(from, moduleName, moduleID, modulePath));
  }
  requireMockedEsmModule(from, moduleName, moduleID, resolvedPath) {
    const scratch = new Map();
    const mocked = this.importMockSync(from, moduleName, moduleID, this.getContext(), scratch, 'sync-required', resolvedPath);
    (0, _jestUtil().invariant)(mocked !== LOAD_ASYNC, 'importMockSync must throw rather than bail in sync-required mode. This is a bug in Jest, please report it!');
    const module = scratch.get(mocked.cacheKey).module;
    if (module.status === 'evaluated') {
      return module;
    }
    const evaluated = this.evaluateLinkedModule(module, 'sync-required');
    (0, _jestUtil().invariant)(evaluated !== LOAD_ASYNC, 'evaluateLinkedModule must throw rather than bail in sync-required mode. This is a bug in Jest, please report it!');
    return evaluated;
  }

  // Node builds the facade as a real source-text module rather than a copy or
  // a Proxy - re-exporting keeps the original's bindings live and enumerable
  // with no per-access overhead. The original is already evaluated and the
  // facade body has no top-level await, so evaluation completes synchronously.
  requireFacadeFor(module) {
    const cached = this.requireFacades.get(module);
    if (cached) return cached;
    const facade = new (_nodeVm().SourceTextModule)("export * from 'original';\nexport {default} from 'original';\nexport const __esModule = true;", {
      context: this.getContext(),
      identifier: `jest-require-facade:${module.identifier}`
    });
    (0, _jestUtil().invariant)(typeof facade.linkRequests === 'function' && typeof facade.instantiate === 'function', 'linkRequests/instantiate unavailable on the require facade');
    facade.linkRequests([module]);
    facade.instantiate();
    facade.evaluate().catch(_helpers.noop);
    (0, _jestUtil().invariant)(facade.status === 'evaluated', `Expected the require facade for ${module.identifier} to evaluate synchronously, but its status is "${facade.status}". This is a bug in Jest, please report it!`);
    this.requireFacades.set(module, facade);
    return facade;
  }

  // Public for unit-test access. Production callers reach the sync graph
  // through `requireEsmModule` (sync require entry) or via `loadEsmModule`
  // (the legacy async entry, which first-tries this).
  tryLoadGraphSync(rootPath, rootSuffix, mode, requiredFrom) {
    this.testState.throwIfTornDown('You are trying to `import` a file after the Jest environment has been torn down.');
    const registry = this.registries.getActiveEsmRegistry();
    const {
      cacheKey: rootKey,
      modulePath: canonicalRootPath
    } = this.canonicalizeRoot(rootPath, rootSuffix);
    const cached = registry.get(rootKey);
    if (cached) {
      if (cached instanceof Promise) return LOAD_ASYNC;
      // The legacy `loadEsmModule` source-text branch does `registry.set`
      // while the `SourceTextModule` is still `'unlinked'` (link runs later
      // in `linkAndEvaluateModule`); accessing `.namespace` on a non-evaluated
      // module throws `ERR_VM_MODULE_STATUS`. So: reuse `'evaluated'`,
      // rethrow `'errored'`, evaluate `'linked'` (already instantiated, just
      // never evaluated), and bail on everything still being linked.
      if (cached.status === 'evaluated') return cached;
      if (cached.status === 'errored') throw cached.error;
      if (cached.status === 'linked') {
        return this.evaluateLinkedModule(cached, mode);
      }
      // `'unlinked'` / `'linking'` / `'evaluating'`. When the key belongs to
      // a walk still on the stack this is a require() of a module whose own
      // evaluation is in progress - a cycle, not a concurrent import().
      if (mode === 'sync-required' && this.isReEnteringActiveWalk(rootKey, registry)) {
        throw makeRequireCycleError(canonicalRootPath, requiredFrom);
      }
      return LOAD_ASYNC;
    }

    // A require() re-entering a graph that is still being walked would build
    // and evaluate a second copy of every uncommitted module (scratch entries
    // reach the registry only after the root instantiates). Node throws
    // ERR_REQUIRE_CYCLE_MODULE here; a dynamic import() of the same shape is
    // a legal ESM cycle, so `'sync-preferred'` is exempt.
    if (mode === 'sync-required' && this.isReEnteringActiveWalk(rootKey, registry)) {
      throw makeRequireCycleError(canonicalRootPath, requiredFrom);
    }
    const context = this.getContext();
    if (this.transformCache.hasMutex(rootKey)) return LOAD_ASYNC;
    const scratch = new Map();
    const worklist = [{
      cacheKey: rootKey,
      modulePath: canonicalRootPath
    }];
    const activeWalk = new Set();
    this.activeSyncWalks.push({
      keys: activeWalk,
      registry,
      rootKey
    });
    try {
      return this.walkGraphSync({
        activeWalk,
        context,
        mode,
        registry,
        rootKey,
        scratch,
        worklist
      });
    } finally {
      this.activeSyncWalks.pop();
    }
  }

  // Node resolves a core specifier with a query or fragment as a builtin
  // lookup of the whole string, which no builtin matches.
  canonicalizeRoot(rootPath, rootSuffix) {
    if (this.resolution.isCoreModule(rootPath)) {
      const canonical = canonicalCoreSpecifier(rootPath);
      if (rootSuffix !== '') {
        throw makeUnknownBuiltinError(canonical + rootSuffix);
      }
      return {
        cacheKey: canonical,
        modulePath: canonical
      };
    }
    if (rootPath.startsWith('data:')) {
      const canonical = new URL(rootPath).href;
      return {
        cacheKey: canonical,
        modulePath: canonical
      };
    }
    return {
      cacheKey: fileCacheKey(rootPath, rootSuffix),
      modulePath: rootPath
    };
  }
  isReEnteringActiveWalk(rootKey, registry) {
    return this.activeSyncWalks.some(walk => walk.registry === registry && walk.keys.has(rootKey));
  }

  // An ancestor walk's root maps to a module Node would report as
  // `'evaluating'` when a mid-walk require()'s graph reaches back to it -
  // Node throws ERR_REQUIRE_CYCLE_MODULE there. The current walk (the last
  // stack entry) is excluded: a dep matching its own root is a static ESM
  // cycle, which is legal.
  isAncestorWalkRoot(cacheKey, registry) {
    return this.activeSyncWalks.slice(0, -1).some(walk => walk.registry === registry && walk.rootKey === cacheKey);
  }

  // A require() fired from a CJS body executing mid-walk can run a nested
  // walk that builds and commits modules this walk has also scratched but
  // not committed (scratch entries are invisible to the nested walk).
  // Adopt the committed instances: linking and evaluating our own copies
  // would evaluate those modules a second time and leave this graph holding
  // different instances than the registry serves everyone else. A dynamic
  // import() cannot race the walk the same way - the walk never awaits, so
  // an import()'s continuation only runs after the walk has settled and
  // committed, where it finds the finished entries.
  adoptCommittedScratchEntries(scratch, registry) {
    for (const [cacheKey, entry] of scratch) {
      const committed = registry.get(cacheKey);
      if (!committed || committed instanceof Promise) continue;
      // Prelinked entries adopted from the registry (or committed eagerly,
      // like @jest/globals synthetics) already hold the registry's module.
      if (committed === entry.module) continue;
      if (committed.status === 'errored') throw committed.error;
      if (committed.status === 'evaluated' || committed.status === 'linked') {
        scratch.set(cacheKey, {
          cacheKey,
          kind: 'prelinked',
          module: committed
        });
      }
    }
  }
  walkGraphSync({
    rootKey,
    activeWalk,
    worklist,
    scratch,
    registry,
    context,
    mode
  }) {
    // CJS deps build only once the worklist has drained, so every resolution
    // and import-attribute error in the graph surfaces before any CJS body
    // executes - as in Node, where a graph that fails to resolve runs
    // nothing. A build whose file turns out to hold unmarked ESM syntax
    // re-enters the worklist, which can in turn discover more CJS deps.
    // Node evaluates these bodies interleaved with the ESM bodies in
    // post-order; here they run in discovery order before the ESM bodies -
    // see the divergences list in docs/ECMAScriptModules.md.
    const pendingBuilds = {
      cjs: [],
      mocks: []
    };
    const confirmedCjsBuilds = [];
    while (worklist.length > 0 || pendingBuilds.cjs.length > 0 || pendingBuilds.mocks.length > 0 || confirmedCjsBuilds.length > 0) {
      if (worklist.length === 0 && pendingBuilds.cjs.length > 0) {
        // Classify without executing: a pending file that turns out to hold
        // unmarked ESM syntax re-enters the worklist, and its subgraph can
        // still fail to resolve - so no CJS body may run until every pending
        // is confirmed. The probe lexes the same transformed source the
        // build would; explicitly-CJS parse failures defer to the build so
        // the canonical compile error surfaces.
        const pending = pendingBuilds.cjs.shift();
        if (this.pendingCjsBuildIsUnmarkedEsm(pending)) {
          worklist.push({
            cacheKey: pending.cacheKey,
            modulePath: pending.modulePath
          });
        } else {
          confirmedCjsBuilds.push(pending);
        }
        continue;
      }
      if (worklist.length === 0 && pendingBuilds.mocks.length > 0) {
        // Generation runs after this graph resolves, but each mock's
        // metadata load evaluates its own real-module graph as it goes - a
        // later pending mock whose real graph fails to load does not undo an
        // earlier one's execution. Preflighting every mock target's graph
        // would need a resolve-only walker mode; documented as a divergence
        // instead.
        const pending = pendingBuilds.mocks.shift();
        const mocked = this.importMockSync(pending.from, pending.specifierPath, pending.moduleID, context, scratch, mode);
        if (mocked === LOAD_ASYNC) return LOAD_ASYNC;
        continue;
      }
      if (worklist.length === 0) {
        // Resolution is complete - executing the confirmed CJS bodies is
        // safe. A build can still surface a CjsParseError the lexer missed
        // (V8 sees ESM syntax the lexer tolerates); that file re-enters the
        // worklist like any other unmarked-ESM fallback.
        const pending = confirmedCjsBuilds.shift();
        try {
          const committed = this.tryCommitSynthetic(pending.cacheKey, registry, scratch, () => this.buildCjsAsEsmSyntheticModule(pending.referencingIdentifier, pending.modulePath, context));
          if (!committed) return LOAD_ASYNC;
        } catch (error) {
          if (!(error instanceof _ModuleExecutor.CjsParseError)) throw error;
          if (this.resolution.isExplicitlyCommonjs(pending.modulePath)) {
            throw error.cause;
          }
          worklist.push({
            cacheKey: pending.cacheKey,
            modulePath: pending.modulePath
          });
        }
        continue;
      }
      const {
        cacheKey,
        modulePath
      } = worklist.pop();
      activeWalk.add(cacheKey);
      if (scratch.has(cacheKey)) continue;

      // Registry first, mutex second. `'unlinked'` / `'linking'` /
      // `'evaluating'` mean the legacy path is mid-flight on this dep;
      // plugging an unlinked module into the parent's `linkRequests` would
      // fail Node's link cascade, so bail. `'linked'` is adoptable: it is
      // already instantiated, so the root's evaluate cascade runs its body.
      const fromRegistry = registry.get(cacheKey);
      if (fromRegistry instanceof Promise) return LOAD_ASYNC;
      if (fromRegistry) {
        if (fromRegistry.status === 'errored') throw fromRegistry.error;
        if (fromRegistry.status !== 'evaluated' && fromRegistry.status !== 'linked') {
          return LOAD_ASYNC;
        }
        scratch.set(cacheKey, {
          cacheKey,
          kind: 'prelinked',
          module: fromRegistry
        });
        continue;
      }
      if (this.transformCache.hasMutex(cacheKey)) return LOAD_ASYNC;
      if (this.resolution.isCoreModule(modulePath)) {
        scratch.set(cacheKey, {
          cacheKey,
          kind: 'prelinked',
          module: (0, _syntheticBuilders.buildCoreSyntheticModule)(modulePath, context, name => this.coreModule.require(name))
        });
        continue;
      }
      if (modulePath.startsWith('data:')) {
        const built = this.buildSyncDataUriEntry({
          cacheKey,
          context,
          mode,
          pendingBuilds,
          registry,
          scratch,
          specifier: modulePath,
          worklist
        });
        if (built === LOAD_ASYNC) return LOAD_ASYNC;
        scratch.set(cacheKey, built);
        continue;
      }
      if ((0, _Resolution.isWasm)(modulePath)) {
        const wasmEntry = this.buildSyncWasmEntry({
          bytes: this.fileCache.readFileBuffer(modulePath),
          cacheKey,
          context,
          identifier: modulePath,
          mode,
          pendingBuilds,
          registry,
          scratch,
          worklist
        });
        if (wasmEntry === LOAD_ASYNC) return LOAD_ASYNC;
        scratch.set(cacheKey, wasmEntry);
        continue;
      }
      if (!this.transformCache.canTransformSync(modulePath)) {
        if (mode === 'sync-required') {
          throw makeRequireAsyncError(modulePath, 'a configured transformer is async-only');
        }
        return LOAD_ASYNC;
      }
      if (modulePath.endsWith('.json')) {
        scratch.set(cacheKey, {
          cacheKey,
          kind: 'prelinked',
          module: this.buildJsonModule(this.transformCache.transformJson(modulePath, ESM_TRANSFORM_OPTIONS), modulePath, context)
        });
        continue;
      }
      const transformedCode = this.transformCache.transform(modulePath, ESM_TRANSFORM_OPTIONS);
      const entry = this.buildSyncSourceEntry({
        cacheKey,
        code: transformedCode,
        context,
        identifier: modulePath,
        initializeImportMeta: meta => {
          const metaUrl = cacheKey;
          meta.url = metaUrl;
          // @ts-expect-error Jest uses @types/node@18.
          meta.filename = modulePath;
          // @ts-expect-error Jest uses @types/node@18.
          meta.dirname = path().dirname(modulePath);
          meta.resolve = (specifier, parent = metaUrl) => {
            const parentPath = (0, _nodeUrl().fileURLToPath)(parent);
            return this.resolveForImportMeta(parentPath, specifier);
          };
          // @ts-expect-error Jest uses @types/node@18.
          meta.main = modulePath === this.testPath;
          meta.jest = this.jestGlobals.jestObjectFor(modulePath);
        },
        mode,
        pendingBuilds,
        registry,
        scratch,
        worklist
      });
      if (entry === LOAD_ASYNC) return LOAD_ASYNC;
      scratch.set(cacheKey, entry);
    }
    this.adoptCommittedScratchEntries(scratch, registry);
    for (const entry of scratch.values()) {
      if (entry.kind !== 'source') continue;
      const depModules = entry.deps.map(depKey => {
        const depEntry = scratch.get(depKey);
        (0, _jestUtil().invariant)(depEntry, `Sync ESM graph missing dep ${depKey} for ${entry.cacheKey}. This is a bug in Jest, please report it!`);
        return depEntry.module;
      });
      (0, _jestUtil().invariant)(typeof entry.module.linkRequests === 'function', `linkRequests unavailable on ${entry.cacheKey}`);
      entry.module.linkRequests(depModules);
    }
    const rootEntry = scratch.get(rootKey);
    (0, _jestUtil().invariant)(rootEntry, 'Sync ESM graph missing root entry');
    const rootModule = rootEntry.module;
    if (rootEntry.kind === 'source') {
      (0, _jestUtil().invariant)(typeof rootModule.instantiate === 'function', 'instantiate unavailable on root');
      rootModule.instantiate();
      if (moduleHasAsyncGraph(rootModule)) {
        if (mode === 'sync-required') {
          let culprit = rootModule.identifier;
          for (const entry of scratch.values()) {
            if (entry.kind === 'source' && typeof entry.module.hasTopLevelAwait === 'function' && entry.module.hasTopLevelAwait()) {
              culprit = entry.module.identifier;
              break;
            }
          }
          throw makeRequireAsyncError(rootModule.identifier, culprit === rootModule.identifier ? 'top-level await' : `a dependency uses top-level await (${culprit})`);
        }
        return LOAD_ASYNC;
      }
    }
    for (const entry of scratch.values()) {
      if (entry.kind === 'prelinked' && entry.excludeFromRegistry) continue;
      if (!registry.has(entry.cacheKey)) {
        registry.set(entry.cacheKey, entry.module);
      }
    }
    rootModule.evaluate().catch(_helpers.noop);
    if (rootModule.status === 'errored') {
      throw rootModule.error;
    }
    (0, _jestUtil().invariant)(rootModule.status === 'evaluated', `Expected synchronous evaluation to complete for ${rootModule.identifier}, but module status is "${rootModule.status}". This is a bug in Jest, please report it!`);
    return rootModule;
  }

  // `hasEsmSyntax` is the discriminator every CjsParseError producer gates
  // on, so probing it directly reproduces the build's ESM-fallback decision
  // without running anything. Files it cannot classify (incomplete syntax
  // both parsers reject) confirm as CJS, and the build surfaces the same
  // canonical compile error it always has.
  pendingCjsBuildIsUnmarkedEsm(pending) {
    if (path().extname(pending.modulePath) === '.node') return false;
    if (this.resolution.isExplicitlyCommonjs(pending.modulePath)) return false;
    return (0, _esmLexer.hasEsmSyntax)(this.transformCache.transform(pending.modulePath, undefined));
  }

  // A module sits in the registry linked-but-unevaluated when an earlier walk
  // linked it and then failed before the evaluate cascade reached it - a
  // sibling threw, or the walk bailed on an unsupported edge. It is fully
  // instantiated, so evaluating it now is all that is left.
  evaluateLinkedModule(module, mode) {
    if (moduleHasAsyncGraph(module)) {
      if (mode === 'sync-required') {
        throw makeRequireAsyncError(module.identifier, 'top-level await');
      }
      return LOAD_ASYNC;
    }

    // Deliberately not recorded in `evaluatingMap`, unlike the legacy path.
    // The async-graph check above means this only ever evaluates a graph that
    // finishes before `evaluate()` returns, so there is no pending promise for
    // another caller to await - and the legacy path only populates that map in
    // its own async branch. Neither path awaits between reading `status` and
    // calling `evaluate()`, so a duplicate evaluation cannot start in between.
    module.evaluate().catch(_helpers.noop);
    if (module.status === 'errored') {
      throw module.error;
    }
    (0, _jestUtil().invariant)(module.status === 'evaluated', `Expected synchronous evaluation to complete for ${module.identifier}, but module status is "${module.status}". This is a bug in Jest, please report it!`);
    return module;
  }

  // Legacy-path counterpart of the walker's per-request check: scan a freshly
  // constructed module's requests where Node exposes them.
  throwOnSourcePhaseRequests(module, identifier) {
    if (module.moduleRequests === undefined) return;
    for (const {
      specifier,
      phase
    } of module.moduleRequests) {
      if (phase === 'source') {
        throw makeSourcePhaseUnsupportedError(specifier, identifier);
      }
    }
  }
  getContext() {
    (0, _jestUtil().invariant)(typeof this.environment.getVmContext === 'function', 'ES Modules are only supported if your test environment has the `getVmContext` function');
    const context = this.environment.getVmContext();
    (0, _jestUtil().invariant)(context, 'Test environment has been torn down');
    return context;
  }

  // Adopts a live entry under `cacheKey` into the local scratch. `'bail'`
  // means the registry holds something the caller can't use synchronously: a
  // mid-flight Promise from the legacy async path, or a module still being
  // linked (legacy can stash an `'unlinked'` SourceTextModule here while
  // link/evaluate runs).
  adoptRegistryEntry(cacheKey, registry, scratch) {
    if (scratch.has(cacheKey)) return 'adopted';
    const fromRegistry = registry.get(cacheKey);
    if (fromRegistry === undefined) return 'absent';
    if (fromRegistry instanceof Promise) return 'bail';
    const cached = fromRegistry;
    if (cached.status === 'errored') throw cached.error;
    if (cached.status !== 'evaluated' && cached.status !== 'linked') {
      return 'bail';
    }
    scratch.set(cacheKey, {
      cacheKey,
      kind: 'prelinked',
      module: cached
    });
    return 'adopted';
  }

  // Commits (or reuses) a synthetic-module entry under `cacheKey` in both the
  // local scratch and the long-lived registry.
  tryCommitSynthetic(cacheKey, registry, scratch, build) {
    const adoption = this.adoptRegistryEntry(cacheKey, registry, scratch);
    if (adoption === 'bail') return false;
    if (adoption === 'adopted') return true;
    const module = build();
    registry.set(cacheKey, module);
    scratch.set(cacheKey, {
      cacheKey,
      kind: 'prelinked',
      module
    });
    return true;
  }

  // Node answers `import.meta.resolve('fs')` with `'node:fs'`, not a file URL -
  // a builtin has no path to turn into one.
  //
  // Deliberate divergence: Node's `import.meta.resolve` is resolution-only -
  // `resolve('./missing.js')` returns the URL without checking existence, and
  // an unresolvable bare specifier throws ERR_MODULE_NOT_FOUND. Here the full
  // jest-resolve pipeline answers (moduleNameMapper, extension resolution,
  // haste), which is the point of the API inside a test - so a missing file
  // throws jest-resolve's MODULE_NOT_FOUND instead of returning a URL.
  resolveForImportMeta(parentPath, specifier) {
    // Node echoes `node:` specifiers verbatim - even with a query or fragment,
    // which only fail later, at load time.
    if (specifier.startsWith('node:')) {
      return specifier;
    }
    const {
      pathOrSpecifier,
      suffix
    } = splitQueryAndFragment(specifier);
    const resolved = this.resolution.resolveEsm(parentPath, pathOrSpecifier);
    if (this.resolution.isCoreModule(resolved)) {
      if (suffix !== '') {
        throw makeUnknownBuiltinError(specifier);
      }
      return canonicalCoreSpecifier(resolved);
    }
    return fileCacheKey(resolved, suffix);
  }
  resolveSpecifierForSyncGraph(referencingIdentifier, specifier, context, scratch, registry, mode, pendingBuilds) {
    if (specifier === '@jest/globals') {
      const cacheKey = `@jest/globals/${referencingIdentifier}`;
      const ok = this.tryCommitSynthetic(cacheKey, registry, scratch, () => this.jestGlobals.esmGlobalsModule(referencingIdentifier, context));
      return ok ? {
        cacheKey,
        enqueue: null,
        modulePath: cacheKey
      } : LOAD_ASYNC;
    }
    const {
      pathOrSpecifier: specifierPath,
      suffix
    } = splitQueryAndFragment(specifier);

    // `data:` URIs pass through the split whole, so the mock decision sees
    // the full specifier - the same form the mock was registered under.
    const {
      shouldMock,
      moduleID
    } = this.mockState.shouldMockEsmSync(referencingIdentifier, specifierPath);
    if (shouldMock && this.mockDecisionServable(moduleID)) {
      // Import-attribute validation runs against the returned module path -
      // an extensionless or bare specifier of a JSON module must still
      // validate as JSON, so hand back the resolved target where one exists.
      const validationPath = this.resolveMockTargetForValidation(referencingIdentifier, specifierPath);
      if (this.registries.getModuleMock(moduleID) !== undefined) {
        // Adopting an already-built instance runs no user code, so it can
        // happen mid-resolution.
        const mocked = this.importMockSync(referencingIdentifier, specifierPath, moduleID, context, scratch, mode);
        if (mocked === LOAD_ASYNC) return LOAD_ASYNC;
        return {
          cacheKey: mocked.cacheKey,
          enqueue: null,
          modulePath: validationPath
        };
      }
      // Anything that executes user code waits until the graph has fully
      // resolved, with the pending CJS builds: a factory's side effects, and
      // the real-module load generation performs for its metadata.
      pendingBuilds.mocks.push({
        from: referencingIdentifier,
        moduleID,
        specifierPath
      });
      return {
        cacheKey: moduleID,
        enqueue: null,
        modulePath: validationPath
      };
    }
    if (specifierPath.startsWith('data:')) {
      const cacheKey = new URL(specifierPath).href;
      return {
        cacheKey,
        enqueue: {
          cacheKey,
          modulePath: cacheKey
        },
        modulePath: cacheKey
      };
    }
    if (this.resolution.isCoreModule(specifierPath)) {
      if (suffix !== '') {
        throw makeUnknownBuiltinError(canonicalCoreSpecifier(specifierPath) + suffix);
      }
      // `fs` and `node:fs` are one module to Node, so they have to share one
      // registry entry - otherwise each form gets its own synthetic wrapper and
      // `import * as a from 'fs'` !== `import * as b from 'node:fs'`.
      const cacheKey = canonicalCoreSpecifier(specifierPath);
      return {
        cacheKey,
        enqueue: {
          cacheKey,
          modulePath: cacheKey
        },
        modulePath: cacheKey
      };
    }

    // The sync resolution is authoritative in both modes: sync-preferred
    // walks never start under a resolver with a distinct async hook, and
    // require() cannot await one. Bailing to the legacy path on failure
    // would execute already-resolved CJS deps before rediscovering the same
    // error, where Node runs nothing when the graph fails to resolve.
    const resolved = this.resolution.resolveEsm(referencingIdentifier, specifierPath);
    const cacheKey = fileCacheKey(resolved, suffix);
    if (mode === 'sync-required' && this.isAncestorWalkRoot(cacheKey, registry)) {
      throw makeRequireCycleError(resolved, referencingIdentifier);
    }
    if (!resolved.endsWith('.json') && !(0, _Resolution.isWasm)(resolved) && !this.shouldLoadAsEsm(resolved)) {
      const adoption = this.adoptRegistryEntry(cacheKey, registry, scratch);
      if (adoption === 'bail') return LOAD_ASYNC;
      if (adoption === 'absent') {
        pendingBuilds.cjs.push({
          cacheKey,
          modulePath: resolved,
          referencingIdentifier
        });
      }
      return {
        cacheKey,
        enqueue: null,
        modulePath: resolved
      };
    }
    return {
      cacheKey,
      enqueue: {
        cacheKey,
        modulePath: resolved
      },
      modulePath: resolved
    };
  }
  importMockSync(from, moduleName, moduleID, context, scratch, mode, resolvedPath) {
    const existing = this.registries.getModuleMock(moduleID);
    if (existing instanceof Promise) {
      if (mode === 'sync-required') {
        throw makeRequireAsyncError(moduleName, 'mock factory is async');
      }
      return LOAD_ASYNC;
    }
    if (existing) {
      if (existing.status === 'errored') throw existing.error;
      if (!scratch.has(moduleID)) {
        scratch.set(moduleID, {
          cacheKey: moduleID,
          excludeFromRegistry: true,
          kind: 'prelinked',
          module: existing
        });
      }
      return {
        cacheKey: moduleID
      };
    }
    const factory = this.mockState.getEsmFactory(moduleID);
    // No registered factory means the decision came from automocking (or a
    // manual `__mocks__` file found for an unresolvable name) - the ESM
    // counterpart of `Runtime._requireMockWithId`'s factory-less branches.
    if (factory === undefined) {
      return this.importGeneratedMockSync(from, moduleName, moduleID, context, scratch, mode, resolvedPath);
    }
    const result = factory();
    if ((0, _jestUtil().isPromise)(result)) {
      // The factory has already run - discarding the promise would invoke it
      // a second time on the legacy retry and turn a rejection into an
      // unhandled rejection that kills the worker. Register the in-flight
      // module instead so the retry (or a later import) adopts it.
      const pending = Promise.resolve(result).then(exports => (0, _syntheticBuilders.evaluateSyntheticModule)((0, _syntheticBuilders.syntheticFromExports)(moduleName, context, exports)));
      pending.catch(_helpers.noop);
      this.registries.setModuleMock(moduleID, pending);
      if (mode === 'sync-required') {
        throw makeRequireAsyncError(moduleName, 'mock factory is async');
      }
      return LOAD_ASYNC;
    }
    const synth = (0, _syntheticBuilders.syntheticFromExports)(moduleName, context, result);
    this.registries.setModuleMock(moduleID, synth);
    scratch.set(moduleID, {
      cacheKey: moduleID,
      excludeFromRegistry: true,
      kind: 'prelinked',
      module: synth
    });
    return {
      cacheKey: moduleID
    };
  }

  // A mock decision is servable mid-generation only when an instance or a
  // registered factory already exists. A factory-less decision would recurse
  // into generation, and a synchronously evaluable cycle (a.mjs <-> b.mjs)
  // then links the in-progress module against its empty metadata placeholder
  // and fails with "does not provide an export". Loading the real module
  // instead keeps the metadata faithful; the dep's own mock generates at its
  // own import edges.
  // Mock decisions can rest on names that never resolve (root manual mocks,
  // virtual modules), so a failed resolution keeps the raw specifier.
  resolveMockTargetForValidation(from, specifierPath) {
    if (specifierPath.startsWith('data:')) return specifierPath;
    try {
      return this.resolution.resolveEsm(from, specifierPath);
    } catch {
      return specifierPath;
    }
  }

  // `jest.unmock`/`jest.deepUnmock` record their decision in the CJS map,
  // and this require() entry consults the independent ESM map afterwards -
  // without this check an automock decision would override unmock's
  // documented guarantee that require() returns the real module. Registered
  // `unstable_mockModule` factories still serve: unmocking those is
  // `unstable_unmockModule`'s job.
  // Consults the ID the CJS decision derived - jest.unmock records it from
  // the requested name, and for a root-mocked bare package the name-derived
  // ID embeds the mock path while a path-derived one does not.
  cjsUnmockBlocksGeneratedMock(from, moduleName, modulePath, moduleID) {
    if (this.mockState.getEsmFactory(moduleID) !== undefined) return false;
    const cjsModuleId = this.mockState.getCjsModuleId(from, moduleName);
    if (!this.mockState.isExplicitlyUnmocked(cjsModuleId)) return false;
    // jest.deepUnmock records its transitive scope under the CJS ID; the
    // real graph about to be walked consults the ESM ID of this root as the
    // importer, so mirror the mark - otherwise the root loads real while its
    // ESM deps stay automocked.
    if (this.mockState.isTransitivelyUnmocked(cjsModuleId)) {
      this.mockState.markTransitive(this.mockState.getEsmModuleId(from, modulePath), false);
    }
    return true;
  }
  mockDecisionServable(moduleID) {
    return this.generatingMockDepth === 0 || this.registries.getModuleMock(moduleID) !== undefined || this.mockState.getEsmFactory(moduleID) !== undefined;
  }
  importGeneratedMockSync(from, moduleName, moduleID, context, scratch, mode, resolvedPath) {
    // A data: URI is its own module - there is no file to have a sibling or
    // root manual mock.
    const manualMockPath = moduleName.startsWith('data:') ? null : this.resolution.findEsmManualMock(from, moduleName, resolvedPath);
    // The load runs against scratch registries so the module executed here
    // (the mock file, or the real module inspected for automock metadata)
    // doesn't pollute the live caches - mirroring `withScratchRegistries` in
    // the CJS automock path.
    const mockModule = manualMockPath ? this.registries.withScratchRegistries(() => this.loadModuleForMockSync(from, manualMockPath, context, mode)) : this.buildAutomockSync(from, moduleName, context, mode, resolvedPath);
    if (mockModule === LOAD_ASYNC) return LOAD_ASYNC;
    this.registries.setModuleMock(moduleID, mockModule);
    scratch.set(moduleID, {
      cacheKey: moduleID,
      excludeFromRegistry: true,
      kind: 'prelinked',
      module: mockModule
    });
    return {
      cacheKey: moduleID
    };
  }

  // Loads a module by path with mocks out of the picture: either the manual
  // mock file itself, or the real module automocking needs metadata from.
  // CJS files can't go through the graph walker (their transform output
  // expects the CJS wrapper), so they load through the mock-free
  // `Runtime.requireModule` seam and get the usual synthetic wrapper.
  loadModuleForMockSync(from, modulePath, context, mode) {
    if (modulePath.startsWith('data:')) {
      return this.tryLoadGraphSync(modulePath, '', mode);
    }
    if (!modulePath.endsWith('.json') && !(0, _Resolution.isWasm)(modulePath) && !this.shouldLoadAsEsm(modulePath)) {
      try {
        const synthetic = (0, _syntheticBuilders.buildCjsAsEsmSyntheticModule)(from, modulePath, context, this.requireModule, this.cjsExportsCache);
        const evaluated = (0, _syntheticBuilders.evaluateSyntheticModule)(synthetic);
        if ((0, _jestUtil().isPromise)(evaluated)) return LOAD_ASYNC;
        return evaluated;
      } catch (error) {
        if (!(error instanceof _ModuleExecutor.CjsParseError)) throw error;
        if (this.resolution.isExplicitlyCommonjs(modulePath)) throw error.cause;
        // ESM syntax without an ESM marker - load through the walker below.
      }
    }
    return this.tryLoadGraphSync(modulePath, '', mode);
  }
  buildAutomockSync(from, moduleName, context, mode, resolvedPath) {
    const moduleMocker = this.environment.moduleMocker;
    (0, _jestUtil().invariant)(moduleMocker, '`moduleMocker` must be set on an environment when created');
    const modulePath = resolvedPath ?? (moduleName.startsWith('data:') ? moduleName : this.resolution.resolveEsm(from, moduleName));
    // The CJS automock caches metadata for the raw module.exports under the
    // bare path; the namespace shape (default/'module.exports' and per-name
    // bindings) differs, so the two must not share an entry.
    const metadataKey = `esm:${modulePath}`;
    if (!this.mockState.hasMockMetadata(metadataKey)) {
      // Seeded before the load so a mock cycle resolves against the
      // placeholder instead of recursing - same trick as `generateMock`.
      this.mockState.setMockMetadata(metadataKey, moduleMocker.getMetadata({}) ?? {});
      let realModule;
      this.generatingMockDepth++;
      try {
        realModule = this.registries.withScratchRegistries(() => this.loadModuleForMockSync(from, modulePath, context, mode));
      } catch (error) {
        this.mockState.deleteMockMetadata(metadataKey);
        throw error;
      } finally {
        this.generatingMockDepth--;
      }
      if (realModule === LOAD_ASYNC) {
        // The placeholder must not survive a bail: the legacy retry would
        // read it back as finished metadata and produce an empty mock.
        this.mockState.deleteMockMetadata(metadataKey);
        return LOAD_ASYNC;
      }
      const metadata = moduleMocker.getMetadata(realModule.namespace);
      if (metadata == null) {
        this.mockState.deleteMockMetadata(metadataKey);
        throw new Error(`Failed to get mock metadata: ${modulePath}\n\n` + 'See: https://jestjs.io/docs/manual-mocks#content');
      }
      this.mockState.setMockMetadata(metadataKey, metadata);
    }
    const generated = moduleMocker.generateFromMetadata(this.mockState.getMockMetadata(metadataKey));
    const mockRecord = this.mockState.notifyMockGenerated(modulePath, generated);
    // The resolved path is the identifier: the legacy dynamic-import path
    // validates import attributes against it, so a JSON target reached
    // through an extensionless specifier must read as a .json module.
    return (0, _syntheticBuilders.syntheticFromExports)(modulePath, context, mockRecord);
  }

  // Construct a wasm SyntheticModule for the sync graph. Wasm imports are
  // resolved (sync) and enqueued like static-import deps. The SyntheticModule's
  // body closure-captures `scratch`; by evaluate-cascade time, every dep entry
  // is fully evaluated so `module.namespace` is safe to read.
  //
  // Uses `new WebAssembly.Module(bytes)` (sync, blocks on large modules).
  buildSyncWasmEntry({
    bytes,
    cacheKey,
    context,
    identifier,
    mode,
    pendingBuilds,
    registry,
    scratch,
    worklist
  }) {
    const wasmModule = new WebAssembly.Module(bytes);
    const moduleSpecToCacheKey = new Map();
    for (const {
      module: depSpec
    } of WebAssembly.Module.imports(wasmModule)) {
      if (moduleSpecToCacheKey.has(depSpec)) continue;
      const resolved = this.resolveSpecifierForSyncGraph(identifier, depSpec, context, scratch, registry, mode, pendingBuilds);
      if (resolved === LOAD_ASYNC) return LOAD_ASYNC;
      moduleSpecToCacheKey.set(depSpec, resolved.cacheKey);
      if (resolved.enqueue) worklist.push(resolved.enqueue);
    }
    const synthetic = (0, _syntheticBuilders.buildWasmSyntheticModule)(wasmModule, identifier, context, depSpec => {
      const depKey = moduleSpecToCacheKey.get(depSpec);
      const depEntry = scratch.get(depKey);
      return depEntry.module.namespace;
    });
    return {
      cacheKey,
      kind: 'prelinked',
      module: synthetic
    };
  }

  // `import.meta.resolve` inside a data: module. A data: base URL is not
  // hierarchical, so Node resolves only built-in modules and specifiers that
  // are themselves absolute URLs - relative and bare specifiers both throw.
  dataUriMetaResolve(specifier) {
    return request => {
      if (this.resolution.isCoreModule(request)) {
        return canonicalCoreSpecifier(request);
      }
      if (urlSchemeRegex.test(request)) {
        return new URL(request).href;
      }
      const error = new TypeError(`Failed to resolve module specifier "${request}" from "${specifier}": Invalid relative URL or base scheme is not hierarchical.`);
      error.code = 'ERR_UNSUPPORTED_RESOLVE_REQUEST';
      throw error;
    };
  }
  buildSyncDataUriEntry({
    cacheKey,
    context,
    mode,
    pendingBuilds,
    registry,
    scratch,
    specifier,
    worklist
  }) {
    const {
      mime,
      code
    } = (0, _dataUri.parseDataUri)(specifier);
    if (mime === 'application/wasm') {
      return this.buildSyncWasmEntry({
        bytes: new Uint8Array(code),
        cacheKey,
        context,
        identifier: specifier,
        mode,
        pendingBuilds,
        registry,
        scratch,
        worklist
      });
    }
    if (mime === 'application/json') {
      return {
        cacheKey,
        kind: 'prelinked',
        module: this.buildJsonModule(code, specifier, context)
      };
    }
    return this.buildSyncSourceEntry({
      cacheKey,
      code: code,
      context,
      identifier: specifier,
      initializeImportMeta: meta => {
        meta.url = specifier;
        // @ts-expect-error Jest uses @types/node@18.
        meta.main = false;
        meta.resolve = this.dataUriMetaResolve(specifier);
        meta.jest = this.jestGlobals.jestObjectFor(specifier);
      },
      mode,
      pendingBuilds,
      registry,
      scratch,
      worklist
    });
  }

  // The source-module half of a sync walk: construct, refuse top-level await,
  // then resolve each static import so the caller can link the graph. Callers
  // differ only in what `import.meta` gets.
  buildSyncSourceEntry({
    cacheKey,
    code,
    context,
    identifier,
    initializeImportMeta,
    mode,
    pendingBuilds,
    registry,
    scratch,
    worklist
  }) {
    const module = new (_nodeVm().SourceTextModule)(code, {
      context,
      identifier,
      importModuleDynamically: this.dynamicImport,
      initializeImportMeta
    });
    if (typeof module.hasTopLevelAwait === 'function' && module.hasTopLevelAwait()) {
      if (mode === 'sync-required') {
        throw makeRequireAsyncError(identifier, 'top-level await');
      }
      return LOAD_ASYNC;
    }

    // If we got here without `moduleRequests`, the capability gate is lying.
    (0, _jestUtil().invariant)(module.moduleRequests !== undefined, `moduleRequests unavailable on ${identifier}`);
    const deps = [];
    for (const {
      specifier,
      attributes,
      phase
    } of module.moduleRequests) {
      if (phase === 'source') {
        throw makeSourcePhaseUnsupportedError(specifier, identifier);
      }
      const resolved = this.resolveSpecifierForSyncGraph(identifier, specifier, context, scratch, registry, mode, pendingBuilds);
      if (resolved === LOAD_ASYNC) return LOAD_ASYNC;
      this.importAttributeValidator.validate(resolved.modulePath, attributes, identifier);
      deps.push(resolved.cacheKey);
      if (resolved.enqueue) worklist.push(resolved.enqueue);
    }
    return {
      cacheKey,
      deps,
      kind: 'source',
      module
    };
  }
  buildJsonModule(jsonText, identifier, context) {
    return (0, _syntheticBuilders.buildJsonSyntheticModule)(jsonText, identifier, context, text => this.environment.global.JSON.parse(text));
  }

  // Synthetic-module wrappers that close over the primitive deps. The
  // `requireModuleOrMock` callback inside `buildCjsAsEsmSyntheticModule`
  // is the extension-point bridge to `Runtime.requireModuleOrMock`.
  buildCjsAsEsmSyntheticModule(from, modulePath, context) {
    return (0, _syntheticBuilders.buildCjsAsEsmSyntheticModule)(from, modulePath, context, this.requireModuleOrMock, this.cjsExportsCache);
  }

  // TODO: legacy async path - everything below is deletable when min-Node
  // ≥ v24.9 (the sync core handles all entry shapes). Drop the `linkingMap`
  // / `evaluatingMap` fields with it.

  // Called from CJS bodies via `compileFunction`'s `importModuleDynamically`.
  dynamicImportFromCjs(specifier, identifier, context, importAttributes, phase) {
    if (phase === 'source') {
      return Promise.reject(makeSourcePhaseUnsupportedError(specifier, identifier));
    }
    return this.resolveModule(specifier, identifier, context).then(m => {
      this.importAttributeValidator.validate(m.identifier, importAttributes ?? {}, identifier);
      return this.linkAndEvaluateModule(m);
    });
  }

  // Public entry for `Runtime.unstable_importModule`. Runtime keeps the
  // public method as the override seam; this is the body.
  async loadAndEvaluate(from, moduleName) {
    (0, _jestUtil().invariant)(_nodeCapabilities.runtimeSupportsVmModules, 'You need to run with a version of node that supports ES Modules in the VM API. See https://jestjs.io/docs/ecmascript-modules');
    const {
      pathOrSpecifier,
      suffix
    } = splitQueryAndFragment(moduleName ?? '');
    const modulePath = await this.resolution.resolveEsmAsync(from, pathOrSpecifier);
    const module = await this.loadEsmModule(modulePath, suffix);
    return this.linkAndEvaluateModule(module);
  }
  async loadEsmModule(modulePath, suffix = '') {
    // Three gates here. `supportsSyncEvaluate` is a Node-version check: the
    // sync core relies on `SyntheticModule` starting `'linked'` and on
    // `evaluate()` completing sync, both of which need v22.21+ / v24.8+.
    // `canResolveSync` is a configured-resolver check: with an async-only
    // user resolver `findNodeModule` silently falls back to the default
    // resolver and would silently miss user mappings. A resolver with a
    // distinct `async` hook next to its sync one never takes the sync walk
    // for imports at all - even a successful sync resolution may disagree
    // with the async hook `resolveModuleAsync` would pick.
    if (_nodeCapabilities.supportsSyncEvaluate && this.resolution.canResolveSync() && !this.resolution.hasDistinctAsyncResolver()) {
      const synced = this.tryLoadGraphSync(modulePath, suffix, 'sync-preferred');
      if (synced !== LOAD_ASYNC) return synced;
    }
    const {
      cacheKey
    } = this.canonicalizeRoot(modulePath, suffix);
    const registry = this.registries.getActiveEsmRegistry();
    if (this.transformCache.hasMutex(cacheKey)) {
      await this.transformCache.awaitMutex(cacheKey);
    }
    if (!registry.has(cacheKey)) {
      const context = this.getContext();
      let transformResolve;
      let transformReject;
      const mutex = new Promise((resolve, reject) => {
        transformResolve = resolve;
        transformReject = reject;
      });
      // Prevent an unhandled-rejection warning when no concurrent caller is
      // awaiting the mutex — the originating caller re-throws the error itself.
      // Concurrent waiters still see the rejection because they await `mutex`.
      mutex.catch(_helpers.noop);
      this.transformCache.setMutex(cacheKey, mutex);
      (0, _jestUtil().invariant)(transformResolve && transformReject, 'Promise initialization should be sync - please report this bug to Jest!');
      try {
        if ((0, _Resolution.isWasm)(modulePath)) {
          const wasm = this.importWasmModule(this.fileCache.readFileBuffer(modulePath), modulePath, context);
          registry.set(cacheKey, wasm);
          transformResolve();
          return wasm;
        }
        if (this.resolution.isCoreModule(modulePath)) {
          const core = (0, _syntheticBuilders.evaluateSyntheticModule)((0, _syntheticBuilders.buildCoreSyntheticModule)(modulePath, context, name => this.coreModule.require(name)));
          registry.set(cacheKey, core);
          transformResolve();
          return core;
        }
        let module;
        if (modulePath.endsWith('.json')) {
          // An async-only transformer matched to JSON cannot go through the
          // sync `transformJson` - await the full transform like the pre-BOM
          // code did and strip the BOM off its output instead.
          const jsonSource = this.transformCache.canTransformSync(modulePath) ? this.transformCache.transformJson(modulePath, ESM_TRANSFORM_OPTIONS) : (0, _stripBom().default)(await this.transformCache.transformAsync(modulePath, ESM_TRANSFORM_OPTIONS));
          module = this.buildJsonModule(jsonSource, modulePath, context);
        } else {
          const transformedCode = this.transformCache.canTransformSync(modulePath) ? this.transformCache.transform(modulePath, ESM_TRANSFORM_OPTIONS) : await this.transformCache.transformAsync(modulePath, ESM_TRANSFORM_OPTIONS);
          module = new (_nodeVm().SourceTextModule)(transformedCode, {
            context,
            identifier: modulePath,
            importModuleDynamically: this.dynamicImport,
            initializeImportMeta: meta => {
              const metaUrl = cacheKey;
              meta.url = metaUrl;
              // @ts-expect-error Jest uses @types/node@18.
              meta.filename = modulePath;
              // @ts-expect-error Jest uses @types/node@18.
              meta.dirname = path().dirname(modulePath);
              meta.resolve = (specifier, parent = metaUrl) => {
                const parentPath = (0, _nodeUrl().fileURLToPath)(parent);
                return this.resolveForImportMeta(parentPath, specifier);
              };
              // @ts-expect-error Jest uses @types/node@18.
              meta.main = modulePath === this.testPath;
              meta.jest = this.jestGlobals.jestObjectFor(modulePath);
            }
          });
        }
        this.throwOnSourcePhaseRequests(module, modulePath);
        (0, _jestUtil().invariant)(!registry.has(cacheKey), `Module cache already has entry ${cacheKey}. This is a bug in Jest, please report it!`);
        registry.set(cacheKey, module);
        transformResolve();
      } catch (error) {
        transformReject(error);
        throw error;
      } finally {
        this.transformCache.clearMutex(cacheKey);
      }
    }
    const module = registry.get(cacheKey);
    (0, _jestUtil().invariant)(module, 'Module cache does not contain module. This is a bug in Jest, please open up an issue');
    return module;
  }
  async resolveModule(specifier, referencingIdentifier, context) {
    if (this.testState.bailIfTornDown('You are trying to `import` a file after the Jest environment has been torn down.')) {
      // @ts-expect-error -- exiting
      return;
    }
    const registry = this.registries.getActiveEsmRegistry();
    if (specifier === '@jest/globals') {
      const globalsIdentifier = `@jest/globals/${referencingIdentifier}`;
      const fromCache = registry.get(globalsIdentifier);
      if (fromCache) {
        return fromCache;
      }
      const globals = (0, _syntheticBuilders.evaluateSyntheticModule)(this.jestGlobals.esmGlobalsModule(referencingIdentifier, context));
      registry.set(globalsIdentifier, globals);
      return globals;
    }
    if (specifier.startsWith('data:')) {
      const dataDecision = await this.mockState.shouldMockEsmAsync(referencingIdentifier, specifier);
      if (dataDecision.shouldMock) {
        return this.importMock(referencingIdentifier, specifier, dataDecision.moduleID, context);
      }
      // The canonical serialization (whitespace stripped, non-ASCII
      // percent-encoded) is the module's URL: spelling variants of one URL
      // share an instance, exactly as in Node.
      specifier = new URL(specifier).href;
      const fromCache = registry.get(specifier);
      if (fromCache) {
        return fromCache;
      }
      const {
        mime,
        code
      } = (0, _dataUri.parseDataUri)(specifier);
      let module;
      if (mime === 'application/wasm') {
        module = await this.importWasmModule(new Uint8Array(code), specifier, context);
      } else if (mime === 'application/json') {
        module = this.buildJsonModule(code, specifier, context);
      } else {
        module = new (_nodeVm().SourceTextModule)(code, {
          context,
          identifier: specifier,
          importModuleDynamically: this.dynamicImport,
          initializeImportMeta: meta => {
            meta.url = specifier;
            // @ts-expect-error Jest uses @types/node@18.
            meta.main = false;
            meta.resolve = this.dataUriMetaResolve(specifier);
            meta.jest = this.jestGlobals.jestObjectFor(specifier);
          }
        });
      }
      this.throwOnSourcePhaseRequests(module, specifier);
      registry.set(specifier, module);
      return module;
    }
    const {
      pathOrSpecifier: specifierPath,
      suffix
    } = splitQueryAndFragment(specifier);
    const decision = await this.mockState.shouldMockEsmAsync(referencingIdentifier, specifierPath);
    if (decision.shouldMock) {
      return this.importMock(referencingIdentifier, specifierPath, decision.moduleID, context);
    }
    const resolved = await this.resolution.resolveEsmAsync(referencingIdentifier, specifierPath);
    if (resolved.endsWith('.json') || this.resolution.isCoreModule(resolved) || this.shouldLoadAsEsm(resolved)) {
      return this.loadEsmModule(resolved, suffix);
    }
    return this.loadCjsAsEsm(referencingIdentifier, resolved, suffix, context);
  }
  async linkAndEvaluateModule(module) {
    if (this.testState.bailIfTornDown('You are trying to `import` a file after the Jest environment has been torn down.')) {
      // @ts-expect-error: exiting early
      return;
    }

    // Already-errored module from a prior failed evaluation.
    if (module.status === 'errored') {
      throw module.error;
    }
    if (module.status === 'unlinked') {
      this.linkingMap.set(module, module.link(async (specifier, referencingModule, extra) => {
        const resolved = await this.resolveModule(specifier, referencingModule.identifier, referencingModule.context);
        const extraAttrs = extra;
        this.importAttributeValidator.validate(resolved.identifier, extraAttrs?.attributes ?? extraAttrs?.assert ?? {}, referencingModule.identifier);
        return resolved;
      }));
    }
    const linkPromise = this.linkingMap.get(module);
    if (linkPromise != null) {
      await linkPromise;
    } else if (module.status === 'linking') {
      // Module entered 'linking' via Node's cascade (a parent's link()
      // recursed into this dep without going through our code). We have no
      // promise to await, so yield via setImmediate - which lets all pending
      // microtasks (including Node's internal linker chain) drain - until
      // linking finishes.
      const deadline = Date.now() + 5000;
      while (module.status === 'linking') {
        if (Date.now() > deadline) {
          throw new Error(`Jest: module ${module.identifier} is stuck in 'linking' state after 5 s - ` + 'this is likely a bug in Jest (please report it).');
        }
        await new Promise(resolve => setImmediate(resolve));
      }
    }
    if (module.status === 'linked') {
      if (_nodeCapabilities.supportsSyncEvaluate && !moduleHasAsyncGraph(module)) {
        // `evaluate()` fulfills synchronously when the graph has no top-level
        // await, so we don't need to yield. Errors land on `module.status`,
        // not as a Promise rejection. Gated on `supportsSyncEvaluate` because
        // pre-v22.21 / pre-v24.8 Node returns a genuinely-async Promise here
        // and the status invariant below would fire on `'evaluating'`.
        void module.evaluate().catch(_helpers.noop);
        const status = module.status;
        if (status === 'errored') {
          throw module.error;
        }
        (0, _jestUtil().invariant)(status === 'evaluated', `Expected synchronous evaluation to complete for ${module.identifier}, but module status is "${status}". This is a bug in Jest, please report it!`);
      } else {
        // Async path: TLA somewhere in the graph, or Node lacks the v22.21+ /
        // v24.8+ sync-evaluate semantics. Store the promise so concurrent
        // callers finding the module in `'evaluating'` await the same one.
        this.evaluatingMap.set(module, module.evaluate());
      }
    }
    await this.evaluatingMap.get(module);

    // A concurrent caller may have driven the evaluation while we awaited the
    // link or evaluate promise, and it throws on its own stack - so re-read
    // the status rather than assuming we are the one that evaluated.
    if (module.status === 'errored') {
      throw module.error;
    }
    return module;
  }
  loadCjsAsEsm(from, modulePath, suffix, context) {
    const registry = this.registries.getActiveEsmRegistry();
    const cacheKey = fileCacheKey(modulePath, suffix);
    const cached = registry.get(cacheKey);
    if (cached) {
      return cached;
    }
    let synthetic;
    try {
      synthetic = this.buildCjsAsEsmSyntheticModule(from, modulePath, context);
    } catch (error) {
      if (!(error instanceof _ModuleExecutor.CjsParseError)) throw error;
      if (this.resolution.isExplicitlyCommonjs(modulePath)) throw error.cause;
      return this.loadEsmModule(modulePath, suffix);
    }
    const evaluated = (0, _syntheticBuilders.evaluateSyntheticModule)(synthetic);
    registry.set(cacheKey, evaluated);
    return evaluated;
  }
  async importMock(from, moduleName, moduleID, context) {
    if (this.registries.hasModuleMock(moduleID)) {
      // A generated instance was built with the sync resolver hook - under a
      // distinct-hook resolver the import path excludes those, so fall
      // through to the factory error instead of reusing it. Factory
      // instances reuse regardless: no resolution shaped them.
      const generatedUnderSyncHook = this.mockState.getEsmFactory(moduleID) === undefined && this.resolution.hasDistinctAsyncResolver();
      if (!generatedUnderSyncHook) {
        return this.registries.getModuleMock(moduleID);
      }
    }
    const factory = this.mockState.getEsmFactory(moduleID);
    if (factory) {
      const invokedFactory = await factory();
      const module = (0, _syntheticBuilders.syntheticFromExports)(moduleName, context, invokedFactory);
      this.registries.setModuleMock(moduleID, module);
      return (0, _syntheticBuilders.evaluateSyntheticModule)(module);
    }

    // Factory-less decision (automock or manual mock). The generated-mock
    // builder is sync-core only; on capable Nodes reuse it here so dynamic
    // `import()` gets the same mocks as static imports and require(esm).
    // Generation is sync-only: it loads the real module's graph through the
    // sync walker, so every nested dependency resolves with the sync hook.
    // A resolver with a distinct async hook is therefore excluded - the
    // wrong graph would feed the metadata whenever the hooks disagree - and
    // keeps the factory error below. An async generation path does not
    // exist yet; the sync route is the default and covers the rest.
    if (_nodeCapabilities.supportsSyncEvaluate && this.resolution.canResolveSync() && !this.resolution.hasDistinctAsyncResolver()) {
      let resolvedPath;
      try {
        resolvedPath = moduleName.startsWith('data:') ? moduleName : await this.resolution.resolveEsmAsync(from, moduleName);
      } catch (error) {
        // The decision can rest on a root manual __mocks__ entry for a name
        // that does not resolve - generation then loads that mock without a
        // target path.
        const manualMock = await this.resolution.getEsmMockModuleAsync(from, moduleName);
        if (manualMock === null) throw error;
      }
      const scratch = new Map();
      const generated = this.importGeneratedMockSync(from, moduleName, moduleID, context, scratch, 'sync-preferred', resolvedPath);
      if (generated !== LOAD_ASYNC) {
        // `linkAndEvaluateModule` evaluates the module for this caller.
        return scratch.get(generated.cacheKey).module;
      }
    }

    // Known hole: with an async-only resolver or transformer, a resolver
    // with distinct sync/async hooks, on Node without sync evaluation
    // (< 24.9), or when the real module's graph needs async evaluation
    // (top-level await), automock and manual __mocks__ decisions still end
    // here. Serving them needs an async twin of importGeneratedMockSync -
    // including generation isolation that survives awaits, which neither
    // the depth counter nor withScratchRegistries offers.
    throw new Error('Attempting to import a mock without a factory');
  }
  async importWasmModule(source, identifier, context) {
    // Use async `WebAssembly.compile` (rather than the sync constructor used
    // by the v24.9+ sync core) to avoid blocking the event loop on large wasm
    // modules in the legacy async path.
    const wasmModule = await WebAssembly.compile(source);
    const moduleLookup = {};
    for (const {
      module
    } of WebAssembly.Module.imports(wasmModule)) {
      if (moduleLookup[module] === undefined) {
        const resolvedModule = await this.resolveModule(module, identifier, context);
        // Do NOT call linkAndEvaluateModule here: we are executing inside the
        // linker callback for the parent module, so Node's cascade may already
        // be linking resolvedModule. Calling linkAndEvaluateModule would
        // spin-wait via setImmediate, but the cascade can't finish until this
        // linker returns - deadlock. The SyntheticModule's body runs only
        // after Node has fully evaluated all deps in topological order.
        moduleLookup[module] = resolvedModule;
      }
    }
    return (0, _syntheticBuilders.buildWasmSyntheticModule)(wasmModule, identifier, context, depSpec => moduleLookup[depSpec].namespace);
  }

  // Shared async dynamic-import callback installed on every SourceTextModule
  // we construct. Goes through the legacy async path; revisit when min-Node
  // reaches v24.9 (Node may handle dynamic imports for us by then).
  dynamicImport = async (specifier, referencingModule, importAttributes, phase) => {
    if (phase === 'source') {
      throw makeSourcePhaseUnsupportedError(specifier, referencingModule.identifier);
    }
    (0, _jestUtil().invariant)(_nodeCapabilities.runtimeSupportsVmModules, 'You need to run with a version of node that supports ES Modules in the VM API. See https://jestjs.io/docs/ecmascript-modules');
    this.testState.throwIfBetweenTests('You are trying to `import` a file outside of the scope of the test code.');
    this.testState.throwIfTornDown('You are trying to `import` a file after the Jest environment has been torn down.');
    const dyn = await this.resolveModule(specifier, referencingModule.identifier, referencingModule.context);
    this.importAttributeValidator.validate(dyn.identifier, importAttributes ?? {}, referencingModule.identifier);
    return this.linkAndEvaluateModule(dyn);
  };
}
exports.EsmLoader = EsmLoader;

/***/ },

/***/ "./src/internals/FileCache.ts"
(__unused_webpack_module, exports) {



Object.defineProperty(exports, "__esModule", ({
  value: true
}));
exports.FileCache = void 0;
function fs() {
  const data = _interopRequireWildcard(require("graceful-fs"));
  fs = function () {
    return data;
  };
  return data;
}
function _interopRequireWildcard(e, t) { if ("function" == typeof WeakMap) var r = new WeakMap(), n = new WeakMap(); return (_interopRequireWildcard = function (e, t) { if (!t && e && e.__esModule) return e; var o, i, f = { __proto__: null, default: e }; if (null === e || "object" != typeof e && "function" != typeof e) return f; if (o = t ? n : r) { if (o.has(e)) return o.get(e); o.set(e, f); } for (const t in e) "default" !== t && {}.hasOwnProperty.call(e, t) && ((i = (o = Object.defineProperty) && Object.getOwnPropertyDescriptor(e, t)) && (i.get || i.set) ? o(f, t, i) : f[t] = e[t]); return f; })(e, t); }
/**
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

class FileCache {
  strings;
  buffers = new Map();
  constructor(cacheFS) {
    this.strings = cacheFS;
  }
  readFile(filename) {
    let source = this.strings.get(filename);
    if (source === undefined) {
      source = this.readFileBuffer(filename).toString();
      this.strings.set(filename, source);
    }
    return source;
  }
  readFileBuffer(filename) {
    let source = this.buffers.get(filename);
    if (!source) {
      source = fs().readFileSync(filename);
      this.buffers.set(filename, source);
    }
    return source;
  }
  clear() {
    this.strings.clear();
    this.buffers.clear();
  }
}
exports.FileCache = FileCache;

/***/ },

/***/ "./src/internals/JestGlobals.ts"
(__unused_webpack_module, exports, __webpack_require__) {



Object.defineProperty(exports, "__esModule", ({
  value: true
}));
exports.JestGlobals = void 0;
var _syntheticBuilders = __webpack_require__("./src/internals/syntheticBuilders.ts");
/**
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

const testTimeoutSymbol = Symbol.for('TEST_TIMEOUT_SYMBOL');
const retryTimesSetterSymbol = Symbol.for('RETRY_TIMES_SETTER');
const retryTimesSymbol = Symbol.for('RETRY_TIMES');
const waitBeforeRetrySymbol = Symbol.for('WAIT_BEFORE_RETRY');
const retryImmediatelySymbol = Symbol.for('RETRY_IMMEDIATELY');
const logErrorsBeforeRetrySymbol = Symbol.for('LOG_ERRORS_BEFORE_RETRY');
class JestGlobals {
  config;
  globalConfig;
  environment;
  mockState;
  moduleMocker;
  setMockBridge;
  setModuleMockBridge;
  generateMock;
  requireActualBridge;
  requireMockBridge;
  resetModulesBridge;
  isolateModulesBridge;
  isolateModulesAsyncBridge;
  clearAllMocksBridge;
  resetAllMocksBridge;
  restoreAllMocksBridge;
  testState;
  logFormattedReferenceError;
  cache = new Map();
  fakeTimersImpl;
  envGlobalsOverride;
  cachedEnvGlobals;
  constructor(options) {
    this.config = options.config;
    this.globalConfig = options.globalConfig;
    this.environment = options.environment;
    this.mockState = options.mockState;
    this.moduleMocker = options.moduleMocker;
    this.setMockBridge = options.setMock;
    this.setModuleMockBridge = options.setModuleMock;
    this.generateMock = options.generateMock;
    this.requireActualBridge = options.requireActual;
    this.requireMockBridge = options.requireMock;
    this.resetModulesBridge = options.resetModules;
    this.isolateModulesBridge = options.isolateModules;
    this.isolateModulesAsyncBridge = options.isolateModulesAsync;
    this.clearAllMocksBridge = options.clearAllMocks;
    this.resetAllMocksBridge = options.resetAllMocks;
    this.restoreAllMocksBridge = options.restoreAllMocks;
    this.testState = options.testState;
    this.logFormattedReferenceError = options.logFormattedReferenceError;
    this.fakeTimersImpl = this.config.fakeTimers.legacyFakeTimers ? this.environment.fakeTimers : this.environment.fakeTimersModern;
  }
  jestObjectFor(from) {
    const cached = this.cache.get(from);
    if (cached) return cached;
    const fresh = this.buildJestObject(from);
    this.cache.set(from, fresh);
    return fresh;
  }
  envGlobals() {
    if (this.envGlobalsOverride) {
      return {
        ...this.envGlobalsOverride
      };
    }
    let cached = this.cachedEnvGlobals;
    if (cached === undefined) {
      cached = {
        afterAll: this.environment.global.afterAll,
        afterEach: this.environment.global.afterEach,
        beforeAll: this.environment.global.beforeAll,
        beforeEach: this.environment.global.beforeEach,
        describe: this.environment.global.describe,
        expect: this.environment.global.expect,
        fdescribe: this.environment.global.fdescribe,
        fit: this.environment.global.fit,
        it: this.environment.global.it,
        test: this.environment.global.test,
        xdescribe: this.environment.global.xdescribe,
        xit: this.environment.global.xit,
        xtest: this.environment.global.xtest
      };
      this.cachedEnvGlobals = cached;
    }
    return {
      ...cached
    };
  }
  cjsGlobals(from) {
    return {
      ...this.envGlobals(),
      jest: this.jestObjectFor(from)
    };
  }
  esmGlobalsModule(from, context) {
    return (0, _syntheticBuilders.syntheticFromExports)('@jest/globals', context, this.cjsGlobals(from));
  }
  setEnvGlobalsOverride(globals) {
    this.envGlobalsOverride = globals;
  }
  clearJestObjectCache() {
    this.cache.clear();
  }
  buildJestObject(from) {
    const disableAutomock = () => {
      this.mockState.disableAutomock();
      return jestObject;
    };
    const enableAutomock = () => {
      this.mockState.enableAutomock();
      return jestObject;
    };
    const unmock = moduleName => {
      this.mockState.unmockCjs(from, moduleName);
      return jestObject;
    };
    const unmockModule = moduleName => {
      this.mockState.unmockEsm(from, moduleName);
      return jestObject;
    };
    const deepUnmock = moduleName => {
      this.mockState.deepUnmock(from, moduleName);
      return jestObject;
    };
    const mock = (moduleName, mockFactory, options) => {
      if (mockFactory !== undefined) {
        return setMockFactory(moduleName, mockFactory, options);
      }
      this.mockState.markExplicitCjsMock(from, moduleName);
      return jestObject;
    };
    const onGenerateMock = cb => {
      this.mockState.addOnGenerateMock(cb);
      return jestObject;
    };
    const setMockFactory = (moduleName, mockFactory, options) => {
      this.setMockBridge(from, moduleName, mockFactory, options);
      return jestObject;
    };
    const mockModule = (moduleName, mockFactory, options) => {
      if (typeof mockFactory !== 'function') {
        throw new TypeError('`unstable_mockModule` must be passed a mock factory');
      }
      this.setModuleMockBridge(from, moduleName, mockFactory, options);
      return jestObject;
    };
    const clearAllMocks = () => {
      this.clearAllMocksBridge();
      return jestObject;
    };
    const resetAllMocks = () => {
      this.resetAllMocksBridge();
      return jestObject;
    };
    const restoreAllMocks = () => {
      this.restoreAllMocksBridge();
      return jestObject;
    };
    const _getFakeTimers = () => {
      if (this.testState.isTornDown() || !(this.environment.fakeTimers || this.environment.fakeTimersModern)) {
        this.logFormattedReferenceError('You are trying to access a property or method of the Jest environment after it has been torn down.');
        process.exitCode = 1;
      }
      this.testState.throwIfBetweenTests('You are trying to access a property or method of the Jest environment outside of the scope of the test code.');
      return this.fakeTimersImpl;
    };
    const useFakeTimers = fakeTimersConfig => {
      fakeTimersConfig = {
        ...this.config.fakeTimers,
        ...fakeTimersConfig
      };
      if (fakeTimersConfig?.legacyFakeTimers) {
        this.fakeTimersImpl = this.environment.fakeTimers;
      } else {
        this.fakeTimersImpl = this.environment.fakeTimersModern;
      }
      this.fakeTimersImpl.useFakeTimers(fakeTimersConfig);
      return jestObject;
    };
    const useRealTimers = () => {
      _getFakeTimers().useRealTimers();
      return jestObject;
    };
    const resetModules = () => {
      this.resetModulesBridge();
      return jestObject;
    };
    const isolateModules = fn => {
      this.isolateModulesBridge(fn);
      return jestObject;
    };
    const isolateModulesAsync = this.isolateModulesAsyncBridge;
    const fn = this.moduleMocker.fn.bind(this.moduleMocker);
    const spyOn = this.moduleMocker.spyOn.bind(this.moduleMocker);
    const mocked = this.moduleMocker.mocked.bind(this.moduleMocker);
    const replaceProperty = this.moduleMocker.replaceProperty.bind(this.moduleMocker);
    const setTimeout = timeout => {
      this.environment.global[testTimeoutSymbol] = timeout;
      return jestObject;
    };
    const retryTimes = (numTestRetries, options) => {
      if (options?.entireDescribe) {
        const retryTimesSetter = this.environment.global[retryTimesSetterSymbol];
        retryTimesSetter?.({
          logErrorsBeforeRetry: options.logErrorsBeforeRetry,
          numRetries: numTestRetries,
          waitBeforeRetry: options.waitBeforeRetry
        });
      } else {
        this.environment.global[retryTimesSymbol] = numTestRetries;
        this.environment.global[logErrorsBeforeRetrySymbol] = options?.logErrorsBeforeRetry;
        this.environment.global[waitBeforeRetrySymbol] = options?.waitBeforeRetry;
        this.environment.global[retryImmediatelySymbol] = options?.retryImmediately;
      }
      return jestObject;
    };
    const jestObject = {
      advanceTimersByTime: msToRun => _getFakeTimers().advanceTimersByTime(msToRun),
      advanceTimersByTimeAsync: async msToRun => {
        const fakeTimers = _getFakeTimers();
        if (fakeTimers === this.environment.fakeTimersModern) {
          await fakeTimers.advanceTimersByTimeAsync(msToRun);
        } else {
          throw new TypeError('`jest.advanceTimersByTimeAsync()` is not available when using legacy fake timers.');
        }
      },
      advanceTimersToNextFrame: () => {
        const fakeTimers = _getFakeTimers();
        if (fakeTimers === this.environment.fakeTimersModern) {
          return fakeTimers.advanceTimersToNextFrame();
        }
        throw new TypeError('`jest.advanceTimersToNextFrame()` is not available when using legacy fake timers.');
      },
      advanceTimersToNextTimer: steps => _getFakeTimers().advanceTimersToNextTimer(steps),
      advanceTimersToNextTimerAsync: async steps => {
        const fakeTimers = _getFakeTimers();
        if (fakeTimers === this.environment.fakeTimersModern) {
          await fakeTimers.advanceTimersToNextTimerAsync(steps);
        } else {
          throw new TypeError('`jest.advanceTimersToNextTimerAsync()` is not available when using legacy fake timers.');
        }
      },
      autoMockOff: disableAutomock,
      autoMockOn: enableAutomock,
      clearAllMocks,
      clearAllTimers: () => _getFakeTimers().clearAllTimers(),
      createMockFromModule: moduleName => this.generateMock(from, moduleName),
      deepUnmock,
      disableAutomock,
      doMock: mock,
      dontMock: unmock,
      enableAutomock,
      fn,
      getRealSystemTime: () => {
        const fakeTimers = _getFakeTimers();
        if (fakeTimers === this.environment.fakeTimersModern) {
          return fakeTimers.getRealSystemTime();
        } else {
          throw new TypeError('`jest.getRealSystemTime()` is not available when using legacy fake timers.');
        }
      },
      getSeed: () => this.globalConfig.seed,
      getTimerCount: () => _getFakeTimers().getTimerCount(),
      isEnvironmentTornDown: () => this.testState.isTornDown(),
      isMockFunction: this.moduleMocker.isMockFunction,
      isolateModules,
      isolateModulesAsync,
      mock,
      mocked,
      now: () => _getFakeTimers().now(),
      onGenerateMock,
      replaceProperty,
      requireActual: moduleName => this.requireActualBridge(from, moduleName),
      requireMock: moduleName => this.requireMockBridge(from, moduleName),
      resetAllMocks,
      resetModules,
      restoreAllMocks,
      retryTimes,
      runAllImmediates: () => {
        const fakeTimers = _getFakeTimers();
        if (fakeTimers === this.environment.fakeTimers) {
          fakeTimers.runAllImmediates();
        } else {
          throw new TypeError('`jest.runAllImmediates()` is only available when using legacy fake timers.');
        }
      },
      runAllTicks: () => _getFakeTimers().runAllTicks(),
      runAllTimers: () => _getFakeTimers().runAllTimers(),
      runAllTimersAsync: async () => {
        const fakeTimers = _getFakeTimers();
        if (fakeTimers === this.environment.fakeTimersModern) {
          await fakeTimers.runAllTimersAsync();
        } else {
          throw new TypeError('`jest.runAllTimersAsync()` is not available when using legacy fake timers.');
        }
      },
      runOnlyPendingTimers: () => _getFakeTimers().runOnlyPendingTimers(),
      runOnlyPendingTimersAsync: async () => {
        const fakeTimers = _getFakeTimers();
        if (fakeTimers === this.environment.fakeTimersModern) {
          await fakeTimers.runOnlyPendingTimersAsync();
        } else {
          throw new TypeError('`jest.runOnlyPendingTimersAsync()` is not available when using legacy fake timers.');
        }
      },
      setMock: (moduleName, mock) => setMockFactory(moduleName, () => mock),
      setSystemTime: now => {
        const fakeTimers = _getFakeTimers();
        if (fakeTimers === this.environment.fakeTimersModern) {
          fakeTimers.setSystemTime(now);
        } else {
          throw new TypeError('`jest.setSystemTime()` is not available when using legacy fake timers.');
        }
      },
      setTimeout,
      setTimerTickMode: mode => {
        const fakeTimers = _getFakeTimers();
        if (fakeTimers === this.environment.fakeTimersModern) {
          fakeTimers.setTimerTickMode(mode);
        } else {
          throw new TypeError('`jest.setTimerTickMode()` is not available when using legacy fake timers.');
        }
        return jestObject;
      },
      spyOn,
      unmock,
      unstable_mockModule: mockModule,
      unstable_unmockModule: unmockModule,
      useFakeTimers,
      useRealTimers
    };
    return jestObject;
  }
}
exports.JestGlobals = JestGlobals;

/***/ },

/***/ "./src/internals/MockState.ts"
(__unused_webpack_module, exports) {



Object.defineProperty(exports, "__esModule", ({
  value: true
}));
exports.MockState = void 0;
exports.generateMock = generateMock;
function path() {
  const data = _interopRequireWildcard(require("node:path"));
  path = function () {
    return data;
  };
  return data;
}
function _jestResolve() {
  const data = _interopRequireDefault(require("jest-resolve"));
  _jestResolve = function () {
    return data;
  };
  return data;
}
function _interopRequireDefault(e) { return e && e.__esModule ? e : { default: e }; }
function _interopRequireWildcard(e, t) { if ("function" == typeof WeakMap) var r = new WeakMap(), n = new WeakMap(); return (_interopRequireWildcard = function (e, t) { if (!t && e && e.__esModule) return e; var o, i, f = { __proto__: null, default: e }; if (null === e || "object" != typeof e && "function" != typeof e) return f; if (o = t ? n : r) { if (o.has(e)) return o.get(e); o.set(e, f); } for (const t in e) "default" !== t && {}.hasOwnProperty.call(e, t) && ((i = (o = Object.defineProperty) && Object.getOwnPropertyDescriptor(e, t)) && (i.get || i.set) ? o(f, t, i) : f[t] = e[t]); return f; })(e, t); }
/**
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

const NODE_MODULES = `${path().sep}node_modules${path().sep}`;
const unmockRegExpCache = new WeakMap();
const transitiveCacheKey = (from, moduleID) => `${from}\0${moduleID}`;
// Spellings that serialize to one data URL are one module in the loader
// (which keys by `new URL(...).href`), so the mock decision and its ID must
// canonicalize the same way. Unparseable URIs stay raw - the loader's own
// Invalid URL error surfaces later.
function canonicalizeDataUri(moduleName) {
  if (!moduleName.startsWith('data:')) return moduleName;
  try {
    return new URL(moduleName).href;
  } catch {
    return moduleName;
  }
}
const NO_MOCK = Object.freeze({
  moduleID: '',
  shouldMock: false
});
class MockState {
  resolution;
  rootDir;
  unmockList;
  shouldAutoMock;
  explicitCjsMock = new Map();
  explicitEsmMock = new Map();
  cjsFactories = new Map();
  esmFactories = new Map();
  virtualCjsMocks = new Map();
  virtualEsmMocks = new Map();
  shouldMockCache = new Map();
  shouldUnmockTransitiveDepsCache = new Map();
  transitiveShouldMock = new Map();
  mockMetaDataCache = new Map();
  onGenerateMockCallbacks = new Set();
  constructor(resolution, config) {
    this.resolution = resolution;
    this.rootDir = config.rootDir;
    this.shouldAutoMock = config.automock;
    let unmock = unmockRegExpCache.get(config);
    if (!unmock && config.unmockedModulePathPatterns) {
      unmock = new RegExp(config.unmockedModulePathPatterns.join('|'));
      unmockRegExpCache.set(config, unmock);
    }
    this.unmockList = unmock;
  }
  shouldMockCjs(from, moduleName) {
    if (this.noMockCanApply(this.explicitCjsMock)) {
      return NO_MOCK;
    }
    const moduleID = this.resolution.getCjsModuleId(this.virtualCjsMocks, from, moduleName);
    return {
      moduleID,
      shouldMock: this.decideSync(from, moduleName, moduleID, 'cjs')
    };
  }
  shouldMockEsmSync(from, moduleName) {
    if (this.noMockCanApply(this.explicitEsmMock)) {
      return NO_MOCK;
    }
    moduleName = canonicalizeDataUri(moduleName);
    const moduleID = this.resolution.getEsmModuleId(this.virtualEsmMocks, from, moduleName);
    return {
      moduleID,
      shouldMock: this.decideSync(from, moduleName, moduleID, 'esm')
    };
  }
  async shouldMockEsmAsync(from, moduleName) {
    if (this.noMockCanApply(this.explicitEsmMock)) {
      return NO_MOCK;
    }
    moduleName = canonicalizeDataUri(moduleName);
    const moduleID = await this.resolution.getEsmModuleIdAsync(this.virtualEsmMocks, from, moduleName);
    return {
      moduleID,
      shouldMock: await this.decideEsmAsync(from, moduleName, moduleID)
    };
  }

  // Deriving a module ID resolves the specifier and runs every
  // `moduleNameMapper` pattern, so the decision skips it when neither an
  // explicit mock nor automocking can make the answer anything but false.
  // Callers read `moduleID` only when `shouldMock` is true.
  noMockCanApply(explicitMocks) {
    return !this.shouldAutoMock && explicitMocks.size === 0;
  }
  async decideEsmAsync(from, moduleName, moduleID) {
    const explicit = this.explicitEsmMock.get(moduleID);
    if (explicit !== undefined) return explicit;
    if (!this.shouldAutoMock || this.resolution.isCoreModule(moduleName)) {
      return false;
    }
    const key = transitiveCacheKey(from, moduleID);
    if (this.shouldUnmockTransitiveDepsCache.get(key)) {
      return false;
    }
    const cached = this.shouldMockCache.get(moduleID);
    if (cached !== undefined) return cached;

    // A data: URI is its own module - the filesystem resolver cannot
    // resolve it, but the automock decision applies like any other ESM.
    if (moduleName.startsWith('data:')) {
      return this.applyEsmDataUriDecision(from, moduleName, moduleID);
    }
    let modulePath;
    try {
      modulePath = await this.resolution.resolveEsmAsync(from, moduleName);
    } catch (error) {
      const manualMock = await this.resolution.getEsmMockModuleAsync(from, moduleName);
      if (manualMock) {
        this.shouldMockCache.set(moduleID, true);
        return true;
      }
      throw error;
    }
    if (this.unmockList?.test(modulePath)) {
      this.shouldMockCache.set(moduleID, false);
      return false;
    }
    const currentModuleID = await this.resolution.getEsmModuleIdAsync(this.virtualEsmMocks, from);
    return this.applyTransitive(moduleID, currentModuleID, modulePath, from, key, this.explicitEsmMock);
  }
  decideSync(from, moduleName, moduleID, mode) {
    const explicitMap = mode === 'cjs' ? this.explicitCjsMock : this.explicitEsmMock;
    const explicit = explicitMap.get(moduleID);
    if (explicit !== undefined) return explicit;
    if (!this.shouldAutoMock || this.resolution.isCoreModule(moduleName)) {
      return false;
    }
    const key = transitiveCacheKey(from, moduleID);
    if (this.shouldUnmockTransitiveDepsCache.get(key)) {
      return false;
    }
    const cached = this.shouldMockCache.get(moduleID);
    if (cached !== undefined) return cached;
    if (mode === 'esm' && moduleName.startsWith('data:')) {
      return this.applyEsmDataUriDecision(from, moduleName, moduleID);
    }
    let modulePath;
    try {
      modulePath = mode === 'cjs' ? this.resolution.resolveCjs(from, moduleName) : this.resolution.resolveEsm(from, moduleName);
    } catch (error) {
      const manualMock = mode === 'cjs' ? this.resolution.getCjsMockModule(from, moduleName) : this.resolution.getEsmMockModule(from, moduleName);
      if (manualMock) {
        this.shouldMockCache.set(moduleID, true);
        return true;
      }
      throw error;
    }
    if (this.unmockList?.test(modulePath)) {
      this.shouldMockCache.set(moduleID, false);
      return false;
    }
    const currentModuleID = mode === 'cjs' ? this.resolution.getCjsModuleId(this.virtualCjsMocks, from) : this.resolution.getEsmModuleId(this.virtualEsmMocks, from);
    return this.applyTransitive(moduleID, currentModuleID, modulePath, from, key, explicitMap);
  }

  // The URI stands in for the module path: the unmock list can match it,
  // and the transitive rules see a location that is never in node_modules.
  applyEsmDataUriDecision(from, dataUri, moduleID) {
    if (this.unmockList?.test(dataUri)) {
      this.shouldMockCache.set(moduleID, false);
      return false;
    }
    const currentModuleID = this.resolution.getEsmModuleId(this.virtualEsmMocks, from);
    return this.applyTransitive(moduleID, currentModuleID, dataUri, from, transitiveCacheKey(from, moduleID), this.explicitEsmMock);
  }
  applyTransitive(moduleID, currentModuleID, modulePath, from, key, explicitMap) {
    if (this.transitiveShouldMock.get(currentModuleID) === false || from.includes(NODE_MODULES) && modulePath.includes(NODE_MODULES) && (this.unmockList && this.unmockList.test(from) || explicitMap.get(currentModuleID) === false)) {
      this.transitiveShouldMock.set(moduleID, false);
      this.shouldUnmockTransitiveDepsCache.set(key, true);
      return false;
    }
    this.shouldMockCache.set(moduleID, true);
    return true;
  }

  // ---- explicit registration ----

  setMock(from, moduleName, factory, options) {
    if (options?.virtual) {
      const mockPath = this.resolution.getModulePath(from, moduleName);
      this.virtualCjsMocks.set(mockPath, true);
    }
    const moduleID = this.getModuleIdForMockRegistration(from, moduleName, 'cjs');
    this.explicitCjsMock.set(moduleID, true);
    this.cjsFactories.set(moduleID, factory);
  }
  setModuleMock(from, moduleName, factory, options) {
    moduleName = canonicalizeDataUri(moduleName);
    if (options?.virtual) {
      const mockPath = this.resolution.getModulePath(from, moduleName);
      this.virtualEsmMocks.set(mockPath, true);
    }
    const moduleID = this.getModuleIdForMockRegistration(from, moduleName, 'esm');
    this.explicitEsmMock.set(moduleID, true);
    this.esmFactories.set(moduleID, factory);
  }

  // Registering a factory mock resolves the mocked name, so mocking a module
  // that does not exist on disk fails right here with a bare
  // "Cannot find module" - point at the `virtual` option, which is the
  // supported way to mock such a module.
  getModuleIdForMockRegistration(from, moduleName, mode) {
    try {
      return mode === 'cjs' ? this.resolution.getCjsModuleId(this.virtualCjsMocks, from, moduleName) : this.resolution.getEsmModuleId(this.virtualEsmMocks, from, moduleName);
    } catch (error) {
      const moduleNotFound = _jestResolve().default.tryCastModuleNotFoundError(error);
      if (moduleNotFound) {
        moduleNotFound.hint = '\n\nTo mock a module that does not exist on disk, pass `{virtual: true}` in the mock options. See https://jestjs.io/docs/jest-object#jestmockmodulename-factory-options';
        moduleNotFound.buildMessage(this.rootDir);
      }
      throw error;
    }
  }
  disableAutomock() {
    this.shouldAutoMock = false;
  }
  enableAutomock() {
    this.shouldAutoMock = true;
  }
  unmockCjs(from, moduleName) {
    const moduleID = this.resolution.getCjsModuleId(this.virtualCjsMocks, from, moduleName);
    this.explicitCjsMock.set(moduleID, false);
  }
  unmockEsm(from, moduleName) {
    moduleName = canonicalizeDataUri(moduleName);
    const moduleID = this.resolution.getEsmModuleId(this.virtualEsmMocks, from, moduleName);
    this.explicitEsmMock.set(moduleID, false);
  }
  deepUnmock(from, moduleName) {
    const moduleID = this.resolution.getCjsModuleId(this.virtualCjsMocks, from, moduleName);
    this.explicitCjsMock.set(moduleID, false);
    this.transitiveShouldMock.set(moduleID, false);
  }
  markExplicitCjsMock(from, moduleName) {
    const moduleID = this.resolution.getCjsModuleId(this.virtualCjsMocks, from, moduleName);
    this.explicitCjsMock.set(moduleID, true);
  }
  addOnGenerateMock(callback) {
    this.onGenerateMockCallbacks.add(callback);
  }
  getCjsModuleId(from, moduleName) {
    return this.resolution.getCjsModuleId(this.virtualCjsMocks, from, moduleName);
  }
  getEsmModuleId(from, moduleName) {
    return this.resolution.getEsmModuleId(this.virtualEsmMocks, from, moduleName);
  }
  isExplicitlyUnmocked(moduleID) {
    return this.explicitCjsMock.get(moduleID) === false;
  }
  isTransitivelyUnmocked(moduleID) {
    return this.transitiveShouldMock.get(moduleID) === false;
  }
  getCjsFactory(moduleID) {
    return this.cjsFactories.get(moduleID);
  }
  getEsmFactory(moduleID) {
    return this.esmFactories.get(moduleID);
  }
  markTransitive(moduleID, value) {
    this.transitiveShouldMock.set(moduleID, value);
  }
  hasMockMetadata(modulePath) {
    return this.mockMetaDataCache.has(modulePath);
  }
  getMockMetadata(modulePath) {
    return this.mockMetaDataCache.get(modulePath);
  }
  setMockMetadata(modulePath, metadata) {
    this.mockMetaDataCache.set(modulePath, metadata);
  }
  deleteMockMetadata(modulePath) {
    this.mockMetaDataCache.delete(modulePath);
  }
  notifyMockGenerated(moduleName, moduleMock) {
    let result = moduleMock;
    for (const callback of this.onGenerateMockCallbacks) {
      result = callback(moduleName, result);
    }
    return result;
  }

  // `resetModules` does not touch mock state - explicit mocks, factories, and
  // virtual marks survive a reset. Only `teardown` drops everything.
  clear() {
    this.cjsFactories.clear();
    this.esmFactories.clear();
    this.mockMetaDataCache.clear();
    this.shouldMockCache.clear();
    this.shouldUnmockTransitiveDepsCache.clear();
    this.explicitCjsMock.clear();
    this.explicitEsmMock.clear();
    this.transitiveShouldMock.clear();
    this.virtualCjsMocks.clear();
    this.virtualEsmMocks.clear();
    this.onGenerateMockCallbacks.clear();
  }
}
exports.MockState = MockState;
function generateMock(from, moduleName, options) {
  const {
    resolution,
    mockState,
    moduleMocker,
    registries,
    requireModule
  } = options;
  const modulePath = resolution.resolveCjsStub(from, moduleName) || resolution.resolveCjs(from, moduleName);
  if (!mockState.hasMockMetadata(modulePath)) {
    // This allows us to handle circular dependencies while generating an
    // automock
    mockState.setMockMetadata(modulePath, moduleMocker.getMetadata({}) || {});

    // In order to avoid it being possible for automocking to potentially
    // cause side-effects within the module environment, we need to execute
    // the module in isolation. This could cause issues if the module being
    // mocked has calls into side-effectful APIs on another module.
    const moduleExports = registries.withScratchRegistries(() => requireModule(from, moduleName));
    const mockMetadata = moduleMocker.getMetadata(moduleExports);
    if (mockMetadata == null) {
      throw new Error(`Failed to get mock metadata: ${modulePath}\n\n` + 'See: https://jestjs.io/docs/manual-mocks#content');
    }
    mockState.setMockMetadata(modulePath, mockMetadata);
  }
  const moduleMock = moduleMocker.generateFromMetadata(mockState.getMockMetadata(modulePath));
  return mockState.notifyMockGenerated(modulePath, moduleMock);
}

/***/ },

/***/ "./src/internals/ModuleExecutor.ts"
(__unused_webpack_module, exports, __webpack_require__) {



Object.defineProperty(exports, "__esModule", ({
  value: true
}));
exports.ModuleExecutor = exports.CjsParseError = void 0;
function _nodeVm() {
  const data = require("node:vm");
  _nodeVm = function () {
    return data;
  };
  return data;
}
function _transform() {
  const data = require("@jest/transform");
  _transform = function () {
    return data;
  };
  return data;
}
function _jestResolve() {
  const data = _interopRequireDefault(require("jest-resolve"));
  _jestResolve = function () {
    return data;
  };
  return data;
}
function _jestUtil() {
  const data = require("jest-util");
  _jestUtil = function () {
    return data;
  };
  return data;
}
var _esmLexer = __webpack_require__("./src/internals/esmLexer.ts");
var _nodeCapabilities = __webpack_require__("./src/internals/nodeCapabilities.ts");
function _interopRequireDefault(e) { return e && e.__esModule ? e : { default: e }; }
/**
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

// Thrown by the CJS-as-ESM fallback paths when a file has ESM syntax but no
// ESM marker (.mjs / "type":"module"). Callers catch it by instanceof and
// retry with loadEsmModule / requireEsm. The original error is in `.cause`.
class CjsParseError extends SyntaxError {
  name = 'CjsParseError';
  constructor(cause) {
    super(cause.message, {
      cause
    });
  }
}
exports.CjsParseError = CjsParseError;
class ModuleExecutor {
  injectedModuleParameters;
  resolution;
  transformCache;
  environment;
  config;
  testPath;
  requireBuilder;
  testMainModule;
  jestGlobals;
  dynamicImport;
  currentlyExecutingManualMock = null;
  constructor(options) {
    this.resolution = options.resolution;
    this.transformCache = options.transformCache;
    this.environment = options.environment;
    this.config = options.config;
    this.testPath = options.testPath;
    this.requireBuilder = options.requireBuilder;
    this.testMainModule = options.testMainModule;
    this.jestGlobals = options.jestGlobals;
    this.dynamicImport = options.dynamicImport;
    this.injectedModuleParameters = ['module', 'exports', 'require', '__dirname', '__filename', this.config.injectGlobals ? 'jest' : undefined, ...this.config.sandboxInjectedGlobals].filter(_jestUtil().isNonNullable);
  }
  getCurrentlyExecutingManualMock() {
    return this.currentlyExecutingManualMock;
  }
  exec(localModule, options, moduleRegistry, from, moduleName) {
    if (!this.environment.global) {
      return 'env-disposed';
    }
    const module = localModule;
    const filename = module.filename;
    const origCurrExecutingManualMock = this.currentlyExecutingManualMock;
    this.currentlyExecutingManualMock = filename;
    try {
      module.children = [];
      Object.defineProperty(module, 'parent', {
        enumerable: true,
        get() {
          const key = from || '';
          return moduleRegistry.get(key) || null;
        }
      });
      const modulePaths = this.resolution.getModulePaths(module.path);
      const globalPaths = this.resolution.getGlobalPaths(moduleName);
      module.paths = [...modulePaths, ...globalPaths];
      Object.defineProperty(module, 'require', {
        value: this.requireBuilder.for(localModule, options)
      });
      const transformedCode = this.transformCache.transform(filename, options);
      const compiledFunction = this.compile(transformedCode, filename);
      if (compiledFunction === null) {
        return 'env-disposed';
      }
      const sandboxGlobals = this.config.sandboxInjectedGlobals.map(globalVariable => {
        if (this.environment.global[globalVariable]) {
          return this.environment.global[globalVariable];
        }
        throw new Error(`You have requested '${globalVariable}' as a global variable, but it was not present. Please check your config or your global environment.`);
      });

      // Positional, so this has to line up with
      // `injectedModuleParameters` name for name.
      const injectedModuleArguments = this.config.injectGlobals ? [this.jestGlobals.jestObjectFor(filename), ...sandboxGlobals] : sandboxGlobals;
      if (!this.testMainModule.current && filename === this.testPath) {
        this.testMainModule.current = module;
      }
      Object.defineProperty(module, 'main', {
        enumerable: true,
        value: this.testMainModule.current
      });
      try {
        compiledFunction.call(module.exports, module,
        // module object
        module.exports,
        // module exports
        module.require,
        // require implementation
        module.path,
        // __dirname
        module.filename,
        // __filename
        ...injectedModuleArguments);
      } catch (error) {
        this.handleExecutionError(error, module);
      }
    } finally {
      this.currentlyExecutingManualMock = origCurrExecutingManualMock;
    }
    return 'loaded';
  }
  compile(scriptSource, filename) {
    const vmContext = this.environment.getVmContext();
    if (vmContext == null) {
      return null;
    }
    try {
      const scriptFilename = this.resolution.isCoreModule(filename) ? `jest-nodejs-core-${filename}` : filename;
      return (0, _nodeVm().compileFunction)(scriptSource, this.injectedModuleParameters, {
        filename: scriptFilename,
        // The fourth argument (the import phase) is missing from
        // @types/node@18's callback type - hence the rest parameter.
        importModuleDynamically: async (specifier, _function, importAttributes, ...rest) => {
          (0, _jestUtil().invariant)(_nodeCapabilities.runtimeSupportsVmModules, 'You need to run with a version of node that supports ES Modules in the VM API. See https://jestjs.io/docs/ecmascript-modules');
          return this.dynamicImport(specifier, scriptFilename, vmContext, importAttributes, rest[0]);
        },
        parsingContext: vmContext
      });
    } catch (error) {
      if (_nodeCapabilities.runtimeSupportsVmModules && (0, _jestUtil().isError)(error) && error.name === 'SyntaxError' && (0, _esmLexer.hasEsmSyntax)(scriptSource)) {
        throw new CjsParseError(error);
      }
      throw (0, _transform().handlePotentialSyntaxError)(error);
    }
  }
  handleExecutionError(error, module) {
    const moduleNotFoundError = _jestResolve().default.tryCastModuleNotFoundError(error);
    if (moduleNotFoundError) {
      if (!moduleNotFoundError.requireStack) {
        moduleNotFoundError.requireStack = [module.filename || module.id];
        for (let cursor = module.parent; cursor; cursor = cursor.parent) {
          moduleNotFoundError.requireStack.push(cursor.filename || cursor.id);
        }
        moduleNotFoundError.buildMessage(this.config.rootDir);
      }
      throw moduleNotFoundError;
    }
    throw error;
  }
}
exports.ModuleExecutor = ModuleExecutor;

/***/ },

/***/ "./src/internals/ModuleRegistries.ts"
(__unused_webpack_module, exports) {



Object.defineProperty(exports, "__esModule", ({
  value: true
}));
exports.ModuleRegistries = void 0;
function _nodeModule() {
  const data = _interopRequireDefault(require("node:module"));
  _nodeModule = function () {
    return data;
  };
  return data;
}
function path() {
  const data = _interopRequireWildcard(require("node:path"));
  path = function () {
    return data;
  };
  return data;
}
function _nodeUrl() {
  const data = require("node:url");
  _nodeUrl = function () {
    return data;
  };
  return data;
}
function _interopRequireWildcard(e, t) { if ("function" == typeof WeakMap) var r = new WeakMap(), n = new WeakMap(); return (_interopRequireWildcard = function (e, t) { if (!t && e && e.__esModule) return e; var o, i, f = { __proto__: null, default: e }; if (null === e || "object" != typeof e && "function" != typeof e) return f; if (o = t ? n : r) { if (o.has(e)) return o.get(e); o.set(e, f); } for (const t in e) "default" !== t && {}.hasOwnProperty.call(e, t) && ((i = (o = Object.defineProperty) && Object.getOwnPropertyDescriptor(e, t)) && (i.get || i.set) ? o(f, t, i) : f[t] = e[t]); return f; })(e, t); }
function _interopRequireDefault(e) { return e && e.__esModule ? e : { default: e }; }
/**
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

// Only expose ESM entries whose `namespace` is readable without throwing or
// exposing TDZ values: `unlinked`/`linking` throw `ERR_VM_MODULE_STATUS`,
// and anything short of `evaluated` keeps uninitialized (TDZ) bindings that
// throw `ReferenceError` on read - including `errored`, whose evaluation
// stopped partway. Node likewise drops a failed `require(esm)` entry from
// `require.cache`.
const isLiveEsm = entry => {
  if (!entry || entry instanceof Promise) return false;
  return entry.status === 'evaluated';
};
const notPermittedMethod = () => true;
class Isolation {
  cjs = new Map();
  esm = new Map();
  mock = new Map();
  moduleMock = new Map();
  clear() {
    this.cjs.clear();
    this.esm.clear();
    this.mock.clear();
    this.moduleMock.clear();
  }
}
class ModuleRegistries {
  requireEsmResult;
  moduleRegistry = new Map();
  internalModuleRegistry = new Map();
  esModuleRegistry = new Map();
  mockRegistry = new Map();
  moduleMockRegistry = new Map();
  isolation = null;
  esmRequireCacheWrappers = new WeakMap();
  requireCacheProxy;
  constructor(requireEsmResult) {
    this.requireEsmResult = requireEsmResult;
  }

  // Reads cascade: isolated overlay first, fall back to main. Writes go to
  // the active overlay only. This lets `jest.isolateModules` inherit mock
  // instances the user set up outside (so `.mockImplementation(...)` on the
  // outer instance still applies to inner reads) while still allowing the
  // isolation block to install its own mocks that don't leak back out.
  getMock(moduleID) {
    const fromIsolated = this.isolation?.mock.get(moduleID);
    if (fromIsolated !== undefined) return fromIsolated;
    return this.mockRegistry.get(moduleID);
  }
  setMock(moduleID, module) {
    (this.isolation?.mock ?? this.mockRegistry).set(moduleID, module);
  }
  hasMock(moduleID) {
    return (this.isolation?.mock.has(moduleID) ?? false) || this.mockRegistry.has(moduleID);
  }

  // Same cascade as `getMock` above: an isolation block inherits the module
  // mocks instantiated outside it, but the instances it creates itself go to
  // the overlay and are dropped on exit.
  getModuleMock(moduleID) {
    return this.isolation?.moduleMock.get(moduleID) ?? this.moduleMockRegistry.get(moduleID);
  }
  setModuleMock(moduleID, module) {
    (this.isolation?.moduleMock ?? this.moduleMockRegistry).set(moduleID, module);
  }
  hasModuleMock(moduleID) {
    return (this.isolation?.moduleMock.has(moduleID) ?? false) || this.moduleMockRegistry.has(moduleID);
  }
  getActiveEsmRegistry() {
    return this.isolation?.esm ?? this.esModuleRegistry;
  }
  getActiveCjsRegistry() {
    return this.isolation?.cjs ?? this.moduleRegistry;
  }
  getInternalCjsRegistry() {
    return this.internalModuleRegistry;
  }
  getActiveMockRegistry() {
    return this.isolation?.mock ?? this.mockRegistry;
  }
  isIsolated() {
    return this.isolation !== null;
  }
  enterIsolated(callerName) {
    if (this.isIsolated()) {
      const other = callerName === 'isolateModules' ? 'isolateModulesAsync' : 'isolateModules';
      throw new Error(`${callerName} cannot be nested inside another ${callerName} or ${other}.`);
    }
    this.isolation = new Isolation();
  }
  exitIsolated() {
    this.isolation?.clear();
    this.isolation = null;
  }

  // Loads `fn` against fresh CJS + mock registries, then restores the
  // originals. Used by `_generateMock` to keep automock loading from
  // polluting the real caches.
  withScratchRegistries(fn) {
    const originalMock = this.mockRegistry;
    const originalModule = this.moduleRegistry;
    const originalEsm = this.esModuleRegistry;
    const originalModuleMock = this.moduleMockRegistry;
    const originalIsolation = this.isolation;
    this.mockRegistry = new Map();
    this.moduleRegistry = new Map();
    this.esModuleRegistry = new Map();
    this.moduleMockRegistry = new Map();
    // Every accessor prefers the isolation overlay, so leaving it in place
    // would send the scratch load into the live isolated registry - the
    // pollution this exists to prevent. All four base maps are swapped too,
    // or an ESM target reached through `requireEsm` would write straight to
    // the long-lived registry once the overlay is out of the way.
    this.isolation = null;
    try {
      return fn();
    } finally {
      this.mockRegistry = originalMock;
      this.moduleRegistry = originalModule;
      this.esModuleRegistry = originalEsm;
      this.moduleMockRegistry = originalModuleMock;
      this.isolation = originalIsolation;
    }
  }
  wrapEsmForRequireCache(filename, esm) {
    const existing = this.esmRequireCacheWrappers.get(esm);
    if (existing) return existing;
    const dir = path().dirname(filename);
    const wrapper = {
      children: [],
      exports: this.requireEsmResult(esm),
      filename,
      id: filename,
      isPreloading: false,
      loaded: true,
      parent: null,
      path: dir,
      paths: _nodeModule().default.Module._nodeModulePaths(dir),
      require: () => {
        throw new Error('require() on a require.cache ESM entry is not supported');
      }
    };
    this.esmRequireCacheWrappers.set(esm, wrapper);
    return wrapper;
  }

  // The ESM registry keys by serialized URL, but `require.cache` addresses
  // modules by path (Node keys its `require(esm)` entries the same way).
  // `pathToFileURL` percent-encodes `?`/`#`, so a path lookup can never reach
  // an entry that carries a query or fragment - those instances exist only
  // for `import`, exactly as in Node. Every non-path key is invisible: URL
  // strings, core specifiers, `data:` URIs and the registry's synthetic
  // entries never appear in Node's `require.cache` either.
  getEsmEntryForRequireCache(key) {
    if (!path().isAbsolute(key)) {
      return undefined;
    }
    const registry = this.isolation?.esm ?? this.esModuleRegistry;
    return registry.get((0, _nodeUrl().pathToFileURL)(key).href);
  }
  getEsmRequireCacheEntry(key) {
    const entry = this.getEsmEntryForRequireCache(key);
    if (!isLiveEsm(entry)) return undefined;
    return this.wrapEsmForRequireCache(key, entry);
  }
  getRequireCacheProxy() {
    this.requireCacheProxy ??= new Proxy(Object.create(null), {
      defineProperty: notPermittedMethod,
      deleteProperty: notPermittedMethod,
      get: (_target, key) => {
        if (typeof key !== 'string') return undefined;
        return (this.isolation?.cjs ?? this.moduleRegistry).get(key) ?? this.getEsmRequireCacheEntry(key);
      },
      getOwnPropertyDescriptor() {
        return {
          configurable: true,
          enumerable: true
        };
      },
      has: (_target, key) => {
        if (typeof key !== 'string') return false;
        return (this.isolation?.cjs ?? this.moduleRegistry).has(key) || isLiveEsm(this.getEsmEntryForRequireCache(key));
      },
      ownKeys: () => {
        const keys = new Set((this.isolation?.cjs ?? this.moduleRegistry).keys());
        for (const [key, entry] of this.isolation?.esm ?? this.esModuleRegistry) {
          if (!isLiveEsm(entry)) continue;
          // Only file URLs become paths; core, data: and synthetic keys
          // are not path-addressable and stay invisible, as in Node.
          // `pathToFileURL` percent-encodes literal `?`/`#`, so their
          // presence always means a query or fragment - an instance a
          // path key cannot address.
          if (!key.startsWith('file://')) continue;
          if (key.includes('?') || key.includes('#')) continue;
          keys.add((0, _nodeUrl().fileURLToPath)(key));
        }
        return [...keys];
      },
      set: notPermittedMethod
    });
    return this.requireCacheProxy;
  }
  clearForReset() {
    this.exitIsolated();
    this.mockRegistry.clear();
    this.moduleRegistry.clear();
    this.esModuleRegistry.clear();
    this.moduleMockRegistry.clear();
  }
  clear() {
    this.clearForReset();
    this.internalModuleRegistry.clear();
  }
}
exports.ModuleRegistries = ModuleRegistries;

/***/ },

/***/ "./src/internals/Resolution.ts"
(__unused_webpack_module, exports, __webpack_require__) {



Object.defineProperty(exports, "__esModule", ({
  value: true
}));
exports.isWasm = exports.Resolution = void 0;
function path() {
  const data = _interopRequireWildcard(require("node:path"));
  path = function () {
    return data;
  };
  return data;
}
function fs() {
  const data = _interopRequireWildcard(require("graceful-fs"));
  fs = function () {
    return data;
  };
  return data;
}
function _jestResolve() {
  const data = _interopRequireDefault(require("jest-resolve"));
  _jestResolve = function () {
    return data;
  };
  return data;
}
var _nodeCapabilities = __webpack_require__("./src/internals/nodeCapabilities.ts");
function _interopRequireDefault(e) { return e && e.__esModule ? e : { default: e }; }
function _interopRequireWildcard(e, t) { if ("function" == typeof WeakMap) var r = new WeakMap(), n = new WeakMap(); return (_interopRequireWildcard = function (e, t) { if (!t && e && e.__esModule) return e; var o, i, f = { __proto__: null, default: e }; if (null === e || "object" != typeof e && "function" != typeof e) return f; if (o = t ? n : r) { if (o.has(e)) return o.get(e); o.set(e, f); } for (const t in e) "default" !== t && {}.hasOwnProperty.call(e, t) && ((i = (o = Object.defineProperty) && Object.getOwnPropertyDescriptor(e, t)) && (i.get || i.set) ? o(f, t, i) : f[t] = e[t]); return f; })(e, t); }
/**
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

const isWasm = modulePath => modulePath.endsWith('.wasm');
exports.isWasm = isWasm;
class Resolution {
  resolver;
  cjsConditions;
  esmConditions;
  // One long-lived, frozen object per condition set - the resolver memoizes
  // its cache-key serialization on options-object identity.
  cjsResolveOptions;
  esmResolveOptions;
  extensionsToTreatAsEsm;
  cjsCache = new Map();
  esmCache = new Map();
  manualMockCache = new Map();
  constructor(resolver, envExportConditions, extensionsToTreatAsEsm) {
    this.resolver = resolver;
    this.cjsConditions = [...new Set(['require',
    // Node only offers `module-sync` to `require()` when it can load ESM
    // that way. Claiming it otherwise resolves dual packages to their ESM
    // entry point, which we then cannot execute.
    ...(_nodeCapabilities.supportsSyncEvaluate ? ['module-sync'] : []), 'node', 'default', ...envExportConditions])];
    this.esmConditions = [...new Set(['import', 'module-sync', 'default', ...envExportConditions])];
    this.cjsResolveOptions = Object.freeze({
      conditions: this.cjsConditions
    });
    this.esmResolveOptions = Object.freeze({
      conditions: this.esmConditions
    });
    this.extensionsToTreatAsEsm = extensionsToTreatAsEsm;
  }
  shouldLoadAsEsm(modulePath) {
    return isWasm(modulePath) || _jestResolve().default.unstable_shouldLoadAsEsm(modulePath, this.extensionsToTreatAsEsm);
  }

  // Package `type` governs only files whose extension leaves the format
  // open - `.mjs` and configured ESM extensions are ESM regardless.
  // The .cjs extension is explicitly CommonJS on its own; the package `type`
  // field governs only .js files.
  isExplicitlyCommonjs(modulePath) {
    const extension = path().extname(modulePath);
    if (extension === '.cjs') {
      return true;
    }
    return extension === '.js' && _jestResolve().default.unstable_isExplicitlyCommonjs(modulePath);
  }
  resolveCjs(from, to) {
    if (!to) return from;
    return this.resolveCached(from, to, this.cjsCache, this.cjsResolveOptions);
  }
  resolveEsm(from, to) {
    if (!to) return from;
    return this.resolveCached(from, to, this.esmCache, this.esmResolveOptions);
  }
  resolveEsmAsync(from, to) {
    if (!to) return Promise.resolve(from);
    return this.resolver.resolveModuleAsync(from, to, this.esmResolveOptions);
  }
  getCjsModuleId(virtualMocks, from, moduleName) {
    return this.resolver.getModuleID(virtualMocks, from, moduleName, this.cjsResolveOptions);
  }
  getEsmModuleId(virtualMocks, from, moduleName) {
    return this.resolver.getModuleID(virtualMocks, from, moduleName, this.esmResolveOptions);
  }
  getEsmModuleIdAsync(virtualMocks, from, moduleName) {
    return this.resolver.getModuleIDAsync(virtualMocks, from, moduleName, this.esmResolveOptions);
  }
  getCjsMockModule(from, moduleName) {
    return this.resolver.getMockModule(from, moduleName, this.cjsResolveOptions);
  }
  getEsmMockModule(from, moduleName) {
    return this.resolver.getMockModule(from, moduleName, this.esmResolveOptions);
  }
  getEsmMockModuleAsync(from, moduleName) {
    return this.resolver.getMockModuleAsync(from, moduleName, this.esmResolveOptions);
  }

  // Resolves the manual mock module path from a (potentially aliased) module
  // name. Covers three shapes:
  //
  // A. Core module specifier i.e. ['fs', 'node:fs']:
  //    Normalize then check for a root manual mock '<rootDir>/__mocks__/'.
  //
  // B. Node module specifier i.e. ['jest', 'react']:
  //    Look for root manual mock.
  //
  // C. Relative/Absolute path:
  //    If the actual module file has a __mocks__ dir sitting immediately next
  //    to it, look to see if there is a manual mock for this file.
  //
  //      subDir1/my_module.js
  //      subDir1/__mocks__/my_module.js
  //      subDir2/my_module.js
  //      subDir2/__mocks__/my_module.js
  //
  //    Where some other module does a relative require into each of the
  //    respective subDir{1,2} directories and expects a manual mock
  //    corresponding to that particular my_module.js file.
  findManualMock(from, moduleName) {
    return this.findManualMockCached(from, moduleName, 'cjs', undefined);
  }

  // The ESM variant resolves the root-name mock and the sibling directory
  // with import conditions; a caller that already resolved the target passes
  // it so the sibling lookup matches the file the mock replaces.
  findEsmManualMock(from, moduleName, resolvedPath) {
    return this.findManualMockCached(from, moduleName, 'esm', resolvedPath);
  }
  findManualMockCached(from, moduleName, mode, resolvedPath) {
    // Normalize core specifiers (`node:fs` → `fs`) before building the cache
    // key so the two forms share an entry instead of populating two.
    const canonicalName = this.isCoreModule(moduleName) ? this.normalizeCoreModuleSpecifier(moduleName) : moduleName;
    const cacheKey = `${mode}\0${from}\0${canonicalName}\0${resolvedPath ?? ''}`;
    let result = this.manualMockCache.get(cacheKey);
    if (result === undefined) {
      result = this.computeManualMock(from, canonicalName, mode, resolvedPath);
      this.manualMockCache.set(cacheKey, result);
    }
    return result;
  }
  computeManualMock(from, moduleName, mode, resolvedPath) {
    const getMockModule = mode === 'cjs' ? name => this.getCjsMockModule(from, name) : name => this.getEsmMockModule(from, name);
    if (this.isCoreModule(moduleName)) {
      return getMockModule(moduleName);
    }
    const rootMock = getMockModule(moduleName);
    if (rootMock) return rootMock;
    const modulePath = resolvedPath ?? (mode === 'cjs' ? this.resolveCjs(from, moduleName) : this.resolveEsm(from, moduleName));
    const sibling = path().join(path().dirname(modulePath), '__mocks__', path().basename(modulePath));
    return fs().existsSync(sibling) ? sibling : null;
  }
  resolveCjsStub(from, moduleName) {
    return this.resolver.resolveStubModuleName(from, moduleName, this.cjsResolveOptions);
  }
  getModulePaths(from) {
    return this.resolver.getModulePaths(from);
  }
  getGlobalPaths(moduleName) {
    return this.resolver.getGlobalPaths(moduleName);
  }
  isCoreModule(name) {
    return this.resolver.isCoreModule(name);
  }
  normalizeCoreModuleSpecifier(name) {
    return this.resolver.normalizeCoreModuleSpecifier(name);
  }
  getModule(name) {
    return this.resolver.getModule(name);
  }
  getModulePath(from, moduleName) {
    return this.resolver.getModulePath(from, moduleName);
  }
  canResolveSync() {
    return this.resolver.canResolveSync();
  }
  hasDistinctAsyncResolver() {
    return this.resolver.hasDistinctAsyncResolver();
  }
  resolveCjsFromDirIfExists(dir, name, paths) {
    return this.resolver.resolveModuleFromDirIfExists(dir, name, {
      conditions: this.cjsConditions,
      paths
    });
  }
  clear() {
    this.cjsCache.clear();
    this.esmCache.clear();
    this.manualMockCache.clear();
  }
  resolveCached(from, to, cache, resolveOptions) {
    const key = `${from}\0${to}`;
    const cached = cache.get(key);
    if (cached !== undefined) return cached;
    const resolved = this.resolver.resolveModule(from, to, resolveOptions);
    cache.set(key, resolved);
    return resolved;
  }
}
exports.Resolution = Resolution;

/***/ },

/***/ "./src/internals/TestMainModule.ts"
(__unused_webpack_module, exports) {



Object.defineProperty(exports, "__esModule", ({
  value: true
}));
exports.TestMainModule = void 0;
/**
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

// Shared cell for `require.main`: the executor writes it when the test file
// loads, the require-builder reads it when attaching `module.require`.
class TestMainModule {
  current = null;
  reset() {
    this.current = null;
  }
}
exports.TestMainModule = TestMainModule;

/***/ },

/***/ "./src/internals/TestState.ts"
(__unused_webpack_module, exports) {



Object.defineProperty(exports, "__esModule", ({
  value: true
}));
exports.TestState = void 0;
/**
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

class TestState {
  state = 'loading';
  logFormattedReferenceError;
  constructor(logFormattedReferenceError) {
    this.logFormattedReferenceError = logFormattedReferenceError;
  }
  isTornDown() {
    return this.state === 'tornDown';
  }

  /**
   * Logs a post-teardown reference error and sets `process.exitCode = 1` if
   * the runtime has been torn down. Returns `true` if the caller should bail
   * out (i.e. it was torn down), `false` otherwise.
   */
  bailIfTornDown(message) {
    if (this.state !== 'tornDown') return false;
    this.logFormattedReferenceError(message);
    process.exitCode = 1;
    return true;
  }

  /**
   * Like {@link bailIfTornDown}, but throws a `ReferenceError` with the same
   * `message` instead of returning a flag. Use at call sites that can't bail
   * with `return` (e.g. inside an `async` function whose return type does not
   * allow `void`/`null`).
   */
  throwIfTornDown(message) {
    if (this.bailIfTornDown(message)) {
      throw new ReferenceError(message);
    }
  }
  throwIfBetweenTests(message) {
    if (this.state === 'betweenTests') {
      throw new ReferenceError(message);
    }
  }
  enterTestCode() {
    this.state = 'inTest';
  }
  leaveTestCode() {
    this.state = 'betweenTests';
  }
  teardown() {
    this.state = 'tornDown';
  }
}
exports.TestState = TestState;

/***/ },

/***/ "./src/internals/TransformCache.ts"
(__unused_webpack_module, exports) {



Object.defineProperty(exports, "__esModule", ({
  value: true
}));
exports.TransformCache = void 0;
function _stripBom() {
  const data = _interopRequireDefault(require("strip-bom"));
  _stripBom = function () {
    return data;
  };
  return data;
}
function _interopRequireDefault(e) { return e && e.__esModule ? e : { default: e }; }
/**
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

class TransformCache {
  scriptTransformer;
  fileCache;
  getFullTransformationOptions;
  transforms = new Map();
  mutex = new Map();
  sourceMaps = new Map();
  constructor(scriptTransformer, fileCache, getFullTransformationOptions) {
    this.scriptTransformer = scriptTransformer;
    this.fileCache = fileCache;
    this.getFullTransformationOptions = getFullTransformationOptions;
  }
  transform(filename, options) {
    const source = this.fileCache.readFile(filename);
    if (options?.isInternalModule) return source;
    const transformedFile = this.scriptTransformer.transform(filename, this.getFullTransformationOptions(options), source);
    this.transforms.set(filename, transformedFile);
    if (transformedFile.sourceMapPath) {
      this.sourceMaps.set(filename, transformedFile.sourceMapPath);
    }
    return transformedFile.code;
  }
  async transformAsync(filename, options) {
    const source = this.fileCache.readFile(filename);
    if (options?.isInternalModule) return source;
    const transformedFile = await this.scriptTransformer.transformAsync(filename, this.getFullTransformationOptions(options), source);
    this.transforms.set(filename, transformedFile);
    if (transformedFile.sourceMapPath) {
      this.sourceMaps.set(filename, transformedFile.sourceMapPath);
    }
    return transformedFile.code;
  }
  canTransformSync(filename) {
    return this.scriptTransformer.canTransformSync(filename);
  }
  getCachedSource(filename) {
    return this.transforms.get(filename)?.code;
  }

  // Reads + transforms a `.json` file's source, returning the transformed
  // text (still a string). Caller is responsible for `JSON.parse`-ing in the
  // appropriate realm.
  transformJson(filename, options) {
    const source = (0, _stripBom().default)(this.fileCache.readFile(filename));
    return this.scriptTransformer.transformJson(filename, this.getFullTransformationOptions(options), source);
  }
  getEntries() {
    return this.transforms;
  }
  getSourceMaps() {
    return this.sourceMaps;
  }

  // Mutex deduplicates parallel `transformAsync`s of the same module across
  // concurrent `loadEsmModule` calls on the legacy async path. Goes away
  // once min-Node ≥ v24.9 makes that path obsolete.
  hasMutex(key) {
    return this.mutex.has(key);
  }
  awaitMutex(key) {
    return this.mutex.get(key);
  }
  setMutex(key, promise) {
    this.mutex.set(key, promise);
  }
  clearMutex(key) {
    this.mutex.delete(key);
  }

  // Source maps outlive both `resetModules` and `teardown`: a stack formatted
  // afterwards — a stray timer, a floating promise — has nowhere else to
  // resolve from, and the registry holds only path strings.
  clear() {
    this.transforms.clear();
    this.mutex.clear();
  }
}
exports.TransformCache = TransformCache;

/***/ },

/***/ "./src/internals/V8CoverageCollector.ts"
(__unused_webpack_module, exports) {



Object.defineProperty(exports, "__esModule", ({
  value: true
}));
exports.V8CoverageCollector = void 0;
function _nodeUrl() {
  const data = require("node:url");
  _nodeUrl = function () {
    return data;
  };
  return data;
}
function _collectV8Coverage() {
  const data = require("collect-v8-coverage");
  _collectV8Coverage = function () {
    return data;
  };
  return data;
}
function _transform() {
  const data = require("@jest/transform");
  _transform = function () {
    return data;
  };
  return data;
}
/**
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

class V8CoverageCollector {
  coverageOptions;
  config;
  transformCache;
  instrumenter;
  result;
  sources;
  constructor(coverageOptions, config, transformCache) {
    this.coverageOptions = coverageOptions;
    this.config = config;
    this.transformCache = transformCache;
  }
  async start() {
    this.instrumenter = new (_collectV8Coverage().CoverageInstrumenter)();
    this.sources = new Map();
    await this.instrumenter.startInstrumenting();
  }
  async stop() {
    if (!this.instrumenter || !this.sources) {
      throw new Error('You need to call `collectV8Coverage` first.');
    }
    this.result = await this.instrumenter.stopInstrumenting();
    this.sources = new Map([...this.sources, ...this.transformCache.getEntries()]);
  }

  // Snapshot transforms about to be cleared (e.g. by `resetModules`) so the
  // mapping from URL to transformed source survives across the reset.
  snapshotTransforms() {
    if (!this.coverageOptions.collectCoverage || this.coverageOptions.coverageProvider !== 'v8' || !this.sources) {
      return;
    }
    this.sources = new Map([...this.sources, ...this.transformCache.getEntries()]);
  }
  getResult() {
    if (!this.result || !this.sources) {
      throw new Error('You need to call `stopCollectingV8Coverage` first.');
    }
    const sources = this.sources;
    const loadedFilenames = [...sources.keys()];
    return this.result.filter(res => res.url.startsWith('file://')).map(res => ({
      ...res,
      url: (0, _nodeUrl().fileURLToPath)(res.url)
    })).filter(res =>
    // TODO: will this work on windows? It might be better if `shouldInstrument` deals with it anyways
    res.url.startsWith(this.coverageOptions.globalRootDir ?? this.config.rootDir) && (0, _transform().shouldInstrument)(res.url, this.coverageOptions, this.config, loadedFilenames)).map(result => ({
      codeTransformResult: sources.get(result.url),
      result
    }));
  }
  reset() {
    this.sources?.clear();
    this.result = undefined;
    this.instrumenter = undefined;
  }
}
exports.V8CoverageCollector = V8CoverageCollector;

/***/ },

/***/ "./src/internals/cjsRequire.ts"
(__unused_webpack_module, exports, __webpack_require__) {



Object.defineProperty(exports, "__esModule", ({
  value: true
}));
exports.RequireBuilder = exports.JEST_RESOLVE_OUTSIDE_VM_OPTION = exports.CoreModuleProvider = void 0;
function _nodeModule() {
  const data = _interopRequireDefault(require("node:module"));
  _nodeModule = function () {
    return data;
  };
  return data;
}
function path() {
  const data = _interopRequireWildcard(require("node:path"));
  path = function () {
    return data;
  };
  return data;
}
function _nodeUrl() {
  const data = require("node:url");
  _nodeUrl = function () {
    return data;
  };
  return data;
}
function _jestResolve() {
  const data = _interopRequireDefault(require("jest-resolve"));
  _jestResolve = function () {
    return data;
  };
  return data;
}
function _jestUtil() {
  const data = require("jest-util");
  _jestUtil = function () {
    return data;
  };
  return data;
}
var _helpers = __webpack_require__("./src/helpers.ts");
var _moduleTypes = __webpack_require__("./src/internals/moduleTypes.ts");
function _interopRequireWildcard(e, t) { if ("function" == typeof WeakMap) var r = new WeakMap(), n = new WeakMap(); return (_interopRequireWildcard = function (e, t) { if (!t && e && e.__esModule) return e; var o, i, f = { __proto__: null, default: e }; if (null === e || "object" != typeof e && "function" != typeof e) return f; if (o = t ? n : r) { if (o.has(e)) return o.get(e); o.set(e, f); } for (const t in e) "default" !== t && {}.hasOwnProperty.call(e, t) && ((i = (o = Object.defineProperty) && Object.getOwnPropertyDescriptor(e, t)) && (i.get || i.set) ? o(f, t, i) : f[t] = e[t]); return f; })(e, t); }
function _interopRequireDefault(e) { return e && e.__esModule ? e : { default: e }; }
/**
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

// Node hands every string with a `file:` scheme to the URL parser, which
// matches the scheme case-insensitively and normalizes missing slashes and a
// `localhost` authority away.
const fileUrlSchemeRegex = /^file:/i;
const JEST_RESOLVE_OUTSIDE_VM_OPTION = exports.JEST_RESOLVE_OUTSIDE_VM_OPTION = Symbol.for('jest-resolve-outside-vm-option');
class RequireBuilder {
  resolution;
  registries;
  testMainModule;
  requireDispatch;
  requireInternal;
  constructor(options) {
    this.resolution = options.resolution;
    this.registries = options.registries;
    this.testMainModule = options.testMainModule;
    this.requireDispatch = options.requireDispatch;
    this.requireInternal = options.requireInternal;
  }
  for(from, options) {
    const resolveImpl = (moduleName, resolveOptions) => {
      const resolved = this.resolve(from.filename, moduleName, resolveOptions);
      if (resolveOptions?.[JEST_RESOLVE_OUTSIDE_VM_OPTION] && options?.isInternalModule) {
        return (0, _helpers.createOutsideJestVmPath)(resolved);
      }
      return resolved;
    };
    resolveImpl.paths = moduleName => this.resolvePaths(from.filename, moduleName);
    const moduleRequire = options?.isInternalModule ? moduleName => this.requireInternal(from.filename, moduleName) : moduleName => this.requireDispatch(from.filename, moduleName);
    moduleRequire.extensions = Object.create(null);
    moduleRequire.resolve = resolveImpl;
    moduleRequire.cache = this.registries.getRequireCacheProxy();

    // A getter, not a snapshot: the test file's own require object is built
    // before the executor assigns the main module, so a captured value would
    // leave the entry point's require.main null.
    Object.defineProperty(moduleRequire, 'main', {
      enumerable: true,
      get: () => this.testMainModule.current
    });
    return moduleRequire;
  }
  forFilename(filename) {
    return this.for((0, _moduleTypes.createInitialModule)(filename), undefined);
  }
  resolve(from, moduleName, options = {}) {
    if (moduleName == null) {
      throw new Error('The first argument to require.resolve must be a string. Received null or undefined.');
    }
    if (path().isAbsolute(moduleName)) {
      const module = this.resolution.resolveCjsFromDirIfExists(moduleName, moduleName, []);
      if (module) {
        return module;
      }
    } else if (options.paths) {
      const module = this.resolution.resolveCjsStub(from, moduleName);
      if (module) {
        return module;
      }
      for (const searchPath of options.paths) {
        const absolutePath = path().resolve(from, '..', searchPath);

        // required to also resolve files without leading './' directly in the path
        const module = this.resolution.resolveCjsFromDirIfExists(absolutePath, moduleName, [absolutePath]);
        if (module) {
          return module;
        }
      }
      throw new (_jestResolve().default.ModuleNotFoundError)(`Cannot resolve module '${moduleName}' from paths ['${options.paths.join("', '")}'] from ${from}`);
    }
    try {
      return this.resolution.resolveCjs(from, moduleName);
    } catch (error) {
      const module = this.resolution.getCjsMockModule(from, moduleName);
      if (module) {
        return module;
      }
      throw error;
    }
  }
  resolvePaths(from, moduleName) {
    const fromDir = path().resolve(from, '..');
    if (moduleName == null) {
      throw new Error('The first argument to require.resolve.paths must be a string. Received null or undefined.');
    }
    if (moduleName.length === 0) {
      throw new Error('The first argument to require.resolve.paths must not be the empty string.');
    }
    if (moduleName[0] === '.') {
      return [fromDir];
    }
    if (this.resolution.isCoreModule(moduleName)) {
      return null;
    }
    const modulePaths = this.resolution.getModulePaths(fromDir);
    const globalPaths = this.resolution.getGlobalPaths(moduleName);
    return [...modulePaths, ...globalPaths];
  }
}
exports.RequireBuilder = RequireBuilder;
class CoreModuleProvider {
  mockedModuleClass;
  resolution;
  environment;
  requireBuilder;
  constructor(options) {
    this.resolution = options.resolution;
    this.environment = options.environment;
    this.requireBuilder = options.requireBuilder;
  }
  require(moduleName) {
    const moduleWithoutNodePrefix = this.resolution.normalizeCoreModuleSpecifier(moduleName);
    if (moduleWithoutNodePrefix === 'process') {
      return this.environment.global.process;
    }
    if (moduleWithoutNodePrefix === 'module') {
      return this.getMockedModuleClass();
    }
    const coreModule = require(moduleName);
    (0, _jestUtil().protectProperties)(coreModule);
    return coreModule;
  }
  getMockedModuleClass() {
    if (this.mockedModuleClass) {
      return this.mockedModuleClass;
    }
    const createRequire = modulePath => {
      const filename = typeof modulePath === 'string' ? fileUrlSchemeRegex.test(modulePath) ? (0, _nodeUrl().fileURLToPath)(new (_nodeUrl().URL)(modulePath)) : modulePath : (0, _nodeUrl().fileURLToPath)(modulePath);
      if (!path().isAbsolute(filename)) {
        const error = new TypeError(`The argument 'filename' must be a file URL object, file URL string, or absolute path string. Received '${filename}'`);
        error.code = 'ERR_INVALID_ARG_TYPE';
        throw error;
      }
      return this.requireBuilder.forFilename(filename);
    };
    class Module extends _nodeModule().default.Module {}
    for (const [key, value] of Object.entries(_nodeModule().default.Module)) {
      // @ts-expect-error: no index signature
      Module[key] = value;
    }
    Module.Module = Module;
    if ('createRequire' in _nodeModule().default) {
      Module.createRequire = createRequire;
    }
    if ('syncBuiltinESMExports' in _nodeModule().default) {
      // cast since TS seems very confused about whether it exists or not
      Module.syncBuiltinESMExports =
      // eslint-disable-next-line @typescript-eslint/no-empty-function
      function syncBuiltinESMExports() {};
    }

    // Customization hooks attach to the loader running Jest itself, never to
    // the sandboxed require/import test code uses - and they stay registered
    // for every later test file in the worker. Fail loudly instead.
    for (const hookRegistrar of ['register', 'registerHooks']) {
      if (hookRegistrar in _nodeModule().default) {
        // @ts-expect-error: no index signature
        Module[hookRegistrar] = function throwHooksUnsupported() {
          throw new Error(`module.${hookRegistrar}() is not supported in Jest: the hooks would attach to the module loader running Jest itself, not to the sandboxed require/import used by test code, and would stay registered for every later test file in this worker. Use Jest's own customization points (transform, resolver, moduleNameMapper) instead.`);
        };
      }
    }
    this.mockedModuleClass = Module;
    return Module;
  }
  reset() {
    this.mockedModuleClass = undefined;
  }
}
exports.CoreModuleProvider = CoreModuleProvider;

/***/ },

/***/ "./src/internals/dataUri.ts"
(__unused_webpack_module, exports) {



Object.defineProperty(exports, "__esModule", ({
  value: true
}));
exports.parseDataUri = parseDataUri;
/**
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

// Decode a `data:` URI specifier into its mime type and decoded code/body.
// `application/wasm` returns a Buffer; everything else returns a UTF-8 string.
const dataURIRegex = /^data:(?<mime>[^;,]*)(?<parameters>(?:;[^;,]*)*),(?<code>.*)$/;

// Node's own mediatype extraction (lib/internal/modules/esm/load.js) - the
// capture is both the format-decision input and what the rejection message
// echoes, and a failed capture reports the literal string "null".
const nodeMediatypeRegex = /^data:([^/]+\/[^;,]+)[^,]*,/;

// Node's mimeToFormat: the JavaScript mime tolerates surrounding spaces and
// matches case-insensitively (text/ and application/ alike), while
// application/json and application/wasm require an exact match.
const javaScriptMimeRegex = /^ *(?:text|application)\/javascript *$/i;
function makeInvalidUrlError() {
  const error = new TypeError('Invalid URL');
  error.code = 'ERR_INVALID_URL';
  return error;
}

// The `data-urls` package implements the full WHATWG data URL processor,
// but it is too heavy for this limited use case - it drags in `whatwg-url`
// and its Unicode tables. See https://github.com/jsdom/data-urls/issues/7.

// The WHATWG forgiving percent-decode: valid %XX escapes decode to their
// byte, anything else passes through as its UTF-8 bytes instead of throwing.
// Literal spans encode in one operation each, so an escape-free payload is a
// single allocation.
function forgivingPercentDecode(input) {
  if (!input.includes('%')) {
    return Buffer.from(input, 'utf8');
  }
  const chunks = [];
  let literalStart = 0;
  let index = 0;
  while (index < input.length) {
    if (input[index] === '%' && /^[0-9A-Fa-f]{2}$/.test(input.slice(index + 1, index + 3))) {
      if (literalStart < index) {
        chunks.push(Buffer.from(input.slice(literalStart, index), 'utf8'));
      }
      chunks.push(Buffer.of(Number.parseInt(input.slice(index + 1, index + 3), 16)));
      index += 3;
      literalStart = index;
    } else {
      index++;
    }
  }
  if (literalStart < input.length) {
    chunks.push(Buffer.from(input.slice(literalStart), 'utf8'));
  }
  return Buffer.concat(chunks);
}

// The WHATWG forgiving base64: ASCII whitespace is stripped, up to two
// trailing `=` are allowed, and anything else outside the base64 alphabet
// (or a leftover length of 1 mod 4) is an invalid URL.
function forgivingBase64Decode(input) {
  let data = input.replaceAll(/[\t\n\f\r ]/g, '');
  if (data.length % 4 === 0) {
    data = data.replace(/={1,2}$/, '');
  }
  if (data.length % 4 === 1 || !/^[A-Za-z0-9+/]*$/.test(data)) {
    throw makeInvalidUrlError();
  }
  return Buffer.from(data, 'base64');
}
function parseDataUri(specifier) {
  // The URL parser strips ASCII tab and newline from the input entirely, and
  // the fragment starts at the first # - a fragment before the comma leaves
  // the data: URL without a payload at all.
  const serialized = specifier.replaceAll(/[\t\n\r]/g, '').split('#', 1)[0];
  const match = serialized.match(dataURIRegex);
  if (!match || !match.groups) {
    throw makeInvalidUrlError();
  }
  // The payload decodes before the format check, so an invalid body wins
  // over an unknown mime type. Mediatype parameters are case-insensitive and
  // unknown ones are ignored; base64 applies only as the final parameter.
  const parameters = match.groups.parameters.split(';').slice(1);
  // Spaces are the only whitespace that can surround the token: the URL
  // parser strips tab and newline and percent-encodes everything else.
  const isBase64 = parameters.at(-1)?.replaceAll(/^ +| +$/g, '').toLowerCase() === 'base64';
  const decodedBody = isBase64 ? forgivingBase64Decode(forgivingPercentDecode(match.groups.code).toString()) : forgivingPercentDecode(match.groups.code);
  const mediatype = serialized.match(nodeMediatypeRegex)?.[1] ?? null;
  let mime = null;
  if (mediatype !== null) {
    if (javaScriptMimeRegex.test(mediatype)) {
      mime = 'text/javascript';
    } else if (mediatype === 'application/json' || mediatype === 'application/wasm') {
      mime = mediatype;
    }
  }
  if (mime === null) {
    const error = new RangeError(`Unknown module format: ${mediatype} for URL ${specifier}`);
    error.code = 'ERR_UNKNOWN_MODULE_FORMAT';
    throw error;
  }
  if (mime === 'application/wasm') {
    if (parameters.length === 0) throw new Error('Missing data URI encoding');
    if (!isBase64) {
      throw new Error(`Invalid data URI encoding: ${parameters.join(';')}`);
    }
    return {
      code: decodedBody,
      mime
    };
  }
  return {
    code: decodedBody.toString(),
    mime
  };
}

/***/ },

/***/ "./src/internals/esmLexer.ts"
(__unused_webpack_module, exports) {



Object.defineProperty(exports, "__esModule", ({
  value: true
}));
exports.hasEsmSyntax = hasEsmSyntax;
function _esModuleLexer() {
  const data = require("es-module-lexer");
  _esModuleLexer = function () {
    return data;
  };
  return data;
}
/**
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

let initialized = false;

// Returns true when `source` contains ESM syntax (import/export declarations).
// Returns false if es-module-lexer itself throws (malformed/incomplete source).
// Lazily initialises the WASM backing on first call.
function hasEsmSyntax(source) {
  if (!initialized) {
    (0, _esModuleLexer().initSync)();
    initialized = true;
  }
  try {
    return (0, _esModuleLexer().parse)(source)[3];
  } catch {
    // parse() throws on syntax errors it cannot recover from. We cannot
    // determine whether the file is ESM — and native ESM would also fail —
    // so return false and let the original CJS error surface.
    return false;
  }
}

/***/ },

/***/ "./src/internals/importAttributes.ts"
(__unused_webpack_module, exports) {



Object.defineProperty(exports, "__esModule", ({
  value: true
}));
exports.ImportAttributeValidator = void 0;
/**
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

// Soft cap so a long-lived validator (watch mode, --runInBand) can't grow the
// warned set without bound. When we hit it we drop everything; users see at
// most one extra repeated warning per pair, which is benign.
const MAX_WARNED_PAIRS = 10_000;
function isJsonModule(modulePath) {
  return modulePath.endsWith('.json') || modulePath.startsWith('data:application/json');
}

// Avoid dumping the full payload of data: URIs (or other very long specifiers)
// into stderr.
function describeForWarning(modulePath) {
  if (modulePath.startsWith('data:')) {
    const comma = modulePath.indexOf(',');
    if (comma > 0) return `${modulePath.slice(0, comma)},…`;
  }
  return modulePath;
}
function makeImportAttributeError(code, message) {
  const error = new TypeError(message);
  error.code = code;
  return error;
}

// Mirrors Node's `validateAttributes` in lib/internal/modules/esm/assert.js.
// The only deliberate divergence: missing `type: 'json'` warns instead of
// throwing — see the JSON branch below. The warned-pairs set lives on the
// instance, so each Runtime (one per test file) warns anew.
class ImportAttributeValidator {
  warnedMissingJsonAttributePairs = new Set();
  validate(modulePath, attributes, referencingIdentifier) {
    for (const key of Object.keys(attributes)) {
      if (key !== 'type') {
        throw makeImportAttributeError('ERR_IMPORT_ATTRIBUTE_UNSUPPORTED', `Import attribute "${key}" with value "${attributes[key]}" is not supported (importing "${modulePath}" from ${referencingIdentifier})`);
      }
    }
    const declaredType = attributes.type;
    const isJson = isJsonModule(modulePath);
    if (isJson) {
      if (declaredType === undefined) {
        // TODO(jest next major): match Node and throw
        // ERR_IMPORT_ATTRIBUTE_MISSING here. Until then, warn so existing users
        // without `with { type: 'json' }` keep working.
        const dedupeKey = `${referencingIdentifier}::${modulePath}`;
        if (!this.warnedMissingJsonAttributePairs.has(dedupeKey)) {
          if (this.warnedMissingJsonAttributePairs.size >= MAX_WARNED_PAIRS) {
            this.warnedMissingJsonAttributePairs.clear();
          }
          this.warnedMissingJsonAttributePairs.add(dedupeKey);
          const moduleLabel = describeForWarning(modulePath);
          console.warn('Jest: importing JSON without an import attribute is deprecated and will be a hard error in the next major. ' + `Update the import of "${moduleLabel}" (from ${referencingIdentifier}): ` + "use `with { type: 'json' }` for static imports, or pass " + "`{ with: { type: 'json' } }` as the second argument to dynamic `import()`.");
        }
        return;
      }
      if (declaredType !== 'json') {
        throw makeImportAttributeError('ERR_IMPORT_ATTRIBUTE_TYPE_INCOMPATIBLE', `Module "${modulePath}" is not of type "${declaredType}"`);
      }
      return;
    }

    // Non-JSON (implicit-type) module. Per HTML spec, the default type cannot
    // be re-asserted, so any explicit `type` attribute is rejected.
    if (declaredType !== undefined) {
      throw makeImportAttributeError('ERR_IMPORT_ATTRIBUTE_TYPE_INCOMPATIBLE', `Module "${modulePath}" is not of type "${declaredType}"`);
    }
  }
}
exports.ImportAttributeValidator = ImportAttributeValidator;

/***/ },

/***/ "./src/internals/moduleTypes.ts"
(__unused_webpack_module, exports) {



Object.defineProperty(exports, "__esModule", ({
  value: true
}));
exports.createInitialModule = createInitialModule;
function path() {
  const data = _interopRequireWildcard(require("node:path"));
  path = function () {
    return data;
  };
  return data;
}
function _interopRequireWildcard(e, t) { if ("function" == typeof WeakMap) var r = new WeakMap(), n = new WeakMap(); return (_interopRequireWildcard = function (e, t) { if (!t && e && e.__esModule) return e; var o, i, f = { __proto__: null, default: e }; if (null === e || "object" != typeof e && "function" != typeof e) return f; if (o = t ? n : r) { if (o.has(e)) return o.get(e); o.set(e, f); } for (const t in e) "default" !== t && {}.hasOwnProperty.call(e, t) && ((i = (o = Object.defineProperty) && Object.getOwnPropertyDescriptor(e, t)) && (i.get || i.set) ? o(f, t, i) : f[t] = e[t]); return f; })(e, t); }
/**
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

// Registered before evaluation so a cycle reached mid-evaluation resolves to
// the same object.
function createInitialModule(filename) {
  return {
    children: [],
    exports: {},
    filename,
    id: filename,
    isPreloading: false,
    loaded: false,
    path: path().dirname(filename)
  };
}

/***/ },

/***/ "./src/internals/nodeCapabilities.ts"
(__unused_webpack_module, exports) {



Object.defineProperty(exports, "__esModule", ({
  value: true
}));
exports.supportsSyncEvaluate = exports.runtimeSupportsVmModules = void 0;
function _nodeVm() {
  const data = require("node:vm");
  _nodeVm = function () {
    return data;
  };
  return data;
}
/**
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

const runtimeSupportsVmModules = exports.runtimeSupportsVmModules = typeof _nodeVm().SyntheticModule === 'function';
const supportsSyncEvaluate = exports.supportsSyncEvaluate =
// @ts-expect-error - `hasAsyncGraph` is in Node v24.9+, not yet typed in @types/node@18
typeof _nodeVm().SourceTextModule?.prototype.hasAsyncGraph === 'function';

/***/ },

/***/ "./src/internals/syntheticBuilders.ts"
(__unused_webpack_module, exports, __webpack_require__) {



Object.defineProperty(exports, "__esModule", ({
  value: true
}));
exports.buildCjsAsEsmSyntheticModule = buildCjsAsEsmSyntheticModule;
exports.buildCoreSyntheticModule = buildCoreSyntheticModule;
exports.buildJsonSyntheticModule = buildJsonSyntheticModule;
exports.buildWasmSyntheticModule = buildWasmSyntheticModule;
exports.evaluateSyntheticModule = evaluateSyntheticModule;
exports.syntheticFromExports = syntheticFromExports;
function _nodeVm() {
  const data = require("node:vm");
  _nodeVm = function () {
    return data;
  };
  return data;
}
function _jestUtil() {
  const data = require("jest-util");
  _jestUtil = function () {
    return data;
  };
  return data;
}
var _helpers = __webpack_require__("./src/helpers.ts");
/**
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

// Build a SyntheticModule from a plain exports record. The set of names and
// the value *references* are snapshotted at construction time, so later
// `exportsObject[k] = v` re-assignments or key add/delete won't leak into
// `evaluate()`. This is a shallow snapshot - mutating an exported object
// (`exportsObject.x.foo = ...`) is still observable through `setExport`.
function syntheticFromExports(identifier, context, exportsObject) {
  const entries = Object.entries(exportsObject);
  return new (_nodeVm().SyntheticModule)(entries.map(([key]) => key), function () {
    for (const [key, value] of entries) {
      this.setExport(key, value);
    }
  }, {
    context,
    identifier
  });
}

// `parseJson` is the importing realm's `JSON.parse` - a host-realm parse
// would hand the test objects whose prototype fails `instanceof Object`
// inside the VM context.
function buildJsonSyntheticModule(jsonText, identifier, context, parseJson) {
  // The parse runs in the body so a parse error surfaces during evaluate(),
  // matching Node's native JSON-module semantics.
  return new (_nodeVm().SyntheticModule)(['default'], function () {
    this.setExport('default', parseJson(jsonText));
  }, {
    context,
    identifier
  });
}

// The body reads each import's namespace via `getDepNamespace`, which both the
// sync graph (closure over `scratch`) and the legacy path (closure over a
// pre-built `moduleLookup`) supply.
function buildWasmSyntheticModule(wasmModule, identifier, context, getDepNamespace) {
  const exports = WebAssembly.Module.exports(wasmModule);
  const imports = WebAssembly.Module.imports(wasmModule);
  return new (_nodeVm().SyntheticModule)(exports.map(({
    name
  }) => name), function () {
    const importsObject = {};
    for (const {
      module: depSpec,
      name
    } of imports) {
      if (!importsObject[depSpec]) {
        importsObject[depSpec] = {};
      }
      const namespace = getDepNamespace(depSpec);
      importsObject[depSpec][name] = namespace[name];
    }
    const wasmInstance = new WebAssembly.Instance(wasmModule, importsObject);
    for (const {
      name
    } of exports) {
      this.setExport(name, wasmInstance.exports[name]);
    }
  }, {
    context,
    identifier
  });
}
function buildCoreSyntheticModule(moduleName, context, requireCoreModule) {
  const required = requireCoreModule(moduleName);
  // should identifier be `node://${moduleName}`?
  return syntheticFromExports(moduleName, context, {
    ...required,
    default: required
  });
}

// Builds a SyntheticModule wrapping a CJS module's `module.exports` for
// import-from-ESM. Merges cjs-module-lexer's static export list with the
// runtime keys of the actual exports object (lexer can miss
// `Object.assign`-style patterns).
function buildCjsAsEsmSyntheticModule(from, modulePath, context, requireModuleOrMock, cjsExportsCache) {
  const cjs = requireModuleOrMock(from, modulePath);
  const parsedExports = cjsExportsCache.getExportsOf(from, modulePath);

  // CJS modules can legally set `module.exports` to `null` or a primitive.
  // Functions are also valid (e.g. `module.exports = fn; fn.helper = ...`).
  const cjsRecord = cjs !== null && (typeof cjs === 'object' || typeof cjs === 'function') ? cjs : null;
  const allCandidates = new Set([...parsedExports, ...(cjsRecord ? Object.keys(cjsRecord) : [])]);
  const cjsExports = [...allCandidates].filter(exportName => {
    // `default` and `module.exports` are handled separately below as the
    // whole module.exports.
    if (exportName === 'default' || exportName === 'module.exports' || cjsRecord == null) {
      return false;
    }
    return Object.hasOwn(cjsRecord, exportName);
  });
  return new (_nodeVm().SyntheticModule)([...cjsExports, 'default', 'module.exports'], function () {
    if (cjsRecord != null) {
      for (const exportName of cjsExports) {
        this.setExport(exportName, Reflect.get(cjsRecord, exportName));
      }
    }
    // module.exports is the ESM default, matching Node's CJS-from-ESM behavior.
    // __esModule is not honored — see Node docs on named exports from CJS.
    this.setExport('default', cjs);
    this.setExport('module.exports', cjs);
  }, {
    context,
    identifier: modulePath
  });
}

// On Node v22.21+ / v24.8+ a SyntheticModule starts in `'linked'` and the
// body runs synchronously even though `evaluate()` returns a Promise -
// return it sync so callers can store the actual module rather than a
// Promise that can poison the registry if microtask draining stalls. On
// older Node it starts `'unlinked'` and link/evaluate are genuinely async;
// fall back to the async path there (the async-only legacy ESM code paths
// handle the Promise return fine, and sync `require(esm)` doesn't exist on
// those versions anyway).
function evaluateSyntheticModule(module) {
  if (module.status === 'unlinked') {
    return evaluateSyntheticModuleAsync(module);
  }
  module.evaluate().catch(_helpers.noop);
  if (module.status === 'errored') {
    throw module.error;
  }
  (0, _jestUtil().invariant)(module.status === 'evaluated', `Synthetic module ${module.identifier} did not evaluate synchronously (status="${module.status}"). This is a bug in Jest, please report it!`);
  return module;
}
async function evaluateSyntheticModuleAsync(module) {
  await module.link(() => {
    throw new Error('This should never happen');
  });
  await module.evaluate();
  return module;
}

/***/ }

/******/ 	});
/************************************************************************/
/******/ 	// The module cache
/******/ 	const __webpack_module_cache__ = {};
/******/ 	
/******/ 	// The require function
/******/ 	function __webpack_require__(moduleId) {
/******/ 		// Check if module is in cache
/******/ 		const cachedModule = __webpack_module_cache__[moduleId];
/******/ 		if (cachedModule !== undefined) {
/******/ 			return cachedModule.exports;
/******/ 		}
/******/ 		// Create a new module (and put it into the cache)
/******/ 		const module = __webpack_module_cache__[moduleId] = {
/******/ 			// no module.id needed
/******/ 			// no module.loaded needed
/******/ 			exports: {}
/******/ 		};
/******/ 	
/******/ 		// Execute the module function
/******/ 		__webpack_modules__[moduleId](module, module.exports, __webpack_require__);
/******/ 	
/******/ 		// Return the exports of the module
/******/ 		return module.exports;
/******/ 	}
/******/ 	
/************************************************************************/
let __webpack_exports__ = {};
// This entry needs to be wrapped in an IIFE because it uses a non-standard name for the exports (exports).
(() => {
let exports = __webpack_exports__;


Object.defineProperty(exports, "__esModule", ({
  value: true
}));
exports["default"] = void 0;
function _nodeModule() {
  const data = _interopRequireDefault(require("node:module"));
  _nodeModule = function () {
    return data;
  };
  return data;
}
function path() {
  const data = _interopRequireWildcard(require("node:path"));
  path = function () {
    return data;
  };
  return data;
}
function _slash() {
  const data = _interopRequireDefault(require("slash"));
  _slash = function () {
    return data;
  };
  return data;
}
function _transform() {
  const data = require("@jest/transform");
  _transform = function () {
    return data;
  };
  return data;
}
function _jestHasteMap() {
  const data = _interopRequireDefault(require("jest-haste-map"));
  _jestHasteMap = function () {
    return data;
  };
  return data;
}
function _jestMessageUtil() {
  const data = require("jest-message-util");
  _jestMessageUtil = function () {
    return data;
  };
  return data;
}
function _jestRegexUtil() {
  const data = require("jest-regex-util");
  _jestRegexUtil = function () {
    return data;
  };
  return data;
}
function _jestResolve() {
  const data = _interopRequireDefault(require("jest-resolve"));
  _jestResolve = function () {
    return data;
  };
  return data;
}
function _jestSnapshot() {
  const data = require("jest-snapshot");
  _jestSnapshot = function () {
    return data;
  };
  return data;
}
function _jestUtil() {
  const data = require("jest-util");
  _jestUtil = function () {
    return data;
  };
  return data;
}
var _helpers = __webpack_require__("./src/helpers.ts");
var _CjsExportsCache = __webpack_require__("./src/internals/CjsExportsCache.ts");
var _CjsLoader = __webpack_require__("./src/internals/CjsLoader.ts");
var _EsmLoader = __webpack_require__("./src/internals/EsmLoader.ts");
var _FileCache = __webpack_require__("./src/internals/FileCache.ts");
var _JestGlobals = __webpack_require__("./src/internals/JestGlobals.ts");
var _MockState = __webpack_require__("./src/internals/MockState.ts");
var _ModuleExecutor = __webpack_require__("./src/internals/ModuleExecutor.ts");
var _ModuleRegistries = __webpack_require__("./src/internals/ModuleRegistries.ts");
var _Resolution = __webpack_require__("./src/internals/Resolution.ts");
var _TestMainModule = __webpack_require__("./src/internals/TestMainModule.ts");
var _TestState = __webpack_require__("./src/internals/TestState.ts");
var _TransformCache = __webpack_require__("./src/internals/TransformCache.ts");
var _V8CoverageCollector = __webpack_require__("./src/internals/V8CoverageCollector.ts");
var _cjsRequire = __webpack_require__("./src/internals/cjsRequire.ts");
var _esmLexer = __webpack_require__("./src/internals/esmLexer.ts");
var _moduleTypes = __webpack_require__("./src/internals/moduleTypes.ts");
var _nodeCapabilities = __webpack_require__("./src/internals/nodeCapabilities.ts");
function _interopRequireWildcard(e, t) { if ("function" == typeof WeakMap) var r = new WeakMap(), n = new WeakMap(); return (_interopRequireWildcard = function (e, t) { if (!t && e && e.__esModule) return e; var o, i, f = { __proto__: null, default: e }; if (null === e || "object" != typeof e && "function" != typeof e) return f; if (o = t ? n : r) { if (o.has(e)) return o.get(e); o.set(e, f); } for (const t in e) "default" !== t && {}.hasOwnProperty.call(e, t) && ((i = (o = Object.defineProperty) && Object.getOwnPropertyDescriptor(e, t)) && (i.get || i.set) ? o(f, t, i) : f[t] = e[t]); return f; })(e, t); }
function _interopRequireDefault(e) { return e && e.__esModule ? e : { default: e }; }
/**
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

// Modules safe to require from the outside (not stateful, not prone to
// realm errors) and slow enough that paying the worker-cache hit is worth
// it. Internal context only - user `require()` from a test still goes
// through the VM.
const INTERNAL_MODULE_REQUIRE_OUTSIDE_OPTIMIZED_MODULES = new Set(['chalk']);

// Framework modules that define shared singleton state (e.g. `JestAssertionError`).
// Redirecting user requires to the internal registry ensures test code and the
// framework see the same class instances.
const FRAMEWORK_SINGLETON_MODULES = new Set(['@jest/expect', 'expect']);
const defaultTransformOptions = {
  isInternalModule: false,
  supportsDynamicImport: _nodeCapabilities.runtimeSupportsVmModules,
  supportsExportNamespaceFrom: false,
  supportsStaticESM: false,
  supportsTopLevelAwait: false
};
const NODE_MODULES = `${path().sep}node_modules${path().sep}`;
const getModuleNameMapper = config => {
  if (Array.isArray(config.moduleNameMapper) && config.moduleNameMapper.length > 0) {
    return config.moduleNameMapper.map(([regex, moduleName]) => ({
      moduleName,
      regex: new RegExp(regex)
    }));
  }
  return null;
};
class Runtime {
  fileCache;
  _config;
  _coverageOptions;
  _environment;
  mockState;
  registries;
  testMainModule;
  requireBuilder;
  executor;
  esmLoader;
  cjsLoader;
  _moduleMocker;
  cjsExportsCache;
  _testPath;
  _resolution;
  transformCache;
  v8Coverage;
  coreModule;
  jestGlobals;
  testState;
  loggedReferenceErrors = new Set();
  constructor(config, environment, resolver, transformer, cacheFS, coverageOptions, testPath, globalConfig) {
    this.fileCache = new _FileCache.FileCache(cacheFS);
    this._config = config;
    this._coverageOptions = coverageOptions;
    this._environment = environment;
    this.registries = new _ModuleRegistries.ModuleRegistries(module => this.esmLoader.requireResultFromModule(module));
    (0, _jestUtil().invariant)(this._environment.moduleMocker, '`moduleMocker` must be set on an environment when created');
    this._moduleMocker = this._environment.moduleMocker;
    this._testPath = testPath;
    this.testState = new _TestState.TestState(msg => this._logFormattedReferenceError(msg));
    this.transformCache = new _TransformCache.TransformCache(transformer, this.fileCache, options => this._getFullTransformationOptions(options));
    this.v8Coverage = new _V8CoverageCollector.V8CoverageCollector(coverageOptions, config, this.transformCache);
    this._resolution = new _Resolution.Resolution(resolver, this._environment.exportConditions?.() ?? [], config.extensionsToTreatAsEsm);
    this.mockState = new _MockState.MockState(this._resolution, config);
    this.cjsExportsCache = new _CjsExportsCache.CjsExportsCache({
      fileCache: this.fileCache,
      loadCoreReexport: (from, coreName) => this.requireModule(from, coreName),
      loadNativeAddon: (from, modulePath) => this.requireModuleOrMock(from, modulePath),
      resolution: this._resolution,
      transformCache: this.transformCache
    });
    // Construction is a DAG: testMainModule → requireBuilder → {coreModule,
    // executor} → cjsLoader. The two lambdas inside `requireBuilder`'s deps
    // close over `cjsLoader` (built last) and `this.requireModuleOrMock`,
    // but those callbacks aren't invoked until user code runs, so the
    // forward references are safe.
    this.testMainModule = new _TestMainModule.TestMainModule();
    this.requireBuilder = new _cjsRequire.RequireBuilder({
      registries: this.registries,
      requireDispatch: (from, moduleName) => this.requireModuleOrMock(from, moduleName),
      requireInternal: (from, moduleName) => this.requireInternalModule(from, moduleName),
      resolution: this._resolution,
      testMainModule: this.testMainModule
    });
    this.coreModule = new _cjsRequire.CoreModuleProvider({
      environment: this._environment,
      requireBuilder: this.requireBuilder,
      resolution: this._resolution
    });
    this.jestGlobals = new _JestGlobals.JestGlobals({
      clearAllMocks: () => this.clearAllMocks(),
      config,
      environment: this._environment,
      generateMock: (from, moduleName) => this._generateMock(from, moduleName),
      globalConfig,
      isolateModules: fn => this.isolateModules(fn),
      isolateModulesAsync: fn => this.isolateModulesAsync(fn),
      logFormattedReferenceError: msg => this._logFormattedReferenceError(msg),
      mockState: this.mockState,
      moduleMocker: this._moduleMocker,
      requireActual: (from, moduleName) => this.requireActual(from, moduleName),
      requireMock: (from, moduleName) => this.requireMock(from, moduleName),
      resetAllMocks: () => this.resetAllMocks(),
      resetModules: () => this.resetModules(),
      restoreAllMocks: () => this.restoreAllMocks(),
      setMock: (from, moduleName, mockFactory, options) => this.setMock(from, moduleName, mockFactory, options),
      setModuleMock: (from, moduleName, mockFactory, options) => this.setModuleMock(from, moduleName, mockFactory, options),
      testState: this.testState
    });
    this.esmLoader = new _EsmLoader.EsmLoader({
      cjsExportsCache: this.cjsExportsCache,
      coreModule: this.coreModule,
      environment: this._environment,
      fileCache: this.fileCache,
      jestGlobals: this.jestGlobals,
      mockState: this.mockState,
      registries: this.registries,
      requireModule: (from, moduleName) => this.requireModule(from, moduleName),
      requireModuleOrMock: (from, moduleName) => this.requireModuleOrMock(from, moduleName),
      resolution: this._resolution,
      shouldLoadAsEsm: modulePath => this.unstable_shouldLoadAsEsm(modulePath),
      testPath: this._testPath,
      testState: this.testState,
      transformCache: this.transformCache
    });
    this.executor = new _ModuleExecutor.ModuleExecutor({
      config,
      dynamicImport: (specifier, identifier, context, importAttributes, phase) => this.esmLoader.dynamicImportFromCjs(specifier, identifier, context, importAttributes, phase),
      environment: this._environment,
      jestGlobals: this.jestGlobals,
      requireBuilder: this.requireBuilder,
      resolution: this._resolution,
      testMainModule: this.testMainModule,
      testPath,
      transformCache: this.transformCache
    });
    this.cjsLoader = new _CjsLoader.CjsLoader({
      coreModule: this.coreModule,
      environment: this._environment,
      executor: this.executor,
      logFormattedReferenceError: msg => this._logFormattedReferenceError(msg),
      mockState: this.mockState,
      registries: this.registries,
      requireEsm: (modulePath, requiredFrom, isRequireActual, moduleName) => this.esmLoader.requireEsmModule(modulePath, requiredFrom, isRequireActual, moduleName),
      resolution: this._resolution,
      testState: this.testState,
      transformCache: this.transformCache
    });
    this.installGetBuiltinModule();
    if (config.automock) {
      for (const filePath of config.setupFiles) {
        if (filePath.includes(NODE_MODULES)) {
          // shouldn't really matter, but in theory this will make sure the caching is correct
          const moduleID = this.unstable_shouldLoadAsEsm(filePath) ? this._resolution.getEsmModuleId(new Map(), filePath) : this._resolution.getCjsModuleId(new Map(), filePath);
          this.mockState.markTransitive(moduleID, false);
        }
      }
    }
    this.resetModules();
  }
  static shouldInstrument = _transform().shouldInstrument;
  static async createContext(config, options) {
    (0, _jestUtil().createDirectory)(config.cacheDirectory);
    const instance = await Runtime.createHasteMap(config, {
      console: options.console,
      maxWorkers: options.maxWorkers,
      resetCache: !config.cache,
      watch: options.watch,
      watchman: options.watchman
    });
    const hasteMap = await instance.build();
    return {
      config,
      hasteFS: hasteMap.hasteFS,
      moduleMap: hasteMap.moduleMap,
      resolver: Runtime.createResolver(config, hasteMap.moduleMap)
    };
  }
  static createHasteMap(config, options) {
    const ignorePatternParts = [...config.modulePathIgnorePatterns, ...(options && options.watch ? config.watchPathIgnorePatterns : []), config.cacheDirectory.startsWith(config.rootDir + path().sep) && config.cacheDirectory].filter(Boolean);
    const ignorePattern = ignorePatternParts.length > 0 ? new RegExp(ignorePatternParts.join('|')) : undefined;
    return _jestHasteMap().default.create({
      cacheDirectory: config.cacheDirectory,
      computeSha1: config.haste.computeSha1,
      console: options?.console,
      dependencyExtractor: config.dependencyExtractor,
      enableSymlinks: config.haste.enableSymlinks,
      extensions: [_jestSnapshot().EXTENSION, ...config.moduleFileExtensions],
      forceNodeFilesystemAPI: config.haste.forceNodeFilesystemAPI,
      hasteImplModulePath: config.haste.hasteImplModulePath,
      hasteMapModulePath: config.haste.hasteMapModulePath,
      id: config.id,
      ignorePattern,
      maxWorkers: options?.maxWorkers || 1,
      mocksPattern: (0, _jestRegexUtil().escapePathForRegex)(`${path().sep}__mocks__${path().sep}`),
      platforms: config.haste.platforms || ['ios', 'android'],
      resetCache: options?.resetCache,
      retainAllFiles: config.haste.retainAllFiles || false,
      rootDir: config.rootDir,
      roots: config.roots,
      throwOnModuleCollision: config.haste.throwOnModuleCollision,
      useWatchman: options?.watchman,
      watch: options?.watch,
      workerThreads: options?.workerThreads
    });
  }
  static createResolver(config, moduleMap) {
    return new (_jestResolve().default)(moduleMap, {
      defaultPlatform: config.haste.defaultPlatform,
      extensions: config.moduleFileExtensions.map(extension => `.${extension}`),
      hasCoreModules: true,
      moduleDirectories: config.moduleDirectories,
      moduleNameMapper: getModuleNameMapper(config),
      modulePaths: config.modulePaths,
      platforms: config.haste.platforms,
      resolver: config.resolver,
      rootDir: config.rootDir
    });
  }

  // unstable as it should be replaced by https://github.com/nodejs/modules/issues/393, and we don't want people to use it
  unstable_shouldLoadAsEsm(modulePath) {
    return this._resolution.shouldLoadAsEsm(modulePath);
  }
  async unstable_importModule(from, moduleName) {
    return this.esmLoader.loadAndEvaluate(from, moduleName);
  }
  requireModule(from, moduleName, options, isRequireActual = false) {
    return this.cjsLoader.requireModule(from, moduleName, options, isRequireActual);
  }
  requireInternalModule(from, to) {
    if (to) {
      if (INTERNAL_MODULE_REQUIRE_OUTSIDE_OPTIMIZED_MODULES.has(to)) {
        return _nodeModule().default.createRequire(from)(to);
      }
      const outsideJestVmPath = (0, _helpers.decodePossibleOutsideJestVmPath)(to);
      if (outsideJestVmPath) {
        return _nodeModule().default.createRequire(from)(outsideJestVmPath);
      }
    }
    return this.requireModule(from, to, {
      isInternalModule: true,
      supportsDynamicImport: _nodeCapabilities.runtimeSupportsVmModules,
      supportsExportNamespaceFrom: false,
      supportsStaticESM: false,
      supportsTopLevelAwait: false
    });
  }
  requireActual(from, moduleName) {
    if (FRAMEWORK_SINGLETON_MODULES.has(moduleName)) {
      return this.requireInternalModule(from, moduleName);
    }
    return this.requireModule(from, moduleName, undefined, true);
  }
  requireMock(from, moduleName) {
    return this._requireMockWithId(from, moduleName, this.mockState.getCjsModuleId(from, moduleName));
  }
  _requireMockWithId(from, moduleName, moduleID) {
    if (this.registries.hasMock(moduleID)) {
      return this.registries.getMock(moduleID);
    }
    const mockRegistry = this.registries.getActiveMockRegistry();
    const factory = this.mockState.getCjsFactory(moduleID);
    if (factory) {
      const module = factory();
      mockRegistry.set(moduleID, module);
      return module;
    }

    // ESM targets serve through the ESM loader, which loads a manual mock in
    // its own format and generates automocks from the real namespace. The
    // CJS branches below would execute an ESM mock file as CommonJS, and the
    // CJS generateMock would recurse into the ESM mock consult and automock
    // the freshly generated mock a second time.
    const esmMockTarget = this._esmMockTarget(from, moduleName);
    if (esmMockTarget !== null) {
      mockRegistry.set(moduleID, this.esmLoader.requireEsmMock(from, moduleName, esmMockTarget));
      return mockRegistry.get(moduleID);
    }
    const manualMockPath = this._resolution.findManualMock(from, moduleName);
    if (manualMockPath) {
      const localModule = (0, _moduleTypes.createInitialModule)(manualMockPath);
      this.cjsLoader.loadModule(localModule, from, moduleName, manualMockPath, undefined, mockRegistry);
      mockRegistry.set(moduleID, localModule.exports);
    } else {
      // Look for a real module to generate an automock from
      mockRegistry.set(moduleID, this._generateMock(from, moduleName));
    }
    return mockRegistry.get(moduleID);
  }

  // The sandbox `process` is a copy, so its `getBuiltinModule` is the host
  // function by reference: it handed back the host `process` (Node guarantees
  // `process.getBuiltinModule('process') === process`) and the host
  // `node:module`, whose `createRequire` loads files outside Jest's registry,
  // mocks and transforms. Route it through the same provider a `require()` of
  // a core module uses.
  installGetBuiltinModule() {
    // `getBuiltinModule` is Node 22.3+ and not in @types/node@18.
    const sandboxProcess = this._environment.global?.process;
    if (sandboxProcess === undefined || typeof sandboxProcess.getBuiltinModule !== 'function') {
      return;
    }
    // Dispatch through the public seam so subclasses that override
    // `requireModule` see builtin loads from `getBuiltinModule` too.
    sandboxProcess.getBuiltinModule = id => _nodeModule().default.isBuiltin(id) ? this.requireModule(this._testPath, id) : undefined;
  }

  // The CJS-resolved path travels with the mock request: a conditional
  // package can resolve `require` and `import` to different ESM files, and a
  // mock served to require() must describe the file require() would load.
  _esmMockTarget(from, moduleName) {
    // With an async-only resolver the sync resolution below silently falls
    // back to the default resolver; declining the shortcut lets the fallback
    // load reach requireEsmModule's ERR_REQUIRE_ASYNC_MODULE guard.
    if (!_nodeCapabilities.supportsSyncEvaluate || !this._resolution.canResolveSync()) {
      return null;
    }
    if (this._resolution.isCoreModule(moduleName)) {
      return null;
    }
    let modulePath;
    try {
      modulePath = this._resolution.resolveCjsStub(from, moduleName) || this._resolution.resolveCjs(from, moduleName);
    } catch {
      // A name that only resolves to a manual mock has no ESM target.
      return null;
    }
    if (this._resolution.shouldLoadAsEsm(modulePath)) {
      return modulePath;
    }
    // Unmarked ESM - a .js file in a CommonJS scope whose transformed source
    // carries ESM syntax - loads through requireEsmModule's parse-error
    // fallback, so the same discriminator classifies it here. Left to the
    // CJS generateMock, its real-module load would generate the ESM mock and
    // the outer path would automock that mock a second time.
    if (path().extname(modulePath) !== '.node' && !modulePath.endsWith('.json') && !this._resolution.isExplicitlyCommonjs(modulePath) && this.transformCache.canTransformSync(modulePath) && (0, _esmLexer.hasEsmSyntax)(this.transformCache.transform(modulePath, undefined))) {
      return modulePath;
    }
    return null;
  }
  _getFullTransformationOptions(options = defaultTransformOptions) {
    return {
      ...options,
      ...this._coverageOptions
    };
  }
  requireModuleOrMock(from, moduleName) {
    if (this.testState.bailIfTornDown('You are trying to `require` a file after the Jest environment has been torn down.')) {
      // @ts-expect-error: exiting early
      return;
    }

    // this module is unmockable
    if (moduleName === '@jest/globals') {
      // @ts-expect-error: we don't care that it's not assignable to T
      return this.jestGlobals.cjsGlobals(from);
    }
    try {
      const {
        shouldMock,
        moduleID
      } = this.mockState.shouldMockCjs(from, moduleName);
      if (shouldMock) {
        return this._requireMockWithId(from, moduleName, moduleID);
      }
      if (FRAMEWORK_SINGLETON_MODULES.has(moduleName)) {
        return this.requireInternalModule(from, moduleName);
      }
      return this.requireModule(from, moduleName);
    } catch (error) {
      const moduleNotFound = _jestResolve().default.tryCastModuleNotFoundError(error);
      if (moduleNotFound) {
        if (moduleNotFound.siblingWithSimilarExtensionFound === null || moduleNotFound.siblingWithSimilarExtensionFound === undefined) {
          moduleNotFound.hint = (0, _helpers.findSiblingsWithFileExtension)(this._config.moduleFileExtensions, from, moduleNotFound.moduleName || moduleName);
          moduleNotFound.siblingWithSimilarExtensionFound = Boolean(moduleNotFound.hint);
        }
        moduleNotFound.buildMessage(this._config.rootDir);
        throw moduleNotFound;
      }
      throw error;
    }
  }
  isolateModules(fn) {
    this.registries.enterIsolated('isolateModules');
    try {
      fn();
    } finally {
      this.registries.exitIsolated();
    }
  }
  async isolateModulesAsync(fn) {
    this.registries.enterIsolated('isolateModulesAsync');
    try {
      await fn();
    } finally {
      this.registries.exitIsolated();
    }
  }
  resetModules() {
    this.registries.clearForReset();
    this.cjsExportsCache.clear();
    this.fileCache.clear();
    this._resolution.clear();
    this.v8Coverage.snapshotTransforms();
    this.transformCache.clear();
    if (this._environment) {
      if (this._environment.global) {
        this._moduleMocker.clearMocksOnScope?.(this._environment.global);
      }
      if (this._environment.fakeTimers) {
        this._environment.fakeTimers.clearAllTimers();
      }
    }
  }
  collectV8Coverage() {
    return this.v8Coverage.start();
  }
  stopCollectingV8Coverage() {
    return this.v8Coverage.stop();
  }
  getAllCoverageInfoCopy() {
    return (0, _jestUtil().deepCyclicCopy)(this._environment.global.__coverage__);
  }
  getAllV8CoverageInfoCopy() {
    return this.v8Coverage.getResult();
  }
  getSourceMaps() {
    return this.transformCache.getSourceMaps();
  }
  setMock(from, moduleName, mockFactory, options) {
    this.mockState.setMock(from, moduleName, mockFactory, options);
  }
  setModuleMock(from, moduleName, mockFactory, options) {
    this.mockState.setModuleMock(from, moduleName, mockFactory, options);
  }
  restoreAllMocks() {
    this._moduleMocker.restoreAllMocks();
  }
  resetAllMocks() {
    this._moduleMocker.resetAllMocks();
  }
  clearAllMocks() {
    this._moduleMocker.clearAllMocks();
  }
  enterTestCode() {
    this.testState.enterTestCode();
  }
  leaveTestCode() {
    this.testState.leaveTestCode();
  }
  teardown() {
    this.restoreAllMocks();
    this.resetModules();
    this.registries.clear();
    this.testMainModule.reset();
    this.mockState.clear();
    this.fileCache.clear();
    this.transformCache.clear();
    this.jestGlobals.clearJestObjectCache();
    this.v8Coverage.reset();
    this.coreModule.reset();
    this.loggedReferenceErrors.clear();
    this.testState.teardown();
  }
  _generateMock(from, moduleName) {
    return (0, _MockState.generateMock)(from, moduleName, {
      mockState: this.mockState,
      moduleMocker: this._moduleMocker,
      registries: this.registries,
      requireModule: (from, moduleName) => this.requireModule(from, moduleName),
      resolution: this._resolution
    });
  }
  _logFormattedReferenceError(errorMessage) {
    const testPath = this._testPath ? ` From ${(0, _slash().default)(path().relative(this._config.rootDir, this._testPath))}.` : '';
    const originalStack = new ReferenceError(`${errorMessage}${testPath}`).stack.split('\n')
    // Remove this file from the stack (jest-message-utils will keep one line)
    .filter(line => !line.includes(__filename)).join('\n');
    const {
      message,
      stack
    } = (0, _jestMessageUtil().separateMessageFromStack)(originalStack);
    const stackTrace = (0, _jestMessageUtil().formatStackTrace)(stack, this._config, {
      noStackTrace: false
    });
    const formattedMessage = `\n${message}${stackTrace ? `\n${stackTrace}` : ''}`;
    if (!this.loggedReferenceErrors.has(formattedMessage)) {
      console.error(formattedMessage);
      this.loggedReferenceErrors.add(formattedMessage);
    }
  }
  setGlobalsForRuntime(globals) {
    this.jestGlobals.setEnvGlobalsOverride(globals);
  }
}
exports["default"] = Runtime;
})();

module.exports = __webpack_exports__;
/******/ })()
;