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

/***/ "./src/HasteFS.ts"
(__unused_webpack_module, exports, __webpack_require__) {



Object.defineProperty(exports, "__esModule", ({
  value: true
}));
exports["default"] = void 0;
function _jestUtil() {
  const data = require("jest-util");
  _jestUtil = function () {
    return data;
  };
  return data;
}
var _constants = _interopRequireDefault(__webpack_require__("./src/constants.ts"));
var fastPath = _interopRequireWildcard(__webpack_require__("./src/lib/fast_path.ts"));
function _interopRequireWildcard(e, t) { if ("function" == typeof WeakMap) var r = new WeakMap(), n = new WeakMap(); return (_interopRequireWildcard = function (e, t) { if (!t && e && e.__esModule) return e; var o, i, f = { __proto__: null, default: e }; if (null === e || "object" != typeof e && "function" != typeof e) return f; if (o = t ? n : r) { if (o.has(e)) return o.get(e); o.set(e, f); } for (const t in e) "default" !== t && {}.hasOwnProperty.call(e, t) && ((i = (o = Object.defineProperty) && Object.getOwnPropertyDescriptor(e, t)) && (i.get || i.set) ? o(f, t, i) : f[t] = e[t]); return f; })(e, t); }
function _interopRequireDefault(e) { return e && e.__esModule ? e : { default: e }; }
/**
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

class HasteFS {
  _rootDir;
  _files;
  constructor({
    rootDir,
    files
  }) {
    this._rootDir = rootDir;
    this._files = files;
  }
  getModuleName(file) {
    const fileMetadata = this._getFileData(file);
    return fileMetadata && fileMetadata[_constants.default.ID] || null;
  }
  getSize(file) {
    const fileMetadata = this._getFileData(file);
    return fileMetadata && fileMetadata[_constants.default.SIZE] || null;
  }
  getDependencies(file) {
    const fileMetadata = this._getFileData(file);
    if (fileMetadata) {
      return fileMetadata[_constants.default.DEPENDENCIES] ? fileMetadata[_constants.default.DEPENDENCIES].split(_constants.default.DEPENDENCY_DELIM) : [];
    } else {
      return null;
    }
  }
  getSha1(file) {
    const fileMetadata = this._getFileData(file);
    return fileMetadata && fileMetadata[_constants.default.SHA1] || null;
  }
  exists(file) {
    return this._getFileData(file) != null;
  }
  getAllFiles() {
    return [...this.getAbsoluteFileIterator()];
  }
  getFileIterator() {
    return this._files.keys();
  }
  *getAbsoluteFileIterator() {
    for (const file of this.getFileIterator()) {
      yield fastPath.resolve(this._rootDir, file);
    }
  }
  matchFiles(pattern) {
    if (!(pattern instanceof RegExp)) {
      pattern = new RegExp(pattern);
    }
    const files = [];
    for (const file of this.getAbsoluteFileIterator()) {
      if (pattern.test(file)) {
        files.push(file);
      }
    }
    return files;
  }
  matchFilesWithGlob(globs, root) {
    const files = new Set();
    const matcher = (0, _jestUtil().globsToMatcher)(globs);
    for (const file of this.getAbsoluteFileIterator()) {
      const filePath = root ? fastPath.relative(root, file) : file;
      if (matcher((0, _jestUtil().replacePathSepForGlob)(filePath))) {
        files.add(file);
      }
    }
    return files;
  }
  _getFileData(file) {
    const relativePath = fastPath.relative(this._rootDir, file);
    return this._files.get(relativePath);
  }
}
exports["default"] = HasteFS;

/***/ },

/***/ "./src/ModuleMap.ts"
(__unused_webpack_module, exports, __webpack_require__) {



Object.defineProperty(exports, "__esModule", ({
  value: true
}));
exports["default"] = void 0;
var _constants = _interopRequireDefault(__webpack_require__("./src/constants.ts"));
var fastPath = _interopRequireWildcard(__webpack_require__("./src/lib/fast_path.ts"));
function _interopRequireWildcard(e, t) { if ("function" == typeof WeakMap) var r = new WeakMap(), n = new WeakMap(); return (_interopRequireWildcard = function (e, t) { if (!t && e && e.__esModule) return e; var o, i, f = { __proto__: null, default: e }; if (null === e || "object" != typeof e && "function" != typeof e) return f; if (o = t ? n : r) { if (o.has(e)) return o.get(e); o.set(e, f); } for (const t in e) "default" !== t && {}.hasOwnProperty.call(e, t) && ((i = (o = Object.defineProperty) && Object.getOwnPropertyDescriptor(e, t)) && (i.get || i.set) ? o(f, t, i) : f[t] = e[t]); return f; })(e, t); }
function _interopRequireDefault(e) { return e && e.__esModule ? e : { default: e }; }
/**
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

const EMPTY_OBJ = {};
const EMPTY_MAP = new Map();
class ModuleMap {
  static DuplicateHasteCandidatesError;
  _raw;
  json;
  static mapToArrayRecursive(map) {
    let arr = [...map];
    if (arr[0] && arr[0][1] instanceof Map) {
      arr = arr.map(el => [el[0], this.mapToArrayRecursive(el[1])]);
    }
    return arr;
  }
  static mapFromArrayRecursive(arr) {
    if (arr[0] && Array.isArray(arr[0][1])) {
      arr = arr.map(el => [el[0], this.mapFromArrayRecursive(el[1])]);
    }
    return new Map(arr);
  }
  constructor(raw) {
    this._raw = raw;
  }
  getModule(name, platform, supportsNativePlatform, type) {
    if (type == null) {
      type = _constants.default.MODULE;
    }
    const module = this._getModuleMetadata(name, platform, !!supportsNativePlatform);
    if (module && module[_constants.default.TYPE] === type) {
      const modulePath = module[_constants.default.PATH];
      return modulePath && fastPath.resolve(this._raw.rootDir, modulePath);
    }
    return null;
  }
  getPackage(name, platform, _supportsNativePlatform) {
    return this.getModule(name, platform, null, _constants.default.PACKAGE);
  }
  getMockModule(name) {
    const mockPath = this._raw.mocks.get(name) || this._raw.mocks.get(`${name}/index`);
    return mockPath && fastPath.resolve(this._raw.rootDir, mockPath);
  }
  getRawModuleMap() {
    return {
      duplicates: this._raw.duplicates,
      map: this._raw.map,
      mocks: this._raw.mocks,
      rootDir: this._raw.rootDir
    };
  }
  toJSON() {
    if (!this.json) {
      this.json = {
        duplicates: ModuleMap.mapToArrayRecursive(this._raw.duplicates),
        map: [...this._raw.map],
        mocks: [...this._raw.mocks],
        rootDir: this._raw.rootDir
      };
    }
    return this.json;
  }
  static fromJSON(serializableModuleMap) {
    return new ModuleMap({
      duplicates: ModuleMap.mapFromArrayRecursive(serializableModuleMap.duplicates),
      map: new Map(serializableModuleMap.map),
      mocks: new Map(serializableModuleMap.mocks),
      rootDir: serializableModuleMap.rootDir
    });
  }

  /**
   * When looking up a module's data, we walk through each eligible platform for
   * the query. For each platform, we want to check if there are known
   * duplicates for that name+platform pair. The duplication logic normally
   * removes elements from the `map` object, but we want to check upfront to be
   * extra sure. If metadata exists both in the `duplicates` object and the
   * `map`, this would be a bug.
   */
  _getModuleMetadata(name, platform, supportsNativePlatform) {
    const map = this._raw.map.get(name) || EMPTY_OBJ;
    const dupMap = this._raw.duplicates.get(name) || EMPTY_MAP;
    if (platform != null) {
      this._assertNoDuplicates(name, platform, supportsNativePlatform, dupMap.get(platform));
      if (map[platform] != null) {
        return map[platform];
      }
    }
    if (supportsNativePlatform) {
      this._assertNoDuplicates(name, _constants.default.NATIVE_PLATFORM, supportsNativePlatform, dupMap.get(_constants.default.NATIVE_PLATFORM));
      if (map[_constants.default.NATIVE_PLATFORM]) {
        return map[_constants.default.NATIVE_PLATFORM];
      }
    }
    this._assertNoDuplicates(name, _constants.default.GENERIC_PLATFORM, supportsNativePlatform, dupMap.get(_constants.default.GENERIC_PLATFORM));
    if (map[_constants.default.GENERIC_PLATFORM]) {
      return map[_constants.default.GENERIC_PLATFORM];
    }
    return null;
  }
  _assertNoDuplicates(name, platform, supportsNativePlatform, relativePathSet) {
    if (relativePathSet == null) {
      return;
    }
    // Force flow refinement
    const previousSet = relativePathSet;
    const duplicates = new Map();
    for (const [relativePath, type] of previousSet) {
      const duplicatePath = fastPath.resolve(this._raw.rootDir, relativePath);
      duplicates.set(duplicatePath, type);
    }
    throw new DuplicateHasteCandidatesError(name, platform, supportsNativePlatform, duplicates);
  }
  static create(rootDir) {
    return new ModuleMap({
      duplicates: new Map(),
      map: new Map(),
      mocks: new Map(),
      rootDir
    });
  }
}
exports["default"] = ModuleMap;
class DuplicateHasteCandidatesError extends Error {
  hasteName;
  platform;
  supportsNativePlatform;
  duplicatesSet;
  constructor(name, platform, supportsNativePlatform, duplicatesSet) {
    const platformMessage = getPlatformMessage(platform);
    super(`The name \`${name}\` was looked up in the Haste module map. It ` + 'cannot be resolved, because there exists several different ' + 'files, or packages, that provide a module for ' + `that particular name and platform. ${platformMessage} You must ` + `delete or exclude files until there remains only one of these:\n\n${[...duplicatesSet].map(([dupFilePath, dupFileType]) => `  * \`${dupFilePath}\` (${getTypeMessage(dupFileType)})\n`).sort().join('')}`);
    this.hasteName = name;
    this.platform = platform;
    this.supportsNativePlatform = supportsNativePlatform;
    this.duplicatesSet = duplicatesSet;
  }
}
function getPlatformMessage(platform) {
  if (platform === _constants.default.GENERIC_PLATFORM) {
    return 'The platform is generic (no extension).';
  }
  return `The platform extension is \`${platform}\`.`;
}
function getTypeMessage(type) {
  switch (type) {
    case _constants.default.MODULE:
      return 'module';
    case _constants.default.PACKAGE:
      return 'package';
  }
  return 'unknown';
}
ModuleMap.DuplicateHasteCandidatesError = DuplicateHasteCandidatesError;

/***/ },

/***/ "./src/blacklist.ts"
(__unused_webpack_module, exports) {



Object.defineProperty(exports, "__esModule", ({
  value: true
}));
exports["default"] = void 0;
/**
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

// This list is compiled after the MDN list of the most common MIME types (see
// https://developer.mozilla.org/en-US/docs/Web/HTTP/Basics_of_HTTP/MIME_types/
// Complete_list_of_MIME_types).
//
// Only MIME types starting with "image/", "video/", "audio/" and "font/" are
// reflected in the list. Adding "application/" is too risky since some text
// file formats (like ".js" and ".json") have an "application/" MIME type.
//
// Feel free to add any extensions that cannot be a Haste module.

const extensions = new Set([
// JSONs are never haste modules, except for "package.json", which is handled.
'.json',
// Image extensions.
'.bmp', '.gif', '.ico', '.jpeg', '.jpg', '.png', '.svg', '.tiff', '.tif', '.webp',
// Video extensions.
'.avi', '.mp4', '.mpeg', '.mpg', '.ogv', '.webm', '.3gp', '.3g2',
// Audio extensions.
'.aac', '.midi', '.mid', '.mp3', '.oga', '.wav', '.3gp', '.3g2',
// Font extensions.
'.eot', '.otf', '.ttf', '.woff', '.woff2']);
var _default = exports["default"] = extensions;

/***/ },

/***/ "./src/constants.ts"
(__unused_webpack_module, exports) {



Object.defineProperty(exports, "__esModule", ({
  value: true
}));
exports["default"] = void 0;
/**
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

/*
 * This file exports a set of constants that are used for Jest's haste map
 * serialization. On very large repositories, the haste map cache becomes very
 * large to the point where it is the largest overhead in starting up Jest.
 *
 * This constant key map allows to keep the map smaller without having to build
 * a custom serialization library.
 */

/* eslint-disable sort-keys */
const constants = {
  /* dependency serialization */
  DEPENDENCY_DELIM: '\0',
  /* file map attributes */
  ID: 0,
  MTIME: 1,
  SIZE: 2,
  VISITED: 3,
  DEPENDENCIES: 4,
  SHA1: 5,
  /* module map attributes */
  PATH: 0,
  TYPE: 1,
  /* module types */
  MODULE: 0,
  PACKAGE: 1,
  /* platforms */
  GENERIC_PLATFORM: 'g',
  NATIVE_PLATFORM: 'native'
};
/* eslint-enable */
var _default = exports["default"] = constants;

/***/ },

/***/ "./src/crawlers/index.ts"
(__unused_webpack_module, exports, __webpack_require__) {



Object.defineProperty(exports, "__esModule", ({
  value: true
}));
exports.crawl = crawl;
var _node = __webpack_require__("./src/crawlers/node.ts");
var _watchman = __webpack_require__("./src/crawlers/watchman.ts");
/**
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

async function crawl(crawlerOptions, useWatchman) {
  const crawlFn = useWatchman ? _watchman.watchmanCrawl : _node.nodeCrawl;
  const retry = retryError => {
    if (crawlFn === _watchman.watchmanCrawl) {
      crawlerOptions.console.warn('jest-haste-map: Watchman crawl failed. Retrying once with node ' + 'crawler.\n' + "  Usually this happens when watchman isn't running. Create an " + "empty `.watchmanconfig` file in your project's root folder or " + 'initialize a git or hg repository in your project.\n' + `  ${retryError}`);
      return (0, _node.nodeCrawl)(crawlerOptions).catch(error => {
        throw new Error('Crawler retry failed:\n' + `  Original error: ${retryError.message}\n` + `  Retry error: ${error.message}\n`);
      });
    }
    throw retryError;
  };
  try {
    return await crawlFn(crawlerOptions);
  } catch (error) {
    return retry(error);
  }
}

/***/ },

