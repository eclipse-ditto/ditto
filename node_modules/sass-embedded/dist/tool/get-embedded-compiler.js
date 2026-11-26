"use strict";
// Copyright 2022 Google Inc. Use of this source code is governed by an
// MIT-style license that can be found in the LICENSE file or at
// https://opensource.org/licenses/MIT.
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", { value: true });
exports.getEmbeddedCompiler = getEmbeddedCompiler;
const fs_1 = require("fs");
const p = __importStar(require("path"));
const shell = __importStar(require("shelljs"));
const compiler_module_1 = require("../lib/src/compiler-module");
const utils = __importStar(require("./utils"));
/**
 * Downloads and builds the Embedded Dart Sass compiler.
 *
 * Can check out and build the source from a Git `ref` or build from the source
 * at `path`. By default, checks out the latest revision from GitHub.
 *
 * The embedded compiler will be built as dart snapshot by default, or pure node
 * js if the `js` option is `true`.
 */
async function getEmbeddedCompiler(options) {
    const repo = 'dart-sass';
    let source;
    if (options !== undefined && 'path' in options) {
        source = options.path;
    }
    else {
        utils.fetchRepo({
            repo,
            outPath: 'build',
            ref: options?.ref ?? 'main',
        });
        source = p.join('build', repo);
    }
    // Make sure the compiler sees the same version of the language repo that the
    // host is using, but if they're already the same directory (as in the Dart
    // Sass CI environment) we don't need to do anything.
    const languageInHost = p.resolve('build/sass');
    const languageInCompiler = p.resolve(p.join(source, 'build/language'));
    if (!(await utils.sameTarget(languageInHost, languageInCompiler))) {
        await utils.cleanDir(languageInCompiler);
        await utils.link(languageInHost, languageInCompiler);
    }
    const js = options?.js ?? false;
    buildDartSassEmbedded(source, js);
    const jsModulePath = p.resolve('node_modules/sass');
    const dartModulePath = p.resolve(p.join('node_modules', compiler_module_1.compilerModule));
    if (js) {
        await fs_1.promises.rm(dartModulePath, { force: true, recursive: true });
        await utils.link(p.join(source, 'build/npm'), jsModulePath);
    }
    else {
        await fs_1.promises.rm(jsModulePath, { force: true, recursive: true });
        await utils.link(p.join(source, 'build'), p.join(dartModulePath, repo));
    }
}
// Builds the Embedded Dart Sass executable from the source at `repoPath`.
function buildDartSassEmbedded(repoPath, js) {
    console.log("Downloading Dart Sass's dependencies.");
    shell.exec('dart pub upgrade', {
        cwd: repoPath,
        silent: true,
    });
    if (js) {
        shell.exec('npm install', {
            cwd: repoPath,
            silent: true,
        });
        console.log('Building the Dart Sass npm package.');
        shell.exec('dart run grinder protobuf pkg-npm-dev', {
            cwd: repoPath,
            env: { ...process.env, UPDATE_SASS_PROTOCOL: 'false' },
        });
    }
    else {
        console.log('Building the Dart Sass executable.');
        shell.exec('dart run grinder protobuf pkg-standalone-dev', {
            cwd: repoPath,
            env: { ...process.env, UPDATE_SASS_PROTOCOL: 'false' },
        });
    }
}
//# sourceMappingURL=get-embedded-compiler.js.map