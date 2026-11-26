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

/***/ "./src/SourceMapCache.ts"
(__unused_webpack_module, exports) {



Object.defineProperty(exports, "__esModule", ({
  value: true
}));
exports.SourceMapCache = void 0;
exports.mapSourcePosition = mapSourcePosition;
function _traceMapping() {
  const data = require("@jridgewell/trace-mapping");
  _traceMapping = function () {
    return data;
  };
  return data;
}
function _convertSourceMap() {
  const data = require("convert-source-map");
  _convertSourceMap = function () {
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

// The *last* sourceMappingURL wins, so one inside a comment or a string does
// not shadow the real one.
function findSourceMapUrl(fileContent) {
  const lastMatch = [...fileContent.matchAll(_convertSourceMap().mapFileCommentRegex)].at(-1);
  return lastMatch == null ? null : lastMatch[1] ?? lastMatch[2] ?? null;
}
function resolveUrl(url, base) {
  try {
    return new URL(url, base).href;
  } catch {
    return null;
  }
}

// Map paths are content-addressed, so a stale one is never asked for again and
// a parsed map can live for the whole process. The base URL joins the key
// because it is baked into the resolved sources, and a transformer with its
// own `getCacheKey` can hand two generated files the same map path.
// Nothing evicts these, so a worker ends up holding one parsed map per file it
// has formatted a stack for. That is the point: re-reading and re-parsing on
// every frame of every stack costs far more than keeping them.
const parsedByCachePath = new Map();

// A worker moves on to the next test file while a stray timer from the
// previous one can still throw; remembering where each file's map lived keeps
// those frames resolvable. `null` marks a file transformed more than one way —
// as ESM and as CJS, say — where a frame does not say which map it came from,
// so decline rather than guess.
// This also grows for the worker's lifetime, holding two path strings per file
// — small enough that bounding it would cost more than it saves, and a frame
// arriving after its file's registry is gone has nowhere else to look.
const rememberedMapPaths = new Map();
function rememberMapPath(generatedPath, sourceMapPath) {
  const remembered = rememberedMapPaths.get(generatedPath);
  rememberedMapPaths.set(generatedPath, remembered === undefined || remembered === sourceMapPath ? sourceMapPath : null);
}
const WINDOWS_DRIVE_PATH_REGEXP = /^[a-zA-Z]:[\\/]/;

// The tracer resolves `sources` as URLs, which reads a Windows drive path such
// as `C:/src/input.ts` as relative and appends it to the map's directory.
function toUrlIfDrivePath(source, reader) {
  return source != null && WINDOWS_DRIVE_PATH_REGEXP.test(source) ? reader.toUrl(source) : source;
}
function withDrivePathsAsUrls(map, reader) {
  if (typeof map === 'string') {
    return withDrivePathsAsUrls(JSON.parse(map), reader);
  }
  if (map instanceof _traceMapping().TraceMap) {
    return map;
  }
  if ('sections' in map) {
    return {
      ...map,
      sections: map.sections.map(section => ({
        ...section,
        map: withDrivePathsAsUrls(section.map, reader)
      }))
    };
  }
  return {
    ...map,
    sourceRoot: toUrlIfDrivePath(map.sourceRoot, reader),
    sources: map.sources.map(source => toUrlIfDrivePath(source, reader))
  };
}

// `mapUrl` is what the map's `sources` resolve against.
function parseMap(content, mapUrl, reader) {
  try {
    // `AnyMap` rather than `TraceMap`, which throws on the indexed maps that
    // bundlers emit as a top-level `sections` array.
    return (0, _traceMapping().AnyMap)(withDrivePathsAsUrls(content, reader), mapUrl);
  } catch {
    return null;
  }
}
class SourceMapCache {
  sourceMaps;
  reader;
  reportUnparsable;
  loaded = new Map();
  attempted = new Map();
  constructor(sourceMaps, reader, reportUnparsable) {
    this.sourceMaps = sourceMaps;
    this.reader = reader;
    this.reportUnparsable = reportUnparsable;
  }
  get(generatedPath) {
    const cached = this.loaded.get(generatedPath);
    const registered = this.sourceMaps?.get(generatedPath);

    // The registry fills in lazily, so a file looked up before its map was
    // registered must not stay unmapped. Retry only when the entry points
    // somewhere new: a map that failed to load would otherwise be re-read for
    // every frame, and the runtime empties the registry at teardown while
    // stacks are still being formatted — an entry going away must not drop a
    // loaded map.
    const isStale = registered != null && this.attempted.get(generatedPath) !== registered;
    if (cached !== undefined && !isStale) {
      return cached;
    }
    const loaded = this.load(generatedPath);
    this.loaded.set(generatedPath, loaded);
    this.attempted.set(generatedPath, registered);
    return loaded;
  }

  // A mapped source comes back as a URL, which is not what a stack frame should
  // show for a file on disk.
  toDisplayPath(url) {
    return this.reader.toPath(url);
  }

  // Formatting a stack remembers a file's map as a side effect, but a file no
  // stack ever mentioned is never recorded that way. Called before this cache
  // stops being the active one, so a late frame from such a file still finds
  // its map. The same ambiguity rule applies: a file already remembered with a
  // different map path flips to `null` and the fallback declines.
  rememberAll() {
    if (this.sourceMaps == null) {
      return;
    }
    for (const [generatedPath, sourceMapPath] of this.sourceMaps) {
      if (sourceMapPath !== '') {
        rememberMapPath(generatedPath, sourceMapPath);
      }
    }
  }
  load(generatedPath) {
    // The map Jest itself produced while transforming the file.
    const registered = this.sourceMaps?.get(generatedPath);
    if (registered != null && registered !== '') {
      rememberMapPath(generatedPath, registered);
    }
    const sourceMapPath = registered != null && registered !== '' ? registered : rememberedMapPaths.get(generatedPath);
    if (sourceMapPath != null && sourceMapPath !== '') {
      return this.parseRegisteredMap(sourceMapPath, generatedPath);
    }
    return this.readAdjacent(generatedPath);
  }
  parseRegisteredMap(sourceMapPath, generatedPath) {
    // Jest's transform writes the map to its cache directory, but the sources
    // it names are relative to the file that was transformed.
    const mapUrl = this.reader.toUrl(generatedPath);
    const cacheKey = `${sourceMapPath}\0${mapUrl}`;
    const cached = parsedByCachePath.get(cacheKey);
    if (cached !== undefined) {
      return cached;
    }
    const content = this.reader.read(sourceMapPath);
    const map = content == null ? null : parseMap(content, mapUrl, this.reader);
    if (content != null && map == null) {
      this.reportUnparsable(sourceMapPath, generatedPath);
    }
    parsedByCachePath.set(cacheKey, map);
    return map;
  }

  // A `sourceMappingURL` comment on a file Jest did not transform, which covers
  // pre-compiled output shipping its own map.
  readAdjacent(generatedPath) {
    const fileContent = this.reader.read(generatedPath);

    // A frame can name a `data:` URL — a dynamic `import()` of one — and what
    // that decodes to is not a file a `sourceMappingURL` comment sits on.
    if (typeof fileContent !== 'string') {
      return null;
    }
    const sourceMapUrl = findSourceMapUrl(fileContent);
    if (sourceMapUrl == null) {
      return null;
    }
    const generatedUrl = this.reader.toUrl(generatedPath);
    const resolvedUrl = resolveUrl(sourceMapUrl, generatedUrl);
    if (resolvedUrl == null) {
      return null;
    }
    const isInline = resolvedUrl.startsWith('data:');
    const content = this.reader.read(resolvedUrl);
    if (content == null) {
      // A `data:` URL that fails to decode is a broken inline map; a missing
      // `.map` file next to the code is not worth reporting.
      if (isInline) {
        this.reportUnparsable(generatedPath, generatedPath);
      }
      return null;
    }

    // An inline map's sources are relative to the file carrying it. Every other
    // map resolves them against wherever the map itself lives.
    const mapUrl = isInline ? generatedUrl : resolvedUrl;
    const map = parseMap(content, mapUrl, this.reader);
    if (map == null) {
      this.reportUnparsable(isInline ? generatedPath : this.reader.toPath(resolvedUrl), generatedPath);
    }
    return map;
  }
}

// Returns the position unchanged when nothing maps to it: a precise location
// in the compiled file beats a vague one in the original.
exports.SourceMapCache = SourceMapCache;
function mapSourcePosition(cache, position) {
  const {
    column,
    line,
    source
  } = position;

  // The tracer throws on out-of-range needles, and a throw inside
  // `prepareStackTrace` replaces the whole stack with the exception.
  if (column == null || line == null || column < 0 || line < 1) {
    return position;
  }
  const map = cache.get(source);
  if (map == null) {
    return position;
  }
  const originalPosition = (0, _traceMapping().originalPositionFor)(map, {
    column,
    line
  });
  if (originalPosition.source == null) {
    return position;
  }
  return {
    column: originalPosition.column,
    line: originalPosition.line,
    name: originalPosition.name,
    source: cache.toDisplayPath(originalPosition.source)
  };
}

/***/ },

/***/ "./src/SourceMapSupport.ts"
(__unused_webpack_module, exports, __webpack_require__) {



Object.defineProperty(exports, "__esModule", ({
  value: true
}));
exports.addSourceMapConsumer = exports.SourceMapSupport = void 0;
function _traceMapping() {
  const data = require("@jridgewell/trace-mapping");
  _traceMapping = function () {
    return data;
  };
  return data;
}
function _callsites() {
  const data = _interopRequireDefault(require("callsites"));
  _callsites = function () {
    return data;
  };
  return data;
}
var _SourceMapCache = __webpack_require__("./src/SourceMapCache.ts");
var _nodeFileReader = __webpack_require__("./src/nodeFileReader.ts");
function _interopRequireDefault(e) { return e && e.__esModule ? e : { default: e }; }
/**
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

// TODO: replace with `util.getCallSites()`, whose `columnNumber` landed in
// Node 22.14 — the floor is still 18.

// Copied from https://github.com/rexxars/sourcemap-decorate-callsites/blob/5b9735a156964973a75dc62fd2c7f0c1975458e8/lib/index.js#L113-L158
const addSourceMapConsumer = (callsite, tracer) => {
  const getLineNumber = callsite.getLineNumber.bind(callsite);
  const getColumnNumber = callsite.getColumnNumber.bind(callsite);
  let position = null;
  function getPosition() {
    if (position != null) {
      return position;
    }

    // The needle is zero-based while V8 counts columns from one, so looking up
    // V8's number directly finds the segment one column to the right.
    const line = getLineNumber();
    const column = (getColumnNumber() ?? 1) - 1;

    // The tracer throws on out-of-range needles.
    position = line == null || line < 1 || column < 0 ? {
      column: null,
      line: null,
      name: null,
      source: null
    } : (0, _traceMapping().originalPositionFor)(tracer, {
      column,
      line
    });
    return position;
  }
  Object.defineProperties(callsite, {
    getColumnNumber: {
      value() {
        // TODO: return `column + 1` in Jest 31, so this matches V8 and
        // jest-circus. Reported zero-based until then, which is what
        // `--testLocationInResults` documents for jest-jasmine2, and changing
        // it breaks anyone reading that field. An unmapped position falls back
        // to V8's one-based column, as it always has — the Jest 31 change
        // turns that fallback consistent instead of one off.
        const {
          column
        } = getPosition();
        return column ?? getColumnNumber();
      },
      writable: false
    },
    getLineNumber: {
      value() {
        const {
          line
        } = getPosition();
        return line ?? getLineNumber();
      },
      writable: false
    }
  });
};

// V8 treats an empty name as no name at all. Every fallback below has to keep
// doing the same, which is why none of them can become `??`.
exports.addSourceMapConsumer = addSourceMapConsumer;
function isPresent(value) {
  return value != null && value !== '';
}
function orAnonymous(value) {
  return isPresent(value) ? value : '<anonymous>';
}
function frameToString(frame) {
  return frame.toString();
}

// Copied almost verbatim from the V8 source, by way of `source-map-support`.
// Every non-native frame is rendered through this rather than through V8's own
// `CallSite#toString`, so it decides the exact shape of every stack frame Jest
// prints.
function callSiteToString() {
  let fileLocation = '';
  let fileName;
  if (this.isNative()) {
    fileLocation = 'native';
  } else {
    fileName = this.getScriptNameOrSourceURL();
    if (!isPresent(fileName) && this.isEval()) {
      fileLocation = `${this.getEvalOrigin() ?? ''}, `;
    }

    // Source code does not originate from a file and is not native, but we can
    // still get the source position inside the source string, e.g. in an eval
    // string.
    fileLocation += orAnonymous(fileName);
    const lineNumber = this.getLineNumber();
    if (lineNumber != null) {
      fileLocation += `:${lineNumber}`;
      const columnNumber = this.getColumnNumber();
      if (columnNumber != null && columnNumber !== 0) {
        fileLocation += `:${columnNumber}`;
      }
    }
  }
  let line = '';
  const functionName = this.getFunctionName();
  const isConstructor = this.isConstructor();
  const isMethodCall = !(this.isToplevel() || isConstructor);
  if (isMethodCall) {
    const typeName = this.getTypeName();
    const methodName = this.getMethodName();
    if (isPresent(functionName)) {
      if (isPresent(typeName) && !functionName.startsWith(typeName)) {
        line += `${typeName}.`;
      }
      line += functionName;

      // Skips the suffix when the function name already is the method name:
      // both sides are -1 then.
      if (isPresent(methodName) && functionName.indexOf(`.${methodName}`) !== functionName.length - methodName.length - 1) {
        line += ` [as ${methodName}]`;
      }
    } else {
      line += `${String(typeName)}.${orAnonymous(methodName)}`;
    }
  } else if (isConstructor) {
    line += `new ${orAnonymous(functionName)}`;
  } else if (isPresent(functionName)) {
    line += functionName;
  } else {
    return fileLocation;
  }
  return `${line} (${fileLocation})`;
}
function cloneCallSite(frame) {
  const clone = {};
  const source = frame;
  for (const name of Object.getOwnPropertyNames(Object.getPrototypeOf(frame))) {
    // Resolved at call time rather than now, so that accessors on the
    // prototype are not invoked here.
    clone[name] = /^(?:is|get)/.test(name) ? () => source[name].call(frame) : source[name];
  }
  clone.toString = callSiteToString;
  return clone;
}
function mapEvalOrigin(cache, origin) {
  // Most eval() calls are in this format
  const topLevel = /^eval at ([^(]+) \((.+):(\d+):(\d+)\)$/.exec(origin);
  if (topLevel) {
    const position = (0, _SourceMapCache.mapSourcePosition)(cache, {
      column: Number(topLevel[4]) - 1,
      line: Number(topLevel[3]),
      source: topLevel[2]
    });
    return `eval at ${topLevel[1]} (${position.source}:${position.line}:${(position.column ?? 0) + 1})`;
  }

  // Parse nested eval() calls using recursion
  const nested = /^eval at ([^(]+) \((.+)\)$/.exec(origin);
  if (nested) {
    return `eval at ${nested[1]} (${mapEvalOrigin(cache, nested[2])})`;
  }

  // Make sure we still return useful information if we didn't find anything
  return origin;
}
function wrapCallSite(cache, frame) {
  if (frame.isNative()) {
    return frame;
  }

  // Most call sites will return the source file from `getFileName()`, but code
  // passed to eval() ending in "//# sourceURL=..." will return the source file
  // from `getScriptNameOrSourceURL()` instead.
  const fileName = frame.getFileName();
  const source = isPresent(fileName) ? fileName : frame.getScriptNameOrSourceURL();
  if (isPresent(source)) {
    const column = frame.getColumnNumber();
    const position = (0, _SourceMapCache.mapSourcePosition)(cache, {
      column: column == null ? null : column - 1,
      line: frame.getLineNumber(),
      source
    });
    const mapped = cloneCallSite(frame);
    const originalGetFunctionName = mapped.getFunctionName.bind(mapped);

    // Deliberately the name at the frame's *own* mapped position, which is the
    // identifier being called there rather than the enclosing function. It
    // annotates each frame with the call on that line — `at Object.toBeTruthy
    // (assertionCount.test.js:12:17)` — which is what makes a failing
    // assertion's stack readable. Taking the caller's position instead would be
    // the spec-correct reading and would collapse these to `Object.<anonymous>`.
    mapped.getFunctionName = () => isPresent(position.name) ? position.name : originalGetFunctionName();
    mapped.getFileName = () => position.source;
    mapped.getLineNumber = () => position.line;
    mapped.getColumnNumber = () => position.column == null ? null : position.column + 1;
    mapped.getScriptNameOrSourceURL = () => position.source;
    return mapped;
  }

  // Code called using eval() needs special handling
  const origin = frame.isEval() ? frame.getEvalOrigin() : undefined;
  if (isPresent(origin)) {
    const mapped = cloneCallSite(frame);
    const mappedOrigin = mapEvalOrigin(cache, origin);
    mapped.getEvalOrigin = () => mappedOrigin;
    return mapped;
  }

  // If we get here then we were unable to change the source position
  return frame;
}
class SourceMapSupport {
  // Holding this keeps the current test file's registry reachable after its
  // environment is gone, which is what lets a stray timer's stack still map.
  // The next `install` drops it, so it is one file's worth of path strings at a
  // time rather than an accumulating set.
  activeCache = null;
  nullCache = null;
  // Keyed weakly, so a registry nothing else holds takes its cache with it.
  cachesByRegistry = new WeakMap();
  suppressWarnings = false;
  reportedMapPaths = new Set();
  // V8 calls the formatter unbound, and `install` compares it by identity, so
  // the bound copy has to be the same object every time.
  boundFormatStackTrace;
  constructor() {
    this.boundFormatStackTrace = this.formatStackTrace.bind(this);
  }

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
  install(sourceMaps, options = {}) {
    this.suppressWarnings = options.suppressWarnings === true;
    const cache = this.cacheFor(sourceMaps);

    // The registry going out of service is the last chance to remember where
    // each file's map lives — a stray timer from the file it served can still
    // throw, and a file no formatted stack mentioned was never recorded.
    if (this.activeCache != null && this.activeCache !== cache) {
      this.activeCache.rememberAll();
    }
    this.activeCache = cache;
    if (Error.prepareStackTrace !== this.boundFormatStackTrace) {
      Error.prepareStackTrace = this.boundFormatStackTrace;
    }
  }

  /** One remapped `CallSite`, `level` frames above the caller. */
  getCallsite(level, sourceMaps) {
    const levelAfterThisCall = level + 1;
    const stack = (0, _callsites().default)()[levelAfterThisCall];
    const sourceMap = this.cacheFor(sourceMaps).get(stack.getFileName() ?? '');
    if (sourceMap != null) {
      addSourceMapConsumer(stack, sourceMap);
    }
    return stack;
  }

  // One cache per registry, so repeated lookups in a test file reuse it.
  cacheFor(sourceMaps) {
    if (sourceMaps == null) {
      this.nullCache ??= new _SourceMapCache.SourceMapCache(null, _nodeFileReader.nodeFileReader, (mapPath, generatedPath) => this.reportUnparsable(mapPath, generatedPath));
      return this.nullCache;
    }
    let cache = this.cachesByRegistry.get(sourceMaps);
    if (cache == null) {
      cache = new _SourceMapCache.SourceMapCache(sourceMaps, _nodeFileReader.nodeFileReader, (mapPath, generatedPath) => this.reportUnparsable(mapPath, generatedPath));
      this.cachesByRegistry.set(sourceMaps, cache);
    }
    return cache;
  }

  // A broken map silently leaves frames at their generated positions, which
  // reads as "source maps do not work". Say so, once per map.
  reportUnparsable(mapPath, generatedPath) {
    if (this.suppressWarnings || this.reportedMapPaths.has(mapPath)) {
      return;
    }
    this.reportedMapPaths.add(mapPath);
    const location = mapPath === generatedPath ? 'the inline source map' : `the source map at ${mapPath}`;
    console.warn(`Failed to parse ${location} for ${generatedPath}; its stack frames stay untranslated.`);
  }
  formatStackTrace(error, stack) {
    const name = isPresent(error.name) ? error.name : 'Error';
    const message = error.message ?? '';
    const errorString = `${name}: ${message}`;
    const cache = this.activeCache;
    if (cache == null) {
      return errorString + stack.map(frame => `\n    at ${frameToString(frame)}`).join('');
    }
    return errorString + stack.map(frame => `\n    at ${frameToString(wrapCallSite(cache, frame))}`).join('');
  }
}
exports.SourceMapSupport = SourceMapSupport;

/***/ },

/***/ "./src/getCallsite.ts"
(__unused_webpack_module, exports, __webpack_require__) {



Object.defineProperty(exports, "__esModule", ({
  value: true
}));
exports["default"] = getCallsite;
function _callsites() {
  const data = _interopRequireDefault(require("callsites"));
  _callsites = function () {
    return data;
  };
  return data;
}
var _SourceMapCache = __webpack_require__("./src/SourceMapCache.ts");
var _SourceMapSupport = __webpack_require__("./src/SourceMapSupport.ts");
var _nodeFileReader = __webpack_require__("./src/nodeFileReader.ts");
function _interopRequireDefault(e) { return e && e.__esModule ? e : { default: e }; }
/**
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

// TODO: replace with `util.getCallSites()`, whose `columnNumber` landed in
// Node 22.14 — the floor is still 18.

const reportNothing = () => {
  // Broken maps stay quiet on this path: the formatter reports them when a
  // stack is formatted, which is when anyone can act on it.
};

// One cache per registry, so repeated lookups in a test file reuse it. Parsed
// maps are shared with the formatter through the process-lifetime parse cache.
const cachesByRegistry = new WeakMap();
let nullCache = null;
function cacheFor(sourceMaps) {
  if (sourceMaps == null) {
    nullCache ??= new _SourceMapCache.SourceMapCache(null, _nodeFileReader.nodeFileReader, reportNothing);
    return nullCache;
  }
  let cache = cachesByRegistry.get(sourceMaps);
  if (cache == null) {
    cache = new _SourceMapCache.SourceMapCache(sourceMaps, _nodeFileReader.nodeFileReader, reportNothing);
    cachesByRegistry.set(sourceMaps, cache);
  }
  return cache;
}

/**
 * One remapped `CallSite`, `level` frames above the caller.
 *
 * @deprecated Use `SourceMapSupport#getCallsite` instead.
 */
function getCallsite(level, sourceMaps) {
  const levelAfterThisCall = level + 1;
  const stack = (0, _callsites().default)()[levelAfterThisCall];
  const sourceMap = cacheFor(sourceMaps).get(stack.getFileName() ?? '');
  if (sourceMap != null) {
    (0, _SourceMapSupport.addSourceMapConsumer)(stack, sourceMap);
  }
  return stack;
}

/***/ },

/***/ "./src/nodeFileReader.ts"
(__unused_webpack_module, exports) {



Object.defineProperty(exports, "__esModule", ({
  value: true
}));
exports.nodeFileReader = void 0;
function _nodeUrl() {
  const data = require("node:url");
  _nodeUrl = function () {
    return data;
  };
  return data;
}
function _convertSourceMap() {
  const data = require("convert-source-map");
  _convertSourceMap = function () {
    return data;
  };
  return data;
}
function _gracefulFs() {
  const data = require("graceful-fs");
  _gracefulFs = function () {
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

// A scheme needs at least two characters before the colon, so a Windows drive
// letter is not mistaken for one.
const ABSOLUTE_URI_REGEXP = /^[a-zA-Z][\w+\-.]+:/;
function toPath(url) {
  if (!url.startsWith('file:')) {
    return url;
  }
  try {
    return (0, _nodeUrl().fileURLToPath)(url);
  } catch {
    return url;
  }
}
const nodeFileReader = exports.nodeFileReader = {
  read(urlOrPath) {
    if (urlOrPath.startsWith('data:')) {
      try {
        // Reads both the base64 and the URI encoding the spec allows. Takes a
        // whole comment rather than the URL on its own.
        return (0, _convertSourceMap().fromComment)(`//# sourceMappingURL=${urlOrPath}`).toObject();
      } catch {
        return null;
      }
    }

    // A resource named by any other scheme — `node:internal/…`,
    // `webpack:///…` — cannot be read off disk.
    if (ABSOLUTE_URI_REGEXP.test(urlOrPath) && !urlOrPath.startsWith('file:')) {
      return null;
    }
    const filePath = toPath(urlOrPath);
    try {
      return (0, _gracefulFs().existsSync)(filePath) ? (0, _gracefulFs().readFileSync)(filePath, 'utf8') : null;
    } catch {
      return null;
    }
  },
  toPath,
  toUrl(pathOrUrl) {
    return ABSOLUTE_URI_REGEXP.test(pathOrUrl) ? pathOrUrl : (0, _nodeUrl().pathToFileURL)(pathOrUrl).href;
  }
};

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
Object.defineProperty(exports, "SourceMapSupport", ({
  enumerable: true,
  get: function () {
    return _SourceMapSupport.SourceMapSupport;
  }
}));
Object.defineProperty(exports, "getCallsite", ({
  enumerable: true,
  get: function () {
    return _getCallsite.default;
  }
}));
var _getCallsite = _interopRequireDefault(__webpack_require__("./src/getCallsite.ts"));
var _SourceMapSupport = __webpack_require__("./src/SourceMapSupport.ts");
function _interopRequireDefault(e) { return e && e.__esModule ? e : { default: e }; }
})();

module.exports = __webpack_exports__;
/******/ })()
;