/***/ "./src/crawlers/node.ts"
(__unused_webpack_module, exports, __webpack_require__) {



Object.defineProperty(exports, "__esModule", ({
  value: true
}));
exports.nodeCrawl = nodeCrawl;
function _nodeChild_process() {
  const data = require("node:child_process");
  _nodeChild_process = function () {
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
function fs() {
  const data = _interopRequireWildcard(require("graceful-fs"));
  fs = function () {
    return data;
  };
  return data;
}
var _constants = _interopRequireDefault(__webpack_require__("./src/constants.ts"));
var fastPath = _interopRequireWildcard(__webpack_require__("./src/lib/fast_path.ts"));
var _walk = __webpack_require__("./src/lib/walk.ts");
function _interopRequireDefault(e) { return e && e.__esModule ? e : { default: e }; }
function _interopRequireWildcard(e, t) { if ("function" == typeof WeakMap) var r = new WeakMap(), n = new WeakMap(); return (_interopRequireWildcard = function (e, t) { if (!t && e && e.__esModule) return e; var o, i, f = { __proto__: null, default: e }; if (null === e || "object" != typeof e && "function" != typeof e) return f; if (o = t ? n : r) { if (o.has(e)) return o.get(e); o.set(e, f); } for (const t in e) "default" !== t && {}.hasOwnProperty.call(e, t) && ((i = (o = Object.defineProperty) && Object.getOwnPropertyDescriptor(e, t)) && (i.get || i.set) ? o(f, t, i) : f[t] = e[t]); return f; })(e, t); }
/**
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

async function hasNativeFindSupport(forceNodeFilesystemAPI) {
  if (forceNodeFilesystemAPI) {
    return false;
  }
  try {
    return await new Promise(resolve => {
      // Check the find binary supports the non-POSIX -iname parameter wrapped in parens.
      const args = ['.', '-type', 'f', '(', '-iname', '*.ts', '-o', '-iname', '*.js', ')'];
      const child = (0, _nodeChild_process().spawn)('find', args, {
        cwd: __dirname
      });
      child.on('error', () => {
        resolve(false);
      });
      child.on('exit', code => {
        resolve(code === 0);
      });
    });
  } catch {
    return false;
  }
}
function find(roots, extensions, ignore, enableSymlinks, callback) {
  const extSet = new Set(extensions);
  const result = [];
  const statCache = new Map();
  let remaining = roots.length;
  if (remaining === 0) {
    callback(result);
    return;
  }
  for (const root of roots) {
    (0, _walk.walk)({
      enableSymlinks,
      exclude: ignore,
      onEntry: (kind, filePath, stats) => {
        if (kind === 'file' && extSet.has(path().extname(filePath).slice(1))) {
          result.push([filePath, stats.mtime.getTime(), stats.size]);
        }
      },
      root,
      statCache
    }, () => {
      remaining--;
      if (remaining === 0) {
        callback(result);
      }
    });
  }
}
function findNative(roots, extensions, ignore, enableSymlinks, callback) {
  const args = [...roots];
  if (enableSymlinks) {
    args.push('(', '-type', 'f', '-o', '-type', 'l', ')');
  } else {
    args.push('-type', 'f');
  }
  if (extensions.length > 0) {
    args.push('(');
  }
  for (const [index, ext] of extensions.entries()) {
    if (index) {
      args.push('-o');
    }
    args.push('-iname', `*.${ext}`);
  }
  if (extensions.length > 0) {
    args.push(')');
  }
  const child = (0, _nodeChild_process().spawn)('find', args);
  if (child.stdout === null) {
    throw new Error('stdout is null - this should never happen. Please open up an issue at https://github.com/jestjs/jest');
  }
  child.stdout.setEncoding('utf8');
  const chunks = [];
  child.stdout.on('data', data => chunks.push(data));
  child.stdout.on('close', () => {
    const lines = chunks.join('').trim().split('\n').filter(x => x && !ignore(x));
    const result = [];
    let count = lines.length;
    if (count) {
      for (const path of lines) {
        fs().stat(path, (err, stat) => {
          // Filter out symlinks that describe directories
          if (!err && stat && !stat.isDirectory()) {
            result.push([path, stat.mtime.getTime(), stat.size]);
          }
          if (--count === 0) {
            callback(result);
          }
        });
      }
    } else {
      callback([]);
    }
  });
}
async function nodeCrawl(options) {
  const {
    data,
    enableSymlinks,
    extensions,
    forceNodeFilesystemAPI,
    ignore,
    rootDir,
    roots
  } = options;
  const useNativeFind = await hasNativeFindSupport(forceNodeFilesystemAPI);
  return new Promise(resolve => {
    const callback = list => {
      const files = new Map();
      const removedFiles = new Map(data.files);
      for (const fileData of list) {
        const [filePath, mtime, size] = fileData;
        const relativeFilePath = fastPath.relative(rootDir, filePath);
        const existingFile = data.files.get(relativeFilePath);
        if (existingFile && existingFile[_constants.default.MTIME] === mtime) {
          files.set(relativeFilePath, existingFile);
        } else {
          // See ../constants.js; SHA-1 will always be null and fulfilled later.
          files.set(relativeFilePath, ['', mtime, size, 0, '', null]);
        }
        removedFiles.delete(relativeFilePath);
      }
      data.files = files;
      resolve({
        hasteMap: data,
        removedFiles
      });
    };
    if (useNativeFind) {
      // TODO: consider making forceNodeFilesystemAPI the default. find(1) does
      // not receive the ignore predicate, so it traverses ignored directories
      // (e.g. node_modules, .git) in full and discards results afterward.
      // find() via fdir prunes those subtrees at readdir time. For a typical
      // project where node_modules dwarfs source files, the wasted traversal
      // likely outweighs find(1)'s native speed advantage.
      findNative(roots, extensions, ignore, enableSymlinks, callback);
    } else {
      find(roots, extensions, ignore, enableSymlinks, callback);
    }
  });
}

/***/ },

/***/ "./src/crawlers/watchman.ts"
(__unused_webpack_module, exports, __webpack_require__) {



Object.defineProperty(exports, "__esModule", ({
  value: true
}));
exports.watchmanCrawl = watchmanCrawl;
function path() {
  const data = _interopRequireWildcard(require("node:path"));
  path = function () {
    return data;
  };
  return data;
}
function watchman() {
  const data = _interopRequireWildcard(require("fb-watchman"));
  watchman = function () {
    return data;
  };
  return data;
}
var _constants = _interopRequireDefault(__webpack_require__("./src/constants.ts"));
var fastPath = _interopRequireWildcard(__webpack_require__("./src/lib/fast_path.ts"));
var _normalizePathSep = _interopRequireDefault(__webpack_require__("./src/lib/normalizePathSep.ts"));
var _watchmanSockname = __webpack_require__("./src/lib/watchmanSockname.ts");
function _interopRequireDefault(e) { return e && e.__esModule ? e : { default: e }; }
function _interopRequireWildcard(e, t) { if ("function" == typeof WeakMap) var r = new WeakMap(), n = new WeakMap(); return (_interopRequireWildcard = function (e, t) { if (!t && e && e.__esModule) return e; var o, i, f = { __proto__: null, default: e }; if (null === e || "object" != typeof e && "function" != typeof e) return f; if (o = t ? n : r) { if (o.has(e)) return o.get(e); o.set(e, f); } for (const t in e) "default" !== t && {}.hasOwnProperty.call(e, t) && ((i = (o = Object.defineProperty) && Object.getOwnPropertyDescriptor(e, t)) && (i.get || i.set) ? o(f, t, i) : f[t] = e[t]); return f; })(e, t); }
/**
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

const watchmanURL = 'https://facebook.github.io/watchman/docs/troubleshooting';
function watchmanError(error) {
  error.message = `Watchman error: ${error.message.trim()}. Make sure watchman ` + `is running for this project. See ${watchmanURL}.`;
  return error;
}

/**
 * Wrap watchman capabilityCheck method as a promise.
 *
 * @param client watchman client
 * @param caps capabilities to verify
 * @returns a promise resolving to a list of verified capabilities
 */
async function capabilityCheck(client, caps) {
  return new Promise((resolve, reject) => {
    client.capabilityCheck(
    // @ts-expect-error: incorrectly typed
    caps, (error, response) => {
      if (error) {
        reject(error);
      } else {
        resolve(response);
      }
    });
  });
}
async function watchmanCrawl(options) {
  const fields = ['name', 'exists', 'mtime_ms', 'size'];
  const {
    console,
    data,
    extensions,
    ignore,
    rootDir,
    roots
  } = options;
  const defaultWatchExpression = ['allof', ['type', 'f']];
  const clocks = data.clocks;
  const client = new (watchman().Client)();

  // fb-watchman reports connection and daemon-startup failures as a client
  // `error` event rather than through a command callback. Without a listener
  // already attached, the first such failure is an unhandled `error` that
  // takes the process down instead of letting the caller retry with the node
  // crawler, so subscribe before issuing any command.
  let clientError;
  client.on('error', error => clientError = watchmanError(error));
  if (options.watchmanSockname !== undefined) {
    (0, _watchmanSockname.connectClientToSockname)(client, options.watchmanSockname);
  }
  const cmd = (...args) => new Promise((resolve, reject) =>
  // @ts-expect-error: client is typed strictly, but incomplete
  client.command(args, (error, result) => error ? reject(watchmanError(error)) : resolve(result)));
  async function getWatchmanRoots(roots) {
    const watchmanRoots = new Map();
    await Promise.all(roots.map(async root => {
      const response = await cmd('watch-project', root);
      const existing = watchmanRoots.get(response.watch);
      // A root can only be filtered if it was never seen with a
      // relative_path before.
      const canBeFiltered = !existing || existing.length > 0;
      if (canBeFiltered) {
        if (response.relative_path) {
          watchmanRoots.set(response.watch, [...(existing || []), response.relative_path]);
        } else {
          // Make the filter directories an empty array to signal that this
          // root was already seen and needs to be watched for all files or
          // directories.
          watchmanRoots.set(response.watch, []);
        }
      }
    }));
    return watchmanRoots;
  }
  async function queryWatchmanForDirs(rootProjectDirMappings) {
    const results = new Map();
    let isFresh = false;
    await Promise.all([...rootProjectDirMappings].map(async ([root, directoryFilters]) => {
      const expression = [...defaultWatchExpression];
      const glob = [];
      if (directoryFilters.length > 0) {
        expression.push(['anyof', ...directoryFilters.map(dir => ['dirname', dir])]);
        for (const directory of directoryFilters) {
          for (const extension of extensions) {
            glob.push(`${directory}/**/*.${extension}`);
          }
        }
      } else {
        for (const extension of extensions) {
          glob.push(`**/*.${extension}`);
        }
      }

      // Jest is only going to store one type of clock; a string that
      // represents a local clock. However, the Watchman crawler supports
      // a second type of clock that can be written by automation outside of
      // Jest, called an "scm query", which fetches changed files based on
      // source control mergebases. The reason this is necessary is because
      // local clocks are not portable across systems, but scm queries are.
      // By using scm queries, we can create the haste map on a different
      // system and import it, transforming the clock into a local clock.
      const since = clocks.get(fastPath.relative(rootDir, root));
      const query = since === undefined ?
      // Without a clock, enumerate everything matching the globs
      {
        expression,
        fields,
        glob,
        glob_includedotfiles: true
      } :
      // With a clock available, ask only for the delta since then
      {
        expression,
        fields,
        since
      };
      const response = await cmd('query', root, query);
      if ('warning' in response) {
        console.warn('watchman warning:', response.warning);
      }

      // When a source-control query is used, we ignore the "is fresh"
      // response from Watchman because it will be true despite the query
      // being incremental.
      const isSourceControlQuery = typeof since !== 'string' && since?.scm?.['mergebase-with'] !== undefined;
      if (!isSourceControlQuery) {
        isFresh = isFresh || response.is_fresh_instance;
      }
      results.set(root, response);
    }));
    return {
      isFresh,
      results
    };
  }
  let files = data.files;
  let removedFiles = new Map();
  const changedFiles = new Map();
  let results;
  let isFresh = false;
  try {
    // https://facebook.github.io/watchman/docs/capabilities.html
    // Check adds about ~28ms
    const capabilities = await capabilityCheck(client, {
      // If a required capability is missing then an error will be thrown,
      // we don't need this assertion, so using optional instead.
      optional: ['suffix-set']
    });
    if (capabilities?.capabilities['suffix-set']) {
      // If available, use the optimized `suffix-set` operation:
      // https://facebook.github.io/watchman/docs/expr/suffix.html#suffix-set
      defaultWatchExpression.push(['suffix', extensions]);
    } else {
      // Otherwise use the older and less optimal suffix tuple array
      defaultWatchExpression.push(['anyof', ...extensions.map(extension => ['suffix', extension])]);
    }
    if (options.computeSha1) {
      const {
        capabilities: listedCapabilities
      } = await cmd('list-capabilities');
      if (listedCapabilities.includes('field-content.sha1hex')) {
        fields.push('content.sha1hex');
      }
    }
    const watchmanRoots = await getWatchmanRoots(roots);
    const watchmanFileResults = await queryWatchmanForDirs(watchmanRoots);

    // Reset the file map if watchman was restarted and sends us a list of
    // files.
    if (watchmanFileResults.isFresh) {
      files = new Map();
      removedFiles = new Map(data.files);
      isFresh = true;
    }
    results = watchmanFileResults.results;
  } finally {
    client.end();
  }
  if (clientError) {
    throw clientError;
  }
  for (const [watchRoot, response] of results) {
    const fsRoot = (0, _normalizePathSep.default)(watchRoot);
    const relativeFsRoot = fastPath.relative(rootDir, fsRoot);
    clocks.set(relativeFsRoot,
    // Ensure we persist only the local clock.
    typeof response.clock === 'string' ? response.clock : response.clock.clock);
    for (const fileData of response.files) {
      const filePath = fsRoot + path().sep + (0, _normalizePathSep.default)(fileData.name);
      const relativeFilePath = fastPath.relative(rootDir, filePath);
      const existingFileData = data.files.get(relativeFilePath);

      // If watchman is fresh, the removed files map starts with all files
      // and we remove them as we verify they still exist.
      if (isFresh && existingFileData && fileData.exists) {
        removedFiles.delete(relativeFilePath);
      }
      if (!fileData.exists) {
        // No need to act on files that do not exist and were not tracked.
        if (existingFileData) {
          files.delete(relativeFilePath);

          // If watchman is not fresh, we will know what specific files were
          // deleted since we last ran and can track only those files.
          if (!isFresh) {
            removedFiles.set(relativeFilePath, existingFileData);
          }
        }
      } else if (!ignore(filePath)) {
        const mtime = typeof fileData.mtime_ms === 'number' ? fileData.mtime_ms : fileData.mtime_ms.toNumber();
        const size = fileData.size;
        let sha1hex = fileData['content.sha1hex'];
        if (typeof sha1hex !== 'string' || sha1hex.length !== 40) {
          sha1hex = undefined;
        }
        let nextData;
        if (existingFileData && existingFileData[_constants.default.MTIME] === mtime) {
          nextData = existingFileData;
        } else if (existingFileData && sha1hex && existingFileData[_constants.default.SHA1] === sha1hex) {
          nextData = [existingFileData[0], mtime, existingFileData[2], existingFileData[3], existingFileData[4], existingFileData[5]];
        } else {
          // See ../constants.ts
          nextData = ['', mtime, size, 0, '', sha1hex ?? null];
        }
        files.set(relativeFilePath, nextData);
        changedFiles.set(relativeFilePath, nextData);
      }
    }
  }
  data.files = files;
  return {
    changedFiles: isFresh ? undefined : changedFiles,
    hasteMap: data,
    removedFiles
  };
}

/***/ },

/***/ "./src/getMockName.ts"
(__unused_webpack_module, exports) {



Object.defineProperty(exports, "__esModule", ({
  value: true
}));
exports["default"] = void 0;
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

const MOCKS_PATTERN = `${path().sep}__mocks__${path().sep}`;
const getMockName = filePath => {
  const mockPath = filePath.split(MOCKS_PATTERN)[1];
  return mockPath.slice(0, mockPath.lastIndexOf(path().extname(mockPath))).replaceAll('\\', '/');
};
var _default = exports["default"] = getMockName;

/***/ },

/***/ "./src/lib/CacheManager.ts"
(__unused_webpack_module, exports, __webpack_require__) {



Object.defineProperty(exports, "__esModule", ({
  value: true
}));
exports.CacheManager = void 0;
function _nodeV() {
  const data = require("node:v8");
  _nodeV = function () {
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
var _util = __webpack_require__("./src/lib/util.ts");
/**
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

class CacheManager {
  _cachePath;
  constructor(cachePath) {
    this._cachePath = cachePath;
  }
  get path() {
    return this._cachePath;
  }
  read() {
    let hasteMap;
    try {
      hasteMap = (0, _nodeV().deserialize)((0, _gracefulFs().readFileSync)(this._cachePath));
    } catch {
      return (0, _util.createEmptyMap)();
    }
    // A cache written before `mockDuplicates` existed has no record of which
    // files claim which mock name. Defaulting it to empty would read as "no
    // duplicates anywhere" and leave watch mode unable to recover one, so treat
    // it as a miss and let the crawl derive the claims again.
    if (hasteMap.mockDuplicates == null) {
      return (0, _util.createEmptyMap)();
    }
    return hasteMap;
  }
  persist(hasteMap) {
    (0, _gracefulFs().writeFileSync)(this._cachePath, (0, _nodeV().serialize)(hasteMap));
  }
}
exports.CacheManager = CacheManager;

/***/ },

/***/ "./src/lib/FileProcessor.ts"
(__unused_webpack_module, exports, __webpack_require__) {



Object.defineProperty(exports, "__esModule", ({
  value: true
}));
exports.FileProcessor = exports.DuplicateError = void 0;
function path() {
  const data = _interopRequireWildcard(require("node:path"));
  path = function () {
    return data;
  };
  return data;
}
var _constants = _interopRequireDefault(__webpack_require__("./src/constants.ts"));
var _getMockName = _interopRequireDefault(__webpack_require__("./src/getMockName.ts"));
var fastPath = _interopRequireWildcard(__webpack_require__("./src/lib/fast_path.ts"));
var _getPlatformExtension = _interopRequireDefault(__webpack_require__("./src/lib/getPlatformExtension.ts"));
var _isIgnorableFileError = __webpack_require__("./src/lib/isIgnorableFileError.ts");
function _interopRequireDefault(e) { return e && e.__esModule ? e : { default: e }; }
function _interopRequireWildcard(e, t) { if ("function" == typeof WeakMap) var r = new WeakMap(), n = new WeakMap(); return (_interopRequireWildcard = function (e, t) { if (!t && e && e.__esModule) return e; var o, i, f = { __proto__: null, default: e }; if (null === e || "object" != typeof e && "function" != typeof e) return f; if (o = t ? n : r) { if (o.has(e)) return o.get(e); o.set(e, f); } for (const t in e) "default" !== t && {}.hasOwnProperty.call(e, t) && ((i = (o = Object.defineProperty) && Object.getOwnPropertyDescriptor(e, t)) && (i.get || i.set) ? o(f, t, i) : f[t] = e[t]); return f; })(e, t); }
/**
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

const NODE_MODULES = `${path().sep}node_modules${path().sep}`;
const PACKAGE_JSON = `${path().sep}package.json`;
class DuplicateError extends Error {
  mockPath1;
  mockPath2;
  constructor(mockPath1, mockPath2) {
    super('Duplicated files or mocks. Please check the console for more info');
    this.mockPath1 = mockPath1;
    this.mockPath2 = mockPath2;
  }
}
exports.DuplicateError = DuplicateError;
class FileProcessor {
  _console;
  _options;
  _workerPool;
  constructor(options, console, workerPool) {
    this._console = console;
    this._options = options;
    this._workerPool = workerPool;
  }
  processFile(hasteMap, map, mocks, filePath, workerOptions) {
    const rootDir = this._options.rootDir;
    const setModule = (id, module) => {
      let moduleMap = map.get(id);
      if (!moduleMap) {
        moduleMap = Object.create(null);
        map.set(id, moduleMap);
      }
      const platform = (0, _getPlatformExtension.default)(module[_constants.default.PATH], this._options.platforms) || _constants.default.GENERIC_PLATFORM;
      const existingModule = moduleMap[platform];
      if (existingModule && existingModule[_constants.default.PATH] !== module[_constants.default.PATH]) {
        const method = this._options.throwOnModuleCollision ? 'error' : 'warn';
        this._console[method]([`jest-haste-map: Haste module naming collision: ${id}`, '  The following files share their name; please adjust your hasteImpl:', `    * <rootDir>${path().sep}${existingModule[_constants.default.PATH]}`, `    * <rootDir>${path().sep}${module[_constants.default.PATH]}`, ''].join('\n'));
        if (this._options.throwOnModuleCollision) {
          throw new DuplicateError(existingModule[_constants.default.PATH], module[_constants.default.PATH]);
        }

        // We do NOT want consumers to use a module that is ambiguous.
        delete moduleMap[platform];
        if (Object.keys(moduleMap).length === 0) {
          map.delete(id);
        }
        let dupsByPlatform = hasteMap.duplicates.get(id);
        if (dupsByPlatform == null) {
          dupsByPlatform = new Map();
          hasteMap.duplicates.set(id, dupsByPlatform);
        }
        const dups = new Map([[module[_constants.default.PATH], module[_constants.default.TYPE]], [existingModule[_constants.default.PATH], existingModule[_constants.default.TYPE]]]);
        dupsByPlatform.set(platform, dups);
        return;
      }
      const dupsByPlatform = hasteMap.duplicates.get(id);
      if (dupsByPlatform != null) {
        const dups = dupsByPlatform.get(platform);
        if (dups != null) {
          dups.set(module[_constants.default.PATH], module[_constants.default.TYPE]);
        }
        return;
      }
      moduleMap[platform] = module;
    };
    const relativeFilePath = fastPath.relative(rootDir, filePath);
    const fileMetadata = hasteMap.files.get(relativeFilePath);
    if (!fileMetadata) {
      throw new Error('jest-haste-map: File to process was not found in the haste map.');
    }
    const moduleMetadata = hasteMap.map.get(fileMetadata[_constants.default.ID]);
    const computeSha1 = this._options.computeSha1 && !fileMetadata[_constants.default.SHA1];
    const workerReply = metadata => {
      // `1` for truthy values instead of `true` to save cache space.
      fileMetadata[_constants.default.VISITED] = 1;
      const metadataId = metadata.id;
      const metadataModule = metadata.module;
      if (metadataId && metadataModule) {
        fileMetadata[_constants.default.ID] = metadataId;
        setModule(metadataId, metadataModule);
      }
      fileMetadata[_constants.default.DEPENDENCIES] = metadata.dependencies ? metadata.dependencies.join(_constants.default.DEPENDENCY_DELIM) : '';
      if (computeSha1) {
        fileMetadata[_constants.default.SHA1] = metadata.sha1;
      }
    };
    const workerError = error => {
      if (typeof error !== 'object' || !error.message || !error.stack) {
        error = new Error(error);
        error.stack = ''; // Remove stack for stack-less errors.
      }

      // `EACCES` is not in `isIgnorableFileError`: an unreadable file is fatal
      // to a watcher, but here it only means this one file cannot be indexed.
      if (error.code !== 'EACCES' && !(0, _isIgnorableFileError.isIgnorableFileError)(error)) {
        throw error;
      }

      // If a file cannot be read we remove it from the file list and
      // ignore the failure silently.
      hasteMap.files.delete(relativeFilePath);
    };

    // If we retain all files in the virtual HasteFS representation, we avoid
    // reading them if they aren't important (node_modules).
    if (this._options.retainAllFiles && filePath.includes(NODE_MODULES)) {
      if (computeSha1) {
        return this._getWorker(workerOptions).getSha1({
          computeDependencies: this._options.computeDependencies,
          computeSha1,
          dependencyExtractor: this._options.dependencyExtractor,
          filePath,
          hasteImplModulePath: this._options.hasteImplModulePath,
          rootDir
        }).then(workerReply, workerError);
      }
      return null;
    }
    if (this._options.mocksPattern && this._options.mocksPattern.test(filePath)) {
      const mockPath = (0, _getMockName.default)(filePath);
      const existingMockPath = mocks.get(mockPath);
      if (existingMockPath) {
        const secondMockPath = fastPath.relative(rootDir, filePath);
        if (existingMockPath !== secondMockPath) {
          const method = this._options.throwOnModuleCollision ? 'error' : 'warn';
          this._console[method]([`jest-haste-map: duplicate manual mock found: ${mockPath}`, '  The following files share their name; please delete one of them:', `    * <rootDir>${path().sep}${existingMockPath}`, `    * <rootDir>${path().sep}${secondMockPath}`, ''].join('\n'));
          if (this._options.throwOnModuleCollision) {
            throw new DuplicateError(existingMockPath, secondMockPath);
          }
          let duplicates = hasteMap.mockDuplicates.get(mockPath);
          if (duplicates == null) {
            duplicates = new Set();
            hasteMap.mockDuplicates.set(mockPath, duplicates);
          }
          duplicates.add(existingMockPath).add(secondMockPath);
        }
      }
      mocks.set(mockPath, relativeFilePath);
    }
    if (fileMetadata[_constants.default.VISITED]) {
      if (!fileMetadata[_constants.default.ID]) {
        return null;
      }
      const moduleId = fileMetadata[_constants.default.ID];
      const platform = (0, _getPlatformExtension.default)(filePath, this._options.platforms) || _constants.default.GENERIC_PLATFORM;
      if (moduleMetadata != null) {
        const module = moduleMetadata[platform];
        if (module == null) {
          return null;
        }
        let modulesByPlatform = map.get(moduleId);
        if (!modulesByPlatform) {
          modulesByPlatform = Object.create(null);
          map.set(moduleId, modulesByPlatform);
        }
        modulesByPlatform[platform] = module;
        return null;
      }

      // A haste name involved in a collision has no `map` entry — it was
      // deleted when the collision was recorded — but `duplicates` survives a
      // rebuild and already lists this file, so its metadata is up to date and
      // re-extracting it would change nothing.
      if (hasteMap.duplicates.get(moduleId)?.get(platform)?.has(relativeFilePath)) {
        return null;
      }
    }
    return this._getWorker(workerOptions).worker({
      computeDependencies: this._options.computeDependencies,
      computeSha1,
      dependencyExtractor: this._options.dependencyExtractor,
      filePath,
      hasteImplModulePath: this._options.hasteImplModulePath,
      rootDir
    }).then(workerReply, workerError);
  }
  buildHasteMap(data, recoverDuplicates) {
    const {
      removedFiles,
      changedFiles,
      hasteMap
    } = data;

    // If any files were removed or we did not track what files changed, process
    // every file looking for changes. Otherwise, process only changed files.
    let map;
    let mocks;
    let filesToProcess;
    if (changedFiles === undefined || removedFiles.size > 0) {
      map = new Map();
      mocks = new Map();
      // Re-derived along with `mocks` below; keeping the old entries would let a
      // file deleted while Jest was not running be promoted back later.
      hasteMap.mockDuplicates = new Map();
      filesToProcess = hasteMap.files;
    } else {
      map = hasteMap.map;
      mocks = hasteMap.mocks;
      filesToProcess = changedFiles;
    }
    for (const [relativeFilePath, fileMetadata] of removedFiles) {
      recoverDuplicates(hasteMap, relativeFilePath, fileMetadata[_constants.default.ID]);
    }
    const promises = [];
    try {
      for (const relativeFilePath of filesToProcess.keys()) {
        if (this._options.skipPackageJson && relativeFilePath.endsWith(PACKAGE_JSON)) {
          continue;
        }
        // SHA-1, if requested, should already be present thanks to the crawler.
        const filePath = fastPath.resolve(this._options.rootDir, relativeFilePath);
        const promise = this.processFile(hasteMap, map, mocks, filePath);
        if (promise) {
          promises.push(promise);
        }
      }
    } catch (error) {
      // `processFile` throws synchronously on a duplicate manual mock when
      // `throwOnModuleCollision` is set, which would otherwise escape before
      // the settlement handlers below can shut the worker farm down.
      this._workerPool.end();
      throw error;
    }
    return Promise.all(promises).then(() => {
      this._workerPool.end();
      hasteMap.map = map;
      hasteMap.mocks = mocks;
      return hasteMap;
    }, error => {
      this._workerPool.end();
      throw error;
    });
  }
  _getWorker(options) {
    return this._workerPool.get(options?.forceInBand);
  }
}
exports.FileProcessor = FileProcessor;

/***/ },

/***/ "./src/lib/WorkerPool.ts"
(__unused_webpack_module, exports, __webpack_require__) {



Object.defineProperty(exports, "__esModule", ({
  value: true
}));
exports.WorkerPool = void 0;
function _jestWorker() {
  const data = require("jest-worker");
  _jestWorker = function () {
    return data;
  };
  return data;
}
var _worker = __webpack_require__("./src/worker.ts");
/**
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

const inBandWorker = {
  getSha1: _worker.getSha1,
  worker: _worker.worker
};
class WorkerPool {
  _options;
  _farm = null;
  constructor(options) {
    this._options = options;
  }
  get(forceInBand) {
    if (forceInBand || this._options.maxWorkers <= 1) {
      return inBandWorker;
    }
    if (!this._farm) {
      this._farm = new (_jestWorker().Worker)(this._options.workerPath, {
        enableWorkerThreads: this._options.workerThreads,
        exposedMethods: ['getSha1', 'worker'],
        forkOptions: {
          serialization: 'json'
        },
        maxRetries: 3,
        numWorkers: this._options.maxWorkers
      });
    }
    return this._farm;
  }
  end() {
    this._farm?.end();
    this._farm = null;
  }
}
exports.WorkerPool = WorkerPool;

/***/ },

/***/ "./src/lib/buildIgnoreMatcher.ts"
(__unused_webpack_module, exports) {



Object.defineProperty(exports, "__esModule", ({
  value: true
}));
exports.buildIgnoreMatcher = buildIgnoreMatcher;
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

const NODE_MODULES = `${path().sep}node_modules${path().sep}`;
function buildIgnoreMatcher(ignorePattern, retainAllFiles) {
  return filePath => {
    const ignoreMatched = ignorePattern instanceof RegExp ? ignorePattern.test(filePath) : ignorePattern && ignorePattern(filePath);
    return Boolean(ignoreMatched) || !retainAllFiles && filePath.includes(NODE_MODULES);
  };
}

/***/ },

/***/ "./src/lib/dependencyExtractor.ts"
(__unused_webpack_module, exports) {



Object.defineProperty(exports, "__esModule", ({
  value: true
}));
exports.extractor = void 0;
/**
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

const NOT_A_DOT = '(?<!\\.\\s*)';
const CAPTURE_STRING_LITERAL = pos => `([\`'"])([^'"\`]*?)(?:\\${pos})`;
const WORD_SEPARATOR = '\\b';
const LEFT_PARENTHESIS = '\\(';
const RIGHT_PARENTHESIS = '\\)';
const WHITESPACE = '\\s*';
const OPTIONAL_COMMA = '(:?,\\s*)?';
function createRegExp(parts, flags) {
  return new RegExp(parts.join(''), flags);
}
function alternatives(...parts) {
  return `(?:${parts.join('|')})`;
}
function functionCallStart(...names) {
  return [NOT_A_DOT, WORD_SEPARATOR, alternatives(...names), WHITESPACE, LEFT_PARENTHESIS, WHITESPACE];
}
const BLOCK_COMMENT_RE = /\/\*[^]*?\*\//g;
const LINE_COMMENT_RE = /\/\/.*/g;
const REQUIRE_OR_DYNAMIC_IMPORT_RE = createRegExp([...functionCallStart('require', 'import'), CAPTURE_STRING_LITERAL(1), WHITESPACE, OPTIONAL_COMMA, RIGHT_PARENTHESIS], 'g');
const IMPORT_OR_EXPORT_RE = createRegExp(['\\b(?:import|export)\\s+(?!type(?:of)?\\s+)(?:[^\'"]+\\s+from\\s+)?', CAPTURE_STRING_LITERAL(1)], 'g');
const JEST_EXTENSIONS_RE = createRegExp([...functionCallStart('jest\\s*\\.\\s*(?:requireActual|requireMock|createMockFromModule)'), CAPTURE_STRING_LITERAL(1), WHITESPACE, OPTIONAL_COMMA, RIGHT_PARENTHESIS], 'g');
const extractor = exports.extractor = {
  extract(code) {
    const dependencies = new Set();
    const addDependency = (match, _, dep) => {
      dependencies.add(dep);
      return match;
    };
    code.replaceAll(BLOCK_COMMENT_RE, '').replaceAll(LINE_COMMENT_RE, '').replace(IMPORT_OR_EXPORT_RE, addDependency).replace(REQUIRE_OR_DYNAMIC_IMPORT_RE, addDependency).replace(JEST_EXTENSIONS_RE, addDependency);
    return dependencies;
  }
};

/***/ },

/***/ "./src/lib/fast_path.ts"
(__unused_webpack_module, exports) {



Object.defineProperty(exports, "__esModule", ({
  value: true
}));
exports.relative = relative;
exports.resolve = resolve;
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

// rootDir and filename must be absolute paths (resolved)
function relative(rootDir, filename) {
  return filename.startsWith(rootDir + path().sep) ? filename.slice(rootDir.length + 1) : path().relative(rootDir, filename);
}
const INDIRECTION_FRAGMENT = `..${path().sep}`;

// rootDir must be an absolute path and relativeFilename must be simple
// (e.g.: foo/bar or ../foo/bar, but never ./foo or foo/../bar)
function resolve(rootDir, relativeFilename) {
  return relativeFilename.startsWith(INDIRECTION_FRAGMENT) ? path().resolve(rootDir, relativeFilename) : rootDir + path().sep + relativeFilename;
}

/***/ },

/***/ "./src/lib/getPlatformExtension.ts"
(__unused_webpack_module, exports) {



Object.defineProperty(exports, "__esModule", ({
  value: true
}));
exports["default"] = getPlatformExtension;
/**
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

const SUPPORTED_PLATFORM_EXTS = new Set(['android', 'ios', 'native', 'web']);

// Extract platform extension: index.ios.js -> ios
function getPlatformExtension(file, platforms) {
  const last = file.lastIndexOf('.');
  const secondToLast = file.lastIndexOf('.', last - 1);
  if (secondToLast === -1) {
    return null;
  }
  const platform = file.slice(secondToLast + 1, last);
  // If an overriding platform array is passed, check that first

  if (platforms && platforms.includes(platform)) {
    return platform;
  }
  return SUPPORTED_PLATFORM_EXTS.has(platform) ? platform : null;
}

/***/ },

/***/ "./src/lib/isIgnorableFileError.ts"
(__unused_webpack_module, exports) {



Object.defineProperty(exports, "__esModule", ({
  value: true
}));
exports.isIgnorableFileError = isIgnorableFileError;
function os() {
  const data = _interopRequireWildcard(require("node:os"));
  os = function () {
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

const platform = os().platform();

/**
 * A file can vanish between the moment it is observed and the moment it is
 * read, which is `ENOENT`. On Windows an outside process holding the file open
 * -- `git maintenance` touching `.git/index.lock` or `.git/objects`, for
 * instance -- surfaces as `EPERM` instead. Neither means anything is wrong with
 * the watcher or the crawl, so neither should abort it.
 */
function isIgnorableFileError(error) {
  return error.code === 'ENOENT' || error.code === 'EPERM' && platform === 'win32';
}

/***/ },

/***/ "./src/lib/normalizePathSep.ts"
(__unused_webpack_module, exports) {



Object.defineProperty(exports, "__esModule", ({
  value: true
}));
exports["default"] = void 0;
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

let normalizePathSep;
if (path().sep === '/') {
  normalizePathSep = filePath => filePath;
} else {
  normalizePathSep = filePath => filePath.replaceAll('/', path().sep);
}
var _default = exports["default"] = normalizePathSep;

/***/ },

/***/ "./src/lib/util.ts"
(__unused_webpack_module, exports) {



Object.defineProperty(exports, "__esModule", ({
  value: true
}));
exports.copy = copy;
exports.copyMap = copyMap;
exports.createEmptyMap = createEmptyMap;
/**
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

function copy(object) {
  return Object.assign(Object.create(null), object);
}
function copyMap(input) {
  return new Map(input);
}
function createEmptyMap() {
  return {
    clocks: new Map(),
    duplicates: new Map(),
    files: new Map(),
    map: new Map(),
    mockDuplicates: new Map(),
    mocks: new Map()
  };
}

/***/ },

/***/ "./src/lib/walk.ts"
(__unused_webpack_module, exports) {



Object.defineProperty(exports, "__esModule", ({
  value: true
}));
exports.walk = walk;
function os() {
  const data = _interopRequireWildcard(require("node:os"));
  os = function () {
    return data;
  };
  return data;
}
function _fdir() {
  const data = require("fdir");
  _fdir = function () {
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
function _interopRequireWildcard(e, t) { if ("function" == typeof WeakMap) var r = new WeakMap(), n = new WeakMap(); return (_interopRequireWildcard = function (e, t) { if (!t && e && e.__esModule) return e; var o, i, f = { __proto__: null, default: e }; if (null === e || "object" != typeof e && "function" != typeof e) return f; if (o = t ? n : r) { if (o.has(e)) return o.get(e); o.set(e, f); } for (const t in e) "default" !== t && {}.hasOwnProperty.call(e, t) && ((i = (o = Object.defineProperty) && Object.getOwnPropertyDescriptor(e, t)) && (i.get || i.set) ? o(f, t, i) : f[t] = e[t]); return f; })(e, t); }
/**
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

const DEFAULT_CONCURRENCY = Math.max(os().availableParallelism() * 4, 32);
// fdir returns directory paths with a trailing separator; strip it.
const TRAILING_SEP_RE = /[/\\]+$/;

// `includeDirs` and the `dir` entry kind have no caller yet: they are for
// symlinked directories, where following a link means walking its target.

function walk(options, done) {
  const {
    concurrency = DEFAULT_CONCURRENCY,
    enableSymlinks = false,
    exclude,
    onEntry,
    onError,
    root,
    statCache,
    ...fdirOpts
  } = options;

  // Wrap exclude to strip fdir's trailing path separator before delegating.
  const normalizedExclude = exclude ? p => exclude(p.replace(TRAILING_SEP_RE, '')) : undefined;
  const builder = new (_fdir().fdir)({
    ...fdirOpts,
    // Used for directory pruning (prevents fdir from recursing into subtrees).
    // fdir calls exclude(dirName, dirPath) — dirName is the basename and dirPath
    // is the full path, so dirName is unused (it's a subset of dirPath).
    exclude: normalizedExclude ? (_dirName, dirPath) => normalizedExclude(dirPath) : undefined,
    excludeSymlinks: !enableSymlinks,
    // Also used as an output filter so ignored paths never enter the stat pool.
    // Applies to both file and dir entries (dirs also have a trailing sep, hence
    // the shared normalizedExclude that strips it). fdir includes entries where
    // the filter returns true, so we negate: include when NOT excluded.
    filters: normalizedExclude ? [path => !normalizedExclude(path)] : [],
    fs: fs(),
    includeBasePath: true,
    // resolveSymlinks: false — `fdir`'s resolveSymlinks calls realpath and emits
    // the resolved path, losing the original symlink path. haste-map must track
    // files under the path Jest uses to require them (the symlink path), so we
    // keep the original path and use fs.stat to follow the symlink for stats.
    resolveSymlinks: false,
    // Unreadable directories are skipped and the walk continues with partial
    // results.
    suppressErrors: true
  });
  const statFn = enableSymlinks ? fs().stat : fs().lstat;
  builder.crawl(root).withCallback((crawlErr, rawPaths) => {
    // suppressErrors: true means crawlErr is always null, but keep the guard
    // as a safety net in case fdir's default changes.
    /* c8 ignore next 4 */
    if (crawlErr != null) {
      done(crawlErr);
      return;
    }

    // Two-phase design (readdir-all via fdir, then stat-all here): fdir does not
    // stat during the crawl. Pipelining lstat with readdir would not improve
    // throughput on large repos anyway — both share libuv's thread pool, which
    // the concurrent readdir calls already saturate.
    let index = 0;
    let inflight = 0;
    // Prevent done() being called twice: once from the last stat callback and
    // once from the post-while guard when concurrency > remaining paths.
    let finished = false;
    function pump() {
      while (inflight < concurrency && index < rawPaths.length) {
        const filePath = rawPaths[index++].replace(TRAILING_SEP_RE, '');
        const cached = statCache?.get(filePath);
        if (cached != null) {
          onEntry(cached.isDirectory() ? 'dir' : 'file', filePath, cached);
          continue;
        }
        inflight++;
        statFn(filePath, (err, stats) => {
          inflight--;
          if (err) {
            onError?.(err);
          } else {
            statCache?.set(filePath, stats);
            onEntry(stats.isDirectory() ? 'dir' : 'file', filePath, stats);
          }
          if (index < rawPaths.length) {
            pump();
          } else if (inflight === 0 && !finished) {
            finished = true;
            done(null);
          }
        });
      }
      if (inflight === 0 && !finished) {
        finished = true;
        done(null);
      }
    }
    pump();
  });
}

/***/ },

/***/ "./src/lib/watchmanSockname.ts"
(__unused_webpack_module, exports) {



Object.defineProperty(exports, "__esModule", ({
  value: true
}));
exports.connectClientToSockname = connectClientToSockname;
exports.getWatchmanAvailability = getWatchmanAvailability;
function _nodeChild_process() {
  const data = require("node:child_process");
  _nodeChild_process = function () {
    return data;
  };
  return data;
}
function net() {
  const data = _interopRequireWildcard(require("node:net"));
  net = function () {
    return data;
  };
  return data;
}
function os() {
  const data = _interopRequireWildcard(require("node:os"));
  os = function () {
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
function _nodeUtil() {
  const data = require("node:util");
  _nodeUtil = function () {
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
function _jestUtil() {
  const data = require("jest-util");
  _jestUtil = function () {
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

const NOT_INSTALLED = {
  installed: false,
  sockname: undefined
};

// Installed, but `get-sockname` failed. Attempting the crawl lets fb-watchman
// surface the failure through the existing warn-and-fall-back path.
const BROKEN = {
  installed: true,
  sockname: undefined
};

// Codes `child_process` reports when the binary itself could not be started.
// Anything else, including a non-zero exit, means watchman is there but not
// answering, which is the `BROKEN` case.
const SPAWN_FAILURE_CODES = new Set(['EACCES', 'ENOENT', 'ENOTDIR', 'EPERM']);

// Connecting to a live local socket takes well under a millisecond, but a
// socket whose listener is stopped or whose backlog is full accepts neither
// outcome, and there is no default timeout. Without a bound the probe would
// hang startup before a single test runs.
const CONNECT_PROBE_TIMEOUT = 1000;
let availabilityPromise;

/**
 * Resolved once per process, not once per `cacheDirectory`: the socket path is
 * a property of the machine and the user, not of any one project. With
 * per-project cache directories the first caller therefore decides where the
 * entry is written, and later callers reuse the resolved value.
 */
function getWatchmanAvailability(cacheDirectory) {
  availabilityPromise ??= resolveAvailability(cacheDirectory);
  return availabilityPromise;
}
async function resolveAvailability(cacheDirectory) {
  const socknameFromEnv = process.env.WATCHMAN_SOCK;
  if (socknameFromEnv) {
    return {
      installed: true,
      sockname: socknameFromEnv
    };
  }
  const cacheFilePath = socknameCacheFilePath(cacheDirectory);
  const cachedSockname = readCachedSockname(cacheFilePath);
  if (cachedSockname !== undefined && (await canConnect(cachedSockname))) {
    return {
      installed: true,
      sockname: cachedSockname
    };
  }
  const availability = await runGetSockname();
  if (availability.sockname === undefined) {
    if (cachedSockname !== undefined) {
      removeCachedSockname(cacheFilePath);
    }
  } else {
    writeCachedSockname(cacheFilePath, availability.sockname);
  }
  return availability;
}
async function runGetSockname() {
  let stdout;
  try {
    ({
      stdout
    } = await (0, _nodeUtil().promisify)(_nodeChild_process().execFile)('watchman', ['--no-pretty', 'get-sockname']));
  } catch (error) {
    if ((0, _jestUtil().isError)(error) && isSpawnFailure(error)) {
      return NOT_INSTALLED;
    }
    return BROKEN;
  }
  try {
    const response = JSON.parse(stdout);
    if (typeof response.sockname === 'string' && response.error == null) {
      return {
        installed: true,
        sockname: response.sockname
      };
    }
  } catch {}
  return BROKEN;
}

/**
 * fb-watchman has no option for passing a known socket path; the only way to
 * skip its `get-sockname` child process is the `WATCHMAN_SOCK` environment
 * variable, which `Client.connect` reads synchronously. Set it just for that
 * synchronous window so no mutation is observable afterwards.
 */
function connectClientToSockname(client, sockname) {
  const previousSockname = process.env.WATCHMAN_SOCK;
  process.env.WATCHMAN_SOCK = sockname;
  try {
    client.connect();
  } finally {
    if (previousSockname === undefined) {
      delete process.env.WATCHMAN_SOCK;
    } else {
      process.env.WATCHMAN_SOCK = previousSockname;
    }
  }
}
function isSpawnFailure(error) {
  const {
    code
  } = error;
  return code !== undefined && SPAWN_FAILURE_CODES.has(code);
}
function currentUserKey() {
  const uid = process.getuid?.();
  if (uid !== undefined) {
    return String(uid);
  }
  try {
    // Windows has no uid. `userInfo` throws when the account has no entry to
    // look up, which a container running as an unmapped user can produce.
    return os().userInfo().username.replaceAll(/\W/g, '-');
  } catch {
    return 'default';
  }
}
function socknameCacheFilePath(cacheDirectory) {
  // The socket path is per-user, so two users sharing a cache directory need
  // separate entries. The name stays predictable, which means a local user can
  // pre-create it as a symlink and redirect the read and the write - the same
  // exposure every other file in this directory already has, since the cache
  // is written with plain `readFileSync`/`writeFileSync` too. Hardening one
  // file in isolation would not buy anything; it needs `CacheManager` as well.
  return path().join(cacheDirectory, `haste-map-watchman-sockname-${currentUserKey()}`);
}
function readCachedSockname(cacheFilePath) {
  let sockname;
  try {
    sockname = fs().readFileSync(cacheFilePath, 'utf8').trim();
  } catch {
    return undefined;
  }
  return sockname.length > 0 ? sockname : undefined;
}
function writeCachedSockname(cacheFilePath, sockname) {
  try {
    fs().writeFileSync(cacheFilePath, sockname);
  } catch {
    // The cache is best-effort; a failed write only costs the next run a
    // `get-sockname` spawn.
  }
}
function removeCachedSockname(cacheFilePath) {
  try {
    fs().rmSync(cacheFilePath, {
      force: true
    });
  } catch {
    // `force` only ignores a missing file, so a read-only cache directory
    // still throws. Availability is resolved before the crawl starts, outside
    // the node-crawler fallback, so letting this escape would fail the run
    // over a cache entry.
  }
}
function canConnect(sockname) {
  return new Promise(resolve => {
    const socket = net().createConnection(sockname, () => {
      socket.destroy();
      resolve(true);
    });
    socket.setTimeout(CONNECT_PROBE_TIMEOUT, () => {
      socket.destroy();
      resolve(false);
    });
    socket.once('error', () => {
      socket.destroy();
      resolve(false);
    });
  });
}

/***/ },

/***/ "./src/watchers/ChangeQueue.ts"
(__unused_webpack_module, exports, __webpack_require__) {



Object.defineProperty(exports, "__esModule", ({
  value: true
}));
exports.ChangeQueue = void 0;
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
var _HasteFS = _interopRequireDefault(__webpack_require__("./src/HasteFS.ts"));
var _ModuleMap = _interopRequireDefault(__webpack_require__("./src/ModuleMap.ts"));
var _constants = _interopRequireDefault(__webpack_require__("./src/constants.ts"));
var _getMockName = _interopRequireDefault(__webpack_require__("./src/getMockName.ts"));
var fastPath = _interopRequireWildcard(__webpack_require__("./src/lib/fast_path.ts"));
var _getPlatformExtension = _interopRequireDefault(__webpack_require__("./src/lib/getPlatformExtension.ts"));
var _normalizePathSep = _interopRequireDefault(__webpack_require__("./src/lib/normalizePathSep.ts"));
var _util = __webpack_require__("./src/lib/util.ts");
function _interopRequireDefault(e) { return e && e.__esModule ? e : { default: e }; }
function _interopRequireWildcard(e, t) { if ("function" == typeof WeakMap) var r = new WeakMap(), n = new WeakMap(); return (_interopRequireWildcard = function (e, t) { if (!t && e && e.__esModule) return e; var o, i, f = { __proto__: null, default: e }; if (null === e || "object" != typeof e && "function" != typeof e) return f; if (o = t ? n : r) { if (o.has(e)) return o.get(e); o.set(e, f); } for (const t in e) "default" !== t && {}.hasOwnProperty.call(e, t) && ((i = (o = Object.defineProperty) && Object.getOwnPropertyDescriptor(e, t)) && (i.get || i.set) ? o(f, t, i) : f[t] = e[t]); return f; })(e, t); }
/**
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

const CHANGE_INTERVAL = 30;
class ChangeQueue {
  _callbacks;
  _extensions;
  _changeInterval;
  _changeQueue = Promise.resolve();
  _eventsQueue = [];
  _pendingEventKeys = new Set();
  _hasteMap;
  // We only need to copy the entire haste map once per "frame".
  _mustCopy = true;
  constructor(hasteMap, extensions, callbacks) {
    this._hasteMap = hasteMap;
    this._extensions = new Set(extensions.map(extension => `.${extension}`));
    this._callbacks = callbacks;
  }
  start() {
    this._changeInterval = setInterval(() => this._emitChange(), CHANGE_INTERVAL);
  }
  stop() {
    if (this._changeInterval) {
      clearInterval(this._changeInterval);
    }
  }
  onChange(type, filePath, root, stat) {
    const {
      ignore,
      rootDir
    } = this._callbacks;
    filePath = path().join(root, (0, _normalizePathSep.default)(filePath));
    if (stat && stat.isDirectory() || ignore(filePath) || !this._extensions.has(path().extname(filePath))) {
      return;
    }
    const relativeFilePath = fastPath.relative(rootDir, filePath);
    const fileMetadata = this._hasteMap.files.get(relativeFilePath);

    // The file has been accessed, not modified.
    if (type === 'change' && fileMetadata && stat && fileMetadata[_constants.default.MTIME] === stat.mtime.getTime()) {
      return;
    }
    this._changeQueue = this._changeQueue.then(() => {
      const dedupKey = `${type}:${filePath}:${stat ? stat.mtime.getTime() : ''}`;
      if (this._pendingEventKeys.has(dedupKey)) {
        return null;
      }
      if (this._mustCopy) {
        this._mustCopy = false;
        this._hasteMap = {
          clocks: new Map(this._hasteMap.clocks),
          duplicates: new Map(this._hasteMap.duplicates),
          files: new Map(this._hasteMap.files),
          map: new Map(this._hasteMap.map),
          mockDuplicates: new Map(this._hasteMap.mockDuplicates),
          mocks: new Map(this._hasteMap.mocks)
        };
      }
      const add = () => {
        this._pendingEventKeys.add(dedupKey);
        this._eventsQueue.push({
          filePath,
          stat,
          type
        });
        return null;
      };
      const currentMetadata = this._hasteMap.files.get(relativeFilePath);

      // If it's not an addition, delete the file and all its metadata.
      if (currentMetadata != null) {
        const moduleName = currentMetadata[_constants.default.ID];
        const platform = (0, _getPlatformExtension.default)(filePath, this._callbacks.platforms) || _constants.default.GENERIC_PLATFORM;
        this._hasteMap.files.delete(relativeFilePath);
        let moduleMap = this._hasteMap.map.get(moduleName);
        if (moduleMap != null) {
          // We are forced to copy the object because jest-haste-map
          // exposes the map as an immutable entity.
          moduleMap = (0, _util.copy)(moduleMap);
          delete moduleMap[platform];
          if (Object.keys(moduleMap).length === 0) {
            this._hasteMap.map.delete(moduleName);
          } else {
            this._hasteMap.map.set(moduleName, moduleMap);
          }
        }
        if (this._callbacks.mocksPattern && this._callbacks.mocksPattern.test(filePath)) {
          this._removeMock((0, _getMockName.default)(filePath), relativeFilePath);
        }
        this._callbacks.recoverDuplicates(this._hasteMap, relativeFilePath, moduleName);
      }

      // If the file was added or changed, parse it and update the haste map.
      if (type === 'add' || type === 'change') {
        (0, _jestUtil().invariant)(stat, 'since the file exists or changed, it should have stats');
        const newMetadata = ['', stat.mtime.getTime(), stat.size, 0, '', null];
        this._hasteMap.files.set(relativeFilePath, newMetadata);
        const promise = this._callbacks.processFile(this._hasteMap, filePath);
        this._callbacks.cleanup();
        if (promise) {
          return promise.then(add);
        } else {
          // If a file in node_modules has changed, emit an event regardless.
          add();
        }
      } else {
        add();
      }
      return null;
    }).catch(error => {
      this._callbacks.onError(error);
    });
  }

  // A mock name can be claimed by several files. Dropping the name outright
  // when one of them goes loses the others until they change or Jest restarts.
  _removeMock(mockName, relativeFilePath) {
    const claimants = this._hasteMap.mockDuplicates.get(mockName);
    if (claimants != null) {
      // Copied because the previous frame's ChangeEvent still holds the old set.
      const remaining = new Set(claimants);
      remaining.delete(relativeFilePath);
      if (remaining.size === 0) {
        this._hasteMap.mockDuplicates.delete(mockName);
      } else {
        this._hasteMap.mockDuplicates.set(mockName, remaining);
      }
    }
    if (this._hasteMap.mocks.get(mockName) !== relativeFilePath) {
      // Another file owns the name, so there is nothing to replace.
      return;
    }

    // Claimants are recorded in the order they were processed and the last one
    // wins, so search from the end to land on the same file a rebuild would.
    // `files` has already had this path deleted, so a survivor found here is one
    // the haste map still tracks.
    const survivor = [...(this._hasteMap.mockDuplicates.get(mockName) ?? [])].reverse().find(candidate => this._hasteMap.files.has(candidate));
    if (survivor == null) {
      this._hasteMap.mocks.delete(mockName);
    } else {
      this._hasteMap.mocks.set(mockName, survivor);
    }
  }
  _emitChange() {
    if (this._eventsQueue.length > 0) {
      this._mustCopy = true;
      this._pendingEventKeys.clear();
      const {
        emit,
        rootDir
      } = this._callbacks;
      const changeEvent = {
        eventsQueue: this._eventsQueue,
        hasteFS: new _HasteFS.default({
          files: this._hasteMap.files,
          rootDir
        }),
        moduleMap: new _ModuleMap.default({
          duplicates: this._hasteMap.duplicates,
          map: this._hasteMap.map,
          mocks: this._hasteMap.mocks,
          rootDir
        })
      };
      emit(changeEvent);
      this._eventsQueue = [];
    }
  }
}
exports.ChangeQueue = ChangeQueue;

/***/ },

/***/ "./src/watchers/ParcelWatcher.ts"
(__unused_webpack_module, exports, __webpack_require__) {



Object.defineProperty(exports, "__esModule", ({
  value: true
}));
exports.ParcelWatcher = void 0;
function _nodeEvents() {
  const data = require("node:events");
  _nodeEvents = function () {
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
function _anymatch() {
  const data = _interopRequireDefault(require("anymatch"));
  _anymatch = function () {
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
var _isIgnorableFileError = __webpack_require__("./src/lib/isIgnorableFileError.ts");
var _common = __webpack_require__("./src/watchers/common.ts");
function _interopRequireDefault(e) { return e && e.__esModule ? e : { default: e }; }
function _interopRequireWildcard(e, t) { if ("function" == typeof WeakMap) var r = new WeakMap(), n = new WeakMap(); return (_interopRequireWildcard = function (e, t) { if (!t && e && e.__esModule) return e; var o, i, f = { __proto__: null, default: e }; if (null === e || "object" != typeof e && "function" != typeof e) return f; if (o = t ? n : r) { if (o.has(e)) return o.get(e); o.set(e, f); } for (const t in e) "default" !== t && {}.hasOwnProperty.call(e, t) && ((i = (o = Object.defineProperty) && Object.getOwnPropertyDescriptor(e, t)) && (i.get || i.set) ? o(f, t, i) : f[t] = e[t]); return f; })(e, t); }
/**
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

// WatcherDriver always instantiates WatchmanWatcher directly when useWatchman
// is true, so the 'watchman' branch below is unreachable through normal Jest
// operation. It exists so ParcelWatcher can be used standalone (e.g. in tests
// or custom tooling) with watchman as the parcel backend.
function pickBackend(useWatchman) {
  if (useWatchman) {
    return 'watchman';
  }
  switch (process.platform) {
    case 'darwin':
      return 'fs-events';
    case 'linux':
      return 'inotify';
    case 'win32':
      return 'windows';
    default:
      // Jest only supports the three platforms above. Elsewhere, omit the
      // option so parcel's own default resolution picks the best compiled
      // backend
      return undefined;
  }
}

// Both forms are needed: the bare glob prunes the directory itself from
// parcel's initial scan, while the `/**` variant filters per-event paths —
// fs-events delivers paths inside the directory that only match the latter.
const VCS_IGNORE_GLOBS = ['**/.git', '**/.git/**', '**/.hg', '**/.hg/**', '**/.sl', '**/.sl/**'];
const MAX_RESUBSCRIBE_ATTEMPTS = 3;
// Give transient conditions (fd exhaustion, inotify limits) a moment to
// clear instead of burning every attempt within milliseconds.
const RESUBSCRIBE_RETRY_DELAY = 1000;
class ParcelWatcher extends _nodeEvents().EventEmitter {
  root;
  _console;
  _dot;
  _glob;
  _doIgnore;
  _backend;
  _parcelIgnore;
  _subscription = null;
  _closed = false;
  constructor(root, opts) {
    super();
    this.root = path().resolve(root);
    this._console = opts.console;
    this._dot = opts.dot;
    this._glob = opts.glob;
    // Fallback for patterns parcel's native matcher can't take: flagged
    // regexes, and sources std::regex rejects or matches differently.
    this._doIgnore = opts.ignored ? (0, _anymatch().default)(opts.ignored) : () => false;
    this._backend = pickBackend(opts.useWatchman);
    // Parcel matches the pattern against the path relative to the watched
    // root, same as isFileIncluded does — but flags are unsupported. The VCS
    // globs are always appended: the path parcel matches has no leading
    // separator, so HasteMap's separator-anchored VCS alternation
    // (`/\.git/|…`) never matches the root's own `.git` directory.
    this._parcelIgnore = opts.ignored instanceof RegExp && opts.ignored.flags === '' ? [opts.ignored, ...VCS_IGNORE_GLOBS] : VCS_IGNORE_GLOBS;
    setImmediate(() => this._start());
  }
  _parcelOpts() {
    return {
      backend: this._backend,
      ignore: this._parcelIgnore
    };
  }

  // close() ends with removeAllListeners(), and an 'error' emit without a
  // listener throws. Async work started before close() can still fail after
  // it (a hung subscribe finally rejecting, an in-flight lstat) — swallow
  // those instead of crashing the process.
  _emitError(error) {
    if (this._closed) {
      return;
    }
    this.emit('error', error);
  }

  // Parcel compiles the regex source with C++ std::regex, which rejects some
  // JS-valid constructs (lookbehind, named groups, \p{…}) at subscribe time.
  // There is no way to validate against std::regex from JS ahead of time, so
  // rejection is the signal: drop the regex (per-event filtering still happens
  // through _doIgnore) and retry with the VCS globs alone.
  async _subscribe() {
    // Watch mode is the only consumer of the native binding — loading it with
    // the module would take down every jest run on platforms without a
    // prebuild. The build's lazy commonjs transform already defers top-level
    // imports, but that guarantee lives in build config; keep it in the code.
    const {
      subscribe
    } = require('@parcel/watcher');
    try {
      return await subscribe(this.root, this._handleEvents, this._parcelOpts());
    } catch (error) {
      const rejectedPattern = this._parcelIgnore.find(pattern => pattern instanceof RegExp);
      if (rejectedPattern == null) {
        throw error;
      }
      // The retry can also swallow failures unrelated to the regex, silently
      // costing the native-level ignore for the session — make it diagnosable.
      this._console.warn(`jest-haste-map: subscribing with the ignore pattern ${rejectedPattern} failed (${error}); ignored paths will still be watched and filtered in JS`);
      this._parcelIgnore = VCS_IGNORE_GLOBS;
      return subscribe(this.root, this._handleEvents, this._parcelOpts());
    }
  }
  async _resubscribe(cause) {
    // Events occurring while the watcher is down are lost — they surface on
    // the next restart, when the startup crawl picks up the mtime changes.
    this._console.warn(`jest-haste-map: watch error (${cause}); re-subscribing. Changes made in the meantime are not detected until the next restart`);
    this._subscription?.unsubscribe().catch(() => undefined);
    this._subscription = null;
    let lastError = cause;
    for (let attempt = 0; attempt < MAX_RESUBSCRIBE_ATTEMPTS; attempt++) {
      if (attempt > 0) {
        await new Promise(resolve => {
          setTimeout(resolve, RESUBSCRIBE_RETRY_DELAY).unref();
        });
        if (this._closed) {
          return;
        }
      }
      try {
        const subscription = await this._subscribe();
        if (this._closed) {
          await subscription.unsubscribe();
          return;
        }
        this._subscription = subscription;
        return;
      } catch (error) {
        lastError = error;
      }
    }
    this._emitError(lastError);
  }
  async _start() {
    try {
      const subscription = await this._subscribe();

      // WatcherDriver may time out and call close() while subscribe() was in
      // flight. Unsubscribe immediately rather than leaking the subscription.
      if (this._closed) {
        await subscription.unsubscribe();
        return;
      }
      this._subscription = subscription;
      this.emit('ready');
    } catch (error) {
      this._emitError(error);
    }
  }
  _handleEvents = (err, events) => {
    if (this._closed) {
      return;
    }
    if (err) {
      // Parcel tears the subscription down before reporting a watcher error
      // (Backend::handleWatcherError unwatches it and Watcher::notifyError
      // clears its callbacks), so no further events can arrive on it —
      // recover by subscribing anew, like WatchmanWatcher re-inits on a
      // lost connection.
      void this._resubscribe(err);
      return;
    }
    for (const event of events) {
      const absPath = event.path;
      const relPath = path().relative(this.root, absPath);
      if (!(0, _common.isFileIncluded)(this._glob, this._dot, this._doIgnore, relPath)) {
        continue;
      }
      if (event.type === 'delete') {
        this.emit(_common.DELETE_EVENT, relPath, this.root);
        this.emit(_common.ALL_EVENT, _common.DELETE_EVENT, relPath, this.root);
      } else {
        const type = event.type === 'create' ? _common.ADD_EVENT : _common.CHANGE_EVENT;
        fs().lstat(absPath, (error, stat) => {
          if (error) {
            if (!(0, _isIgnorableFileError.isIgnorableFileError)(error)) {
              this._emitError(error);
            }
            return;
          }
          this.emit(type, relPath, this.root, stat);
          this.emit(_common.ALL_EVENT, type, relPath, this.root, stat);
        });
      }
    }
  };
  async close() {
    this._closed = true;
    await this._subscription?.unsubscribe();
    this._subscription = null;
    this.removeAllListeners();
  }
}
exports.ParcelWatcher = ParcelWatcher;

/***/ },

/***/ "./src/watchers/common.ts"
(__unused_webpack_module, exports) {



Object.defineProperty(exports, "__esModule", ({
  value: true
}));
exports.DELETE_EVENT = exports.CHANGE_EVENT = exports.ALL_EVENT = exports.ADD_EVENT = void 0;
exports.isFileIncluded = isFileIncluded;
function _picomatch() {
  const data = _interopRequireDefault(require("picomatch"));
  _picomatch = function () {
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

const CHANGE_EVENT = exports.CHANGE_EVENT = 'change';
const DELETE_EVENT = exports.DELETE_EVENT = 'delete';
const ADD_EVENT = exports.ADD_EVENT = 'add';
const ALL_EVENT = exports.ALL_EVENT = 'all';
const matcherCache = new Map();
function getMatcher(glob, dot) {
  const key = `${dot}:${glob}`;
  let matcher = matcherCache.get(key);
  if (matcher == null) {
    matcher = (0, _picomatch().default)(glob, {
      dot
    });
    matcherCache.set(key, matcher);
  }
  return matcher;
}
function isFileIncluded(globs, dot, doIgnore, relativePath) {
  if (doIgnore(relativePath)) {
    return false;
  }
  return globs.length > 0 ? globs.some(glob => getMatcher(glob, dot)(relativePath)) : dot || getMatcher('**/*', false)(relativePath);
}

/***/ },

/***/ "./src/watchers/index.ts"
(__unused_webpack_module, exports, __webpack_require__) {



Object.defineProperty(exports, "__esModule", ({
  value: true
}));
exports.WatcherDriver = void 0;
exports.shouldUseWatchman = shouldUseWatchman;
var _watchmanSockname = __webpack_require__("./src/lib/watchmanSockname.ts");
var _ParcelWatcher = __webpack_require__("./src/watchers/ParcelWatcher.ts");
var _WatchmanWatcher = _interopRequireDefault(__webpack_require__("./src/watchers/WatchmanWatcher.js"));
function _interopRequireDefault(e) { return e && e.__esModule ? e : { default: e }; }
/**
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

// @ts-expect-error: not converted to TypeScript - it's a fork: https://github.com/jestjs/jest/pull/5387

const WatchmanWatcher = _WatchmanWatcher.default;
async function shouldUseWatchman(useWatchmanOption, cacheDirectory) {
  if (!useWatchmanOption) {
    return false;
  }
  const availability = await (0, _watchmanSockname.getWatchmanAvailability)(cacheDirectory);
  return availability.installed;
}
const MAX_WAIT_TIME = 240_000;
class WatcherDriver {
  _console;
  _extensions;
  _ignorePattern;
  _onError;
  _roots;
  _useWatchman;
  _watchers = [];
  constructor(opts) {
    this._console = opts.console;
    this._extensions = opts.extensions;
    this._ignorePattern = opts.ignorePattern;
    this._onError = opts.onError;
    this._roots = opts.roots;
    this._useWatchman = opts.useWatchman;
  }
  async start(onChange) {
    const Backend = this._useWatchman ? WatchmanWatcher : _ParcelWatcher.ParcelWatcher;
    const results = await Promise.allSettled(this._roots.map(root => this._createWatcher(Backend, root, onChange)));
    const fulfilled = results.filter(r => r.status === 'fulfilled').map(r => r.value);
    const rejected = results.filter(r => r.status === 'rejected').map(r => r.reason);
    if (rejected.length > 0) {
      await Promise.allSettled(fulfilled.map(w => w.close()));
      throw new AggregateError(rejected, 'Failed to start watch mode.');
    }
    this._watchers = fulfilled;
  }
  async close() {
    await Promise.all(this._watchers.map(watcher => watcher.close()));
    this._watchers = [];
  }
  _createWatcher(Backend, root, onChange) {
    const watcher = new Backend(root, {
      console: this._console,
      dot: true,
      glob: this._extensions.map(ext => `**/*.${ext}`),
      ignored: this._ignorePattern,
      useWatchman: this._useWatchman
    });
    return new Promise((resolve, reject) => {
      const onReady = () => {
        clearTimeout(rejectTimeout);
        watcher.off('error', onStartupError);
        // Post-startup errors are non-fatal: the watcher stays subscribed,
        // and an unhandled 'error' emit would crash the process.
        watcher.on('error', error => this._onError(error));
        watcher.on('all', onChange);
        resolve(watcher);
      };
      const onStartupError = startupError => {
        clearTimeout(rejectTimeout);
        watcher.off('ready', onReady);
        watcher.close().catch(() => undefined);
        reject(startupError);
      };
      const rejectTimeout = setTimeout(() => {
        watcher.off('ready', onReady);
        watcher.off('error', onStartupError);
        watcher.close().catch(() => undefined);
        reject(new Error('Failed to start watch mode.'));
      }, MAX_WAIT_TIME);
      watcher.once('ready', onReady);
      watcher.once('error', onStartupError);
    });
  }
}
exports.WatcherDriver = WatcherDriver;

/***/ },

/***/ "./src/worker.ts"
(__unused_webpack_module, exports, __webpack_require__) {



Object.defineProperty(exports, "__esModule", ({
  value: true
}));
exports.getSha1 = getSha1;
exports.worker = worker;
function _nodeCrypto() {
  const data = require("node:crypto");
  _nodeCrypto = function () {
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
function fs() {
  const data = _interopRequireWildcard(require("graceful-fs"));
  fs = function () {
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
var _blacklist = _interopRequireDefault(__webpack_require__("./src/blacklist.ts"));
var _constants = _interopRequireDefault(__webpack_require__("./src/constants.ts"));
var _dependencyExtractor = __webpack_require__("./src/lib/dependencyExtractor.ts");
function _interopRequireDefault(e) { return e && e.__esModule ? e : { default: e }; }
function _interopRequireWildcard(e, t) { if ("function" == typeof WeakMap) var r = new WeakMap(), n = new WeakMap(); return (_interopRequireWildcard = function (e, t) { if (!t && e && e.__esModule) return e; var o, i, f = { __proto__: null, default: e }; if (null === e || "object" != typeof e && "function" != typeof e) return f; if (o = t ? n : r) { if (o.has(e)) return o.get(e); o.set(e, f); } for (const t in e) "default" !== t && {}.hasOwnProperty.call(e, t) && ((i = (o = Object.defineProperty) && Object.getOwnPropertyDescriptor(e, t)) && (i.get || i.set) ? o(f, t, i) : f[t] = e[t]); return f; })(e, t); }
/**
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

const PACKAGE_JSON = `${path().sep}package.json`;
function sha1hex(content) {
  return (0, _nodeCrypto().createHash)('sha1').update(content).digest('hex');
}
async function worker(data) {
  const hasteImpl = data.hasteImplModulePath ? require(data.hasteImplModulePath) : null;
  let content;
  let dependencies;
  let id;
  let module;
  let sha1;
  const {
    computeDependencies,
    computeSha1,
    rootDir,
    filePath
  } = data;
  const getContent = () => {
    if (content === undefined) {
      content = fs().readFileSync(filePath, 'utf8');
    }
    return content;
  };
  if (filePath.endsWith(PACKAGE_JSON)) {
    // Process a package.json that is returned as a PACKAGE type with its name.
    try {
      const fileData = JSON.parse(getContent());
      if (fileData.name) {
        const relativeFilePath = path().relative(rootDir, filePath);
        id = fileData.name;
        module = [relativeFilePath, _constants.default.PACKAGE];
      }
    } catch (error) {
      throw new Error(`Cannot parse ${filePath} as JSON: ${error.message}`, {
        cause: error
      });
    }
  } else if (!_blacklist.default.has(filePath.slice(filePath.lastIndexOf('.')))) {
    // Process a random file that is returned as a MODULE.
    if (hasteImpl) {
      id = hasteImpl.getHasteName(filePath);
    }
    if (computeDependencies) {
      const content = getContent();
      const extractor = data.dependencyExtractor ? await (0, _jestUtil().requireOrImportModule)(data.dependencyExtractor, false) : _dependencyExtractor.extractor;
      dependencies = [...extractor.extract(content, filePath, _dependencyExtractor.extractor.extract)];
    }
    if (id) {
      const relativeFilePath = path().relative(rootDir, filePath);
      module = [relativeFilePath, _constants.default.MODULE];
    }
  }

  // If a SHA-1 is requested on update, compute it.
  if (computeSha1) {
    sha1 = sha1hex(content || fs().readFileSync(filePath));
  }
  return {
    dependencies,
    id,
    module,
    sha1
  };
}
async function getSha1(data) {
  const sha1 = data.computeSha1 ? sha1hex(fs().readFileSync(data.filePath)) : null;
  return {
    dependencies: undefined,
    id: undefined,
    module: undefined,
    sha1
  };
}

/***/ },

/***/ "./src/watchers/WatchmanWatcher.js"
(__unused_webpack_module, __webpack_exports__, __webpack_require__) {

// ESM COMPAT FLAG
__webpack_require__.r(__webpack_exports__);

// EXPORTS
__webpack_require__.d(__webpack_exports__, {
  "default": () => (/* binding */ WatchmanWatcher)
});

;// external "node:assert"
const external_node_assert_namespaceObject = require("node:assert");
;// external "node:events"
const external_node_events_namespaceObject = require("node:events");
;// external "node:path"
const external_node_path_namespaceObject = require("node:path");
;// external "anymatch"
const external_anymatch_namespaceObject = require("anymatch");
var external_anymatch_default = /*#__PURE__*/__webpack_require__.n(external_anymatch_namespaceObject);
;// external "fb-watchman"
const external_fb_watchman_namespaceObject = require("fb-watchman");
var external_fb_watchman_default = /*#__PURE__*/__webpack_require__.n(external_fb_watchman_namespaceObject);
;// external "graceful-fs"
const external_graceful_fs_namespaceObject = require("graceful-fs");
var external_graceful_fs_default = /*#__PURE__*/__webpack_require__.n(external_graceful_fs_namespaceObject);
// EXTERNAL MODULE: ./src/lib/isIgnorableFileError.ts
var isIgnorableFileError = __webpack_require__("./src/lib/isIgnorableFileError.ts");
// EXTERNAL MODULE: ./src/watchers/common.ts
var common = __webpack_require__("./src/watchers/common.ts");
;// ./src/watchers/WatchmanWatcher.js
/**
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */










const CHANGE_EVENT = common.CHANGE_EVENT;
const DELETE_EVENT = common.DELETE_EVENT;
const ADD_EVENT = common.ADD_EVENT;
const ALL_EVENT = common.ALL_EVENT;
const SUB_NAME = 'jest-haste-map';

// Dedupe repeated "Recrawled this watch N times" warnings from watchman.
const RECRAWL_WARNINGS = [];
const RECRAWL_REGEXP =
  /Recrawled this watch (\d+) times, most recently because:\n([^:]+)/;

function isRecrawlWarningDupe(warningMessage) {
  if (typeof warningMessage !== 'string') {
    return false;
  }
  const match = warningMessage.match(RECRAWL_REGEXP);
  if (!match) {
    return false;
  }
  const count = Number(match[1]);
  const root = match[2];
  const existing = RECRAWL_WARNINGS.find(w => w.root === root);
  if (existing) {
    if (existing.count >= count) {
      return true;
    }
    existing.count = count;
    return false;
  }
  RECRAWL_WARNINGS.push({count, root});
  return false;
}

/**
 * Watches `dir`.
 *
 * @class WatchmanWatcher
 * @param String dir
 * @param {Object} opts
 * @public
 */

function WatchmanWatcher(dir, opts) {
  this.globs = opts.glob;
  this.dot = opts.dot;
  this.hasIgnore = Boolean(opts.ignored);
  this.doIgnore = opts.ignored ? external_anymatch_default()(opts.ignored) : () => false;
  this.root = external_node_path_namespaceObject.resolve(dir);
  this._console = opts.console || globalThis.console;
  this._closed = false;
  this.init();
}

Object.setPrototypeOf(WatchmanWatcher.prototype, external_node_events_namespaceObject.EventEmitter.prototype);

/**
 * Run the watchman `watch` command on the root and subscribe to changes.
 *
 * @private
 */

WatchmanWatcher.prototype.init = function () {
  if (this.client) {
    this.client.removeAllListeners();
  }

  const self = this;
  this.client = new (external_fb_watchman_default()).Client();
  this.client.on('error', error => {
    self.emit('error', error);
  });
  this.client.on('subscription', this.handleChangeEvent.bind(this));
  this.client.on('end', () => {
    if (self._closed) {
      return;
    }
    self._console.warn(
      '[jest-haste-map] Warning: Lost connection to watchman, reconnecting..',
    );
    self.init();
  });

  this.watchProjectInfo = null;

  function getWatchRoot() {
    return self.watchProjectInfo ? self.watchProjectInfo.root : self.root;
  }

  function onCapability(error, resp) {
    if (handleError(self, error)) {
      // The Watchman watcher is unusable on this system, we cannot continue
      return;
    }

    handleWarning(self, resp);

    self.capabilities = resp.capabilities;

    if (self.capabilities.relative_root) {
      self.client.command(['watch-project', getWatchRoot()], onWatchProject);
    } else {
      self.client.command(['watch', getWatchRoot()], onWatch);
    }
  }

  function onWatchProject(error, resp) {
    if (handleError(self, error)) {
      return;
    }

    handleWarning(self, resp);

    self.watchProjectInfo = {
      relativePath: resp.relative_path ?? '',
      root: resp.watch,
    };

    self.client.command(['clock', getWatchRoot()], onClock);
  }

  function onWatch(error, resp) {
    if (handleError(self, error)) {
      return;
    }

    handleWarning(self, resp);

    self.client.command(['clock', getWatchRoot()], onClock);
  }

  function onClock(error, resp) {
    if (handleError(self, error)) {
      return;
    }

    handleWarning(self, resp);

    const options = {
      fields: ['name', 'exists', 'new'],
      since: resp.clock,
    };

    // If the server has the wildmatch capability available it supports
    // the recursive **/*.foo style match and we can offload our globs
    // to the watchman server.  This saves both on data size to be
    // communicated back to us and compute for evaluating the globs
    // in our node process.
    if (self.capabilities.wildmatch) {
      if (self.globs.length === 0) {
        if (!self.dot) {
          // Make sure we honor the dot option if even we're not using globs.
          options.expression = [
            'match',
            '**',
            'wholename',
            {
              includedotfiles: false,
            },
          ];
        }
      } else {
        options.expression = ['anyof'];
        for (const glob of self.globs) {
          options.expression.push([
            'match',
            glob,
            'wholename',
            {
              includedotfiles: self.dot,
            },
          ]);
        }
      }
    }

    if (self.capabilities.relative_root) {
      options.relative_root = self.watchProjectInfo.relativePath;
    }

    self.client.command(
      ['subscribe', getWatchRoot(), SUB_NAME, options],
      onSubscribe,
    );
  }

  function onSubscribe(error, resp) {
    if (handleError(self, error)) {
      return;
    }

    handleWarning(self, resp);

    self.emit('ready');
  }

  self.client.capabilityCheck(
    {
      optional: ['wildmatch', 'relative_root'],
    },
    onCapability,
  );
};

/**
 * Handles a change event coming from the subscription.
 *
 * @param {Object} resp
 * @private
 */

WatchmanWatcher.prototype.handleChangeEvent = function (resp) {
  external_node_assert_namespaceObject.strict.equal(resp.subscription, SUB_NAME, 'Invalid subscription event.');
  if (resp.is_fresh_instance) {
    this.emit('fresh_instance');
  }
  if (Array.isArray(resp.files)) {
    for (const file of resp.files) this.handleFileChange(file);
  }
};

/**
 * Handles a single change event record.
 *
 * @param {Object} changeDescriptor
 * @private
 */

WatchmanWatcher.prototype.handleFileChange = function (changeDescriptor) {
  const self = this;
  let absPath;
  let relativePath;

  if (this.capabilities.relative_root) {
    relativePath = changeDescriptor.name;
    absPath = external_node_path_namespaceObject.join(
      this.watchProjectInfo.root,
      this.watchProjectInfo.relativePath,
      relativePath,
    );
  } else {
    absPath = external_node_path_namespaceObject.join(this.root, changeDescriptor.name);
    relativePath = changeDescriptor.name;
  }

  if (
    !(self.capabilities.wildmatch && !this.hasIgnore) &&
    !common.isFileIncluded(this.globs, this.dot, this.doIgnore, relativePath)
  ) {
    return;
  }

  if (changeDescriptor.exists) {
    external_graceful_fs_default().lstat(absPath, (error, stat) => {
      // Files can disappear or temporarily become unreadable between the
      // Watchman event and the lstat call, so ignore that stale event.
      if (error && (0,isIgnorableFileError.isIgnorableFileError)(error)) {
        return;
      }

      if (handleError(self, error)) {
        return;
      }

      const eventType = changeDescriptor.new ? ADD_EVENT : CHANGE_EVENT;

      // Change event on dirs are mostly useless.
      if (!(eventType === CHANGE_EVENT && stat.isDirectory())) {
        self.emitEvent(eventType, relativePath, self.root, stat);
      }
    });
  } else {
    self.emitEvent(DELETE_EVENT, relativePath, self.root);
  }
};

/**
 * Dispatches the event.
 *
 * @param {string} eventType
 * @param {string} filepath
 * @param {string} root
 * @param {fs.Stat} stat
 * @private
 */

WatchmanWatcher.prototype.emitEvent = function (
  eventType,
  filepath,
  root,
  stat,
) {
  // An `lstat` started before close() can still complete after it. Delivering
  // that event would feed the already-stopped ChangeQueue.
  if (this._closed) {
    return;
  }
  this.emit(eventType, filepath, root, stat);
  this.emit(ALL_EVENT, eventType, filepath, root, stat);
};

/**
 * Closes the watcher.
 *
 */

WatchmanWatcher.prototype.close = function () {
  this._closed = true;
  this.client.removeAllListeners();
  this.client.end();
  return Promise.resolve();
};

/**
 * Handles an error and returns true if exists.
 *
 * @param {WatchmanWatcher} self
 * @param {Error} error
 * @private
 */

function handleError(self, error) {
  if (error == null) {
    return false;
  } else {
    if (!self._closed) {
      self.emit('error', error);
    }
    return true;
  }
}

/**
 * Handles a warning in the watchman resp object.
 *
 * @param {WatchmanWatcher} self
 * @param {object} resp
 * @private
 */

function handleWarning(self, resp) {
  if ('warning' in resp) {
    if (isRecrawlWarningDupe(resp.warning)) {
      return true;
    }
    self._console.warn(resp.warning);
    return true;
  } else {
    return false;
  }
}


/***/ },

/***/ "./package.json"
(module) {

module.exports = {"version":"30.5.0"};

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
/******/ 	/* webpack/runtime/compat get default export */
/******/ 	(() => {
/******/ 		// getDefaultExport function for compatibility with non-harmony modules
/******/ 		__webpack_require__.n = (module) => {
/******/ 			const getter = module && module.__esModule ?
/******/ 				() => (module['default']) :
/******/ 				() => (module);
/******/ 			__webpack_require__.d(getter, { a: getter });
/******/ 			return getter;
/******/ 		};
/******/ 	})();
/******/ 	
/******/ 	/* webpack/runtime/define property getters */
/******/ 	(() => {
/******/ 		// define getter/value functions for harmony exports
/******/ 		__webpack_require__.d = (exports, definition) => {
/******/ 			if(Array.isArray(definition)) {
/******/ 				var i = 0;
/******/ 				while(i < definition.length) {
/******/ 					var key = definition[i++];
/******/ 					var binding = definition[i++];
/******/ 					if(!__webpack_require__.o(exports, key)) {
/******/ 						if(binding === 0) {
/******/ 							Object.defineProperty(exports, key, { enumerable: true, value: definition[i++] });
/******/ 						} else {
/******/ 							Object.defineProperty(exports, key, { enumerable: true, get: binding });
/******/ 						}
/******/ 					} else if(binding === 0) { i++; }
/******/ 				}
/******/ 			} else {
/******/ 				for(var key in definition) {
/******/ 					if(__webpack_require__.o(definition, key) && !__webpack_require__.o(exports, key)) {
/******/ 						Object.defineProperty(exports, key, { enumerable: true, get: definition[key] });
/******/ 					}
/******/ 				}
/******/ 			}
/******/ 		};
/******/ 	})();
/******/ 	
/******/ 	/* webpack/runtime/hasOwnProperty shorthand */
/******/ 	(() => {
/******/ 		__webpack_require__.o = (obj, prop) => (Object.prototype.hasOwnProperty.call(obj, prop))
/******/ 	})();
/******/ 	
/******/ 	/* webpack/runtime/make namespace object */
/******/ 	(() => {
/******/ 		// define __esModule on exports
/******/ 		__webpack_require__.r = (exports) => {
/******/ 			if(Symbol.toStringTag) {
/******/ 				Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
/******/ 			}
/******/ 			Object.defineProperty(exports, '__esModule', { value: true });
/******/ 		};
/******/ 	})();
/******/ 	
/************************************************************************/
let __webpack_exports__ = {};
// This entry needs to be wrapped in an IIFE because it uses a non-standard name for the exports (exports).
(() => {
let exports = __webpack_exports__;


Object.defineProperty(exports, "__esModule", ({
  value: true
}));
Object.defineProperty(exports, "DuplicateError", ({
  enumerable: true,
  get: function () {
    return _FileProcessor.DuplicateError;
  }
}));
exports["default"] = exports.ModuleMap = void 0;
function _nodeCrypto() {
  const data = require("node:crypto");
  _nodeCrypto = function () {
    return data;
  };
  return data;
}
function _nodeEvents() {
  const data = require("node:events");
  _nodeEvents = function () {
    return data;
  };
  return data;
}
function _nodeOs() {
  const data = require("node:os");
  _nodeOs = function () {
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
function _jestRegexUtil() {
  const data = require("jest-regex-util");
  _jestRegexUtil = function () {
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
var _HasteFS = _interopRequireDefault(__webpack_require__("./src/HasteFS.ts"));
var _ModuleMap = _interopRequireDefault(__webpack_require__("./src/ModuleMap.ts"));
var _constants = _interopRequireDefault(__webpack_require__("./src/constants.ts"));
var _crawlers = __webpack_require__("./src/crawlers/index.ts");
var _CacheManager = __webpack_require__("./src/lib/CacheManager.ts");
var _FileProcessor = __webpack_require__("./src/lib/FileProcessor.ts");
var _WorkerPool = __webpack_require__("./src/lib/WorkerPool.ts");
var _buildIgnoreMatcher = __webpack_require__("./src/lib/buildIgnoreMatcher.ts");
var fastPath = _interopRequireWildcard(__webpack_require__("./src/lib/fast_path.ts"));
var _getPlatformExtension = _interopRequireDefault(__webpack_require__("./src/lib/getPlatformExtension.ts"));
var _util = __webpack_require__("./src/lib/util.ts");
var _watchmanSockname = __webpack_require__("./src/lib/watchmanSockname.ts");
var _watchers = __webpack_require__("./src/watchers/index.ts");
var _ChangeQueue = __webpack_require__("./src/watchers/ChangeQueue.ts");
function _interopRequireDefault(e) { return e && e.__esModule ? e : { default: e }; }
function _interopRequireWildcard(e, t) { if ("function" == typeof WeakMap) var r = new WeakMap(), n = new WeakMap(); return (_interopRequireWildcard = function (e, t) { if (!t && e && e.__esModule) return e; var o, i, f = { __proto__: null, default: e }; if (null === e || "object" != typeof e && "function" != typeof e) return f; if (o = t ? n : r) { if (o.has(e)) return o.get(e); o.set(e, f); } for (const t in e) "default" !== t && {}.hasOwnProperty.call(e, t) && ((i = (o = Object.defineProperty) && Object.getOwnPropertyDescriptor(e, t)) && (i.get || i.set) ? o(f, t, i) : f[t] = e[t]); return f; })(e, t); }
/**
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

// TypeScript doesn't like us importing from outside `rootDir`, but it doesn't
// understand `require`.
const {
  version: VERSION
} = __webpack_require__("./package.json");
const ModuleMap = exports.ModuleMap = _ModuleMap.default;
const VCS_DIRECTORIES = ['.git', '.hg', '.sl'].map(vcs => (0, _jestRegexUtil().escapePathForRegex)(path().sep + vcs + path().sep)).join('|');

/**
 * HasteMap is a JavaScript implementation of Facebook's haste module system.
 *
 * This implementation is inspired by https://github.com/facebook/node-haste
 * and was built with for high-performance in large code repositories with
 * hundreds of thousands of files. This implementation is scalable and provides
 * predictable performance.
 *
 * Because the haste map creation and synchronization is critical to startup
 * performance and most tasks are blocked by I/O this class makes heavy use of
 * synchronous operations. It uses worker processes for parallelizing file
 * access and metadata extraction.
 *
 * The data structures created by `jest-haste-map` can be used directly from the
 * cache without further processing. The metadata objects in the `files` and
 * `map` objects contain cross-references: a metadata object from one can look
 * up the corresponding metadata object in the other map. Note that in most
 * projects, the number of files will be greater than the number of haste
 * modules one module can refer to many files based on platform extensions.
 *
 * type HasteMap = {
 *   clocks: WatchmanClocks,
 *   files: {[filepath: string]: FileMetaData},
 *   map: {[id: string]: ModuleMapItem},
 *   mocks: {[id: string]: string},
 * }
 *
 * // Watchman clocks are used for query synchronization and file system deltas.
 * type WatchmanClocks = {[filepath: string]: string};
 *
 * type FileMetaData = {
 *   id: ?string, // used to look up module metadata objects in `map`.
 *   mtime: number, // check for outdated files.
 *   size: number, // size of the file in bytes.
 *   visited: boolean, // whether the file has been parsed or not.
 *   dependencies: Array<string>, // all relative dependencies of this file.
 *   sha1: ?string, // SHA-1 of the file, if requested via options.
 * };
 *
 * // Modules can be targeted to a specific platform based on the file name.
 * // Example: platform.ios.js and Platform.android.js will both map to the same
 * // `Platform` module. The platform should be specified during resolution.
 * type ModuleMapItem = {[platform: string]: ModuleMetaData};
 *
 * //
 * type ModuleMetaData = {
 *   path: string, // the path to look up the file object in `files`.
 *   type: string, // the module type (either `package` or `module`).
 * };
 *
 * Note that the data structures described above are conceptual only. The actual
 * implementation uses arrays and constant keys for metadata storage. Instead of
 * `{id: 'flatMap', mtime: 3421, size: 42, visited: true, dependencies: []}` the real
 * representation is similar to `['flatMap', 3421, 42, 1, []]` to save storage space
 * and reduce parse and write time of a big JSON blob.
 *
 * The HasteMap is created as follows:
 *  1. read data from the cache or create an empty structure.
 *
 *  2. crawl the file system.
 *     * empty cache: crawl the entire file system.
 *     * cache available:
 *       * if watchman is available: get file system delta changes.
 *       * if watchman is unavailable: crawl the entire file system.
 *     * build metadata objects for every file. This builds the `files` part of
 *       the `HasteMap`.
 *
 *  3. parse and extract metadata from changed files.
 *     * this is done in parallel over worker processes to improve performance.
 *     * the worst case is to parse all files.
 *     * the best case is no file system access and retrieving all data from
 *       the cache.
 *     * the average case is a small number of changed files.
 *
 *  4. serialize the new `HasteMap` in a cache file.
 *     Worker processes can directly access the cache through `HasteMap.read()`.
 *
 */
class HasteMap extends _nodeEvents().EventEmitter {
  _buildPromise = null;
  _cacheManager;
  _changeQueue;
  _fileProcessor;
  _ignoreFn = () => false;
  _console;
  _options;
  _watcherDriver;
  _workerPool;
  static getStatic(config) {
    if (config.haste.hasteMapModulePath) {
      return require(config.haste.hasteMapModulePath);
    }
    return HasteMap;
  }
  static async create(options) {
    if (options.hasteMapModulePath) {
      const CustomHasteMap = require(options.hasteMapModulePath);
      return new CustomHasteMap(options);
    }
    const hasteMap = new HasteMap(options);
    await hasteMap.setupCachePath(options);
    return hasteMap;
  }
  constructor(options) {
    super();
    this._options = {
      cacheDirectory: options.cacheDirectory || (0, _nodeOs().tmpdir)(),
      computeDependencies: options.computeDependencies ?? true,
      computeSha1: options.computeSha1 || false,
      dependencyExtractor: options.dependencyExtractor || null,
      enableSymlinks: options.enableSymlinks ?? false,
      extensions: options.extensions,
      forceNodeFilesystemAPI: options.forceNodeFilesystemAPI ?? false,
      hasteImplModulePath: options.hasteImplModulePath,
      id: options.id,
      maxWorkers: options.maxWorkers,
      mocksPattern: options.mocksPattern ? new RegExp(options.mocksPattern) : null,
      platforms: options.platforms,
      resetCache: options.resetCache,
      retainAllFiles: options.retainAllFiles,
      rootDir: options.rootDir,
      roots: [...new Set(options.roots)],
      skipPackageJson: !!options.skipPackageJson,
      throwOnModuleCollision: !!options.throwOnModuleCollision,
      useWatchman: options.useWatchman ?? true,
      watch: !!options.watch,
      workerThreads: options.workerThreads
    };
    this._console = options.console || globalThis.console;
    if (options.ignorePattern) {
      if (options.ignorePattern instanceof RegExp) {
        this._options.ignorePattern = new RegExp(`${options.ignorePattern.source}|${VCS_DIRECTORIES}`, options.ignorePattern.flags);
      } else {
        throw new TypeError('jest-haste-map: the `ignorePattern` option must be a RegExp');
      }
    } else {
      this._options.ignorePattern = new RegExp(VCS_DIRECTORIES);
    }
    if (this._options.enableSymlinks && this._options.useWatchman) {
      throw new Error('jest-haste-map: enableSymlinks config option was set, but ' + 'is incompatible with watchman.\n' + 'Set either `enableSymlinks` to false or `useWatchman` to false.');
    }
    this._ignoreFn = (0, _buildIgnoreMatcher.buildIgnoreMatcher)(this._options.ignorePattern, this._options.retainAllFiles);
    this._workerPool = new _WorkerPool.WorkerPool({
      maxWorkers: this._options.maxWorkers,
      workerPath: require.resolve('./worker'),
      workerThreads: this._options.workerThreads
    });
    this._fileProcessor = new _FileProcessor.FileProcessor({
      computeDependencies: this._options.computeDependencies,
      computeSha1: this._options.computeSha1,
      dependencyExtractor: this._options.dependencyExtractor,
      hasteImplModulePath: this._options.hasteImplModulePath,
      mocksPattern: this._options.mocksPattern,
      platforms: this._options.platforms,
      retainAllFiles: this._options.retainAllFiles,
      rootDir: this._options.rootDir,
      skipPackageJson: this._options.skipPackageJson,
      throwOnModuleCollision: this._options.throwOnModuleCollision
    }, this._console, this._workerPool);
  }
  async setupCachePath(options) {
    const rootDirHash = (0, _nodeCrypto().createHash)('sha1').update(options.rootDir).digest('hex').slice(0, 32);
    let hasteImplHash = '';
    let dependencyExtractorHash = '';
    if (options.hasteImplModulePath) {
      const hasteImpl = require(options.hasteImplModulePath);
      if (hasteImpl.getCacheKey) {
        hasteImplHash = String(hasteImpl.getCacheKey());
      }
    }
    if (options.dependencyExtractor) {
      const dependencyExtractor = await (0, _jestUtil().requireOrImportModule)(options.dependencyExtractor, false);
      if (dependencyExtractor.getCacheKey) {
        dependencyExtractorHash = String(dependencyExtractor.getCacheKey());
      }
    }
    const cachePath = HasteMap.getCacheFilePath(this._options.cacheDirectory, `haste-map-${this._options.id}-${rootDirHash}`, VERSION, this._options.id, this._options.roots.map(root => fastPath.relative(options.rootDir, root)).join(':'), this._options.extensions.join(':'), this._options.platforms.join(':'), this._options.computeSha1.toString(), options.mocksPattern || '', (options.ignorePattern || '').toString(), hasteImplHash, dependencyExtractorHash, this._options.computeDependencies.toString());
    this._cacheManager = new _CacheManager.CacheManager(cachePath);
  }
  static getCacheFilePath(tmpdir, id, ...extra) {
    // NUL-delimited so that adjacent fields cannot run together and let two
    // different option sets hash to the same cache file.
    const hash = (0, _nodeCrypto().createHash)('sha1').update(extra.join('\0'));
    return path().join(tmpdir, `${id.replaceAll(/\W/g, '-')}-${hash.digest('hex').slice(0, 32)}`);
  }
  static getModuleMapFromJSON(json) {
    return _ModuleMap.default.fromJSON(json);
  }
  getCacheFilePath() {
    return this._cacheManager.path;
  }
  build() {
    if (!this._buildPromise) {
      this._buildPromise = (async () => {
        const data = await this._buildFileMap();

        // Persist when we don't know if files changed (changedFiles undefined)
        // or when we know a file was changed or deleted.
        let hasteMap;
        if (data.changedFiles === undefined || data.changedFiles.size > 0 || data.removedFiles.size > 0) {
          hasteMap = await this._buildHasteMap(data);
          this._persist(hasteMap);
        } else {
          hasteMap = data.hasteMap;
        }
        const rootDir = this._options.rootDir;
        const hasteFS = new _HasteFS.default({
          files: hasteMap.files,
          rootDir
        });
        const moduleMap = new _ModuleMap.default({
          duplicates: hasteMap.duplicates,
          map: hasteMap.map,
          mocks: hasteMap.mocks,
          rootDir
        });
        const __hasteMapForTest =  false || null;
        await this._watch(hasteMap);
        return {
          __hasteMapForTest,
          hasteFS,
          moduleMap
        };
      })();
    }
    return this._buildPromise;
  }

  /**
   * 1. read data from the cache or create an empty structure.
   */
  read() {
    return this._cacheManager.read();
  }
  readModuleMap() {
    const data = this.read();
    return new _ModuleMap.default({
      duplicates: data.duplicates,
      map: data.map,
      mocks: data.mocks,
      rootDir: this._options.rootDir
    });
  }

  /**
   * 2. crawl the file system.
   */
  async _buildFileMap() {
    const hasteMap = this._options.resetCache ? this._createEmptyMap() : this._cacheManager.read();
    return this._crawl(hasteMap);
  }

  /**
   * 3. parse and extract metadata from changed files.
   */
  _processFile(hasteMap, filePath) {
    return this._fileProcessor.processFile(hasteMap, hasteMap.map, hasteMap.mocks, filePath, {
      forceInBand: true
    });
  }
  _buildHasteMap(data) {
    return this._fileProcessor.buildHasteMap(data, (map, relPath, name) => this._recoverDuplicates(map, relPath, name));
  }

  /**
   * 4. serialize the new `HasteMap` in a cache file.
   */
  _persist(hasteMap) {
    this._cacheManager.persist(hasteMap);
  }
  async _crawl(hasteMap) {
    const options = this._options;
    const watchmanAvailability = options.useWatchman ? await (0, _watchmanSockname.getWatchmanAvailability)(options.cacheDirectory) : undefined;
    return (0, _crawlers.crawl)({
      computeSha1: options.computeSha1,
      console: this._console,
      data: hasteMap,
      enableSymlinks: options.enableSymlinks,
      extensions: options.extensions,
      forceNodeFilesystemAPI: options.forceNodeFilesystemAPI,
      ignore: this._ignore.bind(this),
      rootDir: options.rootDir,
      roots: options.roots,
      watchmanSockname: watchmanAvailability?.sockname
    }, watchmanAvailability?.installed ?? false);
  }

  /**
   * Watch mode
   */
  async _watch(hasteMap) {
    if (!this._options.watch) {
      return;
    }

    // In watch mode, we'll only warn about module collisions and we'll retain
    // all files, even changes to node_modules.
    this._options.throwOnModuleCollision = false;
    this._options.retainAllFiles = true;
    this._ignoreFn = (0, _buildIgnoreMatcher.buildIgnoreMatcher)(this._options.ignorePattern, true);
    this._fileProcessor = new _FileProcessor.FileProcessor({
      computeDependencies: this._options.computeDependencies,
      computeSha1: this._options.computeSha1,
      dependencyExtractor: this._options.dependencyExtractor,
      hasteImplModulePath: this._options.hasteImplModulePath,
      mocksPattern: this._options.mocksPattern,
      platforms: this._options.platforms,
      retainAllFiles: true,
      rootDir: this._options.rootDir,
      skipPackageJson: this._options.skipPackageJson,
      throwOnModuleCollision: false
    }, this._console, this._workerPool);
    this._watcherDriver = new _watchers.WatcherDriver({
      console: this._console,
      extensions: this._options.extensions,
      ignorePattern: this._options.ignorePattern,
      onError: error => this._console.error(`jest-haste-map: watch error:\n  ${error.stack}\n`),
      roots: this._options.roots,
      useWatchman: await (0, _watchers.shouldUseWatchman)(this._options.useWatchman, this._options.cacheDirectory)
    });
    this._changeQueue = new _ChangeQueue.ChangeQueue(hasteMap, this._options.extensions, {
      cleanup: () => this._workerPool.end(),
      emit: event => this.emit('change', event),
      ignore: filePath => this._ignore(filePath),
      mocksPattern: this._options.mocksPattern,
      onError: error => this._console.error(`jest-haste-map: watch error:\n  ${error.stack}\n`),
      platforms: this._options.platforms,
      processFile: (map, filePath) => this._processFile(map, filePath),
      recoverDuplicates: (map, relPath, name) => this._recoverDuplicates(map, relPath, name),
      rootDir: this._options.rootDir
    });
    this._changeQueue.start();
    try {
      await this._watcherDriver.start((type, filePath, root, stat) => this._changeQueue.onChange(type, filePath, root, stat));
    } catch (error) {
      this._changeQueue.stop();
      throw error;
    }
  }

  /**
   * This function should be called when the file under `filePath` is removed
   * or changed. When that happens, we want to figure out if that file was
   * part of a group of files that had the same ID. If it was, we want to
   * remove it from the group. Furthermore, if there is only one file
   * remaining in the group, then we want to restore that single file as the
   * correct resolution for its ID, and cleanup the duplicates index.
   */
  _recoverDuplicates(hasteMap, relativeFilePath, moduleName) {
    let dupsByPlatform = hasteMap.duplicates.get(moduleName);
    if (dupsByPlatform == null) {
      return;
    }
    const platform = (0, _getPlatformExtension.default)(relativeFilePath, this._options.platforms) || _constants.default.GENERIC_PLATFORM;
    let dups = dupsByPlatform.get(platform);
    if (dups == null) {
      return;
    }
    dupsByPlatform = (0, _util.copyMap)(dupsByPlatform);
    hasteMap.duplicates.set(moduleName, dupsByPlatform);
    dups = (0, _util.copyMap)(dups);
    dupsByPlatform.set(platform, dups);
    dups.delete(relativeFilePath);
    if (dups.size !== 1) {
      return;
    }
    const uniqueModule = dups.entries().next().value;
    if (!uniqueModule) {
      return;
    }
    let dedupMap = hasteMap.map.get(moduleName);
    if (!dedupMap) {
      dedupMap = Object.create(null);
      hasteMap.map.set(moduleName, dedupMap);
    }
    dedupMap[platform] = uniqueModule;
    dupsByPlatform.delete(platform);
    if (dupsByPlatform.size === 0) {
      hasteMap.duplicates.delete(moduleName);
    }
  }
  async end() {
    this._changeQueue?.stop();
    await this._watcherDriver?.close();
  }

  /**
   * Helpers
   */
  _ignore(filePath) {
    return this._ignoreFn(filePath);
  }
  _createEmptyMap() {
    return (0, _util.createEmptyMap)();
  }
  static H = _constants.default;
}
// Export the smallest API surface required by Jest

const JestHasteMap = HasteMap;
var _default = exports["default"] = JestHasteMap;
})();

module.exports = __webpack_exports__;
/******/ })()
